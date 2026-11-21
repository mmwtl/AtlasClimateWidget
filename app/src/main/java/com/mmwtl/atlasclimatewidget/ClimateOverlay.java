package com.mmwtl.atlasclimatewidget;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The HOME-only climate surface. ClimateService owns its lifecycle and the car connection. */
final class ClimateOverlay implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final long PERMISSION_RECHECK_MS = 2_000;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    // The owner clears the reference on a mode switch and in onDestroy.
    @SuppressLint("StaticFieldLeak")
    private static ClimateOverlay active;

    private final ClimateService service;
    private final Prefs prefs;
    private final Runnable poll = () -> checkVisibility(false);
    private final Runnable fastPoll = () -> checkVisibility(true);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            runnable -> new Thread(runnable, "climate-foreground"));
    private final BroadcastReceiver wakeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!detector.isDeviceReady()) {
                hide();
            }
            requestCheck(false);
        }
    };

    private ForegroundAppDetector detector;
    private Context windowContext;
    private WindowManager manager;
    private InteractiveClimateView view;
    private ClimateService.PanelHost host;
    private WindowManager.LayoutParams attachedParams;
    private long fastProbeUntil;
    private boolean queryInFlight;
    private boolean checkPending;
    private boolean pendingFast;
    private boolean deviceWasReady;

    ClimateOverlay(ClimateService service, Prefs prefs) {
        this.service = service;
        this.prefs = prefs;
        prefs.raw().registerOnSharedPreferenceChangeListener(this);
    }

    static boolean hasPermissions(Context context) {
        return Settings.canDrawOverlays(context) && ForegroundAppDetector.hasUsageAccess(context)
                && AccessibilityWindowState.isEnabled(context);
    }

    static void onAccessibilityWindowsChanged() {
        // The accessibility reader publishes from a worker thread.
        MAIN.post(() -> {
            if (active != null) {
                active.requestCheck(true);
            }
        });
    }

    @SuppressWarnings("deprecation")
    void syncMode() {
        if (prefs.isWidgetMode()) {
            stopTracking();
        } else if (active != this) {
            active = this;
            detector = new ForegroundAppDetector(service);
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            filter.addAction(Intent.ACTION_USER_UNLOCKED);
            if (Build.VERSION.SDK_INT >= 33) {
                service.registerReceiver(wakeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                service.registerReceiver(wakeReceiver, filter);
            }
            requestCheck(false);
        }
    }

    private void stopTracking() {
        if (active == this) {
            active = null;
            service.unregisterReceiver(wakeReceiver);
            MAIN.removeCallbacks(poll);
            MAIN.removeCallbacks(fastPoll);
            detector = null;
            deviceWasReady = false;
            queryInFlight = false;
            checkPending = false;
            hide();
            windowContext = null;
            manager = null;
        }
    }

    void destroy() {
        prefs.raw().unregisterOnSharedPreferenceChangeListener(this);
        stopTracking();
        executor.shutdownNow();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences values, String key) {
        if (Prefs.KEY_DISPLAY_MODE.equals(key)) {
            syncMode();
        } else if (key == null || key.startsWith("overlay_") || Prefs.isWidgetKey(key)
                || Prefs.KEY_FAN_PRESETS.equals(key)) {
            requestCheck(true);
        }
    }

    void onConfigurationChanged() {
        requestCheck(false);
    }

    private void requestCheck(boolean accessibilityEvent) {
        if (active != this) {
            return;
        }
        fastProbeUntil = SystemClock.elapsedRealtime() + ForegroundPollPolicy.FAST_PROBE_DURATION_MS;
        MAIN.removeCallbacks(poll);
        MAIN.removeCallbacks(fastPoll);
        if (queryInFlight) {
            pendingFast = checkPending ? pendingFast && accessibilityEvent : accessibilityEvent;
            checkPending = true;
        } else {
            MAIN.post(accessibilityEvent ? fastPoll : poll);
        }
    }

    private void schedulePoll(long delay) {
        MAIN.removeCallbacks(poll);
        MAIN.removeCallbacks(fastPoll);
        MAIN.postDelayed(poll, delay);
    }

    private void checkVisibility(boolean accessibilityFast) {
        if (active != this) {
            return;
        }
        if (!hasPermissions(service)) {
            hide();
            schedulePoll(PERMISSION_RECHECK_MS);
            return;
        }
        long now = SystemClock.elapsedRealtime();
        ForegroundAppDetector current = detector;
        boolean ready = current.isDeviceReady();
        if (ready && !deviceWasReady) {
            fastProbeUntil = now + ForegroundPollPolicy.FAST_PROBE_DURATION_MS;
        }
        deviceWasReady = ready;
        if (!ready) {
            hide();
            schedulePoll(ForegroundPollPolicy.nextDelay(false, false, now, fastProbeUntil));
            return;
        }
        queryInFlight = true;
        executor.execute(() -> {
            boolean visible = false;
            try {
                Boolean snapshot = accessibilityFast ? current.isHomeVisibleFromAccessibility() : null;
                visible = snapshot != null ? snapshot : current.isHomeVisible();
            } catch (RuntimeException error) {
                AppLog.warnRateLimited("climate-overlay-home", "HOME visibility query failed", error);
            }
            boolean result = visible;
            MAIN.post(() -> applyVisibility(current, result));
        });
    }

    private void applyVisibility(ForegroundAppDetector current, boolean homeVisible) {
        if (detector != current) {
            return;
        }
        queryInFlight = false;
        if (checkPending) {
            checkPending = false;
            MAIN.post(pendingFast ? fastPoll : poll);
            return;
        }
        boolean ready = current.isDeviceReady();
        if (homeVisible && ready && hasPermissions(service)) {
            show();
        } else {
            hide();
        }
        schedulePoll(ForegroundPollPolicy.nextDelay(homeVisible, ready,
                SystemClock.elapsedRealtime(), fastProbeUntil));
    }

    /** The preview and the actual window both size the layout in the display's safe bounds. */
    static WidgetViews.Size size(Context context, Rect bounds, Prefs prefs,
            WidgetConfig config, ClimateState state) {
        float density = context.getResources().getDisplayMetrics().density;
        prefs.migrateOverlaySize(bounds.width(), bounds.height());
        int width = prefs.overlayWidthPx(bounds.width());
        int heightPx = prefs.overlayHeightPx(bounds.height());
        if (heightPx > 0) {
            return new WidgetViews.Size(width, heightPx);
        }
        WidgetGeometry.Plan plan = WidgetGeometry.plan(config, width, 0, density,
                ClimateCommands.tempRange(state).steps());
        int height = plan.strips.isEmpty() ? Math.round(56 * density)
                : (int) Math.ceil(plan.totalHeight());
        return new WidgetViews.Size(width, Math.max(1, Math.min(bounds.height(), height)));
    }

    static Rect availableBounds(WindowManager manager) {
        WindowMetrics metrics = manager.getCurrentWindowMetrics();
        Rect display = metrics.getBounds();
        Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        Rect safe = new Rect(display.left + insets.left, display.top + insets.top,
                display.right - insets.right, display.bottom - insets.bottom);
        return safe.isEmpty() ? display : safe;
    }

    @SuppressLint("RtlHardcoded") // The selected corner is a physical edge of the display.
    private void show() {
        try {
            if (windowContext == null) {
                Display display = service.getSystemService(DisplayManager.class)
                        .getDisplay(Display.DEFAULT_DISPLAY);
                windowContext = service.createDisplayContext(display)
                        .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
                manager = windowContext.getSystemService(WindowManager.class);
            }
            Rect bounds = availableBounds(manager);
            WidgetViews.Size size = size(windowContext, bounds, prefs,
                    prefs.widget(Prefs.OVERLAY_ID), ClimateService.STORE.snapshot(SystemClock.elapsedRealtime()));
            float density = windowContext.getResources().getDisplayMetrics().density;
            OverlayGeometry.Position position = OverlayGeometry.positionFor(prefs.overlayCorner(),
                    bounds.left, bounds.top, bounds.right, bounds.bottom, size.widthPx, size.heightPx,
                    Math.round(prefs.overlayOffsetDp(Prefs.KEY_OVERLAY_X) * density),
                    Math.round(prefs.overlayOffsetDp(Prefs.KEY_OVERLAY_Y) * density));
            int x = position.x() - bounds.left;
            int y = position.y() - bounds.top;
            boolean first = view == null;
            boolean resized = first || attachedParams.width != size.widthPx
                    || attachedParams.height != size.heightPx;
            if (!resized && attachedParams.x == x && attachedParams.y == y) {
                return;
            }
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    size.widthPx, size.heightPx, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.x = x;
            params.y = y;
            params.setTitle(service.getString(R.string.overlay_title));
            if (first) {
                InteractiveClimateView next = new InteractiveClimateView(windowContext);
                manager.addView(next, params);
                view = next;
                host = next::show;
            } else {
                manager.updateViewLayout(view, params);
            }
            attachedParams = params;
            if (resized) {
                service.setOverlay(host, size);
            }
        } catch (RuntimeException error) {
            AppLog.warnRateLimited("climate-overlay", "Cannot show the climate overlay", error);
            hide();
        }
    }

    private void hide() {
        if (view == null) {
            return;
        }
        service.setOverlay(host, null);
        view.pauseControls();
        try {
            manager.removeViewImmediate(view);
        } catch (IllegalArgumentException error) {
            AppLog.warn("Climate overlay already detached", error);
        }
        view = null;
        host = null;
        attachedParams = null;
    }
}
