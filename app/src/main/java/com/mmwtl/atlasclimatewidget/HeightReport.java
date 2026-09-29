package com.mmwtl.atlasclimatewidget;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/** Explains how a layout uses its launcher cell and frames the preview like that cell. */
final class HeightReport {
    private HeightReport() {
    }

    static String describe(Context context, WidgetConfig config, WidgetGeometry.Plan plan) {
        float density = context.getApplicationContext().getResources().getDisplayMetrics().density;
        if (plan.availableHeight <= 0f) {
            return context.getString(R.string.height_report_unknown);
        }
        int available = Math.round(plan.availableHeight / density);
        int natural = Math.round(plan.naturalHeight / density);
        int used = Math.round(plan.totalHeight() / density);
        if (plan.naturalHeight > plan.availableHeight * WidgetGeometry.FIT_MARGIN) {
            int percent = Math.round(plan.verticalScale * 100f);
            return context.getString(plan.width < plan.fullWidth
                    ? R.string.height_report_squeezed_narrow : R.string.height_report_squeezed,
                    natural, available, percent);
        }
        if (config.heightMode == WidgetConfig.HeightMode.FILL) {
            return context.getString(R.string.height_report_filled, available);
        }
        return context.getString(R.string.height_report_content, used, available,
                Math.max(0, available - used));
    }

    /**
     * Wraps the inert preview in a frame with the cell's proportions, so empty space and
     * alignment look the way they will on the home screen.
     */
    static View framePreview(Context context, View preview, WidgetViews.Size size, int width) {
        FrameLayout cell = new FrameLayout(context);
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(0x00000000);
        outline.setStroke(Math.max(1, Ui.dp(context, 1)), 0x33FFFFFF);
        outline.setCornerRadius(Ui.dp(context, 6));
        cell.setBackground(outline);
        cell.addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                size.heightPx > 0 ? ViewGroup.LayoutParams.MATCH_PARENT
                        : ViewGroup.LayoutParams.WRAP_CONTENT));
        int height = size.heightPx > 0
                ? Math.round(width * (float) size.heightPx / size.widthPx)
                : ViewGroup.LayoutParams.WRAP_CONTENT;
        cell.setLayoutParams(new FrameLayout.LayoutParams(width, height,
                Gravity.CENTER_HORIZONTAL));
        return cell;
    }
}
