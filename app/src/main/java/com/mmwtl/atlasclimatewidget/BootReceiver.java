package com.mmwtl.atlasclimatewidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores the bridge subscription after boot or an app update when widgets are placed. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (ClimateService.widgetIds(context).length > 0) {
            ClimateService.start(context, ClimateService.ACTION_REFRESH);
        }
    }
}
