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
import android.view.Window;
import android.view.WindowManager;

/**
 * The climate window: a large climate panel over whatever app is open, meant for a steering
 * wheel button (GInputBridge «Запуск ярлыка» → «Окно климата», see {@link PanelShortcutActivity}).
 *
 * <p>The window is a widget with its own layout ({@link Prefs#PANEL_ID}): the service draws it
 * with the same {@link WidgetViews}. This activity handles bar drags in place and keeps the
 * released value visible until the service has processed the selection; a long press on an
 * option switches it off directly. While focused it also listens for steering wheel keys from
 * OneOS. Another start while the window is open closes it, so one button toggles it. The window
 * closes on Back, on a tap outside, when another app comes up and after inactivity.
 */
public final class ClimatePanelActivity extends Activity {
    /** Public action for launchers and key mappers; the shortcut starts the same component. */
    static final String ACTION_SHOW = "com.mmwtl.atlasclimatewidget.action.CLIMATE_WINDOW";
    /** The window never covers the whole height, so a tap outside can still close it. */
    private static final float MAX_HEIGHT_SHARE = 0.9f;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable idleClose = this::finish;
    private final ClimateService.PanelHost host = this::show;
    private InteractiveClimateView content;
    private Prefs prefs;
    private boolean resumed;
    private OneOsPanelKeys steering;

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
        content = new InteractiveClimateView(this, this::updateIdleTimer);
        setContentView(content);
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
        content.resumeControls();
        ClimateState state = ClimateService.STORE.snapshot(SystemClock.elapsedRealtime());
        WidgetViews.Size size = size(this, prefs, state);
        // The application's pixels may differ from this window's; size it in its own.
        float toWindow = getResources().getDisplayMetrics().density
                / getApplicationContext().getResources().getDisplayMetrics().density;
        getWindow().setLayout(Math.round(size.widthPx * toWindow),
                Math.round(size.heightPx * toWindow));
        // Draw at once from the last known values; the service follows with fresh ones.
        ClimateService.PanelFrame first = ClimateService.buildPanel(
                WidgetTheme.themed(this), prefs, state, size, null, 0);
        if (first != null) {
            show(first);
        }
        ClimateService.setPanel(this, host, size);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        content.resumeControls();
        restartIdleTimer();
        updateSteering(hasWindowFocus());
    }

    @Override
    protected void onPause() {
        resumed = false;
        updateSteering(false);
        main.removeCallbacks(idleClose);
        content.pauseControls();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        updateSteering(hasFocus);
    }

    private void updateSteering(boolean focused) {
        if (focused && resumed && !isFinishing()) {
            if (steering == null) {
                WidgetConfig config = prefs.widget(Prefs.PANEL_ID);
                int[] keys = OneOsPanelKeys.keys(
                        config.isEnabled(WidgetConfig.Block.TEMPERATURE),
                        config.isEnabled(WidgetConfig.Block.FAN));
                if (keys.length > 0) {
                    steering = new OneOsPanelKeys(this, keys, this::onSteeringClick);
                    steering.open();
                }
            }
        } else if (steering != null) {
            steering.close();
            steering = null;
        }
    }

    private void onSteeringClick(int keyCode) {
        if (!resumed || !hasWindowFocus() || isFinishing() || content.isDragging()) {
            return;
        }
        WidgetConfig config = prefs.widget(Prefs.PANEL_ID);
        String control = switch (keyCode) {
            case OneOsPanelKeys.VOLUME_UP, OneOsPanelKeys.KNOB_VOLUME_UP ->
                    config.isEnabled(WidgetConfig.Block.TEMPERATURE)
                            ? "tempstep/" + Hvac.ZONE_DRIVER + "/1" : null;
            case OneOsPanelKeys.VOLUME_DOWN, OneOsPanelKeys.KNOB_VOLUME_DOWN ->
                    config.isEnabled(WidgetConfig.Block.TEMPERATURE)
                            ? "tempstep/" + Hvac.ZONE_DRIVER + "/-1" : null;
            case OneOsPanelKeys.MEDIA_NEXT ->
                    config.isEnabled(WidgetConfig.Block.FAN) ? "fanstep/1" : null;
            case OneOsPanelKeys.MEDIA_PREVIOUS ->
                    config.isEnabled(WidgetConfig.Block.FAN) ? "fanstep/-1" : null;
            default -> null;
        };
        if (control != null) {
            if (AppLog.debugEnabled()) {
                AppLog.debug("OneOS panel click key=" + keyCode + " control=" + control);
            }
            ClimateService.sendPanelControl(this, control);
            restartIdleTimer();
        }
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

    private void updateIdleTimer() {
        main.removeCallbacks(idleClose);
        if (!content.isDragging() && !isFinishing()) {
            restartIdleTimer();
        }
    }

    private void restartIdleTimer() {
        main.removeCallbacks(idleClose);
        int seconds = prefs.panelCloseSeconds();
        if (seconds > 0) {
            main.postDelayed(idleClose, seconds * 1000L);
        }
    }

    void show(ClimateService.PanelFrame frame) {
        if (!isFinishing() && !isDestroyed()) {
            content.show(frame);
        }
    }

    /** The window has its own task; skip the task close slide. */
    @Override
    @SuppressWarnings("deprecation") // overrideActivityTransition needs API 34.
    public void finish() {
        main.removeCallbacks(idleClose);
        updateSteering(false);
        super.finish();
        overridePendingTransition(0, 0);
    }
}
