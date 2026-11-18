package com.mmwtl.atlasclimatewidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "w720dp-h960dp-mdpi")
public final class ClimatePanelActivityTest {
    @Test
    public void firstDragPreviewsAndSendsOnlyTheReleasedStep() {
        try (ActivityController<ClimatePanelActivity> controller = open()) {
            ViewGroup root = layout(controller.get());
            ClimateService.PanelFrame frame = frame(controller.get());
            ShadowApplication app = Shadows.shadowOf(RuntimeEnvironment.getApplication());
            assertEquals(ClimateService.ACTION_REFRESH, app.getNextStartedService().getAction());

            for (WidgetGeometry.RowKind kind : new WidgetGeometry.RowKind[]{
                    WidgetGeometry.RowKind.TEMPERATURE, WidgetGeometry.RowKind.FAN}) {
                WidgetGeometry.Strip strip = frame.plan().strips.stream()
                        .filter(item -> item.row.kind == kind).findFirst().orElseThrow();
                View row = root.getChildAt(frame.plan().strips.indexOf(strip));
                ViewGroup zones = row.findViewById(R.id.strip_zones);
                ImageView image = row.findViewById(R.id.strip_image);
                Drawable original = image.getDrawable();
                int first = strip.stepStart;
                int last = first + 3 * strip.stepSpan;

                touch(root, row, zones, first, MotionEvent.ACTION_DOWN);
                assertNotSame(original, image.getDrawable());
                Drawable firstPreview = image.getDrawable();
                touch(root, row, zones, last, MotionEvent.ACTION_MOVE);
                assertNotSame(firstPreview, image.getDrawable());
                assertNull(app.getNextStartedService());
                assertNull(app.getNextStartedActivity());

                touch(root, row, zones, last, MotionEvent.ACTION_UP);
                Intent command = app.getNextStartedService();
                assertNotNull(command);
                assertEquals(ClimateService.ACTION_CONTROL, command.getAction());
                assertEquals("/" + WidgetViews.control(frame.config(), strip, last, frame.auto()),
                        command.getData().getPath());
                assertNull(app.getNextStartedService());
                assertNull(app.getNextStartedActivity());
                assertSame(original, image.getDrawable());

                touch(root, row, zones, first, MotionEvent.ACTION_DOWN);
                touch(root, row, zones, last, MotionEvent.ACTION_CANCEL);
                assertSame(original, image.getDrawable());
                assertNull(app.getNextStartedService());
            }
        }
    }

    private ActivityController<ClimatePanelActivity> open() {
        ClimateService.STORE.clear();
        return Robolectric.buildActivity(ClimatePanelActivity.class).setup();
    }

    private ClimateService.PanelFrame frame(ClimatePanelActivity activity) {
        Prefs prefs = new Prefs(activity);
        ClimateState state = ClimateState.EMPTY;
        WidgetViews.Size size = ClimatePanelActivity.size(activity, prefs, state);
        return ClimateService.buildPanel(WidgetTheme.themed(activity), prefs, state, size, null);
    }

    private ViewGroup layout(ClimatePanelActivity activity) {
        ViewGroup root = activity.findViewById(R.id.widget_root);
        int width = activity.getWindow().getAttributes().width;
        int height = activity.getWindow().getAttributes().height;
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        return root;
    }

    private void touch(ViewGroup root, View row, ViewGroup zones, int cell, int action) {
        View zone = zones.getChildAt(cell);
        float x = row.getLeft() + zones.getLeft() + zone.getLeft() + zone.getWidth() / 2f;
        float y = row.getTop() + zones.getTop() + zone.getTop() + zone.getHeight() / 2f;
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        try {
            assertTrue(root.dispatchTouchEvent(event));
        } finally {
            event.recycle();
        }
    }
}
