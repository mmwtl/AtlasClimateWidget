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
        backup.keptWhenOff = true;
        backup.themeMode = WidgetTheme.FIXED_NIGHT;
        backup.fanPresets = "FIVE";
        backup.uiScaleTenths = 17;
        backup.fuelCustomFormula = true;
        backup.fuelMultiplier = 0.466f;
        backup.fuelOffset = 4.4f;
        backup.template.columns = 4;
        backup.template.setTileLevels(ClimateFunction.DRIVER_HEAT, 2);
        backup.panel = new WidgetConfig();
        backup.panel.columns = 3;
        backup.panel.setTileLevels(ClimateFunction.PASSENGER_HEAT, 3);
        backup.panelWidthPercent = 75;
        backup.panelBottom = true;
        backup.panelCloseSeconds = 0;
        WidgetConfig first = new WidgetConfig();
        first.style = WidgetConfig.Style.CONSOLE;
        first.setTileLevels(ClimateFunction.WHEEL_HEAT, 2, 3);
        WidgetConfig second = new WidgetConfig();
        second.setFunctionEnabled(ClimateFunction.ME_HOT, true);
        backup.widgets.add(first);
        backup.widgets.add(second);

        {
            SettingsBackup copy = SettingsBackup.parse(backup.toJson("1.2"));
            assertNotNull(copy);
            assertEquals(CarModel.CITYRAY.name(), copy.carModel);
            assertEquals(10, copy.tempStepTenths);
            assertTrue(copy.levelsFromMax);
            assertTrue(copy.keptWhenOff);
            assertEquals(WidgetTheme.FIXED_NIGHT, copy.themeMode);
            assertEquals("FIVE", copy.fanPresets);
            assertEquals(17, copy.uiScaleTenths);
            assertTrue(copy.fuelCustomFormula);
            assertEquals(0.466f, copy.fuelMultiplier, 1e-6f);
            assertEquals(4.4f, copy.fuelOffset, 1e-6f);
            assertEquals(4, copy.template.columns);
            assertTrue(java.util.Arrays.equals(new int[]{2},
                    copy.template.activeLevels(ClimateFunction.DRIVER_HEAT)));
            assertEquals(3, copy.panel.columns);
            assertTrue(java.util.Arrays.equals(new int[]{3},
                    copy.panel.activeLevels(ClimateFunction.PASSENGER_HEAT)));
            assertEquals(75, copy.panelWidthPercent);
            assertTrue(copy.panelBottom);
            assertEquals(0, copy.panelCloseSeconds);
            assertEquals(2, copy.widgets.size());
            assertEquals(WidgetConfig.Style.CONSOLE, copy.widgets.get(0).style);
            assertTrue(java.util.Arrays.equals(new int[]{2, 3},
                    copy.widgets.get(0).activeLevels(ClimateFunction.WHEEL_HEAT)));
            assertTrue(copy.widgets.get(1).functions.contains(ClimateFunction.ME_HOT));
        }
    }

    @Test public void preservesOverlayModeLayoutAndPlacement() {
        SettingsBackup backup = new SettingsBackup();
        backup.overlayMode = true;
        backup.overlay = new WidgetConfig();
        backup.overlay.temperatureDual = true;
        backup.overlayWidthPercent = 75;
        backup.overlayHeightPercent = 34;
        backup.overlayCorner = OverlayCorner.TOP_END.preferenceValue;
        backup.overlayOffsetX = 16;
        backup.overlayOffsetY = 32;
        backup.overlayHideThreshold = WindowVisibilityPolicy.MIN_HIDE_THRESHOLD_PERCENT;
        backup.overlayAppListOnly = true;

        SettingsBackup copy = SettingsBackup.parse(backup.toJson("1.6.1"));
        assertTrue(copy.overlayMode);
        assertTrue(copy.overlay.temperatureDual);
        assertEquals(75, copy.overlayWidthPercent);
        assertEquals(34, copy.overlayHeightPercent);
        assertEquals(OverlayCorner.TOP_END.preferenceValue, copy.overlayCorner);
        assertEquals(16, copy.overlayOffsetX);
        assertEquals(32, copy.overlayOffsetY);
        assertEquals(WindowVisibilityPolicy.MIN_HIDE_THRESHOLD_PERCENT, copy.overlayHideThreshold);
        assertTrue(copy.overlayAppListOnly);
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

    @Test public void writesTheSiblingFileHeader() throws Exception {
        org.json.JSONObject json = new org.json.JSONObject(new SettingsBackup().toJson("1.2"));
        assertEquals("atlas-climate-widget-settings", json.getString("format"));
        assertEquals(1, json.getInt("schemaVersion"));
        assertEquals("1.2", json.getString("appVersion"));
        assertFalse(SettingsBackup.parse(json.toString()).levelsFromMax);
        assertFalse(SettingsBackup.parse(json.toString()).keptWhenOff);
        json.getJSONObject("settings").remove("themeMode");
        assertEquals("older backups follow the head unit", WidgetTheme.AUTO,
                SettingsBackup.parse(json.toString()).themeMode);
        json.getJSONObject("settings").put("themeMode", 7);
        assertEquals(WidgetTheme.AUTO, SettingsBackup.parse(json.toString()).themeMode);
    }

    @Test public void dropsABrokenFuelFormula() {
        SettingsBackup parsed = SettingsBackup.parse(
                "{\"format\":\"atlas-climate-widget-settings\",\"settings\":{\"fuel\":"
                        + "{\"useCustomFormula\":true,\"multiplier\":500,\"offset\":1}}}");
        assertFalse(parsed.fuelCustomFormula);
        assertEquals(Fuel.OFFSET, parsed.fuelOffset, 0f);
    }

    @Test public void rejectsForeignText() {
        assertNull(SettingsBackup.parse(null));
        assertNull(SettingsBackup.parse("просто текст"));
        assertNull(SettingsBackup.parse("{broken"));
        assertNull(SettingsBackup.parse("{\"columns\":3}"));
        assertNull("another Atlas app's settings",
                SettingsBackup.parse("{\"format\":\"atlas-app-widget-settings\"}"));
    }

    @Test public void toleratesMissingAndUnknownFields() {
        SettingsBackup parsed = SettingsBackup.parse(
                "{\"format\":\"atlas-climate-widget-settings\",\"schemaVersion\":9,\"future\":1,"
                        + "\"settings\":{\"carModel\":\"MARS\",\"tempStepTenths\":7}}");
        assertNotNull(parsed);
        assertEquals(CarModel.ATLAS.name(), parsed.carModel);
        assertEquals(5, parsed.tempStepTenths);
        assertEquals(0, parsed.uiScaleTenths);
        assertFalse(parsed.fuelCustomFormula);
        assertEquals(Fuel.MULTIPLIER, parsed.fuelMultiplier, 0f);
        assertTrue(parsed.widgets.isEmpty());
        assertEquals(new WidgetConfig().columns, parsed.template.columns);
        assertFalse(parsed.overlayMode);
        assertNull(parsed.overlay);
        assertEquals(0, parsed.overlayHeightPercent);
        assertNull(parsed.panel);
        assertEquals(Prefs.PANEL_WIDTH_DEFAULT, parsed.panelWidthPercent);
        assertFalse(parsed.panelBottom);
        assertEquals(Prefs.PANEL_CLOSE_DEFAULT, parsed.panelCloseSeconds);
    }
}
