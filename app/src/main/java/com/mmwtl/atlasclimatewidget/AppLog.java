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
}
