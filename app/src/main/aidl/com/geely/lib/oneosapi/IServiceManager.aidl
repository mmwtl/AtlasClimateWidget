package com.geely.lib.oneosapi;

import com.geely.lib.oneosapi.IServiceManagerCallback;

// Subset of OneOS. Keep the original transaction IDs (AIDL adds FIRST_CALL_TRANSACTION).
interface IServiceManager {
    IBinder getService(int type) = 1;
    oneway void registerCallback(IServiceManagerCallback callback) = 3;
    oneway void unregisterCallback(IServiceManagerCallback callback) = 4;
}
