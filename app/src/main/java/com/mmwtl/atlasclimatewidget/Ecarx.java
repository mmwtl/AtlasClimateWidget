package com.mmwtl.atlasclimatewidget;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;

import java.lang.reflect.Constructor;
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
    /** State of the direct link, not a setting, so not in {@link SettingsBackup}. */
    private static final String STATE = "ecarx_state";
    private static final String SEAT_SLOT = "seat_memory_slot";

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
    private final Method isFunctionSupported;
    private final Method isZonedFunctionSupported;
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
    /**
     * Variants of the API this head unit uses, found from its support answers and forgotten with
     * the car service. Null means the car has not answered yet; a definite «no» is kept too, so
     * reads and change callbacks do not ask again.
     */
    private final Map<Integer, Integer> propertyIds = new HashMap<>();
    private Integer sunshadeArea;
    private Boolean tailgateState;
    private Boolean blower;
    /** The tailgate's last direction, 1 up and 2 down: a half-open stop reverses it. */
    private int tailgateMove;

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
        Method supported;
        try {
            supported = function.getMethod("isFunctionSupported", int.class);
        } catch (NoSuchMethodException missing) {
            // Keep the existing direct link usable if this firmware cannot report support.
            supported = null;
        }
        isFunctionSupported = supported;
        Method zonedSupport;
        try {
            zonedSupport = function.getMethod("isFunctionSupported", int.class, int.class);
        } catch (NoSuchMethodException missing) {
            zonedSupport = null;
        }
        isZonedFunctionSupported = zonedSupport;
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
        int actualId = readId(id, area, true);
        int actualArea = propertyArea(id, area);
        Object value;
        try {
            if (isFloat) {
                value = global ? read(getFloat, functions, actualId)
                        : read(getZonedFloat, functions, actualId, actualArea);
            } else {
                value = global ? read(getInt, functions, actualId)
                        : read(getZonedInt, functions, actualId, actualArea);
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
            double raw = ((Number) value).doubleValue();
            if (actualId == Hvac.TAILGATE_STATE) {
                raw = tailgateShown((int) raw);
            }
            sink.onProperty(id, area, raw);
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
        if (!answered || !watched.containsKey(id) || !ensure()) {
            return;
        }
        int area = watched.get(id).keySet().iterator().next();
        int actualId = readId(id, area, true);
        if (!registered.contains(actualId)
                && Boolean.TRUE.equals(call(registerWatcher, functions, actualId, watcher))) {
            registered.add(actualId);
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
            if (id == Hvac.DOOR && area == Hvac.ZONE_TRUNK
                    && readId(id, area, true) == Hvac.TAILGATE_STATE) {
                // A tap means the tailgate's next move, which its current state decides.
                Object state = call(getZonedInt, functions, Hvac.TAILGATE_STATE, area);
                Integer command = state instanceof Number
                        ? tailgateCommand(((Number) state).intValue()) : null;
                if (command == null) {
                    return;
                }
                value = command;
            }
            if (area == Gib.AREA_GLOBAL) {
                call(setInt, functions, propertyId(id, true), value);
            } else {
                call(setZonedInt, functions, id, propertyArea(id, area), value);
            }
        }
    }

    @Override
    public void setFloat(int id, int area, float value) {
        if (ensure()) {
            if (area == Gib.AREA_GLOBAL) {
                call(setFloat, functions, id, value);
            } else {
                call(setZonedFloat, functions, id, propertyArea(id, area), value);
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
        forgetVariants();
        tailgateMove = 0;
    }

    private void forgetVariants() {
        propertyIds.clear();
        sunshadeArea = null;
        tailgateState = null;
        blower = null;
    }

    /**
     * The windshield heating id this head unit uses. The 2026 one is chosen only after explicit
     * answers, never after an error or silence. Without {@code probe} only a known answer is used.
     */
    private int propertyId(int id, boolean probe) {
        if (id != Hvac.WINDSHIELD_HEAT_ATLAS && id != Hvac.WINDSHIELD_HEAT_PREFACE
                && id != Hvac.WINDSHIELD_HEAT_CITYRAY) {
            return id;
        }
        Integer resolved = propertyIds.get(id);
        if (resolved != null || !probe) {
            return resolved != null ? resolved : id;
        }
        // Probe all legacy variants first: DEFROST_FRONT can mean ordinary airflow on old cars.
        int[] candidates = {id, Hvac.WINDSHIELD_HEAT_ATLAS, Hvac.WINDSHIELD_HEAT_PREFACE,
                Hvac.WINDSHIELD_HEAT_CITYRAY};
        boolean allUnsupported = true;
        for (int candidate : candidates) {
            Boolean exists = supported(candidate);
            if (Boolean.TRUE.equals(exists)) {
                return resolve(id, candidate);
            }
            allUnsupported &= Boolean.FALSE.equals(exists);
        }
        if (!allUnsupported) {
            return id;
        }
        Boolean newer = supported(Hvac.WINDSHIELD_HEAT_2026);
        if (Boolean.TRUE.equals(newer)) {
            return resolve(id, Hvac.WINDSHIELD_HEAT_2026);
        }
        if (Boolean.FALSE.equals(newer)) {
            // The car has no windshield heating at all: keep the model's id and stop asking.
            propertyIds.put(id, id);
        }
        return id;
    }

    private int resolve(int id, int actual) {
        propertyIds.put(id, actual);
        AppLog.info("ECarX windshield heating: " + id + " -> " + actual);
        return actual;
    }

    private int readId(int id, int area, boolean probe) {
        if (id == Hvac.DOOR && area == Hvac.ZONE_TRUNK) {
            if (tailgateState == null && probe) {
                tailgateState = supported(Hvac.TAILGATE_STATE, area);
                if (Boolean.TRUE.equals(tailgateState)) {
                    AppLog.info("ECarX tailgate state: " + Hvac.TAILGATE_STATE);
                }
            }
            return Boolean.TRUE.equals(tailgateState) ? Hvac.TAILGATE_STATE : id;
        }
        return area == Gib.AREA_GLOBAL ? propertyId(id, probe) : id;
    }

    /** The tailgate state as {@link Hvac#DOOR} reports it: 0 closed, 1 open, -1 unknown. */
    private double tailgateShown(int state) {
        noteTailgateMove(state);
        if (state == 0 || state == Hvac.TAILGATE_UNKNOWN || state == Hvac.TAILGATE_CLOSED) {
            return 0;
        }
        return state > Hvac.TAILGATE_CLOSED && state <= Hvac.TAILGATE_MIN_POSITION ? 1 : -1;
    }

    /** The {@link Hvac#DOOR} command a tap sends, as in MyGeelyWidgets; null sends nothing. */
    private Integer tailgateCommand(int state) {
        switch (state) {
            case Hvac.TAILGATE_CLOSED:
            case Hvac.TAILGATE_STOPPED_CLOSING:
            case Hvac.TAILGATE_MIN_POSITION:
                return 1;
            case Hvac.TAILGATE_OPENING:
            case Hvac.TAILGATE_OPENING_SLOW:
            case Hvac.TAILGATE_CLOSING:
            case Hvac.TAILGATE_CLOSING_SLOW:
                noteTailgateMove(state);
                return Hvac.TAILGATE_PAUSE;
            case Hvac.TAILGATE_HALF_OPEN: {
                int command = tailgateMove == 2 ? 1 : 0;
                tailgateMove = 0;
                return command;
            }
            default:
                // Unknown, stopped opening, open and the reserved states close it.
                return state == 0 || state >= Hvac.TAILGATE_UNKNOWN
                        && state <= Hvac.TAILGATE_MIN_POSITION ? 0 : null;
        }
    }

    private void noteTailgateMove(int state) {
        if (state == Hvac.TAILGATE_OPENING || state == Hvac.TAILGATE_OPENING_SLOW) {
            tailgateMove = 1;
        } else if (state == Hvac.TAILGATE_CLOSING || state == Hvac.TAILGATE_CLOSING_SLOW) {
            tailgateMove = 2;
        }
    }

    private int propertyArea(int id, int area) {
        if (id != Hvac.WINDOW_POS || area != Hvac.ZONE_SUNSHADE) {
            return area;
        }
        if (sunshadeArea == null) {
            Boolean old = supported(id, area);
            if (Boolean.TRUE.equals(old)) {
                sunshadeArea = area;
            } else if (Boolean.FALSE.equals(old)) {
                Boolean newer = supported(id, Hvac.ZONE_SUNSHADE_2026);
                if (Boolean.TRUE.equals(newer)) {
                    sunshadeArea = Hvac.ZONE_SUNSHADE_2026;
                    AppLog.info("ECarX sunshade area: " + sunshadeArea);
                } else if (Boolean.FALSE.equals(newer)) {
                    sunshadeArea = area;
                }
            }
        }
        return sunshadeArea == null ? area : sunshadeArea;
    }

    /** As in MyGeelyWidgets: the model's choice, dropped only when the car has no blower step. */
    @Override
    public boolean fanBlower(boolean modelDefault) {
        if (!modelDefault) {
            return false;
        }
        if (blower == null && ensure()) {
            blower = supported(ClimateCommands.FAN_BLOWER, Hvac.ZONE_ROW_1_ALL);
            if (Boolean.FALSE.equals(blower)) {
                AppLog.info("ECarX has no fan blower step, fan -/+ set the speed");
            }
        }
        return !Boolean.FALSE.equals(blower);
    }

    /**
     * Seat memory on head units without {@link Hvac#SEAT_MEMORY_RESTORE}: a slot, then restore.
     * The car does not report the recalled slot, so the last one is kept in the app's storage
     * and taps alternate 1 and 2. Older head units keep the bridge's {@code SEAT_MEMORY}.
     */
    @Override
    public boolean carFunction(String function) {
        if (!"SEAT_MEMORY".equals(function) || !ensure()
                || !Boolean.FALSE.equals(supported(Hvac.SEAT_MEMORY_RESTORE, Hvac.ZONE_DRIVER))
                || !Boolean.TRUE.equals(supported(Hvac.SEAT_POSITION_SET, Hvac.ZONE_DRIVER))
                || !Boolean.TRUE.equals(supported(Hvac.SEAT_POSITION_BUTTON, Hvac.ZONE_DRIVER))) {
            return false;
        }
        SharedPreferences state = context.getSharedPreferences(STATE, Context.MODE_PRIVATE);
        int slot = state.getInt(SEAT_SLOT, 0) == 1 ? 2 : 1;
        AppLog.info("ECarX seat memory: select slot " + slot + ", then restore");
        if (Boolean.TRUE.equals(call(setZonedInt, functions,
                Hvac.SEAT_POSITION_SET, Hvac.ZONE_DRIVER, slot))
                && Boolean.TRUE.equals(call(setZonedInt, functions,
                Hvac.SEAT_POSITION_BUTTON, Hvac.ZONE_DRIVER, Hvac.SEAT_POSITION_RESTORE))) {
            state.edit().putInt(SEAT_SLOT, slot).apply();
        } else {
            AppLog.warn("ECarX seat memory command failed", null);
        }
        return true;
    }

    private Boolean supported(int id) {
        if (isFunctionSupported == null) {
            return null;
        }
        return supportStatus(call(isFunctionSupported, functions, id));
    }

    private Boolean supported(int id, int area) {
        if (area == Gib.AREA_GLOBAL) {
            return supported(id);
        }
        return isZonedFunctionSupported == null ? null
                : supportStatus(call(isZonedFunctionSupported, functions, id, area));
    }

    private static Boolean supportStatus(Object status) {
        if (!(status instanceof Enum<?>)) {
            return null;
        }
        String name = ((Enum<?>) status).name();
        if ("error".equals(name)) {
            return null;
        }
        return "active".equals(name) || "notactive".equals(name);
    }

    /** Gets the car and its managers, which may appear only once the car service is up. */
    private boolean ensure() {
        try {
            if (car == null) {
                car = createCar();
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

    /** Some head units reject the app's signature in the factory, as in MyGeelyWidgets. */
    private Object createCar() throws ReflectiveOperationException {
        try {
            return create.invoke(null, context);
        } catch (InvocationTargetException error) {
            if (!(error.getCause() instanceof SecurityException)) {
                throw error;
            }
            AppLog.warn("ECarX Car.create denied access, trying CarImpl", error.getCause());
            Constructor<?> constructor = Class.forName(PACKAGE + ".car.CarImpl")
                    .getDeclaredConstructor(Context.class);
            constructor.setAccessible(true);
            Object result = constructor.newInstance(context);
            AppLog.info("ECarX CarImpl created after Car.create denied access");
            return result;
        }
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
        forgetVariants();
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
            for (Map.Entry<Integer, Map<Integer, Boolean>> entry
                    : new HashMap<>(watched).entrySet()) {
                int logicalId = entry.getKey();
                int watchedArea = entry.getValue().keySet().iterator().next();
                // Only known variants: a callback must not ask the car about every function.
                if (readId(logicalId, watchedArea, false) != id) {
                    continue;
                }
                for (Map.Entry<Integer, Boolean> area
                        : new HashMap<>(entry.getValue()).entrySet()) {
                    requestProperty(logicalId, area.getKey(), area.getValue());
                }
            }
        });
    }
}
