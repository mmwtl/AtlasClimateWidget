package com.ecarx.xui.adaptapi.car.base;

/** Test double of the head unit's ECarX interface, only the methods the widget calls. */
public interface ICarFunction {
    interface IFunctionValueWatcher {
        void onCustomizeFunctionValueChanged(int function, int zone, float value);

        void onFunctionChanged(int function);

        void onFunctionValueChanged(int function, int zone, int value);
    }

    float getCustomizeFunctionValue(int function);

    float getCustomizeFunctionValue(int function, int zone);

    int getFunctionValue(int function);

    int getFunctionValue(int function, int zone);

    boolean registerFunctionValueWatcher(int function, IFunctionValueWatcher watcher);

    boolean setCustomizeFunctionValue(int function, float value);

    boolean setCustomizeFunctionValue(int function, int zone, float value);

    boolean setFunctionValue(int function, int value);

    boolean setFunctionValue(int function, int zone, int value);

    boolean unregisterFunctionValueWatcher(IFunctionValueWatcher watcher);
}
