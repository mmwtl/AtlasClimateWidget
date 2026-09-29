package com.mmwtl.atlasclimatewidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
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

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable idleFinish = this::finish;

    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private boolean temperature;
    private int zone;
    private int value;
    private int sentValue;
    private boolean scrubbing;

    private WidgetConfig config;
    private CarModel model;
    private WidgetGeometry.Plan plan;
    private WidgetGeometry.Strip strip;
    private float scale;
    private float cell;

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

        ScrubView view = new ScrubView(this);
        setContentView(view);
        Window window = getWindow();
        window.setLayout(width, source.height());
        window.setGravity(Gravity.TOP | Gravity.START);
        window.setElevation(0f);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.x = left;
        attributes.y = source.top;
        window.setAttributes(attributes);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        scrubbing = true;
        ClimateService.setScrubbed(this, widgetId, WidgetViews.stripKey(strip));
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
            finish();
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

    @Override
    public void finish() {
        main.removeCallbacks(idleFinish);
        if (scrubbing) {
            scrubbing = false;
            ClimateService.setScrubbed(this, widgetId, null);
        }
        super.finish();
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
            if (bitmap == null || renderedValue != value) {
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
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    main.removeCallbacks(idleFinish);
                    int next = valueAt(event.getX());
                    if (next != value) {
                        value = next;
                        invalidate();
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
}
