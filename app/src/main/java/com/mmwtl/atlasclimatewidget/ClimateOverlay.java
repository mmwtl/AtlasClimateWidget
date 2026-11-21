package com.mmwtl.atlasclimatewidget;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.FrameLayout;
import android.widget.TextView;

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
            if (detector == null || !detector.isDeviceReady()) {
                hide();
            }
            requestCheck(false);
        }
    };

    private ForegroundAppDetector detector;
    private Context windowContext;
    private WindowManager manager;
    private FrameLayout container;
    private InteractiveClimateView view;
    private TextView dragHandle;
    private ClimateService.PanelHost host;
    private WindowManager.LayoutParams attachedParams;
    private long fastProbeUntil;
    private boolean queryInFlight;
    private boolean checkPending;
    private boolean pendingFast;
    private boolean deviceWasReady;
    private boolean hadPermissions = true;
    private boolean dragging;
    private float dragStartRawX;
    private float dragStartRawY;
    private int dragStartWindowX;
    private int dragStartWindowY;

    ClimateOverlay(ClimateService service, Prefs prefs) {
        this.service = service;
        this.prefs = prefs;
        prefs.raw().registerOnSharedPreferenceChangeListener(this);
    }

    static boolean isTracking() {
        return active != null;
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
        if (prefs.isWidgetMode() || !prefs.overlayEnabled()) {
            stopTracking();
            service.updateNotification();
        } else if (active != this) {
            active = this;
            hadPermissions = hasPermissions(service);
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
            service.updateNotification();
            requestCheck(false);
        }
    }

    private void stopTracking() {
        if (active == this) {
            active = null;
            try {
                service.unregisterReceiver(wakeReceiver);
            } catch (IllegalArgumentException ignored) {
                // Already detached during teardown.
            }
            MAIN.removeCallbacks(poll);
            MAIN.removeCallbacks(fastPoll);
            detector = null;
            deviceWasReady = false;
            queryInFlight = false;
            checkPending = false;
            dragging = false;
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
        if (Prefs.KEY_DISPLAY_MODE.equals(key) || Prefs.KEY_OVERLAY_ENABLED.equals(key)) {
            syncMode();
        } else if (Prefs.KEY_OVERLAY_DRAG_HANDLE.equals(key)) {
            updateDragHandleVisibility();
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
        boolean permitted = hasPermissions(service);
        if (permitted != hadPermissions) {
            hadPermissions = permitted;
            service.updateNotification();
        }
        if (!permitted) {
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

    /** Safe bounds of the full display, unaffected by an Activity's freeform/split bounds. */
    static Rect displayBounds(Context context) {
        return availableBounds(context.getSystemService(WindowManager.class));
    }

    static Rect availableBounds(WindowManager manager) {
        WindowMetrics metrics = manager.getMaximumWindowMetrics();
        Rect display = metrics.getBounds();
        Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        Rect safe = new Rect(display.left + insets.left, display.top + insets.top,
                display.right - insets.right, display.bottom - insets.bottom);
        return safe.isEmpty() ? display : safe;
    }

    @SuppressLint("RtlHardcoded") // The selected corner is a physical edge of the display.
    private void show() {
        if (dragging) {
            return;
        }
        try {
            if (windowContext == null) {
                Display display = service.getSystemService(DisplayManager.class)
                        .getDisplay(Display.DEFAULT_DISPLAY);
                windowContext = service.createDisplayContext(display)
                        .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
                manager = windowContext.getSystemService(WindowManager.class);
            }
            Rect bounds = availableBounds(manager);
            float density = windowContext.getResources().getDisplayMetrics().density;
            prefs.migrateOverlayOffsets(density);
            ClimateState state = ClimateService.STORE.snapshot(SystemClock.elapsedRealtime());
            WidgetViews.Size size = size(windowContext, bounds, prefs,
                    prefs.widget(Prefs.OVERLAY_ID), state);
            OverlayGeometry.Position position = OverlayGeometry.positionFor(prefs.overlayCorner(),
                    bounds.left, bounds.top, bounds.right, bounds.bottom, size.widthPx, size.heightPx,
                    prefs.overlayOffsetXPx(density), prefs.overlayOffsetYPx(density));
            int x = position.x() - bounds.left;
            int y = position.y() - bounds.top;
            boolean first = container == null;
            boolean resized = first || attachedParams.width != size.widthPx
                    || attachedParams.height != size.heightPx;
            if (!resized && attachedParams.x == x && attachedParams.y == y) {
                updateDragHandleVisibility();
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
                FrameLayout nextContainer = new FrameLayout(windowContext);
                InteractiveClimateView nextView = new InteractiveClimateView(windowContext);
                nextContainer.addView(nextView, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
                TextView handle = buildDragHandle(windowContext, density);
                nextContainer.addView(handle);
                // Render the last known state synchronously before attaching so HOME never flashes empty.
                ClimateService.PanelFrame initial = ClimateService.buildOverlay(
                        WidgetTheme.themed(service), prefs, state, size, null, 0);
                if (initial != null) {
                    nextView.show(initial);
                }
                manager.addView(nextContainer, params);
                container = nextContainer;
                view = nextView;
                dragHandle = handle;
                host = nextView::show;
                updateDragHandleVisibility();
            } else {
                manager.updateViewLayout(container, params);
                updateDragHandleVisibility();
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

    @SuppressLint({"ClickableViewAccessibility", "RtlHardcoded"})
    private TextView buildDragHandle(Context context, float density) {
        TextView handle = new TextView(context);
        handle.setText("⋮");
        handle.setTextSize(16);
        handle.setTypeface(Typeface.DEFAULT_BOLD);
        handle.setTextColor(0xE6FFFFFF);
        handle.setGravity(Gravity.CENTER);
        handle.setContentDescription(service.getString(R.string.overlay_drag_handle_description));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0x99171717);
        background.setCornerRadius(10 * density);
        background.setStroke(Math.max(1, Math.round(density)), 0x44FFFFFF);
        handle.setBackground(background);
        int width = Math.round(32 * density);
        int height = Math.round(32 * density);
        int margin = Math.round(6 * density);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                width, height, Gravity.TOP | Gravity.RIGHT);
        params.topMargin = margin;
        params.rightMargin = margin;
        handle.setLayoutParams(params);
        handle.setOnTouchListener(this::onHandleTouch);
        return handle;
    }

    private void updateDragHandleVisibility() {
        if (dragHandle != null) {
            dragHandle.setVisibility(prefs.overlayDragHandle() ? View.VISIBLE : View.GONE);
        }
    }

    private boolean onHandleTouch(View handle, MotionEvent event) {
        if (container == null || attachedParams == null || manager == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                dragging = true;
                dragStartRawX = event.getRawX();
                dragStartRawY = event.getRawY();
                dragStartWindowX = attachedParams.x;
                dragStartWindowY = attachedParams.y;
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    return false;
                }
                Rect bounds = availableBounds(manager);
                int maxX = Math.max(0, bounds.width() - attachedParams.width);
                int maxY = Math.max(0, bounds.height() - attachedParams.height);
                attachedParams.x = clamp(dragStartWindowX + Math.round(event.getRawX() - dragStartRawX),
                        0, maxX);
                attachedParams.y = clamp(dragStartWindowY + Math.round(event.getRawY() - dragStartRawY),
                        0, maxY);
                try {
                    manager.updateViewLayout(container, attachedParams);
                } catch (IllegalArgumentException error) {
                    AppLog.warnRateLimited("climate-overlay-drag",
                            "Cannot update overlay position", error);
                    dragging = false;
                    return false;
                }
                return true;
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) {
                    return false;
                }
                dragging = false;
                Rect bounds = availableBounds(manager);
                OverlayCorner corner = prefs.overlayCorner();
                OverlayGeometry.Offset offsets = OverlayGeometry.offsetsFor(corner,
                        bounds.left, bounds.top, bounds.right, bounds.bottom,
                        attachedParams.width, attachedParams.height,
                        attachedParams.x + bounds.left, attachedParams.y + bounds.top);
                prefs.setOverlayPosition(corner, offsets.x(), offsets.y());
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void hide() {
        dragging = false;
        FrameLayout target = container;
        if (target == null) {
            return;
        }
        service.setOverlay(host, null);
        if (view != null) {
            view.pauseControls();
        }
        // Hide synchronously so a transient removal error cannot leave a clickable orphan.
        target.setVisibility(View.GONE);
        if (manager != null) {
            try {
                manager.removeViewImmediate(target);
            } catch (IllegalArgumentException error) {
                AppLog.warn("Climate overlay already detached", error);
            } catch (IllegalStateException error) {
                AppLog.warnRateLimited("climate-overlay-remove",
                        "Cannot remove climate overlay", error);
            }
        }
        container = null;
        view = null;
        dragHandle = null;
        host = null;
        attachedParams = null;
    }
}
