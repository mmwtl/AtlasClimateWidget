package com.mmwtl.atlasclimatewidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ClimateStoreTest {
    @Test public void valuesExpireAndFailedReadsAreIgnored() {
        ClimateStore store = new ClimateStore();
        store.putProperty(Hvac.AC, Gib.AREA_GLOBAL, 1, 1_000L);
        store.putProperty(Hvac.AUTO, Gib.AREA_GLOBAL, -1, 1_000L);
        assertEquals(1d, store.snapshot(1_000L).property(Hvac.AC, Gib.AREA_GLOBAL), 0d);
        assertNull(store.snapshot(1_000L).property(Hvac.AUTO, Gib.AREA_GLOBAL));
        assertTrue(store.snapshot(1_000L).isConnected());
        long late = 1_000L + ClimateStore.STALE_AFTER_MS + 1L;
        assertNull(store.snapshot(late).property(Hvac.AC, Gib.AREA_GLOBAL));
        assertFalse(store.snapshot(late).isConnected());
    }

    @Test public void zonedReadFallsBackToGlobal() {
        ClimateStore store = new ClimateStore();
        store.putProperty(Hvac.FAN_SPEED, Gib.AREA_GLOBAL, 5, 0L);
        assertEquals(5d, store.snapshot(0L).property(Hvac.FAN_SPEED, Hvac.ZONE_ROW_1_ALL), 0d);
    }

    @Test public void roofPositionDoesNotFallBackToGlobal() {
        ClimateStore store = new ClimateStore();
        // The global window position may be the sunshade's, not the sunroof's.
        store.putProperty(Hvac.WINDOW_POS, Gib.AREA_GLOBAL, 20, 0L);
        assertNull(store.snapshot(0L).property(Hvac.WINDOW_POS, Hvac.ZONE_SUNROOF));
        assertEquals(20d, store.snapshot(0L).property(Hvac.WINDOW_POS, Gib.AREA_GLOBAL), 0d);
    }

    @Test public void pendingValueWinsUntilConfirmedOrExpired() {
        ClimateStore store = new ClimateStore();
        store.putProperty(Hvac.AC, Gib.AREA_GLOBAL, 0, 0L);
        store.setPending(Hvac.AC, Gib.AREA_GLOBAL, 1, 100L);
        store.putProperty(Hvac.AC, Gib.AREA_GLOBAL, 0, 200L);
        assertEquals(1d, store.snapshot(300L).property(Hvac.AC, Gib.AREA_GLOBAL), 0d);
        store.putProperty(Hvac.AC, Gib.AREA_GLOBAL, 0, 100L + ClimateStore.PENDING_GRACE_MS + 1);
        assertEquals(0d, store.snapshot(2_000L).property(Hvac.AC, Gib.AREA_GLOBAL), 0d);

        store.setPending(Hvac.AC, Gib.AREA_GLOBAL, 1, 3_000L);
        assertEquals(0d, store.snapshot(3_000L + ClimateStore.PENDING_TIMEOUT_MS + 1)
                .property(Hvac.AC, Gib.AREA_GLOBAL), 0d);
    }

    @Test public void integerIdsKeepPrecision() {
        ClimateStore store = new ClimateStore();
        store.putProperty(Hvac.FAN_SPEED, Hvac.ZONE_ROW_1_ALL, Hvac.FAN_SPEED_LEVEL_1 + 2, 0L);
        assertEquals(3, ClimateCommands.fanState(store.snapshot(0L)).level);
    }

    @Test public void keepsTheAutoModeTheClimateWasSwitchedOffIn() {
        ClimateStore store = new ClimateStore();
        store.putProperty(Hvac.POWER, Gib.AREA_GLOBAL, 1, 0L);
        store.putProperty(Hvac.AUTO, Hvac.ZONE_DRIVER, 1, 0L);
        assertNull(store.snapshot(0L).autoBeforePowerOff());
        // Switching off reports AUTO off, just before or after the power value.
        store.putProperty(Hvac.AUTO, Hvac.ZONE_DRIVER, 0, 10_000L);
        store.putProperty(Hvac.POWER, Gib.AREA_GLOBAL, 0, 10_300L);
        assertEquals(1d, store.snapshot(10_300L).autoBeforePowerOff(), 0d);
        store.putProperty(Hvac.AUTO, Hvac.ZONE_DRIVER, 0, 40_000L);
        assertEquals(1d, store.snapshot(40_000L).autoBeforePowerOff(), 0d);
        store.putProperty(Hvac.POWER, Gib.AREA_GLOBAL, 1, 50_000L);
        assertNull(store.snapshot(50_000L).autoBeforePowerOff());

        // AUTO reported off after the power value, here from the widget's own command.
        store.putProperty(Hvac.AUTO, Hvac.ZONE_DRIVER, 1, 60_000L);
        store.setPending(Hvac.POWER, Gib.AREA_GLOBAL, 0, 70_000L);
        store.putProperty(Hvac.AUTO, Hvac.ZONE_DRIVER, 0, 70_500L);
        store.putProperty(Hvac.POWER, Gib.AREA_GLOBAL, 0, 70_800L);
        assertEquals(1d, store.snapshot(70_800L).autoBeforePowerOff(), 0d);
    }

    @Test public void manualModeIsKeptAndAnOffStartKeepsNothing() {
        ClimateStore store = new ClimateStore();
        store.putProperty(Hvac.AUTO, Hvac.ZONE_DRIVER, 0, 0L);
        store.putProperty(Hvac.POWER, Gib.AREA_GLOBAL, 1, 0L);
        store.putProperty(Hvac.POWER, Gib.AREA_GLOBAL, 0, 10_000L);
        assertEquals(0d, store.snapshot(10_000L).autoBeforePowerOff(), 0d);

        // The climate was already off when the widget started: the mode is not known.
        ClimateStore late = new ClimateStore();
        late.putProperty(Hvac.AUTO, Hvac.ZONE_DRIVER, 0, 0L);
        late.putProperty(Hvac.POWER, Gib.AREA_GLOBAL, 0, 0L);
        assertNull(late.snapshot(0L).autoBeforePowerOff());
    }

    @Test public void reportedValueIgnoresPendingOnes() {
        ClimateStore store = new ClimateStore();
        assertNull(store.reported(Hvac.WINDOW_POS, Hvac.ZONE_SUNSHADE));
        store.putProperty(Hvac.WINDOW_POS, Hvac.ZONE_SUNSHADE, 20, 0L);
        store.setPending(Hvac.WINDOW_POS, Hvac.ZONE_SUNSHADE, 0, 100L);
        store.putProperty(Hvac.WINDOW_POS, Hvac.ZONE_SUNSHADE, 12, 200L);
        assertEquals(0d, store.snapshot(300L).property(Hvac.WINDOW_POS, Hvac.ZONE_SUNSHADE),
                0d);
        assertEquals(12d, store.reported(Hvac.WINDOW_POS, Hvac.ZONE_SUNSHADE), 0d);
    }
}
