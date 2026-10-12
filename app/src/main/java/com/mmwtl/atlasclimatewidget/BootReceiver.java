package com.mmwtl.atlasclimatewidget;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;

/**
 * Restores the bridge subscription after boot or an app update when widgets are placed.
 *
 * <p>The launcher shows the loading layout until the provider pushes views, and the system
 * wakes the provider well after the launcher is up. {@code LOCKED_BOOT_COMPLETED} comes first,
 * so the widgets are drawn right away and fill in once GInputBridge answers.
 */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        // Settings live in credential storage; a locked user gets BOOT_COMPLETED later.
        UserManager users = context.getSystemService(UserManager.class);
        if (users != null && !users.isUserUnlocked()) {
            return;
        }
        int[] widgetIds = ClimateService.widgetIds(context);
        if (widgetIds.length == 0) {
            return;
        }
        ClimateWidgetProvider.draw(context, AppWidgetManager.getInstance(context), widgetIds);
        ClimateService.start(context, ClimateService.ACTION_REFRESH);
    }
}
