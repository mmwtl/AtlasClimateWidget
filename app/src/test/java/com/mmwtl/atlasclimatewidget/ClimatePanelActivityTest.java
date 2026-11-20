package com.mmwtl.atlasclimatewidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import android.view.ViewConfiguration;
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
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "w720dp-h960dp-mdpi")
public final class ClimatePanelActivityTest {
    @Test
    public void firstDragPreviewsAndSendsOnlyTheReleasedStep() {
        try (ActivityController<ClimatePanelActivity> controller = open()) {
            ClimatePanelActivity activity = controller.get();
            ClimateService.PanelFrame frame = frame(activity, 0);
            ShadowApplication app = Shadows.shadowOf(RuntimeEnvironment.getApplication());
            assertEquals(ClimateService.ACTION_REFRESH, app.getNextStartedService().getAction());

            for (WidgetGeometry.RowKind kind : new WidgetGeometry.RowKind[]{
                    WidgetGeometry.RowKind.TEMPERATURE, WidgetGeometry.RowKind.FAN}) {
                ViewGroup root = layout(activity);
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
                Drawable releasedPreview = image.getDrawable();
                activity.show(frame);
                assertSame(root, activity.findViewById(R.id.widget_root));
                assertNull(app.getNextStartedService());
                assertNull(app.getNextStartedActivity());

                touch(root, row, zones, last, MotionEvent.ACTION_UP);
                Intent command = app.getNextStartedService();
                assertNotNull(command);
                assertEquals(ClimateService.ACTION_CONTROL, command.getAction());
                assertEquals("/" + WidgetViews.control(frame.config(), strip, last, frame.auto()),
                        command.getData().getPath());
                long control = command.getLongExtra(ClimateService.EXTRA_PANEL_CONTROL, 0);
                assertTrue(control > 0);
                assertNull(app.getNextStartedService());
                assertNull(app.getNextStartedActivity());
                assertSame(releasedPreview, image.getDrawable());

                activity.show(frame);
                assertSame(root, activity.findViewById(R.id.widget_root));
                assertSame(releasedPreview, image.getDrawable());

                touch(root, row, zones, first, MotionEvent.ACTION_DOWN);
                touch(root, row, zones, last, MotionEvent.ACTION_CANCEL);
                assertSame(releasedPreview, image.getDrawable());
                assertNull(app.getNextStartedService());

                frame = frame(activity, control);
                activity.show(frame);
                assertNotSame(root, activity.findViewById(R.id.widget_root));
            }
        }
    }

    @Test
    public void longPressOnOptionSwitchesOffWithoutFiringTap() {
        try (ActivityController<ClimatePanelActivity> controller = open()) {
            ClimatePanelActivity activity = controller.get();
            ClimateService.PanelFrame frame = frame(activity, 0);
            ShadowApplication app = Shadows.shadowOf(RuntimeEnvironment.getApplication());
            assertEquals(ClimateService.ACTION_REFRESH, app.getNextStartedService().getAction());

            ViewGroup root = layout(activity);
            WidgetGeometry.Strip tiles = frame.plan().strips.stream()
                    .filter(item -> item.row.kind == WidgetGeometry.RowKind.TILES)
                    .findFirst().orElseThrow();
            View row = root.getChildAt(frame.plan().strips.indexOf(tiles));
            ViewGroup zones = row.findViewById(R.id.strip_zones);

            long downAt = SystemClock.uptimeMillis();
            dispatch(activity, root, row, zones, 0, MotionEvent.ACTION_DOWN, downAt, downAt);
            // A background refresh while holding must not recreate the views and cancel long press.
            activity.show(frame(activity, 0));
            assertSame(root, activity.findViewById(R.id.widget_root));

            ShadowLooper.idleMainLooper(ViewConfiguration.getLongPressTimeout(),
                    TimeUnit.MILLISECONDS);
            Intent command = app.getNextStartedService();
            assertNotNull(command);
            assertEquals(ClimateService.ACTION_CONTROL, command.getAction());
            assertEquals("/fnoff/DRIVER_HEAT", command.getData().getPath());
            long control = command.getLongExtra(ClimateService.EXTRA_PANEL_CONTROL, 0);
            assertTrue(control > 0);

            // Older frames wait, while the service's answer replaces the view even before release.
            activity.show(frame(activity, control - 1));
            assertSame(root, activity.findViewById(R.id.widget_root));
            activity.show(frame(activity, control));
            assertNotSame(root, activity.findViewById(R.id.widget_root));

            // Releasing after the frame updated does not fire the underlying tap.
            MotionEvent up = motion(root, row, zones, 0, MotionEvent.ACTION_UP, downAt,
                    SystemClock.uptimeMillis());
            try {
                activity.dispatchTouchEvent(up);
            } finally {
                up.recycle();
            }
            ShadowLooper.idleMainLooper();
            assertNull(app.getNextStartedService());

            // Releasing before the service responds also never fires the underlying tap.
            ViewGroup secondRoot = layout(activity);
            View secondRow = secondRoot.getChildAt(frame.plan().strips.indexOf(tiles));
            ViewGroup secondZones = secondRow.findViewById(R.id.strip_zones);
            long secondDown = SystemClock.uptimeMillis();
            dispatch(activity, secondRoot, secondRow, secondZones, 0, MotionEvent.ACTION_DOWN,
                    secondDown, secondDown);
            ShadowLooper.idleMainLooper(ViewConfiguration.getLongPressTimeout(),
                    TimeUnit.MILLISECONDS);
            assertEquals("/fnoff/DRIVER_HEAT", app.getNextStartedService().getData().getPath());
            dispatch(activity, secondRoot, secondRow, secondZones, 0, MotionEvent.ACTION_UP,
                    secondDown, SystemClock.uptimeMillis());
            ShadowLooper.idleMainLooper();
            assertNull(app.getNextStartedService());

            // The bar fan layout's AUTO column and direction buttons also support long press off.
            WidgetGeometry.Strip fanTop = frame.plan().strips.stream()
                    .filter(item -> item.row.kind == WidgetGeometry.RowKind.FAN_TOP)
                    .findFirst().orElseThrow();
            assertEquals("fnoff/AUTO",
                    WidgetViews.longPressControl(frame.config(), fanTop, 0, false));
            assertEquals("fnoff/BLOW_WINDOW", WidgetViews.longPressControl(frame.config(),
                    fanTop, fanTop.partStart(0, 3), false));
            assertNull(WidgetViews.longPressControl(frame.config(),
                    fanTop, fanTop.partStart(0, 3), true));
        }
    }

    @Test
    public void shortTapOnOptionStillTriggersNormalControl() {
        try (ActivityController<ClimatePanelActivity> controller = open()) {
            ClimatePanelActivity activity = controller.get();
            ClimateService.PanelFrame frame = frame(activity, 0);
            ShadowApplication app = Shadows.shadowOf(RuntimeEnvironment.getApplication());
            assertEquals(ClimateService.ACTION_REFRESH, app.getNextStartedService().getAction());

            ViewGroup root = layout(activity);
            WidgetGeometry.Strip tiles = frame.plan().strips.stream()
                    .filter(item -> item.row.kind == WidgetGeometry.RowKind.TILES)
                    .findFirst().orElseThrow();
            View row = root.getChildAt(frame.plan().strips.indexOf(tiles));
            ViewGroup zones = row.findViewById(R.id.strip_zones);

            long downAt = SystemClock.uptimeMillis();
            dispatch(activity, root, row, zones, 0, MotionEvent.ACTION_DOWN, downAt, downAt);
            activity.show(frame(activity, 0));
            assertSame(root, activity.findViewById(R.id.widget_root));

            ShadowLooper.idleMainLooper(50, TimeUnit.MILLISECONDS);
            dispatch(activity, root, row, zones, 0, MotionEvent.ACTION_UP, downAt,
                    SystemClock.uptimeMillis());
            ShadowLooper.idleMainLooper();
            Intent command = app.getNextStartedService();
            assertNotNull(command);
            assertEquals(ClimateService.ACTION_CONTROL, command.getAction());
            assertEquals("/fn/DRIVER_HEAT", command.getData().getPath());
            assertNull(app.getNextStartedService());
            assertNotSame(root, activity.findViewById(R.id.widget_root));
        }
    }

    @Test
    public void nonOffOptionsDoNotAttachLongPress() {
        Prefs prefs = new Prefs(RuntimeEnvironment.getApplication());
        WidgetConfig config = new WidgetConfig();
        config.infoEnabled = false;
        config.temperatureEnabled = false;
        config.fanEnabled = false;
        config.tilesEnabled = true;
        config.columns = 4;
        config.functions.clear();
        config.functions.add(ClimateFunction.ME_HOT);
        config.functions.add(ClimateFunction.ALL_OFF);
        config.functions.add(ClimateFunction.FAN_SOFT);
        config.functions.add(ClimateFunction.DRIVER_HEAT);
        prefs.setWidget(Prefs.PANEL_ID, config);

        try (ActivityController<ClimatePanelActivity> controller = open()) {
            ClimatePanelActivity activity = controller.get();
            ClimateService.PanelFrame frame = frame(activity, 0);
            ViewGroup root = layout(activity);
            WidgetGeometry.Strip tiles = frame.plan().strips.get(0);
            View row = root.getChildAt(0);
            ViewGroup zones = row.findViewById(R.id.strip_zones);
            for (int cell = 0; cell < 3; cell++) {
                assertNull(WidgetViews.longPressControl(frame.config(), tiles, cell, false));
                assertFalse(zones.getChildAt(cell).isLongClickable());
            }
            assertEquals("fnoff/DRIVER_HEAT",
                    WidgetViews.longPressControl(frame.config(), tiles, 3, false));
            assertTrue(zones.getChildAt(3).isLongClickable());
        }
    }

    private ActivityController<ClimatePanelActivity> open() {
        ClimateService.STORE.clear();
        return Robolectric.buildActivity(ClimatePanelActivity.class).setup();
    }

    private ClimateService.PanelFrame frame(ClimatePanelActivity activity, long appliedControl) {
        Prefs prefs = new Prefs(activity);
        ClimateState state = ClimateState.EMPTY;
        WidgetViews.Size size = ClimatePanelActivity.size(activity, prefs, state);
        return ClimateService.buildPanel(WidgetTheme.themed(activity), prefs, state, size, null,
                appliedControl);
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
        long now = SystemClock.uptimeMillis();
        MotionEvent event = motion(root, row, zones, cell, action, now, now);
        try {
            assertTrue(root.dispatchTouchEvent(event));
        } finally {
            event.recycle();
        }
    }

    private void dispatch(ClimatePanelActivity activity, ViewGroup root, View row, ViewGroup zones,
            int cell, int action, long downTime, long eventTime) {
        MotionEvent event = motion(root, row, zones, cell, action, downTime, eventTime);
        try {
            assertTrue(activity.dispatchTouchEvent(event));
        } finally {
            event.recycle();
        }
    }

    private MotionEvent motion(ViewGroup root, View row, ViewGroup zones, int cell, int action,
            long downTime, long eventTime) {
        View zone = zones.getChildAt(cell);
        float x = row.getLeft() + zones.getLeft() + zone.getLeft() + zone.getWidth() / 2f;
        float y = row.getTop() + zones.getTop() + zone.getTop() + zone.getHeight() / 2f;
        return MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
    }
}
