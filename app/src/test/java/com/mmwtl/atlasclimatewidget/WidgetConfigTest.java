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
        config.fanStyle = WidgetConfig.FanStyle.PRESETS;
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
        assertEquals(WidgetConfig.FanStyle.PRESETS, copy.fanStyle);
        assertEquals(WidgetConfig.FanStyle.LEVELS, WidgetConfig.fromJson("{}").fanStyle);
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
}
