package com.mmwtl.atlasclimatewidget;

import android.content.Context;
import android.os.Handler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Experimental direct link to the head unit's ECarX API ({@code com.ecarx.xui.adaptapi}), the
 * one GInputBridge and FX11 call: {@code Car.create(context)}, then {@code ICarFunction} for
 * properties and {@code ISensor} for sensors. The API lives in the head unit's framework, so it
 * is reached by reflection and the app bundles nothing of it; elsewhere {@link #connect}
 * returns null.
 *
 * <p>The global area means the API's zoneless calls, as the bridge does without {@code area}.
 * A watcher reports a function's changes in whatever zone the car uses, and its callbacks come
 * on several binder threads, so a quick run of them may arrive out of order: a change only
 * rereads every watched area of the function instead of trusting the reported value. A watcher registered before the car service answers may
 * stay silent, so a watcher is registered only once a read has succeeded, and again after a
 * read fails. The service reads everything first and registers the watchers afterwards.
 */
final class Ecarx implements CarLink {
    private static final String PACKAGE = "com.ecarx.xui.adaptapi";
    private static final String CAR = PACKAGE + ".car.Car";
    private static final String CAR_INTERFACE = PACKAGE + ".car.ICar";
    private static final String FUNCTION = PACKAGE + ".car.base.ICarFunction";
    private static final String WATCHER = FUNCTION + "$IFunctionValueWatcher";
    private static final String SENSOR = PACKAGE + ".car.sensor.ISensor";
    private static final String LISTENER = SENSOR + "$ISensorListener";

    private final Context context;
    private final Handler worker;
    private final Sink sink;
    private final Method create;
    private final Method getFunctionManager;
    private final Method getSensorManager;
    private final Method getInt;
    private final Method getZonedInt;
    private final Method getFloat;
    private final Method getZonedFloat;
    private final Method setInt;
    private final Method setZonedInt;
    private final Method setFloat;
    private final Method setZonedFloat;
    private final Method registerWatcher;
    private final Method unregisterWatcher;
    private final Method getSensor;
    private final Method registerListener;
    private final Method unregisterListener;
    private final Object watcher;
    private final Object listener;

    /** Worker-thread only. */
    private Object car;
    private Object functions;
    private Object sensors;
    private boolean answered;
    private final Map<Integer, Map<Integer, Boolean>> watched = new HashMap<>();
    private final Set<Integer> watchedSensors = new HashSet<>();
    private final Set<Integer> registered = new HashSet<>();
    private final Set<Integer> registeredSensors = new HashSet<>();

    private Ecarx(Context context, Handler worker, Sink sink) throws ReflectiveOperationException {
        this.context = context.getApplicationContext();
        this.worker = worker;
        this.sink = sink;
        Class<?> carClass = Class.forName(CAR);
        Class<?> carInterface = Class.forName(CAR_INTERFACE);
        Class<?> function = Class.forName(FUNCTION);
        Class<?> watcherClass = Class.forName(WATCHER);
        Class<?> sensor = Class.forName(SENSOR);
        Class<?> listenerClass = Class.forName(LISTENER);
        create = carClass.getMethod("create", Context.class);
        getFunctionManager = carInterface.getMethod("getICarFunction");
        getSensorManager = carInterface.getMethod("getSensorManager");
        getInt = function.getMethod("getFunctionValue", int.class);
        getZonedInt = function.getMethod("getFunctionValue", int.class, int.class);
        getFloat = function.getMethod("getCustomizeFunctionValue", int.class);
        getZonedFloat = function.getMethod("getCustomizeFunctionValue", int.class, int.class);
        setInt = function.getMethod("setFunctionValue", int.class, int.class);
        setZonedInt = function.getMethod("setFunctionValue", int.class, int.class, int.class);
        setFloat = function.getMethod("setCustomizeFunctionValue", int.class, float.class);
        setZonedFloat = function.getMethod("setCustomizeFunctionValue", int.class, int.class,
                float.class);
        registerWatcher = function.getMethod("registerFunctionValueWatcher", int.class,
                watcherClass);
        unregisterWatcher = function.getMethod("unregisterFunctionValueWatcher", watcherClass);
        getSensor = sensor.getMethod("getSensorLatestValue", int.class);
        registerListener = sensor.getMethod("registerListener", listenerClass, int.class);
        unregisterListener = sensor.getMethod("unregisterListener", listenerClass);
        watcher = proxy(watcherClass);
        listener = proxy(listenerClass);
    }

    /**
     * Returns the link, or null when the head unit has no ECarX API. The car itself may come
     * up later, so every call asks for it until it does.
     */
    static Ecarx connect(Context context, Handler worker, Sink sink) {
        try {
            Ecarx link = new Ecarx(context, worker, sink);
            link.ensure();
            return link;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            AppLog.warn("ECarX API unavailable", error);
            return null;
        }
    }

    @Override
    public boolean isDirect() {
        return true;
    }

    @Override
    public void listenProperty(int id, int area, boolean isFloat) {
        watched.computeIfAbsent(id, key -> new HashMap<>()).put(area, isFloat);
        watch(id);
    }

    @Override
    public void requestProperty(int id, int area, boolean isFloat) {
        if (!ensure()) {
            return;
        }
        boolean global = area == Gib.AREA_GLOBAL;
        Object value;
        try {
            if (isFloat) {
                value = global ? read(getFloat, functions, id)
                        : read(getZonedFloat, functions, id, area);
            } else {
                value = global ? read(getInt, functions, id)
                        : read(getZonedInt, functions, id, area);
            }
        } catch (UnsupportedOperationException unsupported) {
            // The car answered that it has no such function; the bridge reports that as -1.
            answer();
            sink.onProperty(id, area, -1d);
            return;
        } catch (ReflectiveOperationException | RuntimeException error) {
            lost(error);
            return;
        }
        if (value instanceof Number) {
            answer();
            sink.onProperty(id, area, ((Number) value).doubleValue());
        }
    }

    @Override
    public void listenSensor(int id) {
        watchedSensors.add(id);
        watchSensor(id);
    }

    @Override
    public void requestSensor(int id) {
        if (!ensure() || sensors == null) {
            return;
        }
        Object value;
        try {
            value = read(getSensor, sensors, id);
        } catch (UnsupportedOperationException unsupported) {
            answer();
            return;
        } catch (ReflectiveOperationException | RuntimeException error) {
            lost(error);
            return;
        }
        if (value instanceof Float && Float.isFinite((Float) value)) {
            answer();
            sink.onSensor(id, (Float) value);
        }
    }

    private void watch(int id) {
        if (!answered || !watched.containsKey(id) || registered.contains(id) || !ensure()) {
            return;
        }
        if (Boolean.TRUE.equals(call(registerWatcher, functions, id, watcher))) {
            registered.add(id);
        }
    }

    private void watchSensor(int id) {
        if (!answered || !watchedSensors.contains(id) || registeredSensors.contains(id)
                || !ensure() || sensors == null) {
            return;
        }
        if (Boolean.TRUE.equals(call(registerListener, sensors, listener, id))) {
            registeredSensors.add(id);
        }
    }

    @Override
    public void setInt(int id, int area, int value) {
        if (ensure()) {
            if (area == Gib.AREA_GLOBAL) {
                call(setInt, functions, id, value);
            } else {
                call(setZonedInt, functions, id, area, value);
            }
        }
    }

    @Override
    public void setFloat(int id, int area, float value) {
        if (ensure()) {
            if (area == Gib.AREA_GLOBAL) {
                call(setFloat, functions, id, value);
            } else {
                call(setZonedFloat, functions, id, area, value);
            }
        }
    }

    @Override
    public void close() {
        if (functions != null && !registered.isEmpty()) {
            call(unregisterWatcher, functions, watcher);
        }
        if (sensors != null && !registeredSensors.isEmpty()) {
            call(unregisterListener, sensors, listener);
        }
        registered.clear();
        registeredSensors.clear();
        watched.clear();
        watchedSensors.clear();
    }

    /** Gets the car and its managers, which may appear only once the car service is up. */
    private boolean ensure() {
        try {
            if (car == null) {
                car = create.invoke(null, context);
            }
            if (car != null && functions == null) {
                functions = getFunctionManager.invoke(car);
            }
            if (car != null && sensors == null) {
                sensors = getSensorManager.invoke(car);
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            AppLog.warn("ECarX car is not ready", error);
        }
        return functions != null;
    }

    private void answer() {
        answered = true;
    }

    /** A read failed: the car service is gone or not up yet, and its watchers with it. */
    private void lost(Throwable error) {
        if (answered) {
            AppLog.warn("ECarX stopped answering", error);
        }
        answered = false;
        registered.clear();
        registeredSensors.clear();
    }

    /** Invokes a getter, rethrowing the API's own exception. */
    private static Object read(Method method, Object target, Object... args)
            throws ReflectiveOperationException {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof UnsupportedOperationException) {
                throw (UnsupportedOperationException) error.getCause();
            }
            throw error;
        }
    }

    private static Object call(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException error) {
            Throwable cause = error instanceof InvocationTargetException
                    ? error.getCause() : error;
            AppLog.warn("ECarX " + method.getName() + " failed", cause);
            return null;
        }
    }

    // ---- callbacks, on binder threads --------------------------------------------------------

    private Object proxy(Class<?> type) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return "AtlasClimate " + type.getSimpleName();
                case "onFunctionValueChanged":
                    changed((Integer) args[0], (Integer) args[1], (Integer) args[2]);
                    return null;
                case "onCustomizeFunctionValueChanged":
                    changed((Integer) args[0], (Integer) args[1], (Float) args[2]);
                    return null;
                case "onSensorValueChanged":
                    int sensor = (Integer) args[0];
                    float value = (Float) args[1];
                    if (Float.isFinite(value)) {
                        worker.post(() -> sink.onSensor(sensor, value));
                    }
                    return null;
                default:
                    return null;
            }
        };
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private void changed(int id, int zone, Number value) {
        if (AppLog.debugEnabled()) {
            AppLog.debug("<- ECarX id=" + id + " zone=" + zone + " value=" + value);
        }
        worker.post(() -> {
            Map<Integer, Boolean> areas = watched.get(id);
            if (areas == null) {
                return;
            }
            for (Map.Entry<Integer, Boolean> area : new HashMap<>(areas).entrySet()) {
                requestProperty(id, area.getKey(), area.getValue());
            }
        });
    }
}
