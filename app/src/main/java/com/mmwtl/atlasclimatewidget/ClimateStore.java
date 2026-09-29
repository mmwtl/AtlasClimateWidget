package com.mmwtl.atlasclimatewidget;

import java.util.HashMap;
import java.util.Map;

/**
 * Thread-safe climate values received from GInputBridge.
 *
 * <p>Values expire when the bridge stops answering the periodic refresh, so a stopped bridge
 * shows an explicit "no data" state instead of stale values. A command stores an optimistic
 * pending value that is shown until the bridge confirms it or the pending timeout expires.
 */
final class ClimateStore {
    static final long STALE_AFTER_MS = 150_000L;
    static final long PENDING_TIMEOUT_MS = 4_000L;
    /** Results that arrive this soon after a command may describe the state before it. */
    static final long PENDING_GRACE_MS = 1_500L;
    static final long CONNECTED_WINDOW_MS = STALE_AFTER_MS;

    private final Map<String, Reading> values = new HashMap<>();
    private final Map<String, Pending> pending = new HashMap<>();
    private long lastResultAt;

    /** Stores a property result. The bridge reports failed reads as -1. */
    synchronized void putProperty(int id, int area, double value, long now) {
        lastResultAt = now;
        if (value == -1d) {
            return;
        }
        put(propertyKey(id, area), value, now);
    }

    synchronized void putSensor(int id, double value, long now) {
        lastResultAt = now;
        put(sensorKey(id), value, now);
    }

    synchronized void setPending(int id, int area, double value, long now) {
        pending.put(propertyKey(id, area), new Pending(value, now));
    }

    synchronized long lastResultAt() {
        return lastResultAt;
    }

    synchronized void clear() {
        values.clear();
        pending.clear();
        lastResultAt = 0L;
    }

    /** Returns a view that evaluates freshness at {@code now}. */
    ClimateState snapshot(long now) {
        return new ClimateState() {
            @Override
            public Double property(int id, int area) {
                synchronized (ClimateStore.this) {
                    Double value = read(propertyKey(id, area), now);
                    if (value == null && area != Gib.AREA_GLOBAL) {
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
