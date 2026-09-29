package com.mmwtl.atlasclimatewidget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.List;

/**
 * Hosts the inert settings preview and lets the finger drag its tiles into a new order.
 *
 * <p>The preview is rebuilt from {@link WidgetViews} on every step, so the layer itself keeps
 * the gesture: it hit-tests the strips of the current {@link WidgetGeometry.Plan}, marks the
 * dragged tile's slot and draws a lifted copy of the tile under the finger.
 */
final class TileDragLayer extends FrameLayout {
    interface Listener {
        /** Moves the tile onto a slot of {@link WidgetConfig#tileRows()}; true if it moved. */
        boolean onTileMoved(ClimateFunction function, int row, int column);

        void onTileDropped(boolean moved);
    }

    private static final float LIFT_SCALE = 1.08f;

    private final Paint holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tilePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF slot = new RectF();
    private final RectF lifted = new RectF();

    private Listener listener;
    private WidgetConfig config;
    private WidgetGeometry.Plan plan;

    private ClimateFunction dragged;
    private Bitmap draggedImage;
    private float grabX;
    private float grabY;
    private float touchX;
    private float touchY;
    private boolean moved;

    TileDragLayer(Context context) {
        super(context);
        setWillNotDraw(false);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setColor(Ui.ACCENT);
        outlinePaint.setStrokeWidth(Ui.dp(context, 2));
        float dash = Ui.dp(context, 6);
        outlinePaint.setPathEffect(new DashPathEffect(new float[]{dash, dash * 0.7f}, 0f));
        shadowPaint.setColor(0x66000000);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    /** The layout the preview was built from; {@code null} when there is nothing to drag. */
    void setLayout(WidgetConfig config, WidgetGeometry.Plan plan) {
        this.config = config;
        this.plan = plan;
        if (dragged != null && (config == null || !config.functions.contains(dragged))) {
            endDrag();
        }
        invalidate();
    }

    boolean isDragging() {
        return dragged != null;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return startDrag(event.getX(), event.getY());
            case MotionEvent.ACTION_MOVE:
                if (dragged == null) {
                    return false;
                }
                touchX = event.getX();
                touchY = event.getY();
                int[] target = slotAt(touchX, touchY, false);
                if (target != null && listener != null
                        && listener.onTileMoved(dragged, target[0], target[1])) {
                    moved = true;
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragged == null) {
                    return false;
                }
                boolean changed = moved;
                endDrag();
                if (listener != null) {
                    listener.onTileDropped(changed);
                }
                return true;
            default:
                return dragged != null;
        }
    }

    private boolean startDrag(float x, float y) {
        int[] hit = slotAt(x, y, true);
        if (hit == null) {
            return false;
        }
        ClimateFunction function = config.tileRows().get(hit[0])[hit[1]];
        if (function == null || !config.canDragTile(function) || !tileRect(hit[0], hit[1], slot)) {
            return false;
        }
        draggedImage = snapshot(slot);
        if (draggedImage == null) {
            return false;
        }
        dragged = function;
        moved = false;
        grabX = x - slot.left;
        grabY = y - slot.top;
        touchX = x;
        touchY = y;
        getParent().requestDisallowInterceptTouchEvent(true);
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        invalidate();
        return true;
    }

    private void endDrag() {
        dragged = null;
        if (draggedImage != null) {
            draggedImage.recycle();
            draggedImage = null;
        }
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (dragged == null || draggedImage == null) {
            return;
        }
        int[] position = positionOf(dragged);
        if (position != null && tileRect(position[0], position[1], slot)) {
            float radius = Math.min(slot.width(), slot.height()) * config.tileRadiusPercent
                    / 100f;
            holePaint.setColor(cardColor());
            canvas.drawRoundRect(slot, radius, radius, holePaint);
            float inset = outlinePaint.getStrokeWidth() / 2f;
            slot.inset(inset, inset);
            canvas.drawRoundRect(slot, radius, radius, outlinePaint);
        }
        float width = draggedImage.getWidth() * LIFT_SCALE;
        float height = draggedImage.getHeight() * LIFT_SCALE;
        float left = touchX - grabX * LIFT_SCALE;
        float top = touchY - grabY * LIFT_SCALE;
        lifted.set(left, top, left + width, top + height);
        float radius = Math.min(width, height) * config.tileRadiusPercent / 100f;
        float shadow = Ui.dp(getContext(), 4);
        lifted.offset(0f, shadow);
        canvas.drawRoundRect(lifted, radius, radius, shadowPaint);
        lifted.offset(0f, -shadow);
        canvas.drawBitmap(draggedImage, null, lifted, tilePaint);
    }

    /** The card behind the tiles as seen in the preview, so the empty slot looks vacated. */
    private int cardColor() {
        int card = config.cardColor;
        float alpha = config.cardOpacityPercent / 100f;
        int back = Ui.BACKGROUND;
        return Color.rgb(
                Math.round(Color.red(card) * alpha + Color.red(back) * (1f - alpha)),
                Math.round(Color.green(card) * alpha + Color.green(back) * (1f - alpha)),
                Math.round(Color.blue(card) * alpha + Color.blue(back) * (1f - alpha)));
    }

    private Bitmap snapshot(RectF rect) {
        View cell = getChildCount() > 0 ? getChildAt(0) : null;
        int width = Math.round(rect.width());
        int height = Math.round(rect.height());
        if (cell == null || width <= 0 || height <= 0) {
            return null;
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.translate(cell.getLeft() - rect.left, cell.getTop() - rect.top);
        cell.draw(canvas);
        return bitmap;
    }

    private int[] positionOf(ClimateFunction function) {
        List<ClimateFunction[]> rows = config.tileRows();
        for (int row = 0; row < rows.size(); row++) {
            for (int column = 0; column < rows.get(row).length; column++) {
                if (rows.get(row)[column] == function) {
                    return new int[]{row, column};
                }
            }
        }
        return null;
    }

    /**
     * Tile slot under a point as {row, column}. A pick must hit the tile itself; while
     * dragging, the whole strip counts and the column snaps to the nearest one.
     */
    private int[] slotAt(float x, float y, boolean exact) {
        if (config == null || plan == null) {
            return null;
        }
        for (int index = 0; index < plan.strips.size(); index++) {
            WidgetGeometry.Strip strip = plan.strips.get(index);
            View view = stripView(index);
            if (strip.row.kind != WidgetGeometry.RowKind.TILES || view == null) {
                continue;
            }
            Rect bounds = bounds(view);
            if (y < bounds.top || y >= bounds.bottom) {
                continue;
            }
            float scaleX = bounds.width() / plan.fullWidth;
            float cellWidth = plan.tileCellWidth(config.columns);
            float local = (x - bounds.left) / scaleX - plan.offsetX() - plan.padding
                    + plan.gap / 2f;
            int column = (int) Math.floor(local / cellWidth);
            if (exact && (column < 0 || column >= config.columns)) {
                return null;
            }
            column = Math.max(0, Math.min(config.columns - 1, column));
            if (exact && !(tileRect(strip.row.index, column, slot) && slot.contains(x, y))) {
                return null;
            }
            return new int[]{strip.row.index, column};
        }
        return null;
    }

    /** Where the tile at a slot is drawn, in this layer's coordinates. */
    private boolean tileRect(int row, int column, RectF out) {
        for (int index = 0; index < plan.strips.size(); index++) {
            WidgetGeometry.Strip strip = plan.strips.get(index);
            if (strip.row.kind != WidgetGeometry.RowKind.TILES || strip.row.index != row) {
                continue;
            }
            View view = stripView(index);
            if (view == null) {
                return false;
            }
            Rect bounds = bounds(view);
            float scaleX = bounds.width() / plan.fullWidth;
            float scaleY = bounds.height() / (float) plan.pixelHeight(strip);
            float top = plan.stripTop(strip);
            float cellWidth = plan.tileCellWidth(config.columns);
            float left = plan.offsetX() + plan.padding + column * cellWidth;
            float tileTop = top - Math.round(top) + strip.contentTop;
            out.set(bounds.left + left * scaleX, bounds.top + tileTop * scaleY,
                    bounds.left + (left + cellWidth - plan.gap) * scaleX,
                    bounds.top + (tileTop + strip.contentHeight) * scaleY);
            return true;
        }
        return false;
    }

    /** The preview's strips stack in plan order inside the framed cell's widget root. */
    private View stripView(int index) {
        if (getChildCount() == 0 || !(getChildAt(0) instanceof ViewGroup)) {
            return null;
        }
        ViewGroup cell = (ViewGroup) getChildAt(0);
        if (cell.getChildCount() == 0 || !(cell.getChildAt(0) instanceof ViewGroup)) {
            return null;
        }
        ViewGroup root = (ViewGroup) cell.getChildAt(0);
        return index < root.getChildCount() ? root.getChildAt(index) : null;
    }

    private Rect bounds(View view) {
        Rect rect = new Rect(0, 0, view.getWidth(), view.getHeight());
        offsetDescendantRectToMyCoords(view, rect);
        return rect;
    }
}
