package com.mmwtl.atlasclimatewidget;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;

/** Global preferences and the per-widget layouts. */
final class Prefs {
    static final String NAME = "climate_widget";
    static final String KEY_CAR_MODEL = "car_model";
    static final String KEY_TEMP_STEP_TENTHS = "temp_step_tenths";
    static final String KEY_LEVELS_FROM_MAX = "levels_from_max";
    static final String KEY_KEPT_WHEN_OFF = "kept_when_off";
    static final String KEY_THEME_MODE = "theme_mode";
    static final String KEY_DIRECT_CAR = "direct_car";
    static final String KEY_UI_SCALE_TENTHS = "ui_scale_tenths";
    static final String KEY_TEMPLATE = "widget_template";
    static final String KEY_FAN_PRESETS = "fan_presets";
    static final String KEY_FUEL_CUSTOM_FORMULA = "fuel_custom_formula";
    static final String KEY_FUEL_MULTIPLIER = "fuel_multiplier";
    static final String KEY_FUEL_OFFSET = "fuel_offset";
    static final String KEY_WIDGET_PREFIX = "widget_config_";
    static final String KEY_PANEL_WIDTH = "panel_width_percent";
    static final String KEY_PANEL_BOTTOM = "panel_bottom";
    static final String KEY_PANEL_CLOSE = "panel_close_seconds";

    static final String KEY_DISPLAY_MODE = "overlay_mode";
    static final String KEY_OVERLAY_ENABLED = "overlay_enabled";
    static final String KEY_OVERLAY_AUTO_START = "overlay_auto_start";
    static final String KEY_OVERLAY_DRAG_HANDLE = "overlay_drag_handle";
    private static final String KEY_OVERLAY_WIDTH_PERCENT = "overlay_width_percent";
    private static final String KEY_OVERLAY_HEIGHT_PERCENT = "overlay_height_percent";
    static final String KEY_OVERLAY_WIDTH_PX = "overlay_width_px";
    static final String KEY_OVERLAY_HEIGHT_PX = "overlay_height_px";
    static final String KEY_OVERLAY_CORNER = "overlay_corner";
    static final String KEY_OVERLAY_X = "overlay_offset_x_dp";
    static final String KEY_OVERLAY_Y = "overlay_offset_y_dp";
    static final String KEY_OVERLAY_X_PX = "overlay_offset_x_px";
    static final String KEY_OVERLAY_Y_PX = "overlay_offset_y_px";
    static final String KEY_FREEFORM_HIDE_THRESHOLD = "overlay_hide_threshold";
    static final String KEY_SHOW_ONLY_IN_APP_LIST = "overlay_app_list_only";
    static final int OVERLAY_ID = -3;
    static final int OVERLAY_MIN_WIDTH_PX = 160;
    static final int OVERLAY_MIN_HEIGHT_PX = 100;
    static final int OVERLAY_OFFSET_MAX_DP = 1000;
    static final int OVERLAY_OFFSET_UNSET = -1;
    // The portrait OneOS climate dock occupies 124 dp and reports no window insets.
    static final int OVERLAY_BOTTOM_OFFSET_DP = 124;

    /**
     * Layout slot of the climate window ({@link ClimatePanelActivity}). Widget ids are never
     * negative and the template is {@code INVALID_APPWIDGET_ID} (0), so the window's layout is
     * kept beside the widgets' without clashing with either.
     */
    static final int PANEL_ID = -2;
    static final int PANEL_WIDTH_MIN = 50;
    static final int PANEL_WIDTH_MAX = 100;
    static final int PANEL_WIDTH_DEFAULT = 92;
    /** Seconds without a touch before the window closes; 0 keeps it open. */
    static final int PANEL_CLOSE_MAX = 60;
    static final int PANEL_CLOSE_DEFAULT = 15;

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

    boolean keptWhenOff() {
        return preferences.getBoolean(KEY_KEPT_WHEN_OFF, false);
    }

    void setKeptWhenOff(boolean value) {
        preferences.edit().putBoolean(KEY_KEPT_WHEN_OFF, value).apply();
    }

    /** {@link WidgetTheme#AUTO}, {@link WidgetTheme#FIXED_DAY} or {@link WidgetTheme#FIXED_NIGHT}. */
    int themeMode() {
        return WidgetTheme.validMode(preferences.getInt(KEY_THEME_MODE, WidgetTheme.AUTO));
    }

    void setThemeMode(int value) {
        preferences.edit().putInt(KEY_THEME_MODE, WidgetTheme.validMode(value)).apply();
    }

    /** Experimental: read the car through the ECarX API instead of GInputBridge. */
    boolean directCar() {
        return preferences.getBoolean(KEY_DIRECT_CAR, false);
    }

    void setDirectCar(boolean value) {
        preferences.edit().putBoolean(KEY_DIRECT_CAR, value).apply();
    }

    int uiScaleTenths(int fallback) {
        return preferences.getInt(KEY_UI_SCALE_TENTHS, fallback);
    }

    void setUiScaleTenths(int value) {
        preferences.edit().putInt(KEY_UI_SCALE_TENTHS, value).apply();
    }

    boolean isWidgetMode() {
        return !preferences.getBoolean(KEY_DISPLAY_MODE, false);
    }

    void setWidgetMode(boolean widget) {
        preferences.edit().putBoolean(KEY_DISPLAY_MODE, !widget).apply();
    }

    boolean overlayEnabled() {
        return preferences.getBoolean(KEY_OVERLAY_ENABLED, true);
    }

    void setOverlayEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_OVERLAY_ENABLED, enabled).apply();
    }

    boolean overlayAutoStart() {
        return preferences.getBoolean(KEY_OVERLAY_AUTO_START, true);
    }

    void setOverlayAutoStart(boolean enabled) {
        preferences.edit().putBoolean(KEY_OVERLAY_AUTO_START, enabled).apply();
    }

    boolean overlayDragHandle() {
        return preferences.getBoolean(KEY_OVERLAY_DRAG_HANDLE, true);
    }

    void setOverlayDragHandle(boolean visible) {
        preferences.edit().putBoolean(KEY_OVERLAY_DRAG_HANDLE, visible).apply();
    }

    /** Resolve percentage settings once, using the same safe display area as the overlay. */
    void migrateOverlaySize(int width, int height) {
        int widthPx = preferences.getInt(KEY_OVERLAY_WIDTH_PX, 0);
        int heightPx = preferences.getInt(KEY_OVERLAY_HEIGHT_PX, -1);
        if (widthPx > 0 && heightPx >= 0) {
            return;
        }
        int widthPercent = preferences.getInt(KEY_OVERLAY_WIDTH_PERCENT, PANEL_WIDTH_DEFAULT);
        int heightPercent = preferences.getInt(KEY_OVERLAY_HEIGHT_PERCENT, 0);
        setOverlaySize(widthPx > 0 ? widthPx : Math.round(width * widthPercent / 100f),
                heightPx >= 0 ? heightPx : Math.round(height * heightPercent / 100f));
    }

    int overlayDefaultWidthPx(int availableWidth) {
        int min = Math.min(OVERLAY_MIN_WIDTH_PX, Math.max(1, availableWidth));
        return Math.min(availableWidth,
                Math.max(min, Math.round(availableWidth * PANEL_WIDTH_DEFAULT / 100f)));
    }

    int overlayWidthPx(int availableWidth) {
        int stored = preferences.getInt(KEY_OVERLAY_WIDTH_PX, 0);
        int min = Math.min(OVERLAY_MIN_WIDTH_PX, Math.max(1, availableWidth));
        int width = stored > 0 ? stored : overlayDefaultWidthPx(availableWidth);
        return Math.min(availableWidth, Math.max(min, width));
    }

    /** Zero sizes the overlay to its content. */
    int overlayHeightPx(int availableHeight) {
        int heightPx = preferences.getInt(KEY_OVERLAY_HEIGHT_PX, 0);
        if (heightPx <= 0) {
            return 0;
        }
        int min = Math.min(OVERLAY_MIN_HEIGHT_PX, Math.max(1, availableHeight));
        return Math.min(availableHeight, Math.max(min, heightPx));
    }

    void setOverlaySize(int widthPx, int heightPx) {
        preferences.edit()
                .putInt(KEY_OVERLAY_WIDTH_PX, widthPx)
                .putInt(KEY_OVERLAY_HEIGHT_PX, heightPx)
                .remove(KEY_OVERLAY_WIDTH_PERCENT)
                .remove(KEY_OVERLAY_HEIGHT_PERCENT)
                .apply();
    }

    void resetOverlaySize(int availableWidth) {
        setOverlaySize(overlayDefaultWidthPx(availableWidth), 0);
    }

    OverlayCorner overlayCorner() {
        OverlayCorner corner = OverlayCorner.fromPreference(
                preferences.getString(KEY_OVERLAY_CORNER, null));
        return corner == null ? OverlayCorner.BOTTOM_START : corner;
    }

    /** Convert legacy dp offsets once to physical pixels. */
    void migrateOverlayOffsets(float density) {
        int xPx = preferences.getInt(KEY_OVERLAY_X_PX, OVERLAY_OFFSET_UNSET);
        int yPx = preferences.getInt(KEY_OVERLAY_Y_PX, OVERLAY_OFFSET_UNSET);
        if (xPx >= 0 && yPx >= 0) {
            return;
        }
        int nextX = xPx >= 0 ? xPx : Math.round(overlayOffsetDp(KEY_OVERLAY_X) * density);
        int nextY = yPx >= 0 ? yPx : Math.round(overlayOffsetDp(KEY_OVERLAY_Y) * density);
        preferences.edit()
                .putInt(KEY_OVERLAY_X_PX, Math.max(0, nextX))
                .putInt(KEY_OVERLAY_Y_PX, Math.max(0, nextY))
                .remove(KEY_OVERLAY_X)
                .remove(KEY_OVERLAY_Y)
                .apply();
    }

    int overlayOffsetXPx(float density) {
        int stored = preferences.getInt(KEY_OVERLAY_X_PX, OVERLAY_OFFSET_UNSET);
        return stored >= 0 ? stored : Math.max(0, Math.round(overlayOffsetDp(KEY_OVERLAY_X) * density));
    }

    int overlayOffsetYPx(float density) {
        int stored = preferences.getInt(KEY_OVERLAY_Y_PX, OVERLAY_OFFSET_UNSET);
        return stored >= 0 ? stored : Math.max(0, Math.round(overlayOffsetDp(KEY_OVERLAY_Y) * density));
    }

    void setOverlayPosition(OverlayCorner corner, int xPx, int yPx) {
        preferences.edit()
                .putString(KEY_OVERLAY_CORNER,
                        (corner == null ? OverlayCorner.BOTTOM_START : corner).preferenceValue)
                .putInt(KEY_OVERLAY_X_PX, Math.max(0, xPx))
                .putInt(KEY_OVERLAY_Y_PX, Math.max(0, yPx))
                .remove(KEY_OVERLAY_X)
                .remove(KEY_OVERLAY_Y)
                .apply();
    }

    void resetOverlayPosition(float density) {
        setOverlayPosition(OverlayCorner.BOTTOM_START, 0,
                Math.round(OVERLAY_BOTTOM_OFFSET_DP * density));
    }

    int overlayOffsetDp(String key) {
        int fallback = KEY_OVERLAY_Y.equals(key) ? OVERLAY_BOTTOM_OFFSET_DP : 0;
        return clampOverlayOffset(preferences.getInt(key, fallback));
    }

    boolean overlayAppListOnly() {
        return preferences.getBoolean(KEY_SHOW_ONLY_IN_APP_LIST, false);
    }

    int freeformHideThresholdPercent() {
        return clampHideThreshold(preferences.getInt(KEY_FREEFORM_HIDE_THRESHOLD,
                WindowVisibilityPolicy.DEFAULT_HIDE_THRESHOLD_PERCENT));
    }

    static int clampHideThreshold(int value) {
        return Math.max(WindowVisibilityPolicy.MIN_HIDE_THRESHOLD_PERCENT,
                Math.min(WindowVisibilityPolicy.MAX_HIDE_THRESHOLD_PERCENT, value));
    }

    static int clampOverlayOffset(int value) {
        return Math.max(0, Math.min(OVERLAY_OFFSET_MAX_DP, value));
    }

    /** Layout used for newly placed widgets. */
    WidgetConfig template() {
        return resolved(WidgetConfig.fromJson(preferences.getString(KEY_TEMPLATE, null)));
    }

    /** Auto-fan preset set: {@link FanPresets#AUTO} follows the car model. */
    enum FanPresets {
        AUTO(R.string.fan_presets_auto),
        THREE(R.string.fan_presets_three),
        FIVE(R.string.fan_presets_five);

        final int titleRes;

        FanPresets(int titleRes) {
            this.titleRes = titleRes;
        }
    }

    FanPresets fanPresets() {
        String raw = preferences.getString(KEY_FAN_PRESETS, FanPresets.AUTO.name());
        for (FanPresets value : FanPresets.values()) {
            if (value.name().equals(raw)) {
                return value;
            }
        }
        return FanPresets.AUTO;
    }

    void setFanPresets(FanPresets value) {
        preferences.edit().putString(KEY_FAN_PRESETS, value.name()).apply();
    }

    int fanPresetCount() {
        switch (fanPresets()) {
            case THREE:
                return ClimateCommands.FAN_PRESETS_THREE.length;
            case FIVE:
                return ClimateCommands.FAN_PRESETS_FIVE.length;
            default:
                return carModel() == CarModel.CITYRAY
                        ? ClimateCommands.FAN_PRESETS_FIVE.length
                        : ClimateCommands.FAN_PRESETS_THREE.length;
        }
    }

    /** Whether the user's fuel formula replaces the AtlasAppWidget default. */
    boolean fuelCustomFormula() {
        return preferences.getBoolean(KEY_FUEL_CUSTOM_FORMULA, false);
    }

    void setFuelCustomFormula(boolean value) {
        preferences.edit().putBoolean(KEY_FUEL_CUSTOM_FORMULA, value).apply();
    }

    /** The stored custom multiplier, kept while the default formula is in use. */
    float savedFuelMultiplier() {
        return preferences.getFloat(KEY_FUEL_MULTIPLIER, Fuel.MULTIPLIER);
    }

    float savedFuelOffset() {
        return preferences.getFloat(KEY_FUEL_OFFSET, Fuel.OFFSET);
    }

    void setFuelFormula(float multiplier, float offset) {
        preferences.edit()
                .putFloat(KEY_FUEL_MULTIPLIER, multiplier)
                .putFloat(KEY_FUEL_OFFSET, offset)
                .apply();
    }

    /** The formula in effect; a broken custom one falls back to the default. */
    float fuelMultiplier() {
        return customFuelFormulaValid() ? savedFuelMultiplier() : Fuel.MULTIPLIER;
    }

    float fuelOffset() {
        return customFuelFormulaValid() ? savedFuelOffset() : Fuel.OFFSET;
    }

    private boolean customFuelFormulaValid() {
        return fuelCustomFormula() && Fuel.isValid(savedFuelMultiplier(), savedFuelOffset());
    }

    WidgetConfig resolved(WidgetConfig config) {
        config.fanPresetCount = fanPresetCount();
        config.keptWhenOff = keptWhenOff();
        config.fuelMultiplier = fuelMultiplier();
        config.fuelOffset = fuelOffset();
        return config;
    }

    void setTemplate(WidgetConfig config) {
        preferences.edit().putString(KEY_TEMPLATE, config.toJson()).apply();
    }

    WidgetConfig widget(int widgetId) {
        String raw = preferences.getString(KEY_WIDGET_PREFIX + widgetId, null);
        return raw == null && widgetId != PANEL_ID
                ? template() : resolved(WidgetConfig.fromJson(raw));
    }

    /** Whether the widget has its own saved layout rather than the template. */
    boolean hasWidget(int widgetId) {
        return preferences.contains(KEY_WIDGET_PREFIX + widgetId);
    }

    void setWidget(int widgetId, WidgetConfig config) {
        preferences.edit().putString(KEY_WIDGET_PREFIX + widgetId, config.toJson()).apply();
    }

    void removeWidget(int widgetId) {
        preferences.edit().remove(KEY_WIDGET_PREFIX + widgetId).apply();
    }

    /** Width of the climate window in percent of the screen width. */
    int panelWidthPercent() {
        return clampPanelWidth(preferences.getInt(KEY_PANEL_WIDTH, PANEL_WIDTH_DEFAULT));
    }

    void setPanelWidthPercent(int value) {
        preferences.edit().putInt(KEY_PANEL_WIDTH, clampPanelWidth(value)).apply();
    }

    /** Whether the climate window sits at the bottom of the screen rather than its centre. */
    boolean panelBottom() {
        return preferences.getBoolean(KEY_PANEL_BOTTOM, false);
    }

    void setPanelBottom(boolean value) {
        preferences.edit().putBoolean(KEY_PANEL_BOTTOM, value).apply();
    }

    /** Seconds without a touch before the climate window closes; 0 never. */
    int panelCloseSeconds() {
        return clampPanelClose(preferences.getInt(KEY_PANEL_CLOSE, PANEL_CLOSE_DEFAULT));
    }

    void setPanelCloseSeconds(int value) {
        preferences.edit().putInt(KEY_PANEL_CLOSE, clampPanelClose(value)).apply();
    }

    static int clampPanelWidth(int value) {
        return Math.max(PANEL_WIDTH_MIN, Math.min(PANEL_WIDTH_MAX, value));
    }

    static int clampPanelClose(int value) {
        return Math.max(0, Math.min(PANEL_CLOSE_MAX, value));
    }

    /** Everything the user set, with placed widgets in id order. */
    SettingsBackup exportBackup(int[] widgetIds) {
        SettingsBackup backup = new SettingsBackup();
        backup.carModel = carModel().name();
        backup.tempStepTenths = temperatureStep() >= 1f ? 10 : 5;
        backup.levelsFromMax = levelsFromMax();
        backup.keptWhenOff = keptWhenOff();
        backup.themeMode = themeMode();
        backup.directCar = directCar();
        backup.fanPresets = fanPresets().name();
        backup.uiScaleTenths = preferences.getInt(KEY_UI_SCALE_TENTHS, 0);
        backup.fuelCustomFormula = fuelCustomFormula();
        backup.fuelMultiplier = savedFuelMultiplier();
        backup.fuelOffset = savedFuelOffset();
        backup.template = template();
        for (int id : sorted(widgetIds)) {
            backup.widgets.add(widget(id));
        }
        backup.overlayMode = !isWidgetMode();
        backup.overlay = hasWidget(OVERLAY_ID) ? widget(OVERLAY_ID) : null;
        backup.overlayWidthPx = preferences.getInt(KEY_OVERLAY_WIDTH_PX, 0);
        backup.overlayHeightPx = preferences.getInt(KEY_OVERLAY_HEIGHT_PX, -1);
        backup.overlayWidthPercent = preferences.getInt(KEY_OVERLAY_WIDTH_PERCENT, PANEL_WIDTH_DEFAULT);
        backup.overlayHeightPercent = preferences.getInt(KEY_OVERLAY_HEIGHT_PERCENT, 0);
        backup.overlayCorner = overlayCorner().preferenceValue;
        backup.overlayOffsetX = overlayOffsetDp(KEY_OVERLAY_X);
        backup.overlayOffsetY = overlayOffsetDp(KEY_OVERLAY_Y);
        backup.overlayOffsetXPx = preferences.getInt(KEY_OVERLAY_X_PX, OVERLAY_OFFSET_UNSET);
        backup.overlayOffsetYPx = preferences.getInt(KEY_OVERLAY_Y_PX, OVERLAY_OFFSET_UNSET);
        backup.overlayDragHandle = overlayDragHandle();
        backup.overlayAutoStart = overlayAutoStart();
        backup.overlayHideThreshold = freeformHideThresholdPercent();
        backup.overlayAppListOnly = overlayAppListOnly();
        backup.panel = widget(PANEL_ID);
        backup.panelWidthPercent = panelWidthPercent();
        backup.panelBottom = panelBottom();
        backup.panelCloseSeconds = panelCloseSeconds();
        return backup;
    }

    /** Replaces all settings; widgets on screen take the backup's layouts by position. */
    void importBackup(SettingsBackup backup, int[] widgetIds) {
        SharedPreferences.Editor editor = preferences.edit()
                .putString(KEY_CAR_MODEL, CarModel.fromName(backup.carModel).name())
                .putInt(KEY_TEMP_STEP_TENTHS, backup.tempStepTenths >= 10 ? 10 : 5)
                .putBoolean(KEY_LEVELS_FROM_MAX, backup.levelsFromMax)
                .putBoolean(KEY_KEPT_WHEN_OFF, backup.keptWhenOff)
                .putInt(KEY_THEME_MODE, WidgetTheme.validMode(backup.themeMode))
                .putBoolean(KEY_DIRECT_CAR, backup.directCar)
                .putString(KEY_FAN_PRESETS, backup.fanPresets)
                .putBoolean(KEY_FUEL_CUSTOM_FORMULA, backup.fuelCustomFormula)
                .putFloat(KEY_FUEL_MULTIPLIER, backup.fuelMultiplier)
                .putFloat(KEY_FUEL_OFFSET, backup.fuelOffset)
                .putString(KEY_TEMPLATE, backup.template.toJson())
                .putBoolean(KEY_DISPLAY_MODE, backup.overlayMode)
                .putInt(KEY_OVERLAY_WIDTH_PX, backup.overlayWidthPx)
                .putInt(KEY_OVERLAY_HEIGHT_PX, backup.overlayHeightPx)
                .putInt(KEY_OVERLAY_WIDTH_PERCENT, backup.overlayWidthPercent)
                .putInt(KEY_OVERLAY_HEIGHT_PERCENT, backup.overlayHeightPercent)
                .putString(KEY_OVERLAY_CORNER, backup.overlayCorner)
                .putBoolean(KEY_OVERLAY_DRAG_HANDLE, backup.overlayDragHandle)
                .putBoolean(KEY_OVERLAY_AUTO_START, backup.overlayAutoStart)
                .putInt(KEY_FREEFORM_HIDE_THRESHOLD, clampHideThreshold(backup.overlayHideThreshold))
                .putBoolean(KEY_SHOW_ONLY_IN_APP_LIST, backup.overlayAppListOnly)
                .putInt(KEY_PANEL_WIDTH, clampPanelWidth(backup.panelWidthPercent))
                .putBoolean(KEY_PANEL_BOTTOM, backup.panelBottom)
                .putInt(KEY_PANEL_CLOSE, clampPanelClose(backup.panelCloseSeconds));
        if (backup.overlayOffsetXPx >= 0) {
            editor.putInt(KEY_OVERLAY_X_PX, backup.overlayOffsetXPx).remove(KEY_OVERLAY_X);
        } else {
            editor.remove(KEY_OVERLAY_X_PX)
                    .putInt(KEY_OVERLAY_X, clampOverlayOffset(backup.overlayOffsetX));
        }
        if (backup.overlayOffsetYPx >= 0) {
            editor.putInt(KEY_OVERLAY_Y_PX, backup.overlayOffsetYPx).remove(KEY_OVERLAY_Y);
        } else {
            editor.remove(KEY_OVERLAY_Y_PX)
                    .putInt(KEY_OVERLAY_Y, clampOverlayOffset(backup.overlayOffsetY));
        }
        if (backup.overlay != null) {
            editor.putString(KEY_WIDGET_PREFIX + OVERLAY_ID, backup.overlay.toJson());
        } else {
            editor.remove(KEY_WIDGET_PREFIX + OVERLAY_ID);
        }
        if (backup.panel != null) {
            editor.putString(KEY_WIDGET_PREFIX + PANEL_ID, backup.panel.toJson());
        } else {
            editor.remove(KEY_WIDGET_PREFIX + PANEL_ID);
        }
        if (backup.uiScaleTenths > 0) {
            editor.putInt(KEY_UI_SCALE_TENTHS, backup.uiScaleTenths);
        }
        int[] ids = sorted(widgetIds);
        for (int position = 0; position < ids.length; position++) {
            editor.putString(KEY_WIDGET_PREFIX + ids[position],
                    backup.layoutFor(position).toJson());
        }
        editor.apply();
    }

    private static int[] sorted(int[] ids) {
        int[] copy = ids.clone();
        Arrays.sort(copy);
        return copy;
    }

    static boolean isWidgetKey(String key) {
        return key != null && (key.startsWith(KEY_WIDGET_PREFIX) || KEY_TEMPLATE.equals(key)
                || KEY_CAR_MODEL.equals(key));
    }
}
