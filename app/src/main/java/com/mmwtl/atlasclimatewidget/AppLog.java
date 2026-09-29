package com.mmwtl.atlasclimatewidget;

import android.util.Log;

final class AppLog {
    private static final String TAG = "AtlasClimateWidget";

    private AppLog() {
    }

    static void warn(String message, Throwable error) {
        Log.w(TAG, message, error);
    }

    static void info(String message) {
        Log.i(TAG, message);
    }

    /** Bridge traffic; enable with {@code adb shell setprop log.tag.AtlasClimateWidget DEBUG}. */
    static boolean debugEnabled() {
        return Log.isLoggable(TAG, Log.DEBUG);
    }

    static void debug(String message) {
        Log.d(TAG, message);
    }
}
