package com.mmwtl.atlasclimatewidget;

import java.util.HashMap;
import java.util.Map;

/**
 * Thread-safe climate values received from GInputBridge.
 *
 * <p>Values expire when the bridge stops answering the periodic refresh, so a stopped bridge
 * shows an explicit "no data" state instead of stale values. A command stores an optimistic
 * pending value that is shown until the bridge confirms it or the pending timeout expires.
 *
 * <p>The car reports AUTO off while the climate is off and returns to the kept mode when it is
 * switched on, so the store remembers the AUTO value the climate was switched off in.
 */
final class ClimateStore {
    static final long STALE_AFTER_MS = 150_000L;
    static final long PENDING_TIMEOUT_MS = 4_000L;
    /** Results that arrive this soon after a command may describe the state before it. */
    static final long PENDING_GRACE_MS = 1_500L;
    static final long CONNECTED_WINDOW_MS = STALE_AFTER_MS;
    /** An AUTO change this soon before the climate goes off comes from switching it off. */
    static final long POWER_OFF_WINDOW_MS = 2_000L;

    private final Map<String, Reading> values = new HashMap<>();
    private final Map<String, Pending> pending = new HashMap<>();
    private long lastResultAt;
    private Double auto;
    private Double autoBefore;
    private long autoChangedAt;
    private boolean autoFromDriver;
    private Boolean powerOn;
    private Double autoWhenOff;

    /** Stores a property result. The bridge reports failed reads as -1. */
    synchronized void putProperty(int id, int area, double value, long now) {
        lastResultAt = now;
        if (value == -1d) {
            return;
        }
        put(propertyKey(id, area), value, now);
        track(id, area, value, now);
    }

    synchronized void putSensor(int id, double value, long now) {
        lastResultAt = now;
        put(sensorKey(id), value, now);
    }

    synchronized void setPending(int id, int area, double value, long now) {
        pending.put(propertyKey(id, area), new Pending(value, now));
        track(id, area, value, now);
    }

    synchronized long lastResultAt() {
        return lastResultAt;
    }

    synchronized void clear() {
        values.clear();
        pending.clear();
        lastResultAt = 0L;
        auto = null;
        autoBefore = null;
        autoFromDriver = false;
        powerOn = null;
        autoWhenOff = null;
    }

    /** Returns a view that evaluates freshness at {@code now}. */
    ClimateState snapshot(long now) {
        return new ClimateState() {
            @Override
            public Double property(int id, int area) {
                synchronized (ClimateStore.this) {
                    Double value = read(propertyKey(id, area), now);
                    if (value == null && hasGlobalFallback(id, area)) {
                        value = read(propertyKey(id, Gib.AREA_GLOBAL), now);
                    }
                    return value;
                }
            }

            @Override
            public Double sensor(int id) {
                synchronized (ClimateStore.this) {
                    return read(sensorKey(id), now);
                }
            }

            @Override
            public Double autoBeforePowerOff() {
                synchronized (ClimateStore.this) {
                    return Boolean.FALSE.equals(powerOn) ? autoWhenOff : null;
                }
            }

            @Override
            public boolean isConnected() {
                synchronized (ClimateStore.this) {
                    return lastResultAt > 0L && now - lastResultAt <= CONNECTED_WINDOW_MS;
                }
            }
        };
    }

    private void put(String key, double value, long now) {
        values.put(key, new Reading(value, now));
        Pending current = pending.get(key);
        if (current != null
                && (current.value == value || now - current.setAt > PENDING_GRACE_MS)) {
            pending.remove(key);
        }
    }

    /**
     * Follows AUTO and the climate power. The AUTO change that switching off causes may arrive
     * before or after the power value, so a change just before it does not count. The kept value
     * is known only when the climate is seen going off.
     */
    private void track(int id, int area, double value, long now) {
        if (id == Hvac.AUTO && (area == Hvac.ZONE_DRIVER
                || area == Gib.AREA_GLOBAL && !autoFromDriver)) {
            autoFromDriver |= area == Hvac.ZONE_DRIVER;
            if (auto == null || auto != value) {
                autoBefore = auto;
                auto = value;
                autoChangedAt = now;
            }
        } else if (id == Hvac.POWER && area == Gib.AREA_GLOBAL) {
            boolean on = value != ClimateFunction.POWER.offValue;
            if (!on && Boolean.TRUE.equals(powerOn)) {
                autoWhenOff = auto != null && now - autoChangedAt <= POWER_OFF_WINDOW_MS
                        ? autoBefore : auto;
            } else if (on) {
                autoWhenOff = null;
            }
            powerOn = on;
        }
    }

    private Double read(String key, long now) {
        Pending current = pending.get(key);
        if (current != null) {
            if (now - current.setAt <= PENDING_TIMEOUT_MS) {
                return current.value;
            }
            pending.remove(key);
        }
        Reading reading = values.get(key);
        if (reading == null || now - reading.receivedAt > STALE_AFTER_MS) {
            return null;
        }
        return reading.value;
    }

    /**
     * Whether a zoned read may use the global value. {@link Hvac#WINDOW_POS} describes another
     * glass in each area, so its global value belongs to none of them: the sunroof would show
     * the sunshade's position.
     */
    static boolean hasGlobalFallback(int id, int area) {
        return area != Gib.AREA_GLOBAL && id != Hvac.WINDOW_POS;
    }

    static String propertyKey(int id, int area) {
        return "p" + id + "@" + area;
    }

    static String sensorKey(int id) {
        return "s" + id;
    }

    private static final class Reading {
        final double value;
        final long receivedAt;

        Reading(double value, long receivedAt) {
            this.value = value;
            this.receivedAt = receivedAt;
        }
    }

    private static final class Pending {
        final double value;
        final long setAt;

        Pending(double value, long setAt) {
            this.value = value;
            this.setAt = setAt;
        }
    }
}
