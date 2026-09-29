package com.mmwtl.atlasclimatewidget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;

/** Launcher entry point; the drawing and bridge work live in {@link ClimateService}. */
public final class ClimateWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] widgetIds) {
        Prefs prefs = new Prefs(context);
        ClimateState state = ClimateService.STORE.snapshot(SystemClock.elapsedRealtime());
        for (int widgetId : widgetIds) {
            ClimateService.update(context, manager, prefs, state, widgetId);
        }
        ClimateService.start(context, ClimateService.ACTION_REFRESH);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
            int widgetId, Bundle options) {
        ClimateService.update(context, manager, new Prefs(context),
                ClimateService.STORE.snapshot(SystemClock.elapsedRealtime()), widgetId);
    }

    @Override
    public void onDeleted(Context context, int[] widgetIds) {
        Prefs prefs = new Prefs(context);
        for (int widgetId : widgetIds) {
            prefs.removeWidget(widgetId);
            ClimateService.forget(widgetId);
        }
    }

    @Override
    public void onRestored(Context context, int[] oldWidgetIds, int[] newWidgetIds) {
        Prefs prefs = new Prefs(context);
        for (int index = 0; index < oldWidgetIds.length && index < newWidgetIds.length; index++) {
            prefs.setWidget(newWidgetIds[index], prefs.widget(oldWidgetIds[index]));
            prefs.removeWidget(oldWidgetIds[index]);
        }
    }
}
