package com.mmwtl.atlasclimatewidget;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.List;

/**
 * The climate window: a large climate panel over whatever app is open, meant for a steering
 * wheel button (GInputBridge «Запуск ярлыка» → «Окно климата», see {@link PanelShortcutActivity}).
 *
 * <p>The window is a widget with its own layout ({@link Prefs#PANEL_ID}): the service draws it
 * with the same {@link WidgetViews}. This activity handles bar drags in place and keeps the
 * released value visible until the service has processed the selection. Another
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
    private final Runnable selectionTimeout = this::expireSelection;
    private final ClimateService.PanelHost host = this::show;

    private FrameLayout container;
    private Prefs prefs;
    private Drag activeDrag;
    private long awaitingControl;
    private ClimateService.PanelFrame displayedFrame;
    private ClimateService.PanelFrame pendingFrame;

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
        restartIdleTimer();
    }

    @Override
    protected void onPause() {
        main.removeCallbacks(idleClose);
        if (activeDrag != null) {
            activeDrag.end(false);
        }
        super.onPause();
    }

    /** Another app or HOME came up: the window is a quick control, not a place to return to. */
    @Override
    protected void onStop() {
        ClimateService.setPanel(this, host, null);
        main.removeCallbacks(selectionTimeout);
        awaitingControl = 0;
        pendingFrame = null;
        displayedFrame = null;
        super.onStop();
        if (!isChangingConfigurations() && !isFinishing()) {
            finish();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        boolean handled = super.dispatchTouchEvent(event);
        if (activeDrag == null && !isFinishing()) {
            restartIdleTimer();
        }
        return handled;
    }

    private void restartIdleTimer() {
        main.removeCallbacks(idleClose);
        int seconds = prefs.panelCloseSeconds();
        if (seconds > 0) {
            main.postDelayed(idleClose, seconds * 1000L);
        }
    }

    void show(ClimateService.PanelFrame frame) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (activeDrag != null || frame.appliedControl() < awaitingControl) {
            pendingFrame = frame;
            return;
        }
        try {
            ViewGroup content = (ViewGroup) frame.views().apply(this, container);
            attachDrags(content, frame);
            container.removeAllViews();
            container.addView(content, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            displayedFrame = frame;
            pendingFrame = null;
            awaitingControl = 0;
            main.removeCallbacks(selectionTimeout);
        } catch (RuntimeException error) {
            AppLog.warn("Cannot show the climate window", error);
        }
    }

    private void expireSelection() {
        awaitingControl = 0;
        show(pendingFrame != null ? pendingFrame : displayedFrame);
    }

    private void attachDrags(ViewGroup content, ClimateService.PanelFrame frame) {
        List<WidgetGeometry.Strip> strips = frame.plan().strips;
        for (int index = 0; index < strips.size(); index++) {
            WidgetGeometry.Strip strip = strips.get(index);
            boolean temperature = strip.row.kind == WidgetGeometry.RowKind.TEMPERATURE;
            boolean speed = strip.row.kind == WidgetGeometry.RowKind.FAN
                    && (!frame.auto() || frame.config().fanLayout == WidgetConfig.FanLayout.ROWS);
            if (!temperature && !speed) {
                continue;
            }
            View row = content.getChildAt(index);
            ViewGroup zones = row.findViewById(R.id.strip_zones);
            WidgetGeometry.Strip previous = index > 0 ? strips.get(index - 1) : null;
            WidgetGeometry.Strip readout = temperature && previous != null
                    && previous.row.kind == WidgetGeometry.RowKind.TEMP_VALUE
                    && previous.row.index == strip.row.index ? previous : null;
            ImageView readoutImage = readout == null ? null
                    : content.getChildAt(index - 1).findViewById(R.id.strip_image);
            int steps = temperature ? frame.plan().temperatureSteps : Hvac.FAN_SPEED_LEVEL_COUNT;
            Drag drag = new Drag(frame, strip, zones, row.findViewById(R.id.strip_image),
                    readout, readoutImage, steps);
            int end = strip.stepStart + steps * strip.stepSpan;
            for (int cell = strip.stepStart; cell < end; cell++) {
                zones.getChildAt(cell).setOnTouchListener(drag);
            }
        }
    }

    /** Keeps the same touch targets in place until Android finishes delivering the gesture. */
    private final class Drag implements View.OnTouchListener {
        private final ClimateService.PanelFrame frame;
        private final WidgetGeometry.Strip strip;
        private final ViewGroup zones;
        private final ImageView image;
        private final WidgetGeometry.Strip readout;
        private final ImageView readoutImage;
        private final int steps;
        private Drawable original;
        private Drawable originalReadout;
        private int step;

        Drag(ClimateService.PanelFrame frame, WidgetGeometry.Strip strip, ViewGroup zones,
                ImageView image, WidgetGeometry.Strip readout, ImageView readoutImage, int steps) {
            this.frame = frame;
            this.strip = strip;
            this.zones = zones;
            this.image = image;
            this.readout = readout;
            this.readoutImage = readoutImage;
            this.steps = steps;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                activeDrag = this;
                main.removeCallbacks(idleClose);
                original = image.getDrawable();
                originalReadout = readoutImage == null ? null : readoutImage.getDrawable();
                step = -1;
            }
            if (activeDrag != this) {
                return true;
            }
            switch (action) {
                case MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> move(view, event.getX());
                case MotionEvent.ACTION_UP -> {
                    move(view, event.getX());
                    end(true);
                }
                case MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> end(false);
                default -> { }
            }
            return true;
        }

        private void move(View view, float x) {
            int width = zones.getWidth() - zones.getPaddingLeft() - zones.getPaddingRight();
            int local = (int) Math.floor(view.getLeft() + x) - zones.getPaddingLeft();
            int cell = WidgetGeometry.zoneAt(width, strip.zoneCount,
                    Math.max(0, Math.min(width - 1, local)));
            int next = Math.max(0, Math.min(steps - 1, (cell - strip.stepStart) / strip.stepSpan));
            if (step == next) {
                return;
            }
            step = next;
            ClimateState state = ClimateService.STORE.snapshot(SystemClock.elapsedRealtime());
            WidgetRenderer renderer = new WidgetRenderer(WidgetTheme.themed(ClimatePanelActivity.this),
                    frame.config(), state, frame.model(), frame.plan()).withBarPreview(strip, step);
            image.setImageBitmap(renderer.render(strip));
            if (readoutImage != null) {
                readoutImage.setImageBitmap(renderer.render(readout));
            }
        }

        private void end(boolean commit) {
            activeDrag = null;
            if (commit) {
                int cell = strip.stepStart + step * strip.stepSpan;
                String control = WidgetViews.control(frame.config(), strip, cell, frame.auto());
                long id = ClimateService.sendPanelControl(ClimatePanelActivity.this, control);
                if (id != 0) {
                    awaitingControl = id;
                    main.removeCallbacks(selectionTimeout);
                    main.postDelayed(selectionTimeout, ClimateStore.PENDING_TIMEOUT_MS);
                    original = null;
                    originalReadout = null;
                    return;
                }
            }
            image.setImageDrawable(original);
            original = null;
            if (readoutImage != null) {
                readoutImage.setImageDrawable(originalReadout);
                originalReadout = null;
            }
            if (pendingFrame != null) {
                show(pendingFrame);
            }
        }
    }

    /** The window has its own task; skip the task close slide. */
    @Override
    @SuppressWarnings("deprecation") // overrideActivityTransition needs API 34.
    public void finish() {
        main.removeCallbacks(idleClose);
        main.removeCallbacks(selectionTimeout);
        super.finish();
        overridePendingTransition(0, 0);
    }
}
