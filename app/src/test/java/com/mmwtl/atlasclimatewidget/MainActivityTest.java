package com.mmwtl.atlasclimatewidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
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
            assertTrue(shown(find(root, "Полоса температуры")));
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
    public void sharedBackupTextImportsAfterConfirmation() {
        SettingsBackup backup = new SettingsBackup();
        backup.carModel = CarModel.CITYRAY.name();
        backup.levelsFromMax = true;
        backup.template.columns = 4;
        Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "копия: " + backup.toJson(false));
        ActivityController<MainActivity> controller =
                Robolectric.buildActivity(MainActivity.class, share).create().start().resume();
        try {
            AlertDialog dialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
            assertNotNull(dialog);
            assertTrue(dialog.isShowing());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            ShadowLooper.runUiThreadTasks();
            Prefs prefs = new Prefs(controller.get());
            assertEquals(CarModel.CITYRAY, prefs.carModel());
            assertTrue(prefs.levelsFromMax());
            assertEquals(4, prefs.template().columns);
            assertTrue("handled share is not offered again after recreation",
                    ShadowAlertDialog.getLatestDialog() == dialog);
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
        String json = prefs.exportBackup(new int[]{12, 5}).toJson(true);
        prefs.raw().edit().clear().commit();

        prefs.importBackup(SettingsBackup.parse(json), new int[]{40, 31, 50});
        assertEquals(Prefs.FanPresets.FIVE, prefs.fanPresets());
        assertEquals(3, prefs.widget(31).columns);
        assertEquals(WidgetConfig.Style.CONSOLE, prefs.widget(40).style);
        assertTrue("extra widget gets the template", prefs.hasWidget(50));
        assertEquals(new WidgetConfig().columns, prefs.widget(50).columns);
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
