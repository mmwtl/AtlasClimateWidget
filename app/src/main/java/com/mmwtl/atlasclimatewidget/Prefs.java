package com.mmwtl.atlasclimatewidget;

import android.content.Context;
import android.content.SharedPreferences;

/** Global preferences and the per-widget layouts. */
final class Prefs {
    static final String NAME = "climate_widget";
    static final String KEY_CAR_MODEL = "car_model";
    static final String KEY_TEMP_STEP_TENTHS = "temp_step_tenths";
    static final String KEY_LEVELS_FROM_MAX = "levels_from_max";
    static final String KEY_UI_SCALE_TENTHS = "ui_scale_tenths";
    static final String KEY_TEMPLATE = "widget_template";
    static final String KEY_WIDGET_PREFIX = "widget_config_";

    private final SharedPreferences preferences;

    Prefs(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    SharedPreferences raw() {
        return preferences;
    }

    CarModel carModel() {
        return CarModel.fromName(preferences.getString(KEY_CAR_MODEL, CarModel.ATLAS.name()));
    }

    void setCarModel(CarModel model) {
        preferences.edit().putString(KEY_CAR_MODEL, model.name()).apply();
    }

    float temperatureStep() {
        return preferences.getInt(KEY_TEMP_STEP_TENTHS, 5) >= 10 ? 1f : Hvac.TEMP_STEP;
    }

    void setTemperatureStep(float step) {
        preferences.edit().putInt(KEY_TEMP_STEP_TENTHS, step >= 1f ? 10 : 5).apply();
    }

    boolean levelsFromMax() {
        return preferences.getBoolean(KEY_LEVELS_FROM_MAX, false);
    }

    void setLevelsFromMax(boolean value) {
        preferences.edit().putBoolean(KEY_LEVELS_FROM_MAX, value).apply();
    }

    int uiScaleTenths(int fallback) {
        return preferences.getInt(KEY_UI_SCALE_TENTHS, fallback);
    }

    void setUiScaleTenths(int value) {
        preferences.edit().putInt(KEY_UI_SCALE_TENTHS, value).apply();
    }

    /** Layout used for newly placed widgets. */
    WidgetConfig template() {
        return WidgetConfig.fromJson(preferences.getString(KEY_TEMPLATE, null));
    }

    void setTemplate(WidgetConfig config) {
        preferences.edit().putString(KEY_TEMPLATE, config.toJson()).apply();
    }

    WidgetConfig widget(int widgetId) {
        String raw = preferences.getString(KEY_WIDGET_PREFIX + widgetId, null);
        return raw == null ? template() : WidgetConfig.fromJson(raw);
    }

    void setWidget(int widgetId, WidgetConfig config) {
        preferences.edit().putString(KEY_WIDGET_PREFIX + widgetId, config.toJson()).apply();
    }

    void removeWidget(int widgetId) {
        preferences.edit().remove(KEY_WIDGET_PREFIX + widgetId).apply();
    }

    static boolean isWidgetKey(String key) {
        return key != null && (key.startsWith(KEY_WIDGET_PREFIX) || KEY_TEMPLATE.equals(key)
                || KEY_CAR_MODEL.equals(key));
    }
}
