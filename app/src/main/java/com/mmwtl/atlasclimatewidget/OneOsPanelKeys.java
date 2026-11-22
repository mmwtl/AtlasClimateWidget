package com.mmwtl.atlasclimatewidget;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import com.geely.lib.oneosapi.IServiceManager;
import com.geely.lib.oneosapi.IServiceManagerCallback;
import com.geely.lib.oneosapi.input.IInputListener;
import com.geely.lib.oneosapi.input.IInputManager;

import java.util.function.IntConsumer;

/** OneOS input events for one focused session of the large climate window. */
final class OneOsPanelKeys implements ServiceConnection {
    private static final String PACKAGE = "com.geely.service.oneosapi";
    private static final int INPUT_SERVICE = 8;
    static final int VOLUME_UP = 200024;
    static final int VOLUME_DOWN = 200025;
    static final int KNOB_VOLUME_UP = 210007;
    static final int KNOB_VOLUME_DOWN = 210008;
    static final int MEDIA_NEXT = 200087;
    static final int MEDIA_PREVIOUS = 200088;
    private static final int[] KEYS = {
            VOLUME_UP, VOLUME_DOWN, KNOB_VOLUME_UP, KNOB_VOLUME_DOWN, MEDIA_NEXT, MEDIA_PREVIOUS
    };

    private final Context context;
    private final int[] keyCodes;
    private IntConsumer onClick;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean bound;
    private IServiceManager manager;
    private IInputManager input;

    private final IServiceManagerCallback services = new IServiceManagerCallback.Stub() {
        @Override
        public void onServiceChanged(int type) {
            if (type == INPUT_SERVICE) {
                main.post(OneOsPanelKeys.this::subscribe);
            }
        }
    };

    private final IInputListener keys = new IInputListener.Stub() {
        @Override
        public void onKeyCodeEvent(int keyCode, int event, int softKeyFunction) {
            if (AppLog.debugEnabled()) {
                AppLog.debug("OneOS panel key=" + keyCode + " event=" + event
                        + " soft=" + softKeyFunction);
            }
        }

        @Override
        public void onShortClick(int keyCode, int softKeyFunction) {
            if (AppLog.debugEnabled()) {
                AppLog.debug("OneOS panel shortClick key=" + keyCode
                        + " soft=" + softKeyFunction);
            }
            main.post(() -> {
                IntConsumer listener = onClick;
                if (input != null && listener != null) {
                    listener.accept(keyCode);
                }
            });
        }

        @Override
        public void onHoldingPressStarted(int keyCode, int softKeyFunction) {
            if (AppLog.debugEnabled()) {
                AppLog.debug("OneOS panel holdStart key=" + keyCode
                        + " soft=" + softKeyFunction);
            }
        }

        @Override
        public void onHoldingPressStopped(int keyCode, int softKeyFunction) {
            if (AppLog.debugEnabled()) {
                AppLog.debug("OneOS panel holdStop key=" + keyCode
                        + " soft=" + softKeyFunction);
            }
        }

        @Override
        public void onLongPressTriggered(int keyCode, int softKeyFunction) {
            if (AppLog.debugEnabled()) {
                AppLog.debug("OneOS panel longPress key=" + keyCode
                        + " soft=" + softKeyFunction);
            }
        }

        @Override
        public void onDoubleClick(int keyCode, int softKeyFunction) {
            if (AppLog.debugEnabled()) {
                AppLog.debug("OneOS panel doubleClick key=" + keyCode
                        + " soft=" + softKeyFunction);
            }
        }
    };

    static int[] keys(boolean temperature, boolean fan) {
        if (temperature && fan) {
            return KEYS.clone();
        }
        if (temperature) {
            return new int[]{VOLUME_UP, VOLUME_DOWN, KNOB_VOLUME_UP, KNOB_VOLUME_DOWN};
        }
        if (fan) {
            return new int[]{MEDIA_NEXT, MEDIA_PREVIOUS};
        }
        return new int[0];
    }

    OneOsPanelKeys(Context context, int[] keyCodes, IntConsumer onClick) {
        this.context = context.getApplicationContext();
        this.keyCodes = keyCodes.clone();
        this.onClick = onClick;
    }

    void open() {
        Intent intent = new Intent().setClassName(PACKAGE, PACKAGE + ".OneOSApiService");
        try {
            bound = context.bindService(intent, this, Context.BIND_AUTO_CREATE);
            if (!bound) {
                AppLog.warn("OneOS panel input service is unavailable", null);
                context.unbindService(this);
            }
        } catch (SecurityException | IllegalArgumentException error) {
            AppLog.warn("OneOS panel input service is unavailable", error);
        }
    }

    void close() {
        // Release the activity callback even if the remote process keeps the Binder stubs alive.
        onClick = null;
        main.removeCallbacksAndMessages(null);
        if (!bound) {
            return;
        }
        bound = false;
        unsubscribe();
        if (manager != null) {
            try {
                manager.unregisterCallback(services);
            } catch (RemoteException | RuntimeException error) {
                AppLog.warn("OneOS panel service callback removal failed", error);
            }
            manager = null;
        }
        try {
            context.unbindService(this);
        } catch (IllegalArgumentException error) {
            AppLog.warn("OneOS panel input service unbind failed", error);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        if (!bound) {
            return;
        }
        manager = IServiceManager.Stub.asInterface(service);
        try {
            manager.registerCallback(services);
        } catch (RemoteException | RuntimeException error) {
            AppLog.warn("OneOS panel service callback registration failed", error);
        }
        subscribe();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        manager = null;
        unsubscribe();
    }

    @Override
    public void onBindingDied(ComponentName name) {
        onServiceDisconnected(name);
        close();
    }

    @Override
    public void onNullBinding(ComponentName name) {
        close();
    }

    private void subscribe() {
        if (manager == null) {
            return;
        }
        try {
            IBinder service = manager.getService(INPUT_SERVICE);
            if (input != null && input.asBinder().equals(service)) {
                return;
            }
            unsubscribe();
            if (service != null) {
                IInputManager next = IInputManager.Stub.asInterface(service);
                next.registerListener(keys, context.getPackageName(), keyCodes);
                input = next;
                AppLog.info("OneOS panel steering keys subscribed");
            }
        } catch (RemoteException | RuntimeException error) {
            AppLog.warn("OneOS panel key subscription failed", error);
        }
    }

    private void unsubscribe() {
        if (input != null) {
            try {
                input.unregisterListener(keys, context.getPackageName());
            } catch (RemoteException | RuntimeException error) {
                AppLog.warn("OneOS panel key removal failed", error);
            }
            input = null;
        }
    }
}
