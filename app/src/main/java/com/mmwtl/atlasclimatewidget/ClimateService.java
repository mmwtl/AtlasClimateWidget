package com.mmwtl.atlasclimatewidget;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the GInputBridge subscription alive while widgets exist, executes widget controls and
 * redraws the widgets. The bridge answers with implicit broadcasts, which only a registered
 * receiver in a running process can get.
 */
public final class ClimateService extends Service {
    static final String ACTION_START = "com.mmwtl.atlasclimatewidget.START";
    static final String ACTION_REFRESH = "com.mmwtl.atlasclimatewidget.REFRESH";
    static final String ACTION_CONTROL = "com.mmwtl.atlasclimatewidget.CONTROL";
    static final String ACTION_RENDER = "com.mmwtl.atlasclimatewidget.RENDER";

    static final ClimateStore STORE = new ClimateStore();

    private static final String CHANNEL_ID = "climate_bridge";
    private static final int NOTIFICATION_ID = 17;
    private static final long POLL_INTERVAL_MS = 60_000L;
    private static final long RENDER_DELAY_MS = 120L;
    private static final long CONFIRM_FIRST_MS = 800L;
    private static final long CONFIRM_SECOND_MS = 2_500L;
    /**
     * The car confirms a set in 300–450 ms and silently drops another set of the same property
     * that follows within about that time, even right after the confirmation. Later taps wait
     * and only the last value is sent.
     */
    private static final long SET_SPACING_MS = 700L;
    /** An unconfirmed set is sent once more after this delay. */
    private static final long SET_RETRY_MS = 1_000L;
    /** The settings screen keeps the service alive for its preview without widgets. */
    private static final long PREVIEW_KEEP_ALIVE_MS = 10 * 60_000L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Runnable stateListener;
    private static volatile boolean running;
    /** Content last pushed to each widget, see {@link #update}. */
    private static final Map<Integer, Frame> FRAMES = new HashMap<>();
    /** Widget id and strip key of the bar under an open scrubber. */
    private static volatile int scrubbedWidget = AppWidgetManager.INVALID_APPWIDGET_ID;
    private static volatile String scrubbedStrip;

    private HandlerThread workerThread;
    private Handler worker;
    private Prefs prefs;
    private volatile long keepAliveUntil;
    private boolean polling;
    private boolean receiverRegistered;
    private boolean lastConnected;
    /** Worker-thread only: the last set, sent or retried, and the set waiting for it. */
    private final Map<String, Sent> lastSets = new HashMap<>();
    private final Map<String, ClimateCommands.Command> queued = new HashMap<>();

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            poll();
            worker.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    private final Runnable renderTask = () -> renderWidgets(false);

    private final BroadcastReceiver bridgeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onBridgeResult(intent);
        }
    };

    static void start(Context context, String action) {
        Intent intent = new Intent(context, ClimateService.class).setAction(action);
        try {
            context.startForegroundService(intent);
        } catch (RuntimeException error) {
            AppLog.warn("Cannot start climate service", error);
        }
    }

    /** Marks a bar as covered by the scrubber, or clears it with a null key, and redraws. */
    static void setScrubbed(Context context, int widgetId, String stripKey) {
        scrubbedWidget = stripKey == null ? AppWidgetManager.INVALID_APPWIDGET_ID : widgetId;
        scrubbedStrip = stripKey;
        start(context, ACTION_RENDER);
    }

    static boolean isRunning() {
        return running;
    }

    /** Called on the main thread after every state change while the listener is set. */
    static void setStateListener(Runnable listener) {
        stateListener = listener;
    }

    static int[] widgetIds(Context context) {
        try {
            return AppWidgetManager.getInstance(context).getAppWidgetIds(
                    new ComponentName(context, ClimateWidgetProvider.class));
        } catch (RuntimeException error) {
            return new int[0];
        }
    }

    static WidgetViews.Size widgetSize(Context context, int widgetId) {
        float density = context.getResources().getDisplayMetrics().density;
        Bundle options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId);
        int widthDp = options == null ? 0
                : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        int heightDp = options == null ? 0
                : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        if (widthDp <= 0) {
            widthDp = WidgetGeometry.DEFAULT_WIDTH_DP;
        }
        // Hosts report the content area, already without the default widget padding.
        return new WidgetViews.Size(Math.round(widthDp * density),
                Math.round(Math.max(0, heightDp) * density));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        prefs = new Prefs(this);
        startInForeground();
        workerThread = new HandlerThread("climate-bridge");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
        registerBridgeReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startInForeground();
        String action = intent == null ? null : intent.getAction();
        if (ACTION_START.equals(action)) {
            keepAliveUntil = SystemClock.elapsedRealtime() + PREVIEW_KEEP_ALIVE_MS;
        }
        if (!polling) {
            // The first poll decides whether to stop, so it runs after keepAliveUntil is set.
            polling = true;
            worker.post(pollTask);
        }
        if (ACTION_CONTROL.equals(action)) {
            Uri data = intent.getData();
            worker.post(() -> handleControl(data));
        } else if (ACTION_RENDER.equals(action)) {
            worker.post(renderTask);
        } else if (ACTION_REFRESH.equals(action) || ACTION_START.equals(action)) {
            worker.post(() -> {
                poll();
                renderWidgets(true);
            });
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (receiverRegistered) {
            try {
                unregisterReceiver(bridgeReceiver);
            } catch (IllegalArgumentException ignored) {
                // Already detached during process teardown.
            }
            receiverRegistered = false;
        }
        if (worker != null) {
            worker.removeCallbacksAndMessages(null);
        }
        if (workerThread != null) {
            workerThread.quitSafely();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---- bridge ------------------------------------------------------------------------------

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerBridgeReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Gib.PROPERTY_INT_RESULT);
        filter.addAction(Gib.PROPERTY_INT_CHANGED);
        filter.addAction(Gib.PROPERTY_FLOAT_RESULT);
        filter.addAction(Gib.PROPERTY_FLOAT_CHANGED);
        filter.addAction(Gib.SENSOR_FLOAT_RESULT);
        filter.addAction(Gib.SENSOR_FLOAT_CHANGED);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(bridgeReceiver, filter, null, worker,
                        Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(bridgeReceiver, filter, null, worker);
            }
            receiverRegistered = true;
        } catch (RuntimeException error) {
            AppLog.warn("Cannot register GInputBridge receiver", error);
        }
    }

    private void onBridgeResult(Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            return;
        }
        Integer id = Gib.parseInt(Gib.extra(intent, Gib.EXTRA_ID));
        String rawValue = Gib.extra(intent, Gib.EXTRA_VALUE);
        if (id == null) {
            return;
        }
        if (AppLog.debugEnabled()) {
            AppLog.debug("<- " + action.substring(action.lastIndexOf('.') + 1) + " id=" + id
                    + " area=" + Gib.extra(intent, Gib.EXTRA_AREA) + " value=" + rawValue);
        }
        long now = SystemClock.elapsedRealtime();
        if (action.equals(Gib.SENSOR_FLOAT_RESULT) || action.equals(Gib.SENSOR_FLOAT_CHANGED)) {
            if (id != Hvac.SENSOR_TEMPERATURE_INDOOR && id != Hvac.SENSOR_TEMPERATURE_AMBIENT) {
                return;
            }
            Float value = Gib.parseFloat(rawValue);
            if (value != null) {
                STORE.putSensor(id, value, now);
            }
        } else {
            Integer area = Gib.parseInt(Gib.extra(intent, Gib.EXTRA_AREA));
            Float value = Gib.parseFloat(rawValue);
            if (value == null) {
                return;
            }
            boolean floatAction = action.equals(Gib.PROPERTY_FLOAT_RESULT)
                    || action.equals(Gib.PROPERTY_FLOAT_CHANGED);
            // Integer ids exceed float precision, so parse them as integers.
            double parsed = floatAction ? value : integerValue(rawValue, value);
            int zone = area == null ? Gib.AREA_GLOBAL : area;
            STORE.putProperty(id, zone, parsed, now);
            Sent sent = lastSets.get(ClimateStore.propertyKey(id, zone));
            if (sent != null && sent.command.value == parsed) {
                sent.confirmed = true;
            }
        }
        scheduleRender();
    }

    private static double integerValue(String raw, float fallback) {
        Integer exact = Gib.parseInt(raw);
        return exact == null ? fallback : exact;
    }

    private void poll() {
        if (shouldStop()) {
            stopSelf();
            return;
        }
        WatchList watch = WatchList.of(configs(), prefs.carModel());
        for (WatchList.Key key : watch.properties) {
            Gib.listenProperty(this, key.id, key.area);
            request(key.id, key.area, key.isFloat);
        }
        for (Integer sensor : watch.sensors) {
            Gib.listenSensor(this, sensor);
            Gib.getFloatSensor(this, sensor);
        }
        scheduleRender();
    }

    private void request(int id, int area, boolean isFloat) {
        if (isFloat) {
            Gib.getFloat(this, id, area);
        } else {
            Gib.getInt(this, id, area);
        }
    }

    private boolean shouldStop() {
        return widgetIds(this).length == 0 && SystemClock.elapsedRealtime() > keepAliveUntil;
    }

    private List<WidgetConfig> configs() {
        List<WidgetConfig> configs = new ArrayList<>();
        for (int id : widgetIds(this)) {
            configs.add(prefs.widget(id));
        }
        if (SystemClock.elapsedRealtime() <= keepAliveUntil) {
            configs.add(prefs.template());
        }
        return configs;
    }

    // ---- controls ----------------------------------------------------------------------------

    private void handleControl(Uri data) {
        if (data == null || !WidgetViews.CONTROL_SCHEME.equals(data.getScheme())) {
            return;
        }
        AppLog.info("Control " + data.getPath());
        List<String> parts = data.getPathSegments();
        if (parts.isEmpty()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        ClimateState state = STORE.snapshot(now);
        CarModel model = prefs.carModel();
        List<ClimateCommands.Command> commands;
        try {
            commands = commands(parts, state, model);
        } catch (RuntimeException error) {
            AppLog.warn("Ignoring malformed control " + data, error);
            return;
        }
        for (ClimateCommands.Command command : commands) {
            execute(command, now);
        }
        renderWidgets(false);
    }

    private List<ClimateCommands.Command> commands(List<String> parts, ClimateState state,
            CarModel model) {
        String type = parts.get(0);
        switch (type) {
            case "fn": {
                ClimateFunction function = ClimateFunction.fromName(parts.get(1));
                if (function == null) {
                    return new ArrayList<>();
                }
                return ClimateCommands.press(function, state, model, prefs.levelsFromMax());
            }
            case "temp": {
                int zone = Integer.parseInt(parts.get(1));
                int step = Integer.parseInt(parts.get(2));
                float value = ClimateCommands.tempRange(state).valueAt(step);
                return ClimateCommands.setTemperature(state, zone, value);
            }
            case "tempstep":
                return ClimateCommands.stepTemperature(state, Integer.parseInt(parts.get(1)),
                        Integer.parseInt(parts.get(2)), prefs.temperatureStep());
            case "fan":
                return ClimateCommands.setFan(Integer.parseInt(parts.get(1)));
            case "fanpreset":
                return ClimateCommands.setFanPreset(Integer.parseInt(parts.get(1)),
                        prefs.fanPresetCount());
            case "fanstep":
                return ClimateCommands.stepFan(state, model, Integer.parseInt(parts.get(1)),
                        prefs.fanPresetCount());
            default:
                return new ArrayList<>();
        }
    }

    private void execute(ClimateCommands.Command command, long now) {
        if (command.type == ClimateCommands.Command.Type.CAR_FUNCTION) {
            Gib.carFunction(this, command.function);
            return;
        }
        if (!command.isOptimistic()) {
            // Blower nudges add up, so none of them may be replaced by a later one.
            send(command, now);
            return;
        }
        STORE.setPending(command.id, command.area, command.value, now);
        String key = ClimateStore.propertyKey(command.id, command.area);
        if (queued.containsKey(key) || isSpacing(key, now)) {
            queued.put(key, command);
            worker.postDelayed(() -> sendQueued(key), SET_SPACING_MS);
            return;
        }
        send(command, now);
    }

    private void send(ClimateCommands.Command command, long now) {
        if (command.type == ClimateCommands.Command.Type.FLOAT) {
            Gib.setFloat(this, command.id, command.area, (float) command.value);
        } else {
            Gib.setInt(this, command.id, command.area, (int) command.value);
        }
        if (command.isOptimistic()) {
            // Restart the optimistic window: a queued value leaves later than it was chosen.
            STORE.setPending(command.id, command.area, command.value, now);
            String key = ClimateStore.propertyKey(command.id, command.area);
            Sent previous = lastSets.get(key);
            boolean retry = previous != null && previous.command == command;
            Sent sent = new Sent(command, now);
            lastSets.put(key, sent);
            if (!retry) {
                worker.postDelayed(() -> retry(key, sent), SET_RETRY_MS);
            }
        }
        boolean isFloat = command.type == ClimateCommands.Command.Type.FLOAT;
        int id = command.id == ClimateCommands.FAN_BLOWER ? Hvac.FAN_SPEED : command.id;
        Runnable confirm = () -> {
            request(id, command.area, isFloat);
            if (command.area != Gib.AREA_GLOBAL) {
                request(id, Gib.AREA_GLOBAL, isFloat);
            }
        };
        worker.postDelayed(confirm, CONFIRM_FIRST_MS);
        worker.postDelayed(confirm, CONFIRM_SECOND_MS);
        // An unconfirmed optimistic value must disappear when it expires, not at the next poll.
        worker.postDelayed(this::scheduleRender, ClimateStore.PENDING_TIMEOUT_MS + 50L);
    }

    private boolean isSpacing(String key, long now) {
        Sent sent = lastSets.get(key);
        return sent != null && now - sent.at < SET_SPACING_MS;
    }

    private void sendQueued(String key) {
        long now = SystemClock.elapsedRealtime();
        if (!queued.containsKey(key) || isSpacing(key, now)) {
            return;
        }
        send(queued.remove(key), now);
    }

    /** Sends a set the car has not confirmed once more, unless a newer one replaced it. */
    private void retry(String key, Sent sent) {
        if (lastSets.get(key) != sent || sent.confirmed || queued.containsKey(key)) {
            return;
        }
        AppLog.info("Resending unconfirmed " + key);
        send(sent.command, SystemClock.elapsedRealtime());
    }

    // ---- rendering ---------------------------------------------------------------------------

    private void scheduleRender() {
        worker.removeCallbacks(renderTask);
        worker.postDelayed(renderTask, RENDER_DELAY_MS);
    }

    /** @param force push even unchanged content, as after a settings change */
    private void renderWidgets(boolean force) {
        worker.removeCallbacks(renderTask);
        ClimateState state = STORE.snapshot(SystemClock.elapsedRealtime());
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        for (int widgetId : widgetIds(this)) {
            update(this, manager, prefs, state, widgetId, force);
        }
        if (state.isConnected() != lastConnected) {
            lastConnected = state.isConnected();
            startInForeground();
        }
        Runnable listener = stateListener;
        if (listener != null) {
            MAIN.post(listener);
        }
    }

    static void update(Context context, AppWidgetManager manager, Prefs prefs,
            ClimateState state, int widgetId) {
        update(context, manager, prefs, state, widgetId, true);
    }

    /**
     * Pushes the widget's views unless they match the last push. The cabin sensor reports
     * tenths several times a second while the widget shows whole degrees, and every push
     * replaces the touch cells in the host, which cancels a tap in progress.
     */
    static void update(Context context, AppWidgetManager manager, Prefs prefs,
            ClimateState state, int widgetId, boolean force) {
        WidgetConfig config = prefs.widget(widgetId);
        WidgetViews.Size size = widgetSize(context, widgetId);
        String scrubbed = widgetId == scrubbedWidget ? scrubbedStrip : null;
        String key = config.toJson() + "|" + size.widthPx + "x" + size.heightPx + "|" + scrubbed;
        float quality = 1f;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                List<Bitmap> bitmaps = new ArrayList<>();
                RemoteViews views = WidgetViews.build(context, widgetId, config, state,
                        prefs.carModel(), size, true, quality, scrubbed, bitmaps);
                Frame frame = new Frame(key, bitmaps);
                synchronized (FRAMES) {
                    if (!force && frame.sameAs(FRAMES.get(widgetId))) {
                        return;
                    }
                }
                if (AppLog.debugEnabled()) {
                    AppLog.debug("push widget " + widgetId + (force ? " (forced)" : ""));
                }
                manager.updateAppWidget(widgetId, views);
                synchronized (FRAMES) {
                    FRAMES.put(widgetId, frame);
                }
                return;
            } catch (RuntimeException error) {
                AppLog.warn("Widget " + widgetId + " update failed at quality " + quality, error);
                quality *= 0.6f;
            }
        }
    }

    static void forget(int widgetId) {
        synchronized (FRAMES) {
            FRAMES.remove(widgetId);
        }
    }

    private static final class Frame {
        final String key;
        final List<Bitmap> bitmaps;

        Frame(String key, List<Bitmap> bitmaps) {
            this.key = key;
            this.bitmaps = bitmaps;
        }

        boolean sameAs(Frame other) {
            if (other == null || !key.equals(other.key)
                    || bitmaps.size() != other.bitmaps.size()) {
                return false;
            }
            for (int index = 0; index < bitmaps.size(); index++) {
                if (!bitmaps.get(index).sameAs(other.bitmaps.get(index))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class Sent {
        final ClimateCommands.Command command;
        final long at;
        boolean confirmed;

        Sent(ClimateCommands.Command command, long at) {
            this.command = command;
            this.at = at;
        }
    }

    // ---- notification ------------------------------------------------------------------------

    private void startInForeground() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.notification_channel), NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean connected = STORE.snapshot(SystemClock.elapsedRealtime()).isConnected();
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(connected
                        ? R.string.notification_connected
                        : R.string.notification_waiting))
                .setContentIntent(open)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (RuntimeException error) {
            AppLog.warn("Cannot enter foreground", error);
        }
    }
}
