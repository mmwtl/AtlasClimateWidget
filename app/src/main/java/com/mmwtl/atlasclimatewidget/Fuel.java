package com.mmwtl.atlasclimatewidget;

import java.util.Locale;

/**
 * Tank liters from the bridge's Fuel Percentage sensor, as in AtlasAppWidget:
 * {@code liters = value × multiplier + offset}. The sensor reports 0–100, so the tank holds the
 * larger end of that range (54 liters with the default formula); at 0 the real level is anywhere
 * below the offset, so the values become bounds.
 */
final class Fuel {
    static final float MULTIPLIER = 0.5f;
    static final float OFFSET = 4f;
    static final float API_MIN = 0f;
    static final float API_MAX = 100f;
    /** Limits of a custom formula, the same as in AtlasAppWidget. */
    static final float MULTIPLIER_LIMIT = 100f;
    static final float OFFSET_LIMIT = 1_000f;
    static final int CAPACITY_LITERS = capacityLiters(MULTIPLIER, OFFSET);

    final int liters;
    final int freeLiters;
    final int capacityLiters;
    /** The sensor is at its floor, so {@link #liters} is an upper and the free volume a lower bound. */
    final boolean atFloor;

    private Fuel(int liters, int capacityLiters, boolean atFloor) {
        this.liters = liters;
        this.capacityLiters = capacityLiters;
        this.freeLiters = capacityLiters - liters;
        this.atFloor = atFloor;
    }

    /** Returns {@code null} without a fresh reading. */
    static Fuel of(Double sensorValue) {
        return of(sensorValue, MULTIPLIER, OFFSET);
    }

    /** Returns {@code null} without a fresh reading or with an unusable formula. */
    static Fuel of(Double sensorValue, float multiplier, float offset) {
        if (sensorValue == null || !Double.isFinite(sensorValue)
                || !isValid(multiplier, offset)) {
            return null;
        }
        int capacity = capacityLiters(multiplier, offset);
        float value = Math.max(API_MIN, Math.min(API_MAX, sensorValue.floatValue()));
        int liters = Math.max(0, Math.min(capacity, Math.round(value * multiplier + offset)));
        return new Fuel(liters, capacity, value <= API_MIN);
    }

    static boolean isValid(float multiplier, float offset) {
        return Float.isFinite(multiplier) && Float.isFinite(offset)
                && Math.abs(multiplier) <= MULTIPLIER_LIMIT && Math.abs(offset) <= OFFSET_LIMIT;
    }

    /** The larger end of the formula over the sensor range, never negative. */
    static int capacityLiters(float multiplier, float offset) {
        float atMin = API_MIN * multiplier + offset;
        float atMax = API_MAX * multiplier + offset;
        return Math.max(0, Math.round(Math.max(atMin, atMax)));
    }

    /** A coefficient without trailing zeros: 0.5, 4, -1.25. */
    static String format(float value) {
        if (value == Math.rint(value)) {
            return Integer.toString(Math.round(value));
        }
        String text = String.format(Locale.US, "%.3f", value);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    String litersText() {
        return (atFloor ? "<" : "") + liters;
    }

    String freeText() {
        return (atFloor ? ">" : "") + freeLiters;
    }
}
