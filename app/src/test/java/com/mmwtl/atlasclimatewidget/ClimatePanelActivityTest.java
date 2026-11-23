package com.mmwtl.atlasclimatewidget;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.IBinder;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.geely.lib.oneosapi.IServiceManager;
import com.geely.lib.oneosapi.IServiceManagerCallback;
import com.geely.lib.oneosapi.input.IInputListener;
import com.geely.lib.oneosapi.input.IInputManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "w720dp-h960dp-mdpi")
public final class ClimatePanelActivityTest {
    @Test
    public void disablingSteeringKeepsTheWindowOpenWithoutRegisteringKeys() {
        android.app.Application application = RuntimeEnvironment.getApplication();
        Prefs prefs = new Prefs(application);
        prefs.setPanelSteeringEnabled(false);
        prefs.setPanelShowOutline(true);
        ShadowApplication app = Shadows.shadowOf(application);
        TestOneOs oneOs = new TestOneOs();
        oneOs.ready = true;
        ComponentName component = new ComponentName("com.geely.service.oneosapi",
                "com.geely.service.oneosapi.OneOSApiService");
        app.setComponentNameAndServiceForBindServiceForIntent(
                new Intent().setComponent(component), component, oneOs);

        try (ActivityController<ClimatePanelActivity> controller = open()) {
            controller.windowFocusChanged(true);
            ShadowLooper.idleMainLooper();
            assertEquals(ClimateService.ACTION_REFRESH, app.getNextStartedService().getAction());
            assertNotNull(controller.get().findViewById(R.id.widget_root));
            assertNull(panelView(controller.get()).steeringFocus());
            assertNull(oneOs.services);
            assertNull(oneOs.listener);
        }
    }

    @Test
    public void steeringSubscribesWhenInputBecomesAvailableAndStopsAtFocusLoss() throws Exception {
        ShadowApplication app = Shadows.shadowOf(RuntimeEnvironment.getApplication());
        TestOneOs oneOs = new TestOneOs();
        ComponentName component = new ComponentName("com.geely.service.oneosapi",
                "com.geely.service.oneosapi.OneOSApiService");
        app.setComponentNameAndServiceForBindServiceForIntent(
                new Intent().setComponent(component), component, oneOs);

        try (ActivityController<ClimatePanelActivity> controller = open()) {
            InteractiveClimateView view = panelView(controller.get());
            controller.windowFocusChanged(true);
            ShadowLooper.idleMainLooper();
            assertEquals(ClimateService.ACTION_REFRESH, app.getNextStartedService().getAction());
            assertEquals(component, app.getNextStartedService().getComponent());
            assertNotNull(oneOs.services);
            assertNull(oneOs.listener);
            assertNull(view.steeringFocus());

            oneOs.ready = true;
            oneOs.services.onServiceChanged(8);
            ShadowLooper.idleMainLooper();
            int[] codes = {200087, 200088, 200085};
            assertArrayEquals(codes, oneOs.codes);
            assertEquals(WidgetConfig.Block.TEMPERATURE, view.steeringFocus());
            IInputListener listener = oneOs.listener;
            assertNotNull(listener);
            listener.onKeyCodeEvent(200087, 0, 0);
            listener.onKeyCodeEvent(200087, 1, 0);
            listener.onShortClick(200024, 0);
            ShadowLooper.idleMainLooper();
            assertNull(app.getNextStartedService());

            // Default target is driver temperature.
            String temperature = "/tempstep/" + Hvac.ZONE_DRIVER;
            listener.onShortClick(OneOsPanelKeys.MEDIA_NEXT, 0);
            ShadowLooper.idleMainLooper();
            assertControl(app.getNextStartedService(), temperature + "/1");
            listener.onShortClick(OneOsPanelKeys.MEDIA_PREVIOUS, 0);
            ShadowLooper.idleMainLooper();
            assertControl(app.getNextStartedService(), temperature + "/-1");

            // Play/Pause toggles focus to fan and back without sending a car command.
            listener.onShortClick(OneOsPanelKeys.MEDIA_PLAY_PAUSE, 0);
            ShadowLooper.idleMainLooper();
            assertNull(app.getNextStartedService());
            assertEquals(WidgetConfig.Block.FAN, view.steeringFocus());
            listener.onShortClick(OneOsPanelKeys.MEDIA_NEXT, 0);
            ShadowLooper.idleMainLooper();
            assertControl(app.getNextStartedService(), "/fanstep/1");
            listener.onShortClick(OneOsPanelKeys.MEDIA_PREVIOUS, 0);
            ShadowLooper.idleMainLooper();
            assertControl(app.getNextStartedService(), "/fanstep/-1");

            listener.onShortClick(OneOsPanelKeys.MEDIA_PLAY_PAUSE, 0);
            ShadowLooper.idleMainLooper();
            assertNull(app.getNextStartedService());
            assertEquals(WidgetConfig.Block.TEMPERATURE, view.steeringFocus());

            // Holding an arrow repeats steps until released.
            listener.onLongPressTriggered(OneOsPanelKeys.MEDIA_NEXT, 0);
            ShadowLooper.idleMainLooper();
            assertControl(app.getNextStartedService(), temperature + "/1");
            ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS);
            assertControl(app.getNextStartedService(), temperature + "/1");
            listener.onHoldingPressStopped(OneOsPanelKeys.MEDIA_NEXT, 0);
            ShadowLooper.idleMainLooper(1000, TimeUnit.MILLISECONDS);
            assertNull(app.getNextStartedService());

            controller.windowFocusChanged(false);
            assertNull(oneOs.listener);
            assertNull(oneOs.services);
            assertNull(view.steeringFocus());
            listener.onShortClick(OneOsPanelKeys.MEDIA_NEXT, 0);
            ShadowLooper.idleMainLooper();
            assertNull(app.getNextStartedService());

            controller.windowFocusChanged(true);
            ShadowLooper.idleMainLooper();
            assertEquals(component, app.getNextStartedService().getComponent());
            assertNotNull(oneOs.listener);
            assertNotSame(listener, oneOs.listener);
            assertEquals(WidgetConfig.Block.TEMPERATURE, view.steeringFocus());
            oneOs.listener.onShortClick(OneOsPanelKeys.MEDIA_NEXT, 0);
            controller.get().finish();
            assertNull(oneOs.listener);
            assertNull(oneOs.services);
            assertNull(view.steeringFocus());
            ShadowLooper.idleMainLooper();
            assertNull(app.getNextStartedService());
        }
    }

    @Test
    public void steeringOnlySubscribesToEnabledPanelBlocks() throws Exception {
        ShadowApplication app = Shadows.shadowOf(RuntimeEnvironment.getApplication());
        ComponentName component = new ComponentName("com.geely.service.oneosapi",
                "com.geely.service.oneosapi.OneOSApiService");
        Prefs prefs = new Prefs(RuntimeEnvironment.getApplication());

        // Temperature only: subscribes only to arrows (no Play/Pause) and steps temperature.
        TestOneOs tempOnly = new TestOneOs();
        tempOnly.ready = true;
        app.setComponentNameAndServiceForBindServiceForIntent(
                new Intent().setComponent(component), component, tempOnly);
        WidgetConfig config = new WidgetConfig();
        config.temperatureEnabled = true;
        config.fanEnabled = false;
        prefs.setWidget(Prefs.PANEL_ID, config);
        try (ActivityController<ClimatePanelActivity> controller = open()) {
            InteractiveClimateView view = panelView(controller.get());
            controller.windowFocusChanged(true);
            ShadowLooper.idleMainLooper();
            assertEquals(ClimateService.ACTION_REFRESH, app.getNextStartedService().getAction());
            assertEquals(component, app.getNextStartedService().getComponent());
            assertArrayEquals(new int[]{200087, 200088}, tempOnly.codes);
            assertEquals(WidgetConfig.Block.TEMPERATURE, view.steeringFocus());
            tempOnly.listener.onShortClick(OneOsPanelKeys.MEDIA_PLAY_PAUSE, 0);
            ShadowLooper.idleMainLooper();
            assertNull(app.getNextStartedService());
            assertEquals(WidgetConfig.Block.TEMPERATURE, view.steeringFocus());
            tempOnly.listener.onShortClick(OneOsPanelKeys.MEDIA_NEXT, 0);
            ShadowLooper.idleMainLooper();
            assertControl(app.getNextStartedService(), "/tempstep/" + Hvac.ZONE_DRIVER + "/1");
        }

        // Fan only: subscribes only to arrows, focuses fan, and steps fan.
        TestOneOs fanOnly = new TestOneOs();
        fanOnly.ready = true;
        app.setComponentNameAndServiceForBindServiceForIntent(
                new Intent().setComponent(component), component, fanOnly);
        config = new WidgetConfig();
        config.temperatureEnabled = false;
        config.fanEnabled = true;
        prefs.setWidget(Prefs.PANEL_ID, config);
        try (ActivityController<ClimatePanelActivity> controller = open()) {
            InteractiveClimateView view = panelView(controller.get());
            controller.windowFocusChanged(true);
            ShadowLooper.idleMainLooper();
            assertEquals(ClimateService.ACTION_REFRESH, app.getNextStartedService().getAction());
            assertEquals(component, app.getNextStartedService().getComponent());
            assertArrayEquals(new int[]{200087, 200088}, fanOnly.codes);
            assertEquals(WidgetConfig.Block.FAN, view.steeringFocus());
            fanOnly.listener.onShortClick(OneOsPanelKeys.MEDIA_NEXT, 0);
            ShadowLooper.idleMainLooper();
            assertControl(app.getNextStartedService(), "/fanstep/1");
        }

        // Neither block enabled: does not bind OneOS at all.
        TestOneOs tilesOnly = new TestOneOs();
        tilesOnly.ready = true;
        app.setComponentNameAndServiceForBindServiceForIntent(
                new Intent().setComponent(component), component, tilesOnly);
        config = new WidgetConfig();
        config.temperatureEnabled = false;
        config.fanEnabled = false;
        prefs.setWidget(Prefs.PANEL_ID, config);
        try (ActivityController<ClimatePanelActivity> controller = open()) {
            controller.windowFocusChanged(true);
            ShadowLooper.idleMainLooper();
            assertEquals(ClimateService.ACTION_REFRESH, app.getNextStartedService().getAction());
            assertNull(app.getNextStartedService());
            assertNull(tilesOnly.services);
            assertNull(tilesOnly.listener);
        }
    }

    @Test
    public void steeringCloseToleratesUnbindFailureAndReleasesCallback() {
        int[] received = {0};
        android.content.ContextWrapper failingUnbind = new android.content.ContextWrapper(
                RuntimeEnvironment.getApplication()) {
            @Override
            public android.content.Context getApplicationContext() {
                return this;
            }

            @Override
            public boolean bindService(Intent service, android.content.ServiceConnection conn,
                    int flags) {
                return true;
            }

            @Override
            public void unbindService(android.content.ServiceConnection conn) {
                throw new IllegalArgumentException("Service not registered");
            }
        };
        TestOneOs oneOs = new TestOneOs();
        oneOs.ready = true;
        OneOsPanelKeys keys = new OneOsPanelKeys(failingUnbind, OneOsPanelKeys.keys(true, true),
                new OneOsPanelKeys.Listener() {
                    @Override public void onShortClick(int keyCode) { received[0] = keyCode; }
                    @Override public void onLongPress(int keyCode) { received[0] = keyCode; }
                    @Override public void onHoldStop(int keyCode) { received[0] = keyCode; }
                });
        keys.open();
        keys.onServiceConnected(new ComponentName("com.geely.service.oneosapi",
                "com.geely.service.oneosapi.OneOSApiService"), oneOs);
        IInputListener registered = oneOs.listener;
        assertNotNull(registered);

        keys.close();
        assertNull(oneOs.listener);
        assertNull(oneOs.services);
        try {
            registered.onShortClick(OneOsPanelKeys.MEDIA_NEXT, 0);
            registered.onLongPressTriggered(OneOsPanelKeys.MEDIA_NEXT, 0);
        } catch (Exception error) {
            throw new AssertionError(error);
        }
        ShadowLooper.idleMainLooper();
        assertEquals(0, received[0]);
    }

    @Test
    public void steeringFocusDrawsInSeparateAndSingleCardLayouts() {
        Prefs prefs = new Prefs(RuntimeEnvironment.getApplication());
        prefs.setPanelShowOutline(true);
        for (WidgetConfig.CardLayout cardLayout : WidgetConfig.CardLayout.values()) {
            WidgetConfig config = new WidgetConfig();
            config.cardLayout = cardLayout;
            prefs.setWidget(Prefs.PANEL_ID, config);
            try (ActivityController<ClimatePanelActivity> controller = open()) {
                ClimatePanelActivity activity = controller.get();
                InteractiveClimateView view = panelView(activity);
                int width = activity.getWindow().getAttributes().width;
                int height = activity.getWindow().getAttributes().height;
                view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                view.layout(0, 0, width, height);
                for (WidgetConfig.Block focus : new WidgetConfig.Block[]{
                        WidgetConfig.Block.TEMPERATURE, WidgetConfig.Block.FAN}) {
                    view.setSteeringFocus(focus);
                    android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                            width, height, android.graphics.Bitmap.Config.ARGB_8888);
                    view.draw(new android.graphics.Canvas(bitmap));
                    bitmap.recycle();
                }
            }
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void hiddenOutlineLeavesTheFocusedBlockVisuallyUnchanged() {
        Prefs prefs = new Prefs(RuntimeEnvironment.getApplication());
        prefs.setPanelShowOutline(false);
        try (ActivityController<ClimatePanelActivity> controller = open()) {
            ClimatePanelActivity activity = controller.get();
            InteractiveClimateView view = panelView(activity);
            int width = activity.getWindow().getAttributes().width;
            int height = activity.getWindow().getAttributes().height;
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            view.layout(0, 0, width, height);
            Bitmap before = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Bitmap after = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            try {
                view.draw(new Canvas(before));
                view.setSteeringFocus(WidgetConfig.Block.TEMPERATURE);
                view.draw(new Canvas(after));
                assertEquals(WidgetConfig.Block.TEMPERATURE, view.steeringFocus());
                assertTrue(before.sameAs(after));
            } finally {
                before.recycle();
                after.recycle();
            }
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void doubleOutlineUsesTheCardColorToOrderItsTwoOpaqueStrokes() {
        Prefs prefs = new Prefs(RuntimeEnvironment.getApplication());
        prefs.setThemeMode(WidgetTheme.FIXED_DAY);
        prefs.setPanelShowOutline(true);
        WidgetConfig config = new WidgetConfig();
        config.cardOpacityPercent = 50;
        config.dayCardColor = 0xFF232528;
        prefs.setWidget(Prefs.PANEL_ID, config);
        assertDoubleOutline(0xFF111A20, 0xFFBAE5F6);

        config.dayCardColor = 0xFFEDEEF0;
        prefs.setWidget(Prefs.PANEL_ID, config);
        assertDoubleOutline(Color.WHITE, 0xFF204B61);
    }

    private void assertDoubleOutline(int outer, int inner) {
        try (ActivityController<ClimatePanelActivity> controller = open()) {
            ClimatePanelActivity activity = controller.get();
            InteractiveClimateView view = panelView(activity);
            int width = activity.getWindow().getAttributes().width;
            int height = activity.getWindow().getAttributes().height;
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            view.layout(0, 0, width, height);
            view.setSteeringFocus(WidgetConfig.Block.TEMPERATURE);
            WidgetGeometry.Plan plan = frame(activity, 0).plan();
            int index = 0;
            while (plan.strips.get(index).block != WidgetConfig.Block.TEMPERATURE) {
                index++;
            }
            View row = ((ViewGroup) view.getChildAt(0)).getChildAt(index);
            Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            try {
                view.draw(new Canvas(image));
                assertEquals(outer, image.getPixel(width / 2, row.getTop() + 1));
                assertEquals(inner, image.getPixel(width / 2, row.getTop() + 3));
            } finally {
                image.recycle();
            }
        }
    }

    private static InteractiveClimateView panelView(ClimatePanelActivity activity) {
        return (InteractiveClimateView) activity.findViewById(R.id.widget_root).getParent();
    }

    private static void assertControl(Intent intent, String expectedPath) {
        assertNotNull(intent);
        assertEquals(ClimateService.ACTION_CONTROL, intent.getAction());
        assertEquals(expectedPath, intent.getData().getPath());
    }

    private static final class TestOneOs extends IServiceManager.Stub {
        boolean ready;
        IServiceManagerCallback services;
        IInputListener listener;
        int[] codes;

        private final IInputManager.Stub input = new IInputManager.Stub() {
            @Override
            public void registerListener(IInputListener listener, String tag, int[] keys) {
                assertEquals("com.mmwtl.atlasclimatewidget", tag);
                assertNull(TestOneOs.this.listener);
                TestOneOs.this.listener = listener;
                codes = keys;
            }

            @Override
            public void unregisterListener(IInputListener listener, String tag) {
                assertSame(TestOneOs.this.listener, listener);
                TestOneOs.this.listener = null;
            }
        };

        @Override
        public IBinder getService(int type) {
            assertEquals(8, type);
            return ready ? input : null;
        }

        @Override
        public void registerCallback(IServiceManagerCallback callback) {
            services = callback;
        }

        @Override
        public void unregisterCallback(IServiceManagerCallback callback) {
            assertSame(services, callback);
            services = null;
        }
    }

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
    public void autoFanBarDragsAcrossPresetsAndOnlySendsTheReleasedPreset() {
        try (ActivityController<ClimatePanelActivity> controller = open()) {
            ClimatePanelActivity activity = controller.get();
            Prefs prefs = new Prefs(activity);
            prefs.setFanPresets(Prefs.FanPresets.FIVE);
            long now = SystemClock.elapsedRealtime();
            ClimateService.STORE.putProperty(Hvac.AUTO_FAN_SETTING, Hvac.ZONE_ROW_1_ALL,
                    Hvac.AUTO_FAN_NORMAL, now);
            ClimateState state = ClimateService.STORE.snapshot(now);
            WidgetViews.Size size = ClimatePanelActivity.size(activity, prefs, state);
            ClimateService.PanelFrame frame = ClimateService.buildPanel(WidgetTheme.themed(activity),
                    prefs, state, size, null, 0);
            assertTrue(frame.auto());
            activity.show(frame);

            ShadowApplication app = Shadows.shadowOf(RuntimeEnvironment.getApplication());
            assertEquals(ClimateService.ACTION_REFRESH, app.getNextStartedService().getAction());
            ViewGroup root = layout(activity);
            WidgetGeometry.Strip strip = frame.plan().strips.stream()
                    .filter(item -> item.row.kind == WidgetGeometry.RowKind.FAN)
                    .findFirst().orElseThrow();
            View row = root.getChildAt(frame.plan().strips.indexOf(strip));
            ViewGroup zones = row.findViewById(R.id.strip_zones);
            ImageView image = row.findViewById(R.id.strip_image);
            Drawable original = image.getDrawable();
            int last = strip.levelStart(4, 5);
            touch(root, row, zones, strip.stepStart, MotionEvent.ACTION_DOWN);
            assertNotSame(original, image.getDrawable());
            touch(root, row, zones, last, MotionEvent.ACTION_MOVE);
            assertNull(app.getNextStartedService());
            touch(root, row, zones, last, MotionEvent.ACTION_UP);
            assertEquals("/fanpreset/4", app.getNextStartedService().getData().getPath());
            assertNull(app.getNextStartedService());
            assertNull(app.getNextStartedActivity());
        }
    }

    @Test
    public void longPressOnOptionSwitchesOffWithoutFiringTap() {
        Prefs prefs = new Prefs(RuntimeEnvironment.getApplication());
        WidgetConfig config = prefs.widget(Prefs.PANEL_ID);
        config.setTileLevels(ClimateFunction.DRIVER_HEAT, 2);
        prefs.setWidget(Prefs.PANEL_ID, config);

        try (ActivityController<ClimatePanelActivity> controller = open()) {
            ClimatePanelActivity activity = controller.get();
            ClimateService.PanelFrame frame = frame(activity, 0);
            ShadowApplication app = Shadows.shadowOf(RuntimeEnvironment.getApplication());
            assertEquals(ClimateService.ACTION_REFRESH, app.getNextStartedService().getAction());

            ViewGroup root = layout(activity);
            WidgetGeometry.Strip tiles = frame.plan().strips.stream()
                    .filter(item -> item.row.kind == WidgetGeometry.RowKind.TILES)
                    .findFirst().orElseThrow();
            assertEquals("fn/DRIVER_HEAT/2", WidgetViews.control(frame.config(), tiles, 0));
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
