package com.mmwtl.atlasclimatewidget;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;

import java.util.function.IntConsumer;

/**
 * Experimental steering-wheel volume roller reader via Android media volume changes.
 *
 * <p>Stock OneOS InputService consumes {@code KEYCODE_R_VOLUME_UP/DOWN} internally and calls
 * {@link AudioManager#adjustVolume} without notifying {@code IInputListener}. While the climate
 * window is focused, this observer maps media volume steps to driver temperature steps and
 * immediately restores the media volume to its baseline.
 */
final class PanelVolumeKeys {
    static final String ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";
    static final String EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE";
    static final String EXTRA_VOLUME_STREAM_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE";
    static final String EXTRA_PREV_VOLUME_STREAM_VALUE =
            "android.media.EXTRA_PREV_VOLUME_STREAM_VALUE";

    private final Context context;
    private final AudioManager audio;
    private IntConsumer onStep;
    private boolean registered;
    private int baseline = -1;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onVolumeChanged(intent);
        }
    };

    PanelVolumeKeys(Context context, IntConsumer onStep) {
        this.context = context.getApplicationContext();
        this.audio = this.context.getSystemService(AudioManager.class);
        this.onStep = onStep;
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    void open() {
        if (registered || audio == null) {
            return;
        }
        try {
            baseline = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        } catch (RuntimeException error) {
            AppLog.warn("Cannot read baseline media volume", error);
            return;
        }
        IntentFilter filter = new IntentFilter(ACTION_VOLUME_CHANGED);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
            registered = true;
            if (AppLog.debugEnabled()) {
                AppLog.debug("Panel volume observer open baseline=" + baseline);
            }
        } catch (RuntimeException error) {
            AppLog.warn("Cannot register panel volume observer", error);
        }
    }

    void close() {
        onStep = null;
        if (!registered) {
            return;
        }
        registered = false;
        try {
            context.unregisterReceiver(receiver);
        } catch (IllegalArgumentException error) {
            AppLog.warn("Panel volume observer unregister failed", error);
        }
    }

    private void onVolumeChanged(Intent intent) {
        if (!registered || intent == null || !ACTION_VOLUME_CHANGED.equals(intent.getAction())) {
            return;
        }
        int stream = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1);
        int value = intent.getIntExtra(EXTRA_VOLUME_STREAM_VALUE, -1);
        int prev = intent.getIntExtra(EXTRA_PREV_VOLUME_STREAM_VALUE, -1);
        if (value < 0 && stream >= 0 && audio != null) {
            try {
                value = audio.getStreamVolume(stream);
            } catch (RuntimeException ignored) {
                return;
            }
        }
        if (AppLog.debugEnabled()) {
            AppLog.debug("Panel volume stream=" + stream + " " + prev + "->" + value
                    + " baseline=" + baseline);
        }
        if (stream != AudioManager.STREAM_MUSIC || value < 0 || baseline < 0) {
            return;
        }
        // Ignore our own rollback broadcast (returning to baseline) and no-op updates.
        if (value == baseline || value == prev) {
            return;
        }
        int direction = value > baseline ? 1 : -1;
        restoreVolume(value);
        IntConsumer listener = onStep;
        if (listener != null) {
            listener.accept(direction);
        }
    }

    private void restoreVolume(int observed) {
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, baseline, 0);
        } catch (RuntimeException error) {
            AppLog.warn("Cannot restore panel media volume", error);
            baseline = observed;
        }
    }
}
