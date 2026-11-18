package com.mmwtl.atlasclimatewidget;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RemoteViews;

/**
 * The climate window: a large climate panel over whatever app is open, meant for a steering
 * wheel button (GInputBridge «Запуск ярлыка» → «Окно климата», see {@link PanelShortcutActivity}).
 *
 * <p>The window is a widget with its own layout ({@link Prefs#PANEL_ID}): the service draws it
 * with the same {@link WidgetViews} and this activity applies the views in place, so taps go
 * through the widget's PendingIntents and bars open {@link ScrubActivity} over the window. Another
 * start while the window is open closes it, so one button toggles it. The window closes on Back,
 * on a tap outside, when another app comes up and after the configured time without a touch.
 */
public final class ClimatePanelActivity extends Activity {
    /** Public action for launchers and key mappers; the shortcut starts the same component. */
    static final String ACTION_SHOW = "com.mmwtl.atlasclimatewidget.action.CLIMATE_WINDOW";
    /** The window never covers the whole height, so a tap outside can still close it. */
    private static final float MAX_HEIGHT_SHARE = 0.9f;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable idleClose = this::finish;
    private final ClimateService.PanelHost host = this::show;

    private FrameLayout container;
    private Prefs prefs;

    static Intent intent(Context context) {
        return new Intent(ACTION_SHOW).setClass(context, ClimatePanelActivity.class);
    }

    /**
     * Window size in the application's pixels, which the widget renderer uses: the configured
     * share of the screen width and the layout's natural height, capped below the screen
     * height (the layout then squeezes as in a short widget cell).
     */
    static WidgetViews.Size size(Context context, Prefs prefs, ClimateState state) {
        return size(context, prefs.panelWidthPercent(), prefs.widget(Prefs.PANEL_ID), state);
    }

    static WidgetViews.Size size(Context context, int widthPercent, WidgetConfig config,
            ClimateState state) {
        Context app = context.getApplicationContext();
        DisplayMetrics metrics = app.getResources().getDisplayMetrics();
        int width = Math.round(metrics.widthPixels * widthPercent / 100f);
        int maxHeight = Math.round(metrics.heightPixels * MAX_HEIGHT_SHARE);
        WidgetGeometry.Plan natural = WidgetGeometry.plan(config, width, 0f,
                metrics.density, ClimateCommands.tempRange(state).steps());
        int height = (int) Math.ceil(natural.totalHeight());
        return new WidgetViews.Size(width, Math.max(1, Math.min(maxHeight, height)));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        container = new FrameLayout(this);
        setContentView(container);
        Window window = getWindow();
        window.setGravity(prefs.panelBottom()
                ? Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL : Gravity.CENTER);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    }

    /** A second start (the same steering wheel button) closes the window. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        ClimateState state = ClimateService.STORE.snapshot(SystemClock.elapsedRealtime());
        WidgetViews.Size size = size(this, prefs, state);
        // The application's pixels may differ from this window's; size it in its own.
        float toWindow = getResources().getDisplayMetrics().density
                / getApplicationContext().getResources().getDisplayMetrics().density;
        getWindow().setLayout(Math.round(size.widthPx * toWindow),
                Math.round(size.heightPx * toWindow));
        // Draw at once from the last known values; the service follows with fresh ones.
        RemoteViews first = ClimateService.buildPanel(this, prefs, state, size, null);
        if (first != null) {
            show(first);
        }
        ClimateService.setPanel(this, host, size);
    }

    @Override
    protected void onResume() {
        super.onResume();
        restartIdleTimer();
    }

    @Override
    protected void onPause() {
        // The scrubber pauses the window while a bar is dragged; it must not close meanwhile.
        main.removeCallbacks(idleClose);
        super.onPause();
    }

    /** Another app or HOME came up: the window is a quick control, not a place to return to. */
    @Override
    protected void onStop() {
        ClimateService.setPanel(this, host, null);
        super.onStop();
        if (!isChangingConfigurations() && !isFinishing()) {
            finish();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        restartIdleTimer();
        return super.dispatchTouchEvent(event);
    }

    private void restartIdleTimer() {
        main.removeCallbacks(idleClose);
        int seconds = prefs.panelCloseSeconds();
        if (seconds > 0) {
            main.postDelayed(idleClose, seconds * 1000L);
        }
    }

    private void show(RemoteViews views) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        try {
            View content = views.apply(this, container);
            container.removeAllViews();
            container.addView(content, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } catch (RuntimeException error) {
            AppLog.warn("Cannot show the climate window", error);
        }
    }

    /** The window has its own task; skip the task close slide. */
    @Override
    @SuppressWarnings("deprecation") // overrideActivityTransition needs API 34.
    public void finish() {
        main.removeCallbacks(idleClose);
        super.finish();
        overridePendingTransition(0, 0);
    }
}
