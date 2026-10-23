package com.ecarx.xui.adaptapi.car;

import android.content.Context;

import com.ecarx.xui.adaptapi.car.base.ICarFunction;
import com.ecarx.xui.adaptapi.car.sensor.ISensor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Test double of {@code Car}: a car whose values, failures and watchers tests control. */
public final class Car implements ICar, ICarFunction, ISensor {
    public static Car instance;

    /** Values by {@code id@zone}; the zoneless calls use zone {@code "g"}. */
    public final Map<String, Number> values = new HashMap<>();
    public final List<String> sets = new ArrayList<>();
    public final Map<Integer, IFunctionValueWatcher> watchers = new HashMap<>();
    public final Map<Integer, ISensorListener> listeners = new HashMap<>();
    public final Map<Integer, Float> sensors = new HashMap<>();
    public boolean down;

    public static ICar create(Context context) {
        return instance;
    }

    @Override
    public ICarFunction getICarFunction() {
        return this;
    }

    @Override
    public ISensor getSensorManager() {
        return this;
    }

    private Number value(int function, String zone) {
        if (down) {
            throw new IllegalStateException("car service is down");
        }
        Number value = values.get(function + "@" + zone);
        if (value == null) {
            throw new UnsupportedOperationException("no " + function);
        }
        return value;
    }

    @Override
    public float getCustomizeFunctionValue(int function) {
        return value(function, "g").floatValue();
    }

    @Override
    public float getCustomizeFunctionValue(int function, int zone) {
        return value(function, String.valueOf(zone)).floatValue();
    }

    @Override
    public int getFunctionValue(int function) {
        return value(function, "g").intValue();
    }

    @Override
    public int getFunctionValue(int function, int zone) {
        return value(function, String.valueOf(zone)).intValue();
    }

    @Override
    public boolean registerFunctionValueWatcher(int function, IFunctionValueWatcher watcher) {
        watchers.put(function, watcher);
        return true;
    }

    @Override
    public boolean setCustomizeFunctionValue(int function, float value) {
        sets.add(function + "@g=" + value);
        return true;
    }

    @Override
    public boolean setCustomizeFunctionValue(int function, int zone, float value) {
        sets.add(function + "@" + zone + "=" + value);
        return true;
    }

    @Override
    public boolean setFunctionValue(int function, int value) {
        sets.add(function + "@g=" + value);
        return true;
    }

    @Override
    public boolean setFunctionValue(int function, int zone, int value) {
        sets.add(function + "@" + zone + "=" + value);
        return true;
    }

    @Override
    public boolean unregisterFunctionValueWatcher(IFunctionValueWatcher watcher) {
        watchers.values().removeIf(each -> each == watcher);
        return true;
    }

    @Override
    public float getSensorLatestValue(int sensor) {
        if (down) {
            throw new IllegalStateException("car service is down");
        }
        return sensors.getOrDefault(sensor, Float.NaN);
    }

    @Override
    public boolean registerListener(ISensorListener listener, int sensor) {
        listeners.put(sensor, listener);
        return true;
    }

    @Override
    public void unregisterListener(ISensorListener listener) {
        listeners.values().removeIf(each -> each == listener);
    }
}
