package com.mmwtl.atlasclimatewidget;

/**
 * Tank liters from the bridge's Fuel Percentage sensor, with the AtlasAppWidget formula
 * {@code liters = value × 0.5 + 4}. The sensor reports 0–100, so the tank holds 54 liters;
 * at 0 the real level is anywhere below the offset, so the values become bounds.
 */
final class Fuel {
    static final float MULTIPLIER = 0.5f;
    static final float OFFSET = 4f;
    static final float API_MIN = 0f;
    static final float API_MAX = 100f;
    static final int CAPACITY_LITERS = Math.round(API_MAX * MULTIPLIER + OFFSET);

    final int liters;
    final int freeLiters;
    /** The sensor is at its floor, so {@link #liters} is an upper and the free volume a lower bound. */
    final boolean atFloor;

    private Fuel(int liters, boolean atFloor) {
        this.liters = liters;
        this.freeLiters = CAPACITY_LITERS - liters;
        this.atFloor = atFloor;
    }

    /** Returns {@code null} without a fresh reading. */
    static Fuel of(Double sensorValue) {
        if (sensorValue == null || !Double.isFinite(sensorValue)) {
            return null;
        }
        float value = Math.max(API_MIN, Math.min(API_MAX, sensorValue.floatValue()));
        int liters = Math.max(0, Math.min(CAPACITY_LITERS,
                Math.round(value * MULTIPLIER + OFFSET)));
        return new Fuel(liters, value <= API_MIN);
    }

    String litersText() {
        return (atFloor ? "<" : "") + liters;
    }

    String freeText() {
        return (atFloor ? ">" : "") + freeLiters;
    }
}
