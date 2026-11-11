package com.ecarx.xui.adaptapi.car;

import android.content.Context;

import com.ecarx.xui.adaptapi.car.base.ICarFunction;
import com.ecarx.xui.adaptapi.car.sensor.ISensor;

/** Test double of the non-public constructor used after a signature rejection. */
public final class CarImpl implements ICar {
    public static int creations;
    public static RuntimeException createError;

    private CarImpl(Context context) {
        creations++;
        if (createError != null) {
            throw createError;
        }
    }

    @Override
    public ICarFunction getICarFunction() {
        return Car.instance;
    }

    @Override
    public ISensor getSensorManager() {
        return Car.instance;
    }
}
