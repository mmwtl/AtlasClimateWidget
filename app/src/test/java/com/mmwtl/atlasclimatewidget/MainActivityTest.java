package com.mmwtl.atlasclimatewidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public final class MainActivityTest {

    @Test
    public void tabsSwitchPagesAndSelectionSurvivesSavedState() {
        ActivityController<MainActivity> controller = open();
        try {
            View root = root(controller);
            for (String tab : new String[]{"Блоки", "Плитки", "Вид", "Система"}) {
                assertTrue(tab, shown(find(root, tab)));
            }
            assertTrue(shown(find(root, "Высота виджета")));
            assertTrue(shown(find(root, "Строка датчиков")));
            assertFalse(shown(find(root, "Сетка плиток")));
            assertTrue("preview on layout tabs", shown(find(root, "Предпросмотр")));

            find(root, "Плитки").performClick();
            assertTrue(shown(find(root, "Сетка плиток")));
            assertTrue(shown(find(root, "Функции")));
            assertFalse(shown(find(root, "Высота виджета")));

            find(root, "Вид").performClick();
            assertTrue(shown(find(root, "Оформление")));
            assertFalse(shown(find(root, "Сетка плиток")));

            find(root, "Система").performClick();
            assertTrue(shown(find(root, "Связь с автомобилем")));
            assertTrue(shown(find(root, "Автомобиль")));
            assertTrue(shown(find(root, "Интерфейс приложения")));
            assertFalse("preview hidden on System", shown(find(root, "Предпросмотр")));

            Bundle state = new Bundle();
            controller.saveInstanceState(state);
            ActivityController<MainActivity> restored =
                    Robolectric.buildActivity(MainActivity.class).create(state);
            try {
                View restoredRoot = root(restored);
                assertTrue(shown(find(restoredRoot, "Связь с автомобилем")));
                assertFalse(shown(find(restoredRoot, "Высота виджета")));
            } finally {
                restored.destroy();
            }
        } finally {
            close(controller);
        }
    }

    @Test
    public void displayModeSwitchKeepsWidgetAndOverlayLayouts() {
        Prefs prefs = new Prefs(RuntimeEnvironment.getApplication());
        WidgetConfig placed = new WidgetConfig();
        placed.temperatureDual = true;
        prefs.setWidget(42, placed);
        WidgetConfig overlay = new WidgetConfig();
        overlay.temperatureEnabled = false;
        prefs.setWidget(Prefs.OVERLAY_ID, overlay);

        ActivityController<MainActivity> controller = open();
        try {
            find(root(controller), "Система").performClick();
            assertTrue(prefs.isWidgetMode());
            assertTrue(shown(find(root(controller), "Добавить Android-виджет")));

            find(root(controller), "Оверлей").performClick();
            ShadowLooper.runUiThreadTasks();
            assertFalse(prefs.isWidgetMode());
            assertTrue(shown(find(root(controller), "Настроить макет оверлея")));
            assertFalse(shown(find(root(controller), "Добавить Android-виджет")));

            find(root(controller), "Виджет").performClick();
            ShadowLooper.runUiThreadTasks();
            assertTrue(prefs.isWidgetMode());
            assertTrue(prefs.widget(42).temperatureDual);
            assertFalse(prefs.widget(Prefs.OVERLAY_ID).temperatureEnabled);
        } finally {
            close(controller);
        }
    }

    @Test
    public void overlayManualHeightUsesSafeDisplayWithoutResizingShortcutWindow() {
        var context = RuntimeEnvironment.getApplication();
        Prefs prefs = new Prefs(context);
        WidgetConfig overlay = prefs.widget(Prefs.OVERLAY_ID);
        Rect bounds = new Rect(0, 24, 1440, 1864);
        WidgetViews.Size auto = ClimateOverlay.size(context, bounds, prefs, overlay, ClimateState.EMPTY);
        assertEquals(1325, auto.widthPx);
        assertTrue(auto.heightPx > 368);
        int shortcutHeight = ClimatePanelActivity.size(context, prefs, ClimateState.EMPTY).heightPx;

        SettingsBackup backup = prefs.exportBackup(new int[0]);
        backup.overlayHeightPercent = 20;
        prefs.importBackup(SettingsBackup.parse(backup.toJson("test")), new int[0]);
        WidgetViews.Size manual = ClimateOverlay.size(context, bounds, prefs, overlay, ClimateState.EMPTY);
        assertEquals(auto.widthPx, manual.widthPx);
        assertEquals(368, manual.heightPx);
        assertEquals(shortcutHeight, ClimatePanelActivity.size(context, prefs, ClimateState.EMPTY).heightPx);
        assertEquals(20, prefs.exportBackup(new int[0]).overlayHeightPercent);
    }

    @Test
    public void collapsibleSectionsStartClosedAndOpenFromHeader() {
        ActivityController<MainActivity> controller = open();
        try {
            View root = root(controller);
            find(root, "Вид").performClick();
            assertFalse(shown(find(root, "Внутренний отступ")));
            header(find(root, "Карточки и отступы")).performClick();
            assertTrue(shown(find(root, "Внутренний отступ")));
            header(find(root, "Карточки и отступы")).performClick();
            assertFalse(shown(find(root, "Внутренний отступ")));

            find(root, "Плитки").performClick();
            View available = header(find(root, "Доступные функции"));
            assertNotNull(available);
            assertFalse(shown(find(root, "Ионизатор")));
            available.performClick();
            assertTrue(shown(find(root, "Ионизатор")));
        } finally {
            close(controller);
        }
    }

    @Test
    public void bodyFunctionChoicesFollowModelChanges() {
        ActivityController<MainActivity> controller = open();
        try {
            View root = root(controller);
            for (String model : new String[]{"Geely Atlas", "Geely Preface", "Geely Cityray"}) {
                find(root, model).performClick();
                boolean preface = model.equals("Geely Preface");
                assertEquals(preface, find(root, "Фары") != null);
                assertEquals(!preface, find(root, "Память сидений (GInputBridge)") != null);
            }
        } finally {
            close(controller);
        }
    }

    @Test
    public void changingModelKeepsSavedUnsupportedTilesRemovable() {
        Prefs prefs = new Prefs(RuntimeEnvironment.getApplication());
        WidgetConfig config = new WidgetConfig();
        config.setFunctionEnabled(ClimateFunction.HEADLIGHTS, true);
        prefs.setCarModel(CarModel.PREFACE);
        prefs.setTemplate(config);
        ActivityController<MainActivity> controller = open();
        try {
            View root = root(controller);
            find(root, "Geely Atlas").performClick();
            String title = "Фары (недоступно для выбранной модели)";
            assertNotNull(find(root, title));
            assertTrue(prefs.template().functions.contains(ClimateFunction.HEADLIGHTS));
            find(root, title).performClick();
            ShadowLooper.runUiThreadTasks();
            assertFalse(prefs.template().functions.contains(ClimateFunction.HEADLIGHTS));
            assertTrue(find(root, title) == null);
        } finally {
            close(controller);
        }
    }

    @Test
    public void importButtonPicksFileAndImportsAfterConfirmation() throws Exception {
        SettingsBackup backup = new SettingsBackup();
        backup.carModel = CarModel.CITYRAY.name();
        backup.levelsFromMax = true;
        backup.template.columns = 4;
        java.io.File file = java.io.File.createTempFile("backup", ".json");
        file.deleteOnExit();
        java.nio.file.Files.write(file.toPath(),
                backup.toJson("test").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ActivityController<MainActivity> controller = open();
        try {
            View root = root(controller);
            find(root, "Система").performClick();
            find(root, "Импортировать настройки из JSON").performClick();
            ShadowActivity shadow = Shadows.shadowOf(controller.get());
            Intent picker = shadow.getNextStartedActivityForResult().intent;
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.getAction());
            shadow.receiveResult(picker, android.app.Activity.RESULT_OK,
                    new Intent().setData(android.net.Uri.fromFile(file)));

            AlertDialog dialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
            assertNotNull(dialog);
            assertTrue(dialog.isShowing());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            ShadowLooper.runUiThreadTasks();
            Prefs prefs = new Prefs(controller.get());
            assertEquals(CarModel.CITYRAY, prefs.carModel());
            assertTrue(prefs.levelsFromMax());
            assertEquals(4, prefs.template().columns);
        } finally {
            close(controller);
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void exportButtonSharesJsonFile() {
        ActivityController<MainActivity> controller = open();
        try {
            View root = root(controller);
            find(root, "Система").performClick();
            find(root, "Экспортировать настройки в JSON").performClick();
            Intent chooser = Shadows.shadowOf(controller.get()).getNextStartedActivity();
            assertEquals(Intent.ACTION_CHOOSER, chooser.getAction());
            Intent send = chooser.getParcelableExtra(Intent.EXTRA_INTENT);
            assertEquals(Intent.ACTION_SEND, send.getAction());
            assertEquals("application/json", send.getType());
            android.net.Uri uri = send.getParcelableExtra(Intent.EXTRA_STREAM);
            assertTrue(uri.getLastPathSegment().endsWith("_AtlasClimateWidget-settings.json"));
        } finally {
            close(controller);
        }
    }

    @Test
    public void exportedBackupRestoresWidgetsByPosition() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).create().get();
        Prefs prefs = new Prefs(activity);
        WidgetConfig first = new WidgetConfig();
        first.columns = 3;
        WidgetConfig second = new WidgetConfig();
        second.style = WidgetConfig.Style.CONSOLE;
        prefs.setWidget(12, second);
        prefs.setWidget(5, first);
        prefs.setFanPresets(Prefs.FanPresets.FIVE);
        String json = prefs.exportBackup(new int[]{12, 5}).toJson("test");
        prefs.raw().edit().clear().commit();

        prefs.importBackup(SettingsBackup.parse(json), new int[]{40, 31, 50});
        assertEquals(Prefs.FanPresets.FIVE, prefs.fanPresets());
        assertEquals(3, prefs.widget(31).columns);
        assertEquals(WidgetConfig.Style.CONSOLE, prefs.widget(40).style);
        assertTrue("extra widget gets the template", prefs.hasWidget(50));
        assertEquals(new WidgetConfig().columns, prefs.widget(50).columns);
    }

    @Test
    public void tileLevelsCollapseExpandToggleAndPersist() {
        ActivityController<MainActivity> controller = open();
        try {
            View root = root(controller);
            find(root, "Плитки").performClick();

            View level2 = findByDescription(root, "Подогрев водителя: 2");
            assertNotNull(level2);
            assertFalse(shown(level2));

            header(find(root, "Уровни плиток")).performClick();
            assertTrue(shown(level2));

            View level1 = findByDescription(root, "Подогрев водителя: 1");
            View level3 = findByDescription(root, "Подогрев водителя: 3");
            level1.performClick();
            level3.performClick();
            Prefs prefs = new Prefs(controller.get());
            assertTrue(java.util.Arrays.equals(new int[]{2},
                    prefs.template().activeLevels(ClimateFunction.DRIVER_HEAT)));
            assertFalse(level1.isSelected());
            assertTrue(level2.isSelected());
            assertFalse(level3.isSelected());

            // Clicking the last remaining level leaves it selected.
            level2.performClick();
            assertTrue(level2.isSelected());
            assertTrue(java.util.Arrays.equals(new int[]{2},
                    prefs.template().activeLevels(ClimateFunction.DRIVER_HEAT)));

            level3.performClick();
            assertTrue(level3.isSelected());
            assertTrue(java.util.Arrays.equals(new int[]{2, 3},
                    prefs.template().activeLevels(ClimateFunction.DRIVER_HEAT)));

            // Adding Sunroof reveals its position level labels in the expanded section.
            header(find(root, "Доступные функции")).performClick();
            find(root, "Люк").performClick();
            ShadowLooper.runUiThreadTasks();
            assertTrue(shown(findByDescription(root, "Люк: Проветр.")));
            assertTrue(shown(findByDescription(root, "Люк: 20%")));
            assertTrue(shown(findByDescription(root, "Люк: 100%")));
        } finally {
            close(controller);
        }
    }

    @Test
    public void tileLevelsShowsEmptyHintWhenNoMultiLevelTilesAreSelected() {
        Prefs prefs = new Prefs(RuntimeEnvironment.getApplication());
        WidgetConfig config = new WidgetConfig();
        config.functions.clear();
        config.functions.add(ClimateFunction.AC);
        prefs.setTemplate(config);
        ActivityController<MainActivity> controller = open();
        try {
            View root = root(controller);
            find(root, "Плитки").performClick();
            header(find(root, "Уровни плиток")).performClick();
            assertTrue(shown(find(root,
                    "На виджете нет плиток с несколькими уровнями — добавьте их в списке выше.")));
        } finally {
            close(controller);
        }
    }

    private static ActivityController<MainActivity> open() {
        return Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
    }

    private static void close(ActivityController<MainActivity> controller) {
        controller.pause().stop().destroy();
        ShadowLooper.runUiThreadTasks();
    }

    private static View root(ActivityController<MainActivity> controller) {
        return controller.get().findViewById(android.R.id.content);
    }

    /** The collapsible header is the grandparent of its title: header → title row → title. */
    private static View header(View title) {
        return (View) title.getParent().getParent();
    }

    private static View findByDescription(View view, String description) {
        CharSequence contentDescription = view.getContentDescription();
        if (contentDescription != null && description.contentEquals(contentDescription)) {
            return view;
        }
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                View match = findByDescription(group.getChildAt(index), description);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static View find(View view, String text) {
        if (view instanceof TextView label && text.contentEquals(label.getText())) {
            return view;
        }
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                View match = find(group.getChildAt(index), text);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    /** Visible on screen only if the view and every ancestor are VISIBLE. */
    private static boolean shown(View view) {
        if (view == null) {
            return false;
        }
        for (View current = view; current != null;
                current = current.getParent() instanceof View parent ? parent : null) {
            if (current.getVisibility() != View.VISIBLE) {
                return false;
            }
        }
        return true;
    }
}
