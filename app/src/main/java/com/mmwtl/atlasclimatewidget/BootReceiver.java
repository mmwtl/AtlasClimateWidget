package com.mmwtl.atlasclimatewidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores the bridge subscription after boot or an app update when widgets are placed. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        if (ClimateService.widgetIds(context).length > 0) {
            ClimateService.start(context, ClimateService.ACTION_REFRESH);
        }
    }
}
