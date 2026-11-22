package com.geely.lib.oneosapi.input;

import com.geely.lib.oneosapi.input.IInputListener;

interface IInputManager {
    void registerListener(IInputListener listener, String tag, in int[] keyCodes) = 2;
    void unregisterListener(IInputListener listener, String tag) = 3;
}
