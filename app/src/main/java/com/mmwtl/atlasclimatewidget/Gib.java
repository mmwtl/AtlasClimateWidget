package com.mmwtl.atlasclimatewidget;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

/**
 * Broadcast contract of the GInputBridge external API ({@code com.salat.gbinder}).
 *
 * <p>Requests are explicit broadcasts to the bridge package. Results and change events are
 * implicit broadcasts with string extras {@code id}, {@code area} and {@code value}; only a
 * dynamically registered receiver can get them, so the widget keeps a foreground service.
 */
final class Gib {
    static final String PACKAGE = "com.salat.gbinder";

    static final String SET_INT_PROPERTY = PACKAGE + ".SET_INT_PROPERTY";
    static final String SET_FLOAT_PROPERTY = PACKAGE + ".SET_FLOAT_PROPERTY";
    static final String GET_INT_PROPERTY = PACKAGE + ".GET_INT_PROPERTY";
    static final String GET_FLOAT_PROPERTY = PACKAGE + ".GET_FLOAT_PROPERTY";
    static final String LISTEN_PROPERTY_CHANGES = PACKAGE + ".LISTEN_PROPERTY_CHANGES";
    static final String GET_FLOAT_SENSOR = PACKAGE + ".GET_FLOAT_SENSOR";
    static final String LISTEN_SENSOR_CHANGES = PACKAGE + ".LISTEN_SENSOR_CHANGES";
    static final String CAR_FUNCTION = PACKAGE + ".CAR_FUNCTION";

    static final String PROPERTY_INT_RESULT = PACKAGE + ".PROPERTY_INT_RESULT";
    static final String PROPERTY_INT_CHANGED = PACKAGE + ".PROPERTY_INT_CHANGED";
    static final String PROPERTY_FLOAT_RESULT = PACKAGE + ".PROPERTY_FLOAT_RESULT";
    static final String PROPERTY_FLOAT_CHANGED = PACKAGE + ".PROPERTY_FLOAT_CHANGED";
    static final String SENSOR_FLOAT_RESULT = PACKAGE + ".SENSOR_FLOAT_RESULT";
    static final String SENSOR_FLOAT_CHANGED = PACKAGE + ".SENSOR_FLOAT_CHANGED";

    static final String EXTRA_ID = "id";
    static final String EXTRA_AREA = "area";
    static final String EXTRA_VALUE = "value";
    static final String EXTRA_FUNCTION = "function";

    /** Area the bridge uses when a request has no {@code area} extra. */
    static final int AREA_GLOBAL = Integer.MIN_VALUE;

    private Gib() {
    }

    static boolean isInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException error) {
            return false;
        }
    }

    static void setInt(Context context, int id, int area, int value) {
        send(context, request(SET_INT_PROPERTY, id, area).putExtra(EXTRA_VALUE, value));
    }

    static void setFloat(Context context, int id, int area, float value) {
        send(context, request(SET_FLOAT_PROPERTY, id, area).putExtra(EXTRA_VALUE, value));
    }

    static void getInt(Context context, int id, int area) {
        send(context, request(GET_INT_PROPERTY, id, area));
    }

    static void getFloat(Context context, int id, int area) {
        send(context, request(GET_FLOAT_PROPERTY, id, area));
    }

    static void listenProperty(Context context, int id, int area) {
        send(context, request(LISTEN_PROPERTY_CHANGES, id, area));
    }

    static void getFloatSensor(Context context, int id) {
        send(context, new Intent(GET_FLOAT_SENSOR).setPackage(PACKAGE).putExtra(EXTRA_ID, id));
    }

    static void listenSensor(Context context, int id) {
        send(context, new Intent(LISTEN_SENSOR_CHANGES).setPackage(PACKAGE).putExtra(EXTRA_ID, id));
    }

    /** Runs one of the bridge's own car functions such as {@code ME_HOT}. */
    static void carFunction(Context context, String function) {
        send(context, new Intent(CAR_FUNCTION).setPackage(PACKAGE)
                .putExtra(EXTRA_FUNCTION, function));
    }

    private static Intent request(String action, int id, int area) {
        return new Intent(action)
                .setPackage(PACKAGE)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_AREA, area);
    }

    private static void send(Context context, Intent intent) {
        try {
            context.sendBroadcast(intent);
        } catch (RuntimeException error) {
            AppLog.warn("Cannot send " + intent.getAction(), error);
        }
    }

    /** Reads an extra that the bridge sends as a string but older builds may send as a number. */
    @SuppressWarnings("deprecation")
    static String extra(Intent intent, String name) {
        try {
            Bundle extras = intent.getExtras();
            Object value = extras == null ? null : extras.get(name);
            return value == null ? null : value.toString().trim();
        } catch (RuntimeException error) {
            return null;
        }
    }

    static Integer parseInt(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException error) {
            try {
                double value = Double.parseDouble(raw);
                return Double.isFinite(value) ? (int) Math.round(value) : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    static Float parseFloat(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            float value = Float.parseFloat(raw);
            return Float.isFinite(value) ? value : null;
        } catch (NumberFormatException error) {
            return null;
        }
    }
}
