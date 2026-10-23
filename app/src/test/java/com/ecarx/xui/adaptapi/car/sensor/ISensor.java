package com.ecarx.xui.adaptapi.car.sensor;

/** Test double of the head unit's ECarX interface, only the methods the widget calls. */
public interface ISensor {
    interface ISensorListener {
        void onSensorEventChanged(int sensor, int event);

        void onSensorValueChanged(int sensor, float value);
    }

    float getSensorLatestValue(int sensor);

    boolean registerListener(ISensorListener listener, int sensor);

    void unregisterListener(ISensorListener listener);
}
