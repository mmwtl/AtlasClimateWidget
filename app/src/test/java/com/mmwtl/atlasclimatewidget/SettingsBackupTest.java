package com.mmwtl.atlasclimatewidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SettingsBackupTest {
    @Test public void roundTripsSettingsTemplateAndWidgets() {
        SettingsBackup backup = new SettingsBackup();
        backup.carModel = CarModel.CITYRAY.name();
        backup.tempStepTenths = 10;
        backup.levelsFromMax = true;
        backup.fanPresets = "FIVE";
        backup.uiScaleTenths = 17;
        backup.template.columns = 4;
        WidgetConfig first = new WidgetConfig();
        first.style = WidgetConfig.Style.CONSOLE;
        WidgetConfig second = new WidgetConfig();
        second.setFunctionEnabled(ClimateFunction.ME_HOT, true);
        backup.widgets.add(first);
        backup.widgets.add(second);

        for (boolean indented : new boolean[]{false, true}) {
            SettingsBackup copy = SettingsBackup.parse(backup.toJson(indented));
            assertNotNull(copy);
            assertEquals(CarModel.CITYRAY.name(), copy.carModel);
            assertEquals(10, copy.tempStepTenths);
            assertTrue(copy.levelsFromMax);
            assertEquals("FIVE", copy.fanPresets);
            assertEquals(17, copy.uiScaleTenths);
            assertEquals(4, copy.template.columns);
            assertEquals(2, copy.widgets.size());
            assertEquals(WidgetConfig.Style.CONSOLE, copy.widgets.get(0).style);
            assertTrue(copy.widgets.get(1).functions.contains(ClimateFunction.ME_HOT));
        }
        System.out.println("compact backup: " + backup.toJson(false).length() + " chars");
    }

    @Test public void extraWidgetsTakeTheTemplate() {
        SettingsBackup backup = new SettingsBackup();
        backup.template.columns = 5;
        WidgetConfig only = new WidgetConfig();
        only.columns = 3;
        backup.widgets.add(only);
        assertEquals(3, backup.layoutFor(0).columns);
        assertEquals(5, backup.layoutFor(1).columns);
        backup.layoutFor(1).columns = 2;
        assertEquals("layouts are copies", 5, backup.template.columns);
    }

    @Test public void findsBackupInsideMessengerText() {
        String json = new SettingsBackup().toJson(false);
        SettingsBackup parsed = SettingsBackup.parse("Мои настройки:\n«" + json + "»\n");
        assertNotNull(parsed);
        assertFalse(parsed.levelsFromMax);
    }

    @Test public void rejectsForeignText() {
        assertNull(SettingsBackup.parse(null));
        assertNull(SettingsBackup.parse("просто текст"));
        assertNull(SettingsBackup.parse("{broken"));
        assertNull(SettingsBackup.parse("{\"columns\":3}"));
    }

    @Test public void toleratesMissingAndUnknownFields() {
        SettingsBackup parsed = SettingsBackup.parse(
                "{\"app\":\"AtlasClimateWidget\",\"format\":9,\"future\":1,"
                        + "\"settings\":{\"carModel\":\"MARS\",\"tempStepTenths\":7}}");
        assertNotNull(parsed);
        assertEquals(CarModel.ATLAS.name(), parsed.carModel);
        assertEquals(5, parsed.tempStepTenths);
        assertEquals(0, parsed.uiScaleTenths);
        assertTrue(parsed.widgets.isEmpty());
        assertEquals(new WidgetConfig().columns, parsed.template.columns);
    }
}
