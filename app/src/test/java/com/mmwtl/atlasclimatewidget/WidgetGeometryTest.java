package com.mmwtl.atlasclimatewidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WidgetGeometryTest {
    @Test public void stripsCoverEachCardExactly() {
        WidgetConfig config = new WidgetConfig();
        WidgetGeometry.Plan plan = WidgetGeometry.plan(config, 640f, 0f, 1f, 25);
        float card = 0f;
        for (WidgetGeometry.Strip strip : plan.strips) {
            if (strip.firstInCard) {
                card = 0f;
            }
            assertEquals(card, strip.cardOffset, 0.01f);
            card += strip.height;
            if (strip.lastInCard) {
                assertEquals(strip.cardHeight, card, 0.01f);
            }
        }
        // header, temperature, fan and two tile rows
        assertEquals(5, plan.strips.size());
    }

    @Test public void tileCellsCentreOnTiles() {
        WidgetConfig config = new WidgetConfig();
        WidgetGeometry.Plan plan = WidgetGeometry.plan(config, 640f, 0f, 1f, 25);
        WidgetGeometry.Strip tiles = plan.strips.get(plan.strips.size() - 1);
        float cell = (plan.width - 2f * tiles.zonePadding) / tiles.zoneCount;
        float tileCell = plan.tileCellWidth(config.columns);
        assertEquals(tileCell, cell, 0.01f);
    }

    @Test public void fitsAvailableHeight() {
        WidgetConfig config = new WidgetConfig();
        float natural = WidgetGeometry.plan(config, 640f, 0f, 1f, 25).totalHeight();
        WidgetGeometry.Plan fitted = WidgetGeometry.plan(config, 640f, natural * 0.8f, 1f, 25);
        assertEquals(natural * 0.8f * WidgetGeometry.FIT_MARGIN, fitted.totalHeight(),
                natural * 0.02f);
    }

    @Test public void narrowsContentWhenSquashingIsNotEnough() {
        WidgetConfig config = new WidgetConfig();
        WidgetGeometry.Plan plan = WidgetGeometry.plan(config, 264f, 120f, 1f, 25);
        assertTrue(plan.totalHeight() <= 120f);
        assertTrue(plan.width < plan.fullWidth);
        assertEquals(264f, plan.fullWidth, 0f);
    }

    @Test public void temperatureCellsMapToSteps() {
        WidgetConfig config = new WidgetConfig();
        config.temperatureHeader = false;
        WidgetGeometry.Plan plan = WidgetGeometry.plan(config, 640f, 0f, 1f, 25);
        WidgetGeometry.Strip temp = plan.strips.get(0);
        assertTrue(temp.buttonCells >= 1);
        assertEquals(25 + 2 * temp.buttonCells, temp.zoneCount);
        assertEquals("tempstep/1/-1", WidgetViews.control(config, temp, 0));
        assertEquals("temp/1/0", WidgetViews.control(config, temp, temp.buttonCells));
        assertEquals("temp/1/24", WidgetViews.control(config, temp,
                temp.zoneCount - temp.buttonCells - 1));
        assertEquals("tempstep/1/1", WidgetViews.control(config, temp, temp.zoneCount - 1));
    }

    @Test public void emptyTileCellsAreInert() {
        WidgetConfig config = new WidgetConfig();
        config.functions.remove(ClimateFunction.PASSENGER_VENT);
        WidgetGeometry.Plan plan = WidgetGeometry.plan(config, 640f, 0f, 1f, 25);
        WidgetGeometry.Strip last = plan.strips.get(plan.strips.size() - 1);
        assertNull(WidgetViews.control(config, last, config.columns - 1));
        assertEquals("fn/DRIVER_HEAT", WidgetViews.control(config,
                plan.strips.get(plan.strips.size() - 2), 0));
    }
}
