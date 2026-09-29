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
import java.util.List;

/**
 * Keeps the GInputBridge subscription alive while widgets exist, executes widget controls and
 * redraws the widgets. The bridge answers with implicit broadcasts, which only a registered
 * receiver in a running process can get.
 */
public final class ClimateService extends Service {
    static final String ACTION_START = "com.mmwtl.atlasclimatewidget.START";
    static final String ACTION_REFRESH = "com.mmwtl.atlasclimatewidget.REFRESH";
    static final String ACTION_CONTROL = "com.mmwtl.atlasclimatewidget.CONTROL";

    static final ClimateStore STORE = new ClimateStore();

    private static final String CHANNEL_ID = "climate_bridge";
    private static final int NOTIFICATION_ID = 17;
    private static final long POLL_INTERVAL_MS = 60_000L;
    private static final long RENDER_DELAY_MS = 120L;
    private static final long CONFIRM_FIRST_MS = 800L;
    private static final long CONFIRM_SECOND_MS = 2_500L;
    /** The settings screen keeps the service alive for its preview without widgets. */
    private static final long PREVIEW_KEEP_ALIVE_MS = 10 * 60_000L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Runnable stateListener;
    private static volatile boolean running;

    private HandlerThread workerThread;
    private Handler worker;
    private Prefs prefs;
    private volatile long keepAliveUntil;
    private boolean polling;
    private boolean receiverRegistered;
    private boolean lastConnected;

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            poll();
            worker.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    private final Runnable renderTask = this::renderWidgets;

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
        } else if (ACTION_REFRESH.equals(action) || ACTION_START.equals(action)) {
            worker.post(() -> {
                poll();
                renderWidgets();
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
            STORE.putProperty(id, area == null ? Gib.AREA_GLOBAL : area, parsed, now);
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
        renderWidgets();
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
            case "fanstep":
                return ClimateCommands.stepFan(state, model, Integer.parseInt(parts.get(1)));
            default:
                return new ArrayList<>();
        }
    }

    private void execute(ClimateCommands.Command command, long now) {
        switch (command.type) {
            case CAR_FUNCTION:
                Gib.carFunction(this, command.function);
                return;
            case FLOAT:
                Gib.setFloat(this, command.id, command.area, (float) command.value);
                break;
            default:
                Gib.setInt(this, command.id, command.area, (int) command.value);
                break;
        }
        if (command.isOptimistic()) {
            STORE.setPending(command.id, command.area, command.value, now);
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
    }

    // ---- rendering ---------------------------------------------------------------------------

    private void scheduleRender() {
        worker.removeCallbacks(renderTask);
        worker.postDelayed(renderTask, RENDER_DELAY_MS);
    }

    private void renderWidgets() {
        worker.removeCallbacks(renderTask);
        ClimateState state = STORE.snapshot(SystemClock.elapsedRealtime());
        updateAll(this, prefs, state);
        if (state.isConnected() != lastConnected) {
            lastConnected = state.isConnected();
            startInForeground();
        }
        Runnable listener = stateListener;
        if (listener != null) {
            MAIN.post(listener);
        }
    }

    static void updateAll(Context context, Prefs prefs, ClimateState state) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (int widgetId : widgetIds(context)) {
            update(context, manager, prefs, state, widgetId);
        }
    }

    static void update(Context context, AppWidgetManager manager, Prefs prefs,
            ClimateState state, int widgetId) {
        WidgetConfig config = prefs.widget(widgetId);
        WidgetViews.Size size = widgetSize(context, widgetId);
        float quality = 1f;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                RemoteViews views = WidgetViews.build(context, config, state, prefs.carModel(),
                        size, true, quality);
                manager.updateAppWidget(widgetId, views);
                return;
            } catch (RuntimeException error) {
                AppLog.warn("Widget " + widgetId + " update failed at quality " + quality, error);
                quality *= 0.6f;
            }
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
