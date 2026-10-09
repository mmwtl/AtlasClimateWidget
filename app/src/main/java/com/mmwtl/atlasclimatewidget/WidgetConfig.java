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
        INFO(R.string.block_info, R.string.block_info_short),
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

    /** How the layout uses the launcher cell's height. */
    enum HeightMode {
        FILL(R.string.height_fill),
        CONTENT(R.string.height_content);

        final int titleRes;

        HeightMode(int titleRes) {
            this.titleRes = titleRes;
        }
    }

    /** Position of a shorter-than-cell layout in {@link HeightMode#CONTENT}. */
    enum VerticalAlign {
        TOP(R.string.align_top),
        CENTER(R.string.align_center),
        BOTTOM(R.string.align_bottom);

        final int titleRes;

        VerticalAlign(int titleRes) {
            this.titleRes = titleRes;
        }
    }

    /** Position of the cabin/outside sensor line across the widget. */
    enum HeaderAlign {
        LEFT(R.string.align_left),
        CENTER(R.string.align_center),
        RIGHT(R.string.align_right);

        final int titleRes;

        HeaderAlign(int titleRes) {
            this.titleRes = titleRes;
        }
    }

    /** Whether each block gets its own card or all blocks share one. */
    enum CardLayout {
        SEPARATE(R.string.card_layout_separate),
        SINGLE(R.string.card_layout_single);

        final int titleRes;

        CardLayout(int titleRes) {
            this.titleRes = titleRes;
        }
    }

    /** Overall look: slider bars with the value on the knob, or a console with large values. */
    enum Style {
        CLASSIC(R.string.style_classic),
        CONSOLE(R.string.style_console);

        final int titleRes;

        Style(int titleRes) {
            this.titleRes = titleRes;
        }
    }

    /**
     * Fan block layout: one bar that shows speeds in manual mode and auto-fan presets in AUTO,
     * or FX11-style rows with the speed bar, directions and presets all shown at once.
     */
    enum FanLayout {
        BAR(R.string.fan_layout_bar),
        ROWS(R.string.fan_layout_rows);

        final int titleRes;

        FanLayout(int titleRes) {
            this.titleRes = titleRes;
        }
    }

    /** Drawn icons: the Atlas set, or the stock Geely HVAC icons traced from the head unit. */
    enum IconSet {
        ATLAS(R.string.icons_atlas),
        OEM(R.string.icons_oem);

        final int titleRes;

        IconSet(int titleRes) {
            this.titleRes = titleRes;
        }
    }

    /**
     * 2: console tiles follow the user's order instead of fixed groups. 3: the sensor line is
     * its own block instead of a part of the temperature block. 4: the card radius is in
     * screen dp instead of being multiplied by the widget scale.
     */
    static final int VERSION = 4;
    static final int COLUMNS_MIN = 2;
    static final int COLUMNS_MAX = 8;
    static final int CARD_RADIUS_MAX_DP = 40;
    /** Radius of the stock OneOS cards, shared by all Atlas widgets. */
    static final int CARD_RADIUS_DEFAULT_DP = 24;
    /** Default before version 4, scaled by the widget scale. */
    static final int CARD_RADIUS_LEGACY_DP = 18;
    static final int TILE_RADIUS_MAX_PERCENT = 50;
    static final int GAP_MIN_DP = 4;
    static final int GAP_MAX_DP = 24;
    static final int PADDING_MIN_DP = 6;
    static final int PADDING_MAX_DP = 28;
    static final int SCALE_MIN_PERCENT = 80;
    static final int SCALE_MAX_PERCENT = 250;
    /** Head units sit at arm's length, so new layouts start enlarged. */
    static final int SCALE_DEFAULT_PERCENT = 160;
    /** Layouts saved before the scale setting existed were drawn at this size. */
    static final int SCALE_LEGACY_PERCENT = 100;

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
    boolean infoEnabled = true;
    boolean temperatureEnabled = true;
    boolean fanEnabled = true;
    boolean tilesEnabled = true;

    boolean temperatureDual;
    /** Sensor line parts: cabin and outside temperature, liters in the tank, free volume. */
    HeaderAlign headerAlign = HeaderAlign.CENTER;
    boolean headerCabin = true;
    boolean headerOutside = true;
    boolean headerFuel;
    boolean headerFuelFree;
    boolean temperatureButtons = true;
    boolean fanButtons = true;
    /** Fan block parts, as in FX11: speed bar, blowing directions and auto-fan presets. */
    boolean fanBar = true;
    boolean fanDirections = true;
    boolean fanPresets = true;
    /** Layouts saved before the bar existed keep their rows, see {@link #fromJson}. */
    FanLayout fanLayout = FanLayout.BAR;
    /** The bar layout's AUTO button, fixed at the bar's start in both modes. */
    boolean fanAuto = true;
    HeightMode heightMode = HeightMode.FILL;
    /** Resolved from the global car setting by {@link Prefs}; not part of the saved layout. */
    int fanPresetCount = 3;
    /** Fuel formula, resolved from the global setting by {@link Prefs}; not saved either. */
    float fuelMultiplier = Fuel.MULTIPLIER;
    float fuelOffset = Fuel.OFFSET;
    VerticalAlign verticalAlign = VerticalAlign.TOP;
    CardLayout cardLayout = CardLayout.SEPARATE;
    Style style = Style.CLASSIC;

    boolean headerShowsFuel() {
        return headerFuel || headerFuelFree;
    }

    /** Enabled tiles in display order. */
    final List<ClimateFunction> functions = new ArrayList<>(DEFAULT_FUNCTIONS);
    int columns = 6;
    TileStyle tileStyle = TileStyle.TILE;

    Palette palette = Palette.ATLAS;
    boolean filledActive = true;
    IconSet iconSet = IconSet.ATLAS;
    int cardColor = Ui.SURFACE;
    int cardOpacityPercent = 100;
    /** Screen dp, independent of {@link #scalePercent} and of the card size. */
    int cardRadiusDp = CARD_RADIUS_DEFAULT_DP;
    int cardPaddingDp = 14;
    int tileRadiusPercent = 22;
    int gapDp = 10;
    /** Size of bars, text and spacing; tile width still follows the column count. */
    int scalePercent = SCALE_DEFAULT_PERCENT;

    boolean isEnabled(Block block) {
        switch (block) {
            case INFO:
                return infoEnabled && hasInfoParts();
            case TEMPERATURE:
                return temperatureEnabled;
            case FAN:
                return fanEnabled && hasFanParts();
            default:
                return tilesEnabled && !functions.isEmpty();
        }
    }

    boolean hasInfoParts() {
        return headerCabin || headerOutside || headerShowsFuel();
    }

    boolean hasFanParts() {
        return fanLayout == FanLayout.BAR || fanBar || fanDirections || fanPresets;
    }

    /** Direction buttons of the fan block, in FX11's order. */
    static final ClimateFunction[] FAN_DIRECTIONS = {
            ClimateFunction.BLOW_WINDOW, ClimateFunction.BLOW_FACE, ClimateFunction.BLOW_LEGS
    };

    int fanControlCount() {
        return (fanDirections ? FAN_DIRECTIONS.length : 0)
                + (fanPresets ? ClimateCommands.fanPresets(fanPresetCount).length : 0);
    }

    /** Console fan rows: blowing directions, then auto-fan presets. */
    static final int FAN_ROW_DIRECTIONS = 0;
    static final int FAN_ROW_PRESETS = 1;

    /** Row indexes of the fan button rows, see {@link #FAN_ROW_DIRECTIONS}. */
    List<Integer> fanControlRows() {
        List<Integer> rows = new ArrayList<>();
        if (fanLayout == FanLayout.BAR) {
            return rows;
        }
        if (style != Style.CONSOLE) {
            if (fanControlCount() > 0) {
                rows.add(0);
            }
            return rows;
        }
        if (fanDirections) {
            rows.add(FAN_ROW_DIRECTIONS);
        }
        if (fanPresets && ClimateCommands.fanPresets(fanPresetCount).length > 0) {
            rows.add(FAN_ROW_PRESETS);
        }
        return rows;
    }

    /** Touch cells of a fan button row. */
    int fanControlCells(int row) {
        if (style != Style.CONSOLE) {
            return fanControlCount();
        }
        return row == FAN_ROW_DIRECTIONS ? FAN_DIRECTIONS.length
                : ClimateCommands.fanPresets(fanPresetCount).length;
    }

    /**
     * Console seat order of layouts saved before console tiles were free, mirrored like the
     * cabin: driver side, the wheel, then the passenger side.
     */
    private static final List<ClimateFunction> LEGACY_CONSOLE_SEATS = Arrays.asList(
            ClimateFunction.DRIVER_HEAT, ClimateFunction.DRIVER_VENT,
            ClimateFunction.REAR_LEFT_HEAT, ClimateFunction.WHEEL_HEAT,
            ClimateFunction.REAR_RIGHT_HEAT, ClimateFunction.PASSENGER_VENT,
            ClimateFunction.PASSENGER_HEAT);
    private static final List<ClimateFunction> LEGACY_CONSOLE_GLASS = Arrays.asList(
            ClimateFunction.WINDSHIELD_HEAT, ClimateFunction.DEFROST_MAX,
            ClimateFunction.REAR_DEFROST);

    /** Tile slots row by row in the user's order; {@code null} marks an empty cell. */
    List<ClimateFunction[]> tileRows() {
        List<ClimateFunction[]> rows = new ArrayList<>();
        appendWrapped(rows, functions, 0);
        return rows;
    }

    /**
     * Console layouts used to group tiles (climate modes, glass, mirrored seats) whatever the
     * saved order; puts the saved order into that sequence so they open looking the same.
     */
    private void adoptLegacyConsoleOrder() {
        List<ClimateFunction> climate = new ArrayList<>();
        List<ClimateFunction> glass = new ArrayList<>();
        for (ClimateFunction function : functions) {
            if (LEGACY_CONSOLE_GLASS.contains(function)) {
                glass.add(function);
            } else if (!LEGACY_CONSOLE_SEATS.contains(function)) {
                climate.add(function);
            }
        }
        List<ClimateFunction> ordered = new ArrayList<>(climate);
        ordered.addAll(glass);
        for (ClimateFunction function : LEGACY_CONSOLE_SEATS) {
            if (functions.contains(function)) {
                ordered.add(function);
            }
        }
        functions.clear();
        functions.addAll(ordered);
    }

    /** Wraps the functions into new rows; returns the cells used in the last row. */
    private int appendWrapped(List<ClimateFunction[]> rows, List<ClimateFunction> items,
            int used) {
        for (ClimateFunction function : items) {
            if (used == 0 || used == columns) {
                rows.add(new ClimateFunction[columns]);
                used = 0;
            }
            rows.get(rows.size() - 1)[used++] = function;
        }
        return used;
    }

    void setEnabled(Block block, boolean enabled) {
        switch (block) {
            case INFO:
                infoEnabled = enabled;
                break;
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

    /**
     * Drops a dragged tile onto a slot of {@link #tileRows()}: it takes the place of the tile
     * there, or goes last on an empty slot.
     *
     * @return whether the order changed
     */
    boolean dropTile(ClimateFunction function, int row, int column) {
        List<ClimateFunction[]> rows = tileRows();
        if (!functions.contains(function) || row < 0
                || row >= rows.size() || column < 0 || column >= columns) {
            return false;
        }
        ClimateFunction target = rows.get(row)[column];
        if (target == function) {
            return false;
        }
        int index;
        if (target == null) {
            index = functions.size() - 1;
        } else {
            index = functions.indexOf(target);
        }
        if (functions.indexOf(function) == index) {
            return false;
        }
        functions.remove(function);
        functions.add(index, function);
        return true;
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
            json.put("version", VERSION);
            JSONArray order = new JSONArray();
            for (Block block : blockOrder) {
                order.put(block.name());
            }
            json.put("blockOrder", order);
            json.put("infoEnabled", infoEnabled);
            json.put("temperatureEnabled", temperatureEnabled);
            json.put("fanEnabled", fanEnabled);
            json.put("tilesEnabled", tilesEnabled);
            json.put("temperatureDual", temperatureDual);
            json.put("headerAlign", headerAlign.name());
            json.put("headerCabin", headerCabin);
            json.put("headerOutside", headerOutside);
            json.put("headerFuel", headerFuel);
            json.put("headerFuelFree", headerFuelFree);
            json.put("temperatureButtons", temperatureButtons);
            json.put("fanButtons", fanButtons);
            json.put("fanBar", fanBar);
            json.put("fanDirections", fanDirections);
            json.put("fanPresets", fanPresets);
            json.put("fanLayout", fanLayout.name());
            json.put("fanAuto", fanAuto);
            json.put("heightMode", heightMode.name());
            json.put("verticalAlign", verticalAlign.name());
            json.put("cardLayout", cardLayout.name());
            json.put("style", style.name());
            JSONArray tiles = new JSONArray();
            for (ClimateFunction function : functions) {
                tiles.put(function.name());
            }
            json.put("functions", tiles);
            json.put("columns", columns);
            json.put("tileStyle", tileStyle.name());
            json.put("palette", palette.name());
            json.put("filledActive", filledActive);
            json.put("iconSet", iconSet.name());
            json.put("cardColor", cardColor);
            json.put("cardOpacityPercent", cardOpacityPercent);
            json.put("cardRadiusDp", cardRadiusDp);
            json.put("cardPaddingDp", cardPaddingDp);
            json.put("tileRadiusPercent", tileRadiusPercent);
            json.put("gapDp", gapDp);
            json.put("scalePercent", scalePercent);
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
            int temperature = parsed.indexOf(Block.TEMPERATURE);
            if (!parsed.contains(Block.INFO) && temperature >= 0) {
                // The sensor line used to head the temperature block.
                parsed.add(temperature, Block.INFO);
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
        config.headerAlign = enumValue(HeaderAlign.class, json.optString("headerAlign"),
                config.headerAlign);
        // Both temperatures used to share one switch.
        boolean temperatures = json.optBoolean("headerTemperatures", true);
        config.headerCabin = json.optBoolean("headerCabin", temperatures);
        config.headerOutside = json.optBoolean("headerOutside", temperatures);
        config.headerFuel = json.optBoolean("headerFuel", config.headerFuel);
        config.headerFuelFree = json.optBoolean("headerFuelFree", config.headerFuelFree);
        if (json.optInt("version", 1) < 3) {
            // The temperature block held the sensor line and the bar; each is a block now.
            config.infoEnabled = config.temperatureEnabled
                    && json.optBoolean("temperatureHeader", true);
            config.temperatureEnabled = config.temperatureEnabled
                    && json.optBoolean("temperatureBar", true);
            boolean onlyTemperatures = !config.headerShowsFuel();
            config.headerCabin |= onlyTemperatures;
            config.headerOutside |= onlyTemperatures;
        } else {
            config.infoEnabled = json.optBoolean("infoEnabled", config.infoEnabled);
        }
        config.temperatureButtons = json.optBoolean("temperatureButtons",
                config.temperatureButtons);
        config.fanButtons = json.optBoolean("fanButtons", config.fanButtons);
        if (json.has("fanBar")) {
            config.fanBar = json.optBoolean("fanBar", config.fanBar);
            config.fanDirections = json.optBoolean("fanDirections", config.fanDirections);
            config.fanPresets = json.optBoolean("fanPresets", config.fanPresets);
        } else if (json.has("fanStyle")) {
            // Layouts saved before the combined fan block keep their single part.
            boolean presets = "PRESETS".equals(json.optString("fanStyle"));
            config.fanBar = !presets;
            config.fanPresets = presets;
            config.fanDirections = false;
        }
        // Layouts saved before the bar layout keep their rows.
        config.fanLayout = enumValue(FanLayout.class, json.optString("fanLayout"),
                FanLayout.ROWS);
        config.fanAuto = json.optBoolean("fanAuto", config.fanAuto);
        config.heightMode = enumValue(HeightMode.class, json.optString("heightMode"),
                config.heightMode);
        config.verticalAlign = enumValue(VerticalAlign.class, json.optString("verticalAlign"),
                config.verticalAlign);
        config.cardLayout = enumValue(CardLayout.class, json.optString("cardLayout"),
                config.cardLayout);
        config.style = enumValue(Style.class, json.optString("style"), config.style);
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
        if (config.style == Style.CONSOLE && json.optInt("version", 1) < 2) {
            config.adoptLegacyConsoleOrder();
        }
        config.columns = clamp(json.optInt("columns", config.columns), COLUMNS_MIN, COLUMNS_MAX);
        config.tileStyle = enumValue(TileStyle.class, json.optString("tileStyle"),
                config.tileStyle);
        config.palette = enumValue(Palette.class, json.optString("palette"), config.palette);
        config.filledActive = json.optBoolean("filledActive", config.filledActive);
        config.iconSet = enumValue(IconSet.class, json.optString("iconSet"), config.iconSet);
        config.cardColor = json.optInt("cardColor", config.cardColor) | 0xFF000000;
        config.cardOpacityPercent = clamp(json.optInt("cardOpacityPercent",
                config.cardOpacityPercent), 0, 100);
        config.cardPaddingDp = clamp(json.optInt("cardPaddingDp", config.cardPaddingDp),
                PADDING_MIN_DP, PADDING_MAX_DP);
        config.tileRadiusPercent = clamp(json.optInt("tileRadiusPercent",
                config.tileRadiusPercent), 0, TILE_RADIUS_MAX_PERCENT);
        config.gapDp = clamp(json.optInt("gapDp", config.gapDp), GAP_MIN_DP, GAP_MAX_DP);
        config.scalePercent = clamp(json.optInt("scalePercent", SCALE_LEGACY_PERCENT),
                SCALE_MIN_PERCENT, SCALE_MAX_PERCENT);
        if (json.optInt("version", 1) < 4) {
            // The radius used to grow with the widget scale; keep the corners users saw.
            config.cardRadiusDp = Math.round(json.optInt("cardRadiusDp", CARD_RADIUS_LEGACY_DP)
                    * config.scalePercent / 100f);
        } else {
            config.cardRadiusDp = json.optInt("cardRadiusDp", config.cardRadiusDp);
        }
        config.cardRadiusDp = clamp(config.cardRadiusDp, 0, CARD_RADIUS_MAX_DP);
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
