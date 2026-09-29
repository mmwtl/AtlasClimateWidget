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
}
