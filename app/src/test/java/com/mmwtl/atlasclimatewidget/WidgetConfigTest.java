package com.mmwtl.atlasclimatewidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WidgetConfigTest {
    @Test public void roundTripsAllFields() {
        WidgetConfig config = new WidgetConfig();
        config.moveBlock(WidgetConfig.Block.TILES, -2);
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
        config.style = WidgetConfig.Style.CONSOLE;
        WidgetConfig copy = WidgetConfig.fromJson(config.toJson());
        assertEquals(WidgetConfig.Block.TILES, copy.blockOrder.get(0));
        assertFalse(copy.fanEnabled);
        assertTrue(copy.temperatureDual);
        assertTrue(copy.functions.contains(ClimateFunction.ME_HOT));
        assertFalse(copy.functions.contains(ClimateFunction.AC));
        assertEquals(4, copy.columns);
        assertEquals(WidgetConfig.TileStyle.ICON, copy.tileStyle);
        assertEquals(Palette.SEMANTIC, copy.palette);
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

    @Test public void consoleGroupsTilesAndMirrorsSeats() {
        WidgetConfig config = new WidgetConfig();
        config.style = WidgetConfig.Style.CONSOLE;
        config.columns = 5;
        config.functions.clear();
        config.functions.addAll(java.util.Arrays.asList(
                ClimateFunction.PASSENGER_HEAT, ClimateFunction.WINDSHIELD_HEAT,
                ClimateFunction.AC, ClimateFunction.DRIVER_HEAT, ClimateFunction.AUTO,
                ClimateFunction.REAR_DEFROST, ClimateFunction.WHEEL_HEAT,
                ClimateFunction.DEFROST_MAX, ClimateFunction.POWER));
        java.util.List<ClimateFunction[]> rows = config.tileRows();
        assertEquals(3, rows.size());
        // Climate modes in the user's order; glass does not fit whole, so it wraps.
        assertEquals(ClimateFunction.AC, rows.get(0)[0]);
        assertEquals(ClimateFunction.AUTO, rows.get(0)[1]);
        assertEquals(ClimateFunction.POWER, rows.get(0)[2]);
        assertEquals(null, rows.get(0)[3]);
        assertEquals(ClimateFunction.WINDSHIELD_HEAT, rows.get(1)[0]);
        assertEquals(ClimateFunction.REAR_DEFROST, rows.get(1)[1]);
        assertEquals(ClimateFunction.DEFROST_MAX, rows.get(1)[2]);
        assertEquals(ClimateFunction.DRIVER_HEAT, rows.get(2)[0]);
        assertEquals(ClimateFunction.WHEEL_HEAT, rows.get(2)[2]);
        assertEquals(ClimateFunction.PASSENGER_HEAT, rows.get(2)[4]);
        config.functions.remove(ClimateFunction.POWER);
        config.functions.remove(ClimateFunction.DEFROST_MAX);
        rows = config.tileRows();
        // Two climate modes plus two glass tiles fit one row; seats get a mirrored row.
        assertEquals(2, rows.size());
        assertEquals(ClimateFunction.REAR_DEFROST, rows.get(0)[3]);
        assertEquals(null, rows.get(0)[4]);
        assertEquals(ClimateFunction.DRIVER_HEAT, rows.get(1)[0]);
        assertEquals(null, rows.get(1)[1]);
        assertEquals(ClimateFunction.WHEEL_HEAT, rows.get(1)[2]);
        assertEquals(null, rows.get(1)[3]);
        assertEquals(ClimateFunction.PASSENGER_HEAT, rows.get(1)[4]);
    }

    @Test public void toleratesUnknownAndInvalidValues() {
        WidgetConfig config = WidgetConfig.fromJson("{\"blockOrder\":[\"FAN\",\"NOPE\"],"
                + "\"functions\":[\"AC\",\"GONE\",\"AC\"],\"columns\":99,\"palette\":\"X\"}");
        assertEquals(3, config.blockOrder.size());
        assertEquals(WidgetConfig.Block.FAN, config.blockOrder.get(0));
        assertEquals(1, config.functions.size());
        assertEquals(WidgetConfig.COLUMNS_MAX, config.columns);
        assertEquals(Palette.ATLAS, config.palette);
        assertEquals(12, WidgetConfig.fromJson("broken").functions.size());
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
