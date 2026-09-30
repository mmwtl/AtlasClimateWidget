package com.mmwtl.atlasclimatewidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.util.List;

/**
 * Drag scrubber over a widget bar.
 *
 * <p>RemoteViews report only completed taps, never a touch position or a drag. A tap on the
 * temperature or fan bar applies that cell at once and opens this window exactly over the bar;
 * the finger can then drag the value. The command is sent when the finger is lifted, so the car
 * never receives the intermediate values. Touches outside the bar go to the launcher.
 */
public final class ScrubActivity extends Activity {
    private static final long IDLE_FINISH_MS = 3_000L;
    /** Time for the launcher to draw the restored strip before the window leaves. */
    private static final long RESTORE_FINISH_MS = 300L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable idleFinish = this::close;

    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private boolean temperature;
    private int zone;
    private int value;
    private int sentValue;
    private boolean scrubbing;
    private boolean closing;

    private WidgetConfig config;
    private CarModel model;
    private WidgetGeometry.Plan plan;
    private WidgetGeometry.Strip strip;
    private float scale;
    private float cell;
    private ScrubView view;
    /** Screen position of the scrubber window. */
    private int windowLeft;
    private int windowTop;
    private int windowHeight;
    private ValueBubble bubble;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri data = getIntent().getData();
        List<String> parts = data == null ? null : data.getPathSegments();
        if (parts == null || parts.size() < 3 || !parse(parts)) {
            finish();
            return;
        }
        String control = String.join("/", parts.subList(1, parts.size()));
        startForegroundService(new Intent(this, ClimateService.class)
                .setAction(ClimateService.ACTION_CONTROL)
                .setData(Uri.parse(WidgetViews.CONTROL_SCHEME + "://control/" + control)));
        sentValue = value;
        if (!showScrubber(getIntent().getSourceBounds())) {
            finish();
        }
    }

    private boolean parse(List<String> parts) {
        try {
            widgetId = Integer.parseInt(parts.get(0));
            if ("temp".equals(parts.get(1)) && parts.size() >= 4) {
                temperature = true;
                zone = Integer.parseInt(parts.get(2));
                value = Integer.parseInt(parts.get(3));
                return true;
            }
            if ("fan".equals(parts.get(1))) {
                value = Integer.parseInt(parts.get(2)) - 1;
                return true;
            }
        } catch (NumberFormatException error) {
            AppLog.warn("Malformed scrub control " + parts, error);
        }
        return false;
    }

    private boolean showScrubber(Rect source) {
        if (source == null || source.isEmpty()) {
            AppLog.warn("Widget host did not report the tapped bounds", null);
            return false;
        }
        Context app = getApplicationContext();
        Prefs prefs = new Prefs(app);
        config = prefs.widget(widgetId);
        model = prefs.carModel();
        ClimateState state = ClimateService.STORE.snapshot(SystemClock.elapsedRealtime());
        WidgetViews.Size size = ClimateService.widgetSize(app, widgetId);
        plan = WidgetGeometry.plan(config, size.widthPx, size.heightPx,
                app.getResources().getDisplayMetrics().density,
                ClimateCommands.tempRange(state).steps());
        strip = findStrip();
        if (strip == null) {
            return false;
        }
        // The tapped cell anchors the whole bar: its width gives the host's scale.
        cell = (plan.width - 2f * strip.zonePadding) / strip.zoneCount;
        int cellIndex = strip.buttonCells + value;
        scale = source.width() / cell;
        int left = Math.round(source.left
                - (plan.offsetX() + strip.zonePadding + cellIndex * cell) * scale);
        int width = Math.round(plan.fullWidth * scale);

        view = new ScrubView(this);
        setContentView(view);
        Window window = getWindow();
        window.setLayout(width, source.height());
        window.setGravity(Gravity.TOP | Gravity.START);
        window.setElevation(0f);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.x = left;
        attributes.y = source.top;
        windowLeft = left;
        windowTop = source.top;
        windowHeight = source.height();
        window.setAttributes(attributes);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        // Hide the widget's content only once this window's content is on screen, so the bar
        // never shows an empty card while the window starts.
        String stripKey = WidgetViews.stripKey(strip);
        window.getDecorView().getViewTreeObserver().registerFrameCommitCallback(() -> {
            if (!closing && !isFinishing()) {
                scrubbing = true;
                ClimateService.setScrubbed(this, widgetId, stripKey);
                showBubble();
            }
        });
        main.postDelayed(idleFinish, IDLE_FINISH_MS);
        return true;
    }

    private WidgetGeometry.Strip findStrip() {
        int index = zone == Hvac.ZONE_PASSENGER ? 1 : 0;
        for (WidgetGeometry.Strip candidate : plan.strips) {
            if (temperature && candidate.row.kind == WidgetGeometry.RowKind.TEMPERATURE
                    && candidate.row.index == index) {
                return candidate;
            }
            if (!temperature && candidate.row.kind == WidgetGeometry.RowKind.FAN) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
            close();
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isFinishing()) {
            finish();
        }
    }

    /** Restores the widget's content under the window first, then removes the window. */
    private void close() {
        if (closing || isFinishing()) {
            return;
        }
        closing = true;
        main.removeCallbacks(idleFinish);
        hideBubble();
        if (!scrubbing) {
            finish();
            return;
        }
        scrubbing = false;
        ClimateService.setScrubbed(this, widgetId, null);
        // A tap outside may be a preset that changes this bar; draw what the widget draws.
        view.invalidate();
        main.postDelayed(this::finish, RESTORE_FINISH_MS);
    }

    /** The window belongs to its own task; skip the task close slide over HOME. */
    @Override
    @SuppressWarnings("deprecation") // overrideActivityTransition needs API 34.
    public void finish() {
        main.removeCallbacks(idleFinish);
        hideBubble();
        if (scrubbing) {
            scrubbing = false;
            ClimateService.setScrubbed(this, widgetId, null);
        }
        super.finish();
        overridePendingTransition(0, 0);
    }

    /**
     * Shows the dragged temperature above the bar, where the finger does not cover it; the
     * classic knob sits under the finger and the widget hides the console's large value while
     * the bar is dragged. The bubble is a separate untouchable window, so taps around the bar
     * still reach the widget.
     */
    private void showBubble() {
        if (!temperature || bubble != null) {
            return;
        }
        bubble = new ValueBubble(this);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.token = getWindow().getDecorView().getWindowToken();
        params.gravity = Gravity.TOP | Gravity.START;
        params.windowAnimations = 0;
        params.setTitle("ScrubValue");
        bubble.place(params);
        getWindowManager().addView(bubble, params);
    }

    private void updateBubble() {
        if (bubble == null) {
            return;
        }
        WindowManager.LayoutParams params =
                (WindowManager.LayoutParams) bubble.getLayoutParams();
        bubble.place(params);
        getWindowManager().updateViewLayout(bubble, params);
        bubble.invalidate();
    }

    private void hideBubble() {
        if (bubble != null) {
            getWindowManager().removeViewImmediate(bubble);
            bubble = null;
        }
    }

    /** Value under the finger: a temperature step or a zero-based fan level. */
    private int valueAt(float x) {
        float local = x / scale - plan.offsetX() - strip.zonePadding;
        int index = (int) Math.floor(local / cell);
        int first = strip.buttonCells;
        int last = strip.zoneCount - strip.buttonCells - 1;
        return Math.max(first, Math.min(last, index)) - first;
    }

    private void commit() {
        if (value == sentValue) {
            return;
        }
        sentValue = value;
        String control = temperature ? "temp/" + zone + "/" + value : "fan/" + (value + 1);
        startForegroundService(new Intent(this, ClimateService.class)
                .setAction(ClimateService.ACTION_CONTROL)
                .setData(Uri.parse(WidgetViews.CONTROL_SCHEME + "://control/" + control)));
    }

    /** Shows the dragged value in place of the car's value. */
    private final class DraggedState implements ClimateState {
        private final ClimateState base;

        DraggedState(ClimateState base) {
            this.base = base;
        }

        @Override
        public Double property(int id, int area) {
            if (temperature && id == Hvac.TEMP && area == zone) {
                return (double) ClimateCommands.tempRange(base).valueAt(value);
            }
            if (!temperature && id == Hvac.FAN_SPEED) {
                return (double) (Hvac.FAN_SPEED_LEVEL_1 + value);
            }
            if (!temperature && id == Hvac.AUTO_FAN_SETTING) {
                return 0d;
            }
            return base.property(id, area);
        }

        @Override
        public Double sensor(int id) {
            return base.sensor(id);
        }

        @Override
        public boolean isConnected() {
            return base.isConnected();
        }
    }

    private final class ScrubView extends View {
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private Bitmap bitmap;
        private int renderedValue = Integer.MIN_VALUE;

        ScrubView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (closing) {
                // The restored strip below shows the live state, so the window follows it
                // every frame until it leaves.
                bitmap = new WidgetRenderer(getApplicationContext(), config,
                        ClimateService.STORE.snapshot(SystemClock.elapsedRealtime()), model, plan)
                        .render(strip, false, true);
                postInvalidateOnAnimation();
            } else if (bitmap == null || renderedValue != value) {
                renderedValue = value;
                ClimateState state = new DraggedState(
                        ClimateService.STORE.snapshot(SystemClock.elapsedRealtime()));
                bitmap = new WidgetRenderer(getApplicationContext(), config, state, model, plan)
                        .render(strip, false, true);
            }
            canvas.save();
            canvas.scale(getWidth() / (float) bitmap.getWidth(),
                    getHeight() / (float) bitmap.getHeight());
            canvas.drawBitmap(bitmap, 0f, 0f, paint);
            canvas.restore();
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (closing) {
                return true;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    main.removeCallbacks(idleFinish);
                    int next = valueAt(event.getX());
                    if (next != value) {
                        value = next;
                        invalidate();
                        updateBubble();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    performClick();
                    commit();
                    main.postDelayed(idleFinish, IDLE_FINISH_MS);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    commit();
                    main.postDelayed(idleFinish, IDLE_FINISH_MS);
                    return true;
                default:
                    return true;
            }
        }
    }

    /** Pill with the dragged value and a pointer towards the bar, like the classic knob. */
    private final class ValueBubble extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF pill = new RectF();
        private final Path pointer = new Path();
        private final Rect bounds = new Rect();
        private final float pillHeight;
        private final float pointerSize;
        private final float gap;
        private boolean below;
        private float pointerX;
        private String label = "";

        ValueBubble(Context context) {
            super(context);
            // The bubble follows the widget's size on screen, like the bar under it.
            float unit = plan.density * scale;
            pillHeight = 40f * unit;
            pointerSize = 7f * unit;
            gap = 6f * unit;
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(pillHeight * 0.5f);
        }

        /** Sizes and places the window over the dragged step, above the bar if it fits. */
        void place(WindowManager.LayoutParams params) {
            ClimateState state = ClimateService.STORE.snapshot(SystemClock.elapsedRealtime());
            ClimateCommands.TempRange range = ClimateCommands.tempRange(state);
            label = WidgetRenderer.formatTemperature(range.valueAt(value), range);
            float width = Math.max(pillHeight * 1.8f,
                    paint.measureText(label) + pillHeight * 0.9f);
            int height = Math.round(pillHeight + pointerSize);
            float knob = windowLeft + (plan.offsetX() + strip.zonePadding
                    + (strip.buttonCells + value + 0.5f) * cell) * scale;
            int screen = getResources().getDisplayMetrics().widthPixels;
            int left = Math.round(Math.max(0f, Math.min(screen - width, knob - width / 2f)));
            int top = Math.round(windowTop - gap - height);
            below = top < 0;
            if (below) {
                top = Math.round(windowTop + windowHeight + gap);
            }
            pointerX = knob - left;
            params.width = Math.round(width);
            params.height = height;
            params.x = left;
            params.y = top;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float top = below ? pointerSize : 0f;
            pill.set(0f, top, getWidth(), top + pillHeight);
            paint.setColor(Ui.TEXT);
            canvas.drawRoundRect(pill, pillHeight / 2f, pillHeight / 2f, paint);
            float x = Math.max(pillHeight / 2f, Math.min(getWidth() - pillHeight / 2f, pointerX));
            pointer.reset();
            float tip = below ? 0f : getHeight();
            float base = below ? pointerSize + 1f : pillHeight - 1f;
            pointer.moveTo(x - pointerSize, base);
            pointer.lineTo(x, tip);
            pointer.lineTo(x + pointerSize, base);
            pointer.close();
            canvas.drawPath(pointer, paint);
            paint.setColor(Ui.BACKGROUND);
            paint.getTextBounds(label, 0, label.length(), bounds);
            canvas.drawText(label, pill.centerX() - paint.measureText(label) / 2f,
                    pill.centerY() - bounds.exactCenterY(), paint);
        }
    }
}
