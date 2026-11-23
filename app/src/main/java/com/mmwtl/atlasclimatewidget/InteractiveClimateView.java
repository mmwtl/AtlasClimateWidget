package com.mmwtl.atlasclimatewidget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.List;

/** Local touch handling for the HOME overlay and the shortcut's climate window. */
final class InteractiveClimateView extends FrameLayout {
    private static final float FOCUS_OUTER_DP = 2f;
    private static final float FOCUS_INNER_DP = 3f;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable selectionTimeout = this::expireSelection;
    private final Runnable flushPending = this::showPending;
    private final Paint focusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF focusRect = new RectF();

    private final Runnable onInteraction;
    private final boolean showSteeringOutline;
    private boolean acceptsControls = true;
    private Drag activeDrag;
    private boolean touchHeld;
    private long awaitingControl;
    private ClimateService.PanelFrame displayedFrame;
    private ClimateService.PanelFrame pendingFrame;
    private WidgetConfig.Block steeringFocus;

    InteractiveClimateView(Context context) {
        this(context, () -> {}, false);
    }

    InteractiveClimateView(Context context, Runnable onInteraction, boolean showSteeringOutline) {
        super(context);
        this.onInteraction = onInteraction;
        this.showSteeringOutline = showSteeringOutline;
        setWillNotDraw(false);
    }

    boolean isDragging() {
        return activeDrag != null;
    }

    WidgetConfig.Block steeringFocus() {
        return steeringFocus;
    }

    void setSteeringFocus(WidgetConfig.Block block) {
        if (steeringFocus != block) {
            steeringFocus = block;
            invalidate();
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (showSteeringOutline) {
            drawSteeringFocus(canvas);
        }
    }

    /** Outlines the block currently driven by steering-wheel arrows in the climate window. */
    private void drawSteeringFocus(Canvas canvas) {
        WidgetConfig.Block target = steeringFocus;
        ClimateService.PanelFrame frame = displayedFrame;
        if (target == null || frame == null || getChildCount() == 0) {
            return;
        }
        View child = getChildAt(0);
        if (!(child instanceof ViewGroup root)) {
            return;
        }
        List<WidgetGeometry.Strip> strips = frame.plan().strips;
        int first = -1;
        int last = -1;
        for (int i = 0; i < strips.size(); i++) {
            if (strips.get(i).block == target) {
                if (first < 0) {
                    first = i;
                }
                last = i;
            }
        }
        if (first < 0 || last >= root.getChildCount()) {
            return;
        }
        WidgetGeometry.Plan plan = frame.plan();
        WidgetGeometry.Strip firstStrip = strips.get(first);
        WidgetGeometry.Strip lastStrip = strips.get(last);
        View firstRow = root.getChildAt(first);
        View lastRow = root.getChildAt(last);
        if (firstRow.getWidth() <= 0 || firstRow.getHeight() <= 0 || lastRow.getHeight() <= 0
                || plan.fullWidth <= 0f || firstStrip.height <= 0f || lastStrip.height <= 0f) {
            return;
        }
        float scaleX = firstRow.getWidth() / plan.fullWidth;
        float firstScaleY = firstRow.getHeight() / firstStrip.height;
        float lastScaleY = lastRow.getHeight() / lastStrip.height;
        float density = getResources().getDisplayMetrics().density;
        float stroke = Math.max(1f, FOCUS_OUTER_DP * density);
        float cardLeft = root.getLeft() + firstRow.getLeft() + plan.offsetX() * scaleX;
        float cardRight = cardLeft + plan.width * scaleX;
        float radius;
        if (firstStrip.firstInCard && lastStrip.lastInCard) {
            float inset = stroke / 2f;
            float top = root.getTop() + firstRow.getTop() + inset;
            float bottom = root.getTop() + lastRow.getBottom()
                    - lastStrip.trailingGap * lastScaleY - inset;
            focusRect.set(cardLeft + inset, top, cardRight - inset, bottom);
            radius = Math.max(0f, plan.cardRadius * scaleX - inset);
        } else {
            float padX = Math.max(stroke, plan.padding * scaleX * 0.45f);
            float padY = Math.max(stroke, plan.padding * firstScaleY * 0.35f);
            float top = root.getTop() + firstRow.getTop()
                    + Math.max(stroke / 2f, firstStrip.contentTop * firstScaleY - padY);
            float bottom = root.getTop() + lastRow.getTop()
                    + Math.min(lastRow.getHeight() - stroke / 2f,
                            (lastStrip.contentTop + lastStrip.contentHeight) * lastScaleY + padY);
            focusRect.set(cardLeft + padX, top, cardRight - padX, bottom);
            radius = Math.max(0f, Math.min(plan.cardRadius * scaleX * 0.65f,
                    focusRect.height() / 2f));
        }
        if (focusRect.width() <= 0f || focusRect.height() <= 0f) {
            return;
        }
        WidgetTheme mode = WidgetTheme.of(WidgetTheme.themed(getContext()));
        WidgetTheme cardTheme = WidgetTheme.forCard(frame.config(), mode);
        drawDoubleSteeringOutline(canvas, radius, stroke, density,
                WidgetTheme.cardColor(frame.config(), mode), cardTheme.night);
    }

    private void drawDoubleSteeringOutline(Canvas canvas, float radius, float outerStroke,
            float density, int cardColor, boolean darkCard) {
        focusPaint.setStyle(Paint.Style.FILL);
        focusPaint.setColor(darkCard ? 0x246BAAC4 : 0x236A9DB6);
        canvas.drawRoundRect(focusRect, radius, radius, focusPaint);

        // Use opaque strokes so the card opacity and the wallpaper cannot wash out both lines.
        focusPaint.setStyle(Paint.Style.STROKE);
        focusPaint.setStrokeWidth(outerStroke);
        focusPaint.setColor(darkCard ? 0xFF111A20 : Color.WHITE);
        canvas.drawRoundRect(focusRect, radius, radius, focusPaint);

        float innerStroke = Math.max(1f, FOCUS_INNER_DP * density);
        float inset = (outerStroke + innerStroke) / 2f;
        focusRect.inset(inset, inset);
        if (focusRect.isEmpty()) {
            return;
        }
        int inner = darkCard ? 0xFFBAE5F6 : 0xFF204B61;
        if (WidgetTheme.contrast(inner, cardColor) < 3) {
            inner = darkCard ? Color.WHITE : Color.BLACK;
        }
        focusPaint.setStrokeWidth(innerStroke);
        focusPaint.setColor(inner);
        float innerRadius = Math.max(0f, radius - inset);
        canvas.drawRoundRect(focusRect, innerRadius, innerRadius, focusPaint);
    }

    void resumeControls() {
        acceptsControls = true;
        showPending();
    }

    void pauseControls() {
        acceptsControls = false;
        touchHeld = false;
        if (activeDrag != null) {
            activeDrag.end(false);
        }
        for (int index = 0; index < getChildCount(); index++) {
            getChildAt(index).cancelPendingInputEvents();
        }
        main.removeCallbacksAndMessages(null);
        awaitingControl = 0;
        pendingFrame = null;
        displayedFrame = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        pauseControls();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            touchHeld = true;
            main.removeCallbacks(flushPending);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                || action == MotionEvent.ACTION_OUTSIDE) {
            touchHeld = false;
        }
        boolean handled = super.dispatchTouchEvent(event);
        if (!touchHeld && pendingFrame != null && activeDrag == null) {
            // Defer past View's posted PerformClick on ACTION_UP, which detaching cancels.
            main.removeCallbacks(flushPending);
            main.post(flushPending);
        }
        if (activeDrag == null) {
            onInteraction.run();
        }
        return handled;
    }

    private void showPending() {
        if (!touchHeld && pendingFrame != null && activeDrag == null) {
            show(pendingFrame);
        }
    }

    void show(ClimateService.PanelFrame frame) {
        if (!acceptsControls || activeDrag != null || touchHeld
                || frame.appliedControl() < awaitingControl) {
            pendingFrame = frame;
            return;
        }
        try {
            ViewGroup content = (ViewGroup) frame.views().apply(getContext(), this);
            attachDrags(content, frame);
            attachLongPresses(content, frame);
            removeAllViews();
            addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
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
        ClimateService.PanelFrame next = pendingFrame != null ? pendingFrame : displayedFrame;
        if (next != null) {
            show(next);
        }
    }

    private void attachLongPresses(ViewGroup content, ClimateService.PanelFrame frame) {
        List<WidgetGeometry.Strip> strips = frame.plan().strips;
        for (int index = 0; index < strips.size(); index++) {
            WidgetGeometry.Strip strip = strips.get(index);
            View row = content.getChildAt(index);
            ViewGroup zones = row.findViewById(R.id.strip_zones);
            for (int cell = 0; cell < strip.zoneCount; cell++) {
                String control = WidgetViews.longPressControl(
                        frame.config(), strip, cell, frame.auto());
                if (control != null) {
                    zones.getChildAt(cell).setOnLongClickListener(view -> {
                        onLongPress(control);
                        return true;
                    });
                }
            }
        }
    }

    private void onLongPress(String control) {
        if (!acceptsControls) {
            return;
        }
        // Long press is complete; let the service's off-state frame draw immediately even while
        // the finger is still down.
        touchHeld = false;
        onInteraction.run();
        long id = ClimateService.sendPanelControl(getContext(), control);
        if (id != 0) {
            awaitingControl = id;
            main.removeCallbacks(selectionTimeout);
            main.postDelayed(selectionTimeout, ClimateStore.PENDING_TIMEOUT_MS);
        } else if (pendingFrame != null) {
            show(pendingFrame);
        }
    }

    private void attachDrags(ViewGroup content, ClimateService.PanelFrame frame) {
        List<WidgetGeometry.Strip> strips = frame.plan().strips;
        for (int index = 0; index < strips.size(); index++) {
            WidgetGeometry.Strip strip = strips.get(index);
            boolean temperature = strip.row.kind == WidgetGeometry.RowKind.TEMPERATURE;
            boolean speed = strip.row.kind == WidgetGeometry.RowKind.FAN;
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
            boolean presets = speed && frame.auto()
                    && frame.config().fanLayout == WidgetConfig.FanLayout.BAR;
            int baseSteps = temperature ? frame.plan().temperatureSteps : Hvac.FAN_SPEED_LEVEL_COUNT;
            int cells = baseSteps * strip.stepSpan;
            int steps = presets ? ClimateCommands.fanPresets(frame.config().fanPresetCount).length
                    : baseSteps;
            Drag drag = new Drag(frame, strip, zones, row.findViewById(R.id.strip_image),
                    readout, readoutImage, steps, cells / steps, presets);
            int end = strip.stepStart + cells;
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
        private final int stepSpan;
        private final boolean presets;
        private Drawable original;
        private Drawable originalReadout;
        private int step;

        Drag(ClimateService.PanelFrame frame, WidgetGeometry.Strip strip, ViewGroup zones,
                ImageView image, WidgetGeometry.Strip readout, ImageView readoutImage, int steps,
                int stepSpan, boolean presets) {
            this.frame = frame;
            this.strip = strip;
            this.zones = zones;
            this.image = image;
            this.readout = readout;
            this.readoutImage = readoutImage;
            this.steps = steps;
            this.stepSpan = stepSpan;
            this.presets = presets;
        }

        // Accessibility clicks use the cell's existing action. A touch release can select
        // a different cell and sends that selection directly.
        @Override
        @SuppressLint("ClickableViewAccessibility")
        public boolean onTouch(View view, MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                activeDrag = this;
                onInteraction.run();
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
            int next = Math.max(0, Math.min(steps - 1, (cell - strip.stepStart) / stepSpan));
            if (step == next) {
                return;
            }
            step = next;
            ClimateState state = ClimateService.STORE.snapshot(SystemClock.elapsedRealtime());
            WidgetRenderer renderer = new WidgetRenderer(WidgetTheme.themed(getContext()),
                    frame.config(), state, frame.model(), frame.plan()).withBarPreview(strip, step, presets);
            image.setImageBitmap(renderer.render(strip));
            if (readoutImage != null) {
                readoutImage.setImageBitmap(renderer.render(readout));
            }
        }

        private void end(boolean commit) {
            activeDrag = null;
            if (commit) {
                int cell = strip.stepStart + step * stepSpan;
                String control = WidgetViews.control(frame.config(), strip, cell, frame.auto());
                long id = ClimateService.sendPanelControl(getContext(), control);
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

}
