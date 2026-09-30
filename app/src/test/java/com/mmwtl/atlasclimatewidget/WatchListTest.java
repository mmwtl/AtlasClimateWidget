package com.mmwtl.atlasclimatewidget;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;

public final class WatchListTest {
    @Test public void airDirectionTilesWatchDirectionAndAuto() {
        WidgetConfig config = new WidgetConfig();
        config.setFunctionEnabled(ClimateFunction.BLOW_FACE, true);
        WatchList watch = WatchList.of(Collections.singletonList(config), CarModel.ATLAS);
        assertTrue(watches(watch, Hvac.ZONE_DRIVER));
        assertTrue(watches(watch, Gib.AREA_GLOBAL));

        assertTrue(watch.properties.contains(
                new WatchList.Key(Hvac.AUTO, Hvac.ZONE_DRIVER, false)));

        for (ClimateFunction function : ClimateFunction.values()) {
            if (function.kind == ClimateFunction.Kind.BLOW) {
                config.setFunctionEnabled(function, false);
            }
        }
        config.fanDirections = false;
        watch = WatchList.of(Collections.singletonList(config), CarModel.ATLAS);
        assertFalse(watches(watch, Hvac.ZONE_DRIVER));
    }

    @Test public void fanBlockDirectionsAreWatchedWithoutTiles() {
        WidgetConfig config = new WidgetConfig();
        config.tilesEnabled = false;
        WatchList watch = WatchList.of(Collections.singletonList(config), CarModel.ATLAS);
        assertTrue(watches(watch, Hvac.ZONE_DRIVER));
    }

    private static boolean watches(WatchList watch, int area) {
        return watch.properties.contains(new WatchList.Key(Hvac.BLOWING_MODE, area, false));
    }
}
