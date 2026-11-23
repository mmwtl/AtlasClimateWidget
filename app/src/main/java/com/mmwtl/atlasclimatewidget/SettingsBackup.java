package com.mmwtl.atlasclimatewidget;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * All settings as one JSON file, in the format family of AtlasAppWidget and AtlasMediaWidget:
 * global behaviour, the template for new widgets and the layouts of placed widgets in widget-id
 * order. Widget ids do not survive a reinstall, so layouts are matched to the widgets on screen
 * by position.
 */
final class SettingsBackup {
    static final String FORMAT = "atlas-climate-widget-settings";
    static final int SCHEMA_VERSION = 1;
    static final String FILE_NAME = "AtlasClimateWidget-settings.json";
    static final String MIME = "application/json";

    String carModel = CarModel.ATLAS.name();
    int tempStepTenths = 5;
    boolean levelsFromMax;
    boolean keptWhenOff;
    /** Widget theme, {@link WidgetTheme#AUTO} by default and in older backups. */
    int themeMode = WidgetTheme.AUTO;
    boolean directCar;
    String fanPresets = "AUTO";
    /** Settings screen scale; 0 when the backup does not carry one. */
    int uiScaleTenths;
    /** Fuel formula; the custom coefficients are kept even while the default is in use. */
    boolean fuelCustomFormula;
    float fuelMultiplier = Fuel.MULTIPLIER;
    float fuelOffset = Fuel.OFFSET;
    WidgetConfig template = new WidgetConfig();
    final List<WidgetConfig> widgets = new ArrayList<>();
    /** The climate window's own layout, or {@code null} in older backups. */
    WidgetConfig panel;
    int panelWidthPercent = Prefs.PANEL_WIDTH_DEFAULT;
    boolean panelBottom;
    int panelCloseSeconds = Prefs.PANEL_CLOSE_DEFAULT;
    boolean panelShowOutline;

    boolean overlayMode;
    WidgetConfig overlay;
    int overlayWidthPx;
    int overlayHeightPx = -1;
    int overlayWidthPercent = Prefs.PANEL_WIDTH_DEFAULT;
    int overlayHeightPercent;
    String overlayCorner = OverlayCorner.BOTTOM_START.preferenceValue;
    int overlayOffsetX;
    int overlayOffsetY = Prefs.OVERLAY_BOTTOM_OFFSET_DP;
    int overlayOffsetXPx = Prefs.OVERLAY_OFFSET_UNSET;
    int overlayOffsetYPx = Prefs.OVERLAY_OFFSET_UNSET;
    boolean overlayDragHandle = true;
    boolean overlayAutoStart = true;
    int overlayHideThreshold = WindowVisibilityPolicy.DEFAULT_HIDE_THRESHOLD_PERCENT;
    boolean overlayAppListOnly;

    String toJson(String appVersion) {
        try {
            JSONObject json = new JSONObject();
            json.put("format", FORMAT);
            json.put("schemaVersion", SCHEMA_VERSION);
            json.put("appVersion", appVersion == null ? "" : appVersion);
            JSONObject settings = new JSONObject();
            settings.put("carModel", carModel);
            settings.put("tempStepTenths", tempStepTenths);
            settings.put("levelsFromMax", levelsFromMax);
            settings.put("keptWhenOff", keptWhenOff);
            settings.put("themeMode", themeMode);
            settings.put("directCar", directCar);
            settings.put("fanPresets", fanPresets);
            if (uiScaleTenths > 0) {
                settings.put("uiScaleTenths", uiScaleTenths);
            }
            JSONObject fuel = new JSONObject();
            fuel.put("useCustomFormula", fuelCustomFormula);
            fuel.put("multiplier", (double) fuelMultiplier);
            fuel.put("offset", (double) fuelOffset);
            settings.put("fuel", fuel);
            json.put("settings", settings);
            json.put("template", new JSONObject(template.toJson()));
            JSONArray layouts = new JSONArray();
            for (WidgetConfig widget : widgets) {
                layouts.put(new JSONObject(widget.toJson()));
            }
            json.put("widgets", layouts);
            JSONObject window = new JSONObject();
            window.put("widthPercent", panelWidthPercent);
            window.put("bottom", panelBottom);
            window.put("closeSeconds", panelCloseSeconds);
            window.put("doubleOutline", panelShowOutline);
            if (panel != null) {
                window.put("layout", new JSONObject(panel.toJson()));
            }
            json.put("climateWindow", window);
            JSONObject overlaySettings = new JSONObject();
            overlaySettings.put("enabled", overlayMode);
            if (overlayWidthPx > 0) {
                overlaySettings.put("widthPx", overlayWidthPx);
            } else {
                overlaySettings.put("widthPercent", overlayWidthPercent);
            }
            if (overlayHeightPx >= 0) {
                overlaySettings.put("heightPx", overlayHeightPx);
            } else {
                overlaySettings.put("heightPercent", overlayHeightPercent);
            }
            overlaySettings.put("corner", overlayCorner);
            if (overlayOffsetXPx >= 0) {
                overlaySettings.put("offsetXPx", overlayOffsetXPx);
            } else {
                overlaySettings.put("offsetX", overlayOffsetX);
            }
            if (overlayOffsetYPx >= 0) {
                overlaySettings.put("offsetYPx", overlayOffsetYPx);
            } else {
                overlaySettings.put("offsetY", overlayOffsetY);
            }
            overlaySettings.put("dragHandle", overlayDragHandle);
            overlaySettings.put("autoStart", overlayAutoStart);
            overlaySettings.put("hideThreshold", overlayHideThreshold);
            overlaySettings.put("appListOnly", overlayAppListOnly);
            if (overlay != null) {
                overlaySettings.put("layout", new JSONObject(overlay.toJson()));
            }
            json.put("overlay", overlaySettings);
            return json.toString(2);
        } catch (JSONException error) {
            throw new IllegalStateException(error);
        }
    }

    /** Reads a backup file; {@code null} when it is not one of ours. */
    static SettingsBackup parse(String text) {
        if (text == null) {
            return null;
        }
        JSONObject json;
        try {
            json = new JSONObject(text.trim());
        } catch (JSONException error) {
            return null;
        }
        if (!FORMAT.equals(json.optString("format"))) {
            return null;
        }
        SettingsBackup backup = new SettingsBackup();
        JSONObject settings = json.optJSONObject("settings");
        if (settings != null) {
            backup.carModel = CarModel.fromName(settings.optString("carModel")).name();
            backup.tempStepTenths = settings.optInt("tempStepTenths", 5) >= 10 ? 10 : 5;
            backup.levelsFromMax = settings.optBoolean("levelsFromMax", false);
            backup.keptWhenOff = settings.optBoolean("keptWhenOff", false);
            backup.themeMode = WidgetTheme.validMode(settings.optInt("themeMode", WidgetTheme.AUTO));
            backup.directCar = settings.optBoolean("directCar", false);
            backup.fanPresets = settings.optString("fanPresets", backup.fanPresets);
            backup.uiScaleTenths = settings.optInt("uiScaleTenths", 0);
            JSONObject fuel = settings.optJSONObject("fuel");
            if (fuel != null) {
                float multiplier = (float) fuel.optDouble("multiplier", Fuel.MULTIPLIER);
                float offset = (float) fuel.optDouble("offset", Fuel.OFFSET);
                // A broken formula is dropped whole rather than mixed with the default.
                if (Fuel.isValid(multiplier, offset)) {
                    backup.fuelCustomFormula = fuel.optBoolean("useCustomFormula", false);
                    backup.fuelMultiplier = multiplier;
                    backup.fuelOffset = offset;
                }
            }
        }
        JSONObject template = json.optJSONObject("template");
        if (template != null) {
            backup.template = WidgetConfig.fromJson(template.toString());
        }
        JSONArray layouts = json.optJSONArray("widgets");
        if (layouts != null) {
            for (int index = 0; index < layouts.length(); index++) {
                JSONObject layout = layouts.optJSONObject(index);
                if (layout != null) {
                    backup.widgets.add(WidgetConfig.fromJson(layout.toString()));
                }
            }
        }
        JSONObject window = json.optJSONObject("climateWindow");
        if (window != null) {
            backup.panelWidthPercent = Prefs.clampPanelWidth(
                    window.optInt("widthPercent", Prefs.PANEL_WIDTH_DEFAULT));
            backup.panelBottom = window.optBoolean("bottom", false);
            backup.panelCloseSeconds = Prefs.clampPanelClose(
                    window.optInt("closeSeconds", Prefs.PANEL_CLOSE_DEFAULT));
            backup.panelShowOutline = window.optBoolean("doubleOutline", false);
            JSONObject layout = window.optJSONObject("layout");
            if (layout != null) {
                backup.panel = WidgetConfig.fromJson(layout.toString());
            }
        }
        JSONObject overlaySettings = json.optJSONObject("overlay");
        if (overlaySettings != null) {
            backup.overlayMode = overlaySettings.optBoolean("enabled");
            backup.overlayWidthPx = Math.max(0, overlaySettings.optInt("widthPx"));
            backup.overlayHeightPx = Math.max(-1, overlaySettings.optInt("heightPx", -1));
            backup.overlayWidthPercent = Prefs.clampPanelWidth(
                    overlaySettings.optInt("widthPercent", backup.overlayWidthPercent));
            backup.overlayHeightPercent = Math.max(0, Math.min(100, overlaySettings.optInt("heightPercent")));
            OverlayCorner corner = OverlayCorner.fromPreference(overlaySettings.optString("corner"));
            if (corner != null) {
                backup.overlayCorner = corner.preferenceValue;
            }
            backup.overlayOffsetX = Prefs.clampOverlayOffset(overlaySettings.optInt("offsetX"));
            backup.overlayOffsetY = Prefs.clampOverlayOffset(overlaySettings.optInt("offsetY", backup.overlayOffsetY));
            int xPx = overlaySettings.optInt("offsetXPx", Prefs.OVERLAY_OFFSET_UNSET);
            int yPx = overlaySettings.optInt("offsetYPx", Prefs.OVERLAY_OFFSET_UNSET);
            backup.overlayOffsetXPx = xPx >= 0 ? xPx : Prefs.OVERLAY_OFFSET_UNSET;
            backup.overlayOffsetYPx = yPx >= 0 ? yPx : Prefs.OVERLAY_OFFSET_UNSET;
            backup.overlayDragHandle = overlaySettings.optBoolean("dragHandle", true);
            backup.overlayAutoStart = overlaySettings.optBoolean("autoStart", true);
            backup.overlayHideThreshold = Prefs.clampHideThreshold(
                    overlaySettings.optInt("hideThreshold", backup.overlayHideThreshold));
            backup.overlayAppListOnly = overlaySettings.optBoolean("appListOnly");
            JSONObject layout = overlaySettings.optJSONObject("layout");
            if (layout != null) {
                backup.overlay = WidgetConfig.fromJson(layout.toString());
            }
        }
        return backup;
    }

    /** Layout for the widget at {@code position} on screen; extra widgets get the template. */
    WidgetConfig layoutFor(int position) {
        return (position < widgets.size() ? widgets.get(position) : template).copy();
    }
}
