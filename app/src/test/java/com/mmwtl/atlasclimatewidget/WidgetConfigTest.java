package com.mmwtl.atlasclimatewidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WidgetConfigTest {
    @Test public void roundTripsAllFields() {
        WidgetConfig config = new WidgetConfig();
        config.moveBlock(WidgetConfig.Block.TILES, -3);
        config.fanEnabled = false;
        config.temperatureDual = true;
        config.setFunctionEnabled(ClimateFunction.ME_HOT, true);
        config.setFunctionEnabled(ClimateFunction.AC, false);
        config.columns = 4;
        config.tileStyle = WidgetConfig.TileStyle.ICON;
        config.palette = Palette.SEMANTIC;
        config.cardOpacityPercent = 40;
        config.fanBar = false;
        config.fanDirections = false;
        config.heightMode = WidgetConfig.HeightMode.CONTENT;
        config.verticalAlign = WidgetConfig.VerticalAlign.BOTTOM;
        config.cardLayout = WidgetConfig.CardLayout.SINGLE;
        config.headerAlign = WidgetConfig.HeaderAlign.RIGHT;
        config.headerFuelFree = true;
        config.headerTemperatures = false;
        config.style = WidgetConfig.Style.CONSOLE;
        config.iconSet = WidgetConfig.IconSet.OEM;
        WidgetConfig copy = WidgetConfig.fromJson(config.toJson());
        assertEquals(WidgetConfig.Block.TILES, copy.blockOrder.get(0));
        assertFalse(copy.fanEnabled);
        assertTrue(copy.temperatureDual);
        assertTrue(copy.functions.contains(ClimateFunction.ME_HOT));
        assertFalse(copy.functions.contains(ClimateFunction.AC));
        assertEquals(4, copy.columns);
        assertEquals(WidgetConfig.TileStyle.ICON, copy.tileStyle);
        assertEquals(Palette.SEMANTIC, copy.palette);
        assertEquals(WidgetConfig.IconSet.OEM, copy.iconSet);
        assertEquals(40, copy.cardOpacityPercent);
        assertFalse(copy.fanBar);
        assertFalse(copy.fanDirections);
        assertTrue(copy.fanPresets);
        WidgetConfig fresh = WidgetConfig.fromJson("{}");
        assertTrue(fresh.fanBar && fresh.fanDirections && fresh.fanPresets);
        WidgetConfig legacyPresets = WidgetConfig.fromJson("{\"fanStyle\":\"PRESETS\"}");
        assertFalse(legacyPresets.fanBar);
        assertTrue(legacyPresets.fanPresets);
        WidgetConfig legacyLevels = WidgetConfig.fromJson("{\"fanStyle\":\"LEVELS\"}");
        assertTrue(legacyLevels.fanBar);
        assertFalse(legacyLevels.fanPresets || legacyLevels.fanDirections);
        assertEquals(WidgetConfig.HeightMode.CONTENT, copy.heightMode);
        assertEquals(WidgetConfig.VerticalAlign.BOTTOM, copy.verticalAlign);
        assertEquals(WidgetConfig.HeightMode.FILL, WidgetConfig.fromJson("{}").heightMode);
        assertEquals(WidgetConfig.CardLayout.SINGLE, copy.cardLayout);
        assertEquals(WidgetConfig.CardLayout.SEPARATE, WidgetConfig.fromJson("{}").cardLayout);
        assertEquals(WidgetConfig.HeaderAlign.RIGHT, copy.headerAlign);
        assertTrue(copy.headerFuelFree);
        assertFalse(copy.headerFuel);
        assertFalse(fresh.headerFuel || fresh.headerFuelFree);
        assertFalse(copy.headerTemperatures);
        assertTrue(fresh.headerTemperatures);
        assertEquals(WidgetConfig.HeaderAlign.CENTER, WidgetConfig.fromJson("{}").headerAlign);
        assertEquals(WidgetConfig.Style.CONSOLE, copy.style);
        assertEquals(WidgetConfig.Style.CLASSIC, WidgetConfig.fromJson("{}").style);
        assertEquals(WidgetConfig.Style.CLASSIC,
                WidgetConfig.fromJson("{\"style\":\"NOPE\"}").style);
    }

    @Test public void classicTileRowsFollowTheUserOrder() {
        WidgetConfig config = new WidgetConfig();
        config.columns = 5;
        java.util.List<ClimateFunction[]> rows = config.tileRows();
        assertEquals(3, rows.size());
        assertEquals(config.functions.get(0), rows.get(0)[0]);
        assertEquals(config.functions.get(5), rows.get(1)[0]);
        assertEquals(config.functions.get(11), rows.get(2)[1]);
        assertEquals(null, rows.get(2)[2]);
    }

    @Test public void droppedTileTakesTheTargetSlot() {
        WidgetConfig config = new WidgetConfig();
        config.columns = 3;
        config.functions.clear();
        config.functions.addAll(java.util.Arrays.asList(
                ClimateFunction.AC, ClimateFunction.AUTO, ClimateFunction.POWER,
                ClimateFunction.DRIVER_HEAT));
        // Forward: AC lands where POWER was.
        assertTrue(config.dropTile(ClimateFunction.AC, 0, 2));
        assertEquals(java.util.Arrays.asList(ClimateFunction.AUTO, ClimateFunction.POWER,
                ClimateFunction.AC, ClimateFunction.DRIVER_HEAT), config.functions);
        // Backward, across rows.
        assertTrue(config.dropTile(ClimateFunction.DRIVER_HEAT, 0, 0));
        assertEquals(ClimateFunction.DRIVER_HEAT, config.functions.get(0));
        // An empty slot after the last tile moves it last.
        assertTrue(config.dropTile(ClimateFunction.DRIVER_HEAT, 1, 2));
        assertEquals(ClimateFunction.DRIVER_HEAT, config.functions.get(3));
        assertFalse(config.dropTile(ClimateFunction.DRIVER_HEAT, 1, 1));
        assertFalse(config.dropTile(ClimateFunction.DRIVER_HEAT, 2, 0));
    }

    @Test public void consoleTilesFollowTheUserOrderAndMoveFreely() {
        WidgetConfig config = new WidgetConfig();
        config.style = WidgetConfig.Style.CONSOLE;
        config.columns = 3;
        config.functions.clear();
        config.functions.addAll(java.util.Arrays.asList(
                ClimateFunction.DRIVER_HEAT, ClimateFunction.WINDSHIELD_HEAT,
                ClimateFunction.AC, ClimateFunction.PASSENGER_HEAT));
        java.util.List<ClimateFunction[]> rows = config.tileRows();
        assertEquals(2, rows.size());
        assertEquals(ClimateFunction.DRIVER_HEAT, rows.get(0)[0]);
        assertEquals(ClimateFunction.PASSENGER_HEAT, rows.get(1)[0]);
        // Seats and glass move across the former groups, and onto an empty slot.
        assertTrue(config.dropTile(ClimateFunction.PASSENGER_HEAT, 0, 1));
        assertEquals(ClimateFunction.PASSENGER_HEAT, config.tileRows().get(0)[1]);
        assertTrue(config.dropTile(ClimateFunction.DRIVER_HEAT, 1, 2));
        assertEquals(ClimateFunction.DRIVER_HEAT, config.functions.get(3));
    }

    @Test public void legacyConsoleLayoutKeepsItsGroupedOrder() {
        WidgetConfig config = WidgetConfig.fromJson("{\"version\":1,\"style\":\"CONSOLE\","
                + "\"functions\":[\"PASSENGER_HEAT\",\"WINDSHIELD_HEAT\",\"AC\","
                + "\"DRIVER_HEAT\",\"AUTO\",\"REAR_DEFROST\",\"WHEEL_HEAT\"]}");
        assertEquals(java.util.Arrays.asList(ClimateFunction.AC, ClimateFunction.AUTO,
                ClimateFunction.WINDSHIELD_HEAT, ClimateFunction.REAR_DEFROST,
                ClimateFunction.DRIVER_HEAT, ClimateFunction.WHEEL_HEAT,
                ClimateFunction.PASSENGER_HEAT), config.functions);
        // Once saved, the order is the user's own.
        config.dropTile(ClimateFunction.PASSENGER_HEAT, 0, 0);
        assertEquals(config.functions, WidgetConfig.fromJson(config.toJson()).functions);
        // Classic layouts never had groups.
        assertEquals(ClimateFunction.PASSENGER_HEAT, WidgetConfig.fromJson(
                "{\"functions\":[\"PASSENGER_HEAT\",\"AC\"]}").functions.get(0));
    }

    @Test public void sensorLineLeavesTheTemperatureBlock() {
        WidgetConfig fresh = WidgetConfig.fromJson("{}");
        assertEquals(WidgetConfig.Block.INFO, fresh.blockOrder.get(0));
        assertTrue(fresh.isEnabled(WidgetConfig.Block.INFO));
        WidgetConfig lineOnly = WidgetConfig.fromJson("{\"version\":2,"
                + "\"blockOrder\":[\"TILES\",\"TEMPERATURE\",\"FAN\"],"
                + "\"temperatureBar\":false,\"headerTemperatures\":false}");
        assertEquals(WidgetConfig.Block.INFO, lineOnly.blockOrder.get(1));
        assertEquals(WidgetConfig.Block.TEMPERATURE, lineOnly.blockOrder.get(2));
        assertTrue(lineOnly.isEnabled(WidgetConfig.Block.INFO));
        assertFalse(lineOnly.isEnabled(WidgetConfig.Block.TEMPERATURE));
        assertTrue("without fuel the old line kept the temperatures",
                lineOnly.headerTemperatures);
        WidgetConfig barOnly = WidgetConfig.fromJson(
                "{\"version\":2,\"temperatureHeader\":false}");
        assertFalse(barOnly.isEnabled(WidgetConfig.Block.INFO));
        assertTrue(barOnly.isEnabled(WidgetConfig.Block.TEMPERATURE));
        WidgetConfig hidden = WidgetConfig.fromJson(
                "{\"version\":2,\"temperatureEnabled\":false}");
        assertFalse(hidden.isEnabled(WidgetConfig.Block.INFO));
        WidgetConfig config = new WidgetConfig();
        config.headerTemperatures = false;
        assertFalse("the line needs a part", config.isEnabled(WidgetConfig.Block.INFO));
        config.headerFuelFree = true;
        assertTrue(WidgetConfig.fromJson(config.toJson()).isEnabled(WidgetConfig.Block.INFO));
    }

    @Test public void toleratesUnknownAndInvalidValues() {
        WidgetConfig config = WidgetConfig.fromJson("{\"blockOrder\":[\"FAN\",\"NOPE\"],"
                + "\"functions\":[\"AC\",\"GONE\",\"AC\"],\"columns\":99,\"palette\":\"X\"}");
        assertEquals(4, config.blockOrder.size());
        assertEquals(WidgetConfig.Block.FAN, config.blockOrder.get(0));
        assertEquals(1, config.functions.size());
        assertEquals(WidgetConfig.COLUMNS_MAX, config.columns);
        assertEquals(Palette.ATLAS, config.palette);
        assertEquals(WidgetConfig.IconSet.ATLAS, config.iconSet);
        assertEquals(12, WidgetConfig.fromJson("broken").functions.size());
    }

    @Test public void iconSetPicksStockIconsWithAtlasFallback() {
        WidgetConfig.IconSet oem = WidgetConfig.IconSet.OEM;
        WidgetConfig.IconSet atlas = WidgetConfig.IconSet.ATLAS;
        assertEquals(R.drawable.ic_oem_seat_heat_driver_2,
                ClimateFunction.DRIVER_HEAT.icon(oem, 2));
        assertEquals(R.drawable.ic_fn_seat_heat_driver_3,
                ClimateFunction.DRIVER_HEAT.icon(atlas, 9));
        assertEquals(R.drawable.ic_oem_rear_seat_left,
                ClimateFunction.REAR_LEFT_HEAT.icon(oem, 3));
        assertEquals(R.drawable.ic_oem_auto, ClimateFunction.AUTO.icon(oem, 0));
        assertEquals(0, ClimateFunction.AUTO.icon(atlas, 0));
        assertEquals(0, ClimateFunction.TEMP_SYNC.icon(oem, 0));
        assertEquals(R.drawable.ic_power, ClimateFunction.POWER.icon(oem, 1));
    }

    @Test public void tilesBlockHidesWithoutFunctions() {
        WidgetConfig config = new WidgetConfig();
        config.functions.clear();
        assertFalse(config.visibleBlocks().contains(WidgetConfig.Block.TILES));
    }

    @Test public void scaleRoundTripsAndDefaults() {
        WidgetConfig config = new WidgetConfig();
        config.scalePercent = 130;
        assertEquals(130, WidgetConfig.fromJson(config.toJson()).scalePercent);
        assertEquals(100, WidgetConfig.fromJson("{\"columns\":4}").scalePercent);
        assertEquals(WidgetConfig.SCALE_DEFAULT_PERCENT, WidgetConfig.fromJson(null).scalePercent);
        assertEquals(WidgetConfig.SCALE_MAX_PERCENT,
                WidgetConfig.fromJson("{\"scalePercent\":999}").scalePercent);
    }
}
