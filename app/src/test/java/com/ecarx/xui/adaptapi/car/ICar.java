package com.ecarx.xui.adaptapi.car;

import com.ecarx.xui.adaptapi.car.base.ICarFunction;
import com.ecarx.xui.adaptapi.car.sensor.ISensor;

/** Test double of the head unit's ECarX interface. */
public interface ICar {
    ICarFunction getICarFunction();

    ISensor getSensorManager();
}
