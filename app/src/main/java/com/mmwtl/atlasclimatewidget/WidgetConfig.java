package com.mmwtl.atlasclimatewidget;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** User-built layout of one widget instance. */
final class WidgetConfig {
    enum Block {
        TEMPERATURE(R.string.block_temperature, R.string.block_temperature_short),
        FAN(R.string.block_fan, R.string.block_fan_short),
        TILES(R.string.block_tiles, R.string.block_tiles_short);

        final int titleRes;
        final int titleShortRes;

        Block(int titleRes, int titleShortRes) {
            this.titleRes = titleRes;
            this.titleShortRes = titleShortRes;
        }
    }

    enum TileStyle {
        TILE(R.string.tile_style_tile),
        TILE_LABEL(R.string.tile_style_tile_label),
        ICON(R.string.tile_style_icon);

        final int titleRes;

        TileStyle(int titleRes) {
            this.titleRes = titleRes;
        }
    }

    /** How the fan bar sets the airflow. */
    enum FanStyle {
        LEVELS(R.string.fan_style_levels),
        PRESETS(R.string.fan_style_presets);

        final int titleRes;

        FanStyle(int titleRes) {
            this.titleRes = titleRes;
        }
    }

    static final int COLUMNS_MIN = 2;
    static final int COLUMNS_MAX = 8;
    static final int CARD_RADIUS_MAX_DP = 40;
    static final int TILE_RADIUS_MAX_PERCENT = 50;
    static final int GAP_MIN_DP = 4;
    static final int GAP_MAX_DP = 24;
    static final int PADDING_MIN_DP = 6;
    static final int PADDING_MAX_DP = 28;

    static final List<ClimateFunction> DEFAULT_FUNCTIONS = Arrays.asList(
            ClimateFunction.DRIVER_HEAT,
            ClimateFunction.WINDSHIELD_HEAT,
            ClimateFunction.WHEEL_HEAT,
            ClimateFunction.DEFROST_MAX,
            ClimateFunction.RECIRCULATION,
            ClimateFunction.PASSENGER_HEAT,
            ClimateFunction.DRIVER_VENT,
            ClimateFunction.REAR_DEFROST,
            ClimateFunction.AC,
            ClimateFunction.AC_MAX,
            ClimateFunction.AUTO,
            ClimateFunction.PASSENGER_VENT
    );

    /** All blocks in display order. */
    final List<Block> blockOrder = new ArrayList<>(Arrays.asList(Block.values()));
    boolean temperatureEnabled = true;
    boolean fanEnabled = true;
    boolean tilesEnabled = true;

    boolean temperatureDual;
    boolean temperatureHeader = true;
    boolean temperatureButtons = true;
    boolean fanButtons = true;
    FanStyle fanStyle = FanStyle.LEVELS;

    /** Enabled tiles in display order. */
    final List<ClimateFunction> functions = new ArrayList<>(DEFAULT_FUNCTIONS);
    int columns = 6;
    TileStyle tileStyle = TileStyle.TILE;

    Palette palette = Palette.ATLAS;
    boolean filledActive = true;
    int cardColor = Ui.SURFACE;
    int cardOpacityPercent = 100;
    int cardRadiusDp = 18;
    int cardPaddingDp = 14;
    int tileRadiusPercent = 22;
    int gapDp = 10;

    boolean isEnabled(Block block) {
        switch (block) {
            case TEMPERATURE:
                return temperatureEnabled;
            case FAN:
                return fanEnabled;
            default:
                return tilesEnabled && !functions.isEmpty();
        }
    }

    void setEnabled(Block block, boolean enabled) {
        switch (block) {
            case TEMPERATURE:
                temperatureEnabled = enabled;
                break;
            case FAN:
                fanEnabled = enabled;
                break;
            default:
                tilesEnabled = enabled;
                break;
        }
    }

    List<Block> visibleBlocks() {
        List<Block> visible = new ArrayList<>();
        for (Block block : blockOrder) {
            if (isEnabled(block)) {
                visible.add(block);
            }
        }
        return visible;
    }

    void moveBlock(Block block, int direction) {
        move(blockOrder, block, direction);
    }

    void moveFunction(ClimateFunction function, int direction) {
        move(functions, function, direction);
    }

    void setFunctionEnabled(ClimateFunction function, boolean enabled) {
        if (enabled && !functions.contains(function)) {
            functions.add(function);
        } else if (!enabled) {
            functions.remove(function);
        }
    }

    WidgetConfig copy() {
        return fromJson(toJson());
    }

    String toJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("version", 1);
            JSONArray order = new JSONArray();
            for (Block block : blockOrder) {
                order.put(block.name());
            }
            json.put("blockOrder", order);
            json.put("temperatureEnabled", temperatureEnabled);
            json.put("fanEnabled", fanEnabled);
            json.put("tilesEnabled", tilesEnabled);
            json.put("temperatureDual", temperatureDual);
            json.put("temperatureHeader", temperatureHeader);
            json.put("temperatureButtons", temperatureButtons);
            json.put("fanButtons", fanButtons);
            json.put("fanStyle", fanStyle.name());
            JSONArray tiles = new JSONArray();
            for (ClimateFunction function : functions) {
                tiles.put(function.name());
            }
            json.put("functions", tiles);
            json.put("columns", columns);
            json.put("tileStyle", tileStyle.name());
            json.put("palette", palette.name());
            json.put("filledActive", filledActive);
            json.put("cardColor", cardColor);
            json.put("cardOpacityPercent", cardOpacityPercent);
            json.put("cardRadiusDp", cardRadiusDp);
            json.put("cardPaddingDp", cardPaddingDp);
            json.put("tileRadiusPercent", tileRadiusPercent);
            json.put("gapDp", gapDp);
            return json.toString();
        } catch (JSONException error) {
            throw new IllegalStateException(error);
        }
    }

    static WidgetConfig fromJson(String raw) {
        WidgetConfig config = new WidgetConfig();
        if (raw == null || raw.isEmpty()) {
            return config;
        }
        JSONObject json;
        try {
            json = new JSONObject(raw);
        } catch (JSONException error) {
            return config;
        }
        JSONArray order = json.optJSONArray("blockOrder");
        if (order != null) {
            List<Block> parsed = new ArrayList<>();
            for (int index = 0; index < order.length(); index++) {
                Block block = enumValue(Block.class, order.optString(index), null);
                if (block != null && !parsed.contains(block)) {
                    parsed.add(block);
                }
            }
            for (Block block : Block.values()) {
                if (!parsed.contains(block)) {
                    parsed.add(block);
                }
            }
            config.blockOrder.clear();
            config.blockOrder.addAll(parsed);
        }
        config.temperatureEnabled = json.optBoolean("temperatureEnabled", config.temperatureEnabled);
        config.fanEnabled = json.optBoolean("fanEnabled", config.fanEnabled);
        config.tilesEnabled = json.optBoolean("tilesEnabled", config.tilesEnabled);
        config.temperatureDual = json.optBoolean("temperatureDual", config.temperatureDual);
        config.temperatureHeader = json.optBoolean("temperatureHeader", config.temperatureHeader);
        config.temperatureButtons = json.optBoolean("temperatureButtons",
                config.temperatureButtons);
        config.fanButtons = json.optBoolean("fanButtons", config.fanButtons);
        config.fanStyle = enumValue(FanStyle.class, json.optString("fanStyle"), config.fanStyle);
        JSONArray tiles = json.optJSONArray("functions");
        if (tiles != null) {
            config.functions.clear();
            for (int index = 0; index < tiles.length(); index++) {
                ClimateFunction function = ClimateFunction.fromName(tiles.optString(index));
                if (function != null && !config.functions.contains(function)) {
                    config.functions.add(function);
                }
            }
        }
        config.columns = clamp(json.optInt("columns", config.columns), COLUMNS_MIN, COLUMNS_MAX);
        config.tileStyle = enumValue(TileStyle.class, json.optString("tileStyle"),
                config.tileStyle);
        config.palette = enumValue(Palette.class, json.optString("palette"), config.palette);
        config.filledActive = json.optBoolean("filledActive", config.filledActive);
        config.cardColor = json.optInt("cardColor", config.cardColor) | 0xFF000000;
        config.cardOpacityPercent = clamp(json.optInt("cardOpacityPercent",
                config.cardOpacityPercent), 0, 100);
        config.cardRadiusDp = clamp(json.optInt("cardRadiusDp", config.cardRadiusDp),
                0, CARD_RADIUS_MAX_DP);
        config.cardPaddingDp = clamp(json.optInt("cardPaddingDp", config.cardPaddingDp),
                PADDING_MIN_DP, PADDING_MAX_DP);
        config.tileRadiusPercent = clamp(json.optInt("tileRadiusPercent",
                config.tileRadiusPercent), 0, TILE_RADIUS_MAX_PERCENT);
        config.gapDp = clamp(json.optInt("gapDp", config.gapDp), GAP_MIN_DP, GAP_MAX_DP);
        return config;
    }

    private static <T> void move(List<T> list, T item, int direction) {
        int index = list.indexOf(item);
        int target = index + direction;
        if (index < 0 || target < 0 || target >= list.size()) {
            return;
        }
        list.remove(index);
        list.add(target, item);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name, E fallback) {
        if (name == null || name.isEmpty()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException error) {
            return fallback;
        }
    }
}
