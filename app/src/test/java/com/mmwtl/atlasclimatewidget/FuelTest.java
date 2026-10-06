package com.mmwtl.atlasclimatewidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class FuelTest {
    @Test public void defaultFormulaGivesFiftyFourLiterTank() {
        assertEquals(54, Fuel.CAPACITY_LITERS);
        Fuel full = Fuel.of(100d);
        assertEquals("54", full.litersText());
        assertEquals("0", full.freeText());
    }

    @Test public void midLevelSplitsTheTank() {
        Fuel fuel = Fuel.of(25.4d);
        assertEquals(17, fuel.liters);
        assertEquals(37, fuel.freeLiters);
        assertEquals("17", fuel.litersText());
    }

    @Test public void sensorFloorShowsBounds() {
        Fuel fuel = Fuel.of(0d);
        assertEquals("<4", fuel.litersText());
        assertEquals(">50", fuel.freeText());
    }

    @Test public void customFormulaDerivesTheTank() {
        assertEquals(51, Fuel.capacityLiters(0.466f, 4.4f));
        Fuel fuel = Fuel.of(20d, 0.5f, 2f);
        assertEquals(12, fuel.liters);
        assertEquals(52, fuel.capacityLiters);
        assertEquals(40, fuel.freeLiters);
        assertNull(Fuel.of(20d, 101f, 0f));
        assertNull(Fuel.of(20d, Float.NaN, 0f));
    }

    @Test public void coefficientsPrintWithoutTrailingZeros() {
        assertEquals("0.5", Fuel.format(0.5f));
        assertEquals("4", Fuel.format(4f));
        assertEquals("0.466", Fuel.format(0.466f));
        assertEquals("-1.25", Fuel.format(-1.25f));
    }

    @Test public void outOfRangeIsClampedAndMissingIsNull() {
        assertEquals(54, Fuel.of(200d).liters);
        assertEquals("<4", Fuel.of(-3d).litersText());
        assertNull(Fuel.of(null));
        assertNull(Fuel.of(Double.NaN));
    }
}
