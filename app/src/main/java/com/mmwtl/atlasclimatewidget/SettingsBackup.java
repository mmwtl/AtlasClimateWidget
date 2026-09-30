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
    static final String EXTENSION = ".json";
    static final String MIME = "application/json";
    static final String FORMAT = "atlas-climate-widget-settings";
    static final int SCHEMA_VERSION = 1;

    String carModel = CarModel.ATLAS.name();
    int tempStepTenths = 5;
    boolean levelsFromMax;
    String fanPresets = "AUTO";
    /** Settings screen scale; 0 when the backup does not carry one. */
    int uiScaleTenths;
    WidgetConfig template = new WidgetConfig();
    final List<WidgetConfig> widgets = new ArrayList<>();

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
            settings.put("fanPresets", fanPresets);
            if (uiScaleTenths > 0) {
                settings.put("uiScaleTenths", uiScaleTenths);
            }
            json.put("settings", settings);
            json.put("template", new JSONObject(template.toJson()));
            JSONArray layouts = new JSONArray();
            for (WidgetConfig widget : widgets) {
                layouts.put(new JSONObject(widget.toJson()));
            }
            json.put("widgets", layouts);
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
            backup.fanPresets = settings.optString("fanPresets", backup.fanPresets);
            backup.uiScaleTenths = settings.optInt("uiScaleTenths", 0);
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
        return backup;
    }

    /** Layout for the widget at {@code position} on screen; extra widgets get the template. */
    WidgetConfig layoutFor(int position) {
        return (position < widgets.size() ? widgets.get(position) : template).copy();
    }
}
