package com.mmwtl.atlasclimatewidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure state evaluation and command calculation for widget controls. */
final class ClimateCommands {
    static final int BLOW_FACE = 1;
    static final int BLOW_LEGS = 2;
    static final int BLOW_WINDOW = 4;

    static final float FALLBACK_TEMPERATURE = 22f;
    static final int FAN_BLOWER = 269752064;
    static final int FAN_BLOWER_UP = 269752065;
    static final int FAN_BLOWER_DOWN = 269752066;

    private static final int SEAT_HEAT_MAX_ALIAS = 0x1005020F;
    private static final int SEAT_VENT_MAX_ALIAS = 0x1005010F;
    /** Atlas auto-fan presets «Мягко», «Комфорт», «Сильно», as used by GInputBridge. */
    static final int[] FAN_PRESETS_THREE = {
            Hvac.AUTO_FAN_QUIETER, Hvac.AUTO_FAN_NORMAL, Hvac.AUTO_FAN_HIGHER
    };
    /** All five auto-fan profiles in GInputBridge's order, from quietest to strongest. */
    static final int[] FAN_PRESETS_FIVE = {
            Hvac.AUTO_FAN_QUIETER, Hvac.AUTO_FAN_SILENT, Hvac.AUTO_FAN_NORMAL,
            Hvac.AUTO_FAN_HIGH, Hvac.AUTO_FAN_HIGHER
    };

    static int[] fanPresets(int count) {
        return count >= FAN_PRESETS_FIVE.length ? FAN_PRESETS_FIVE : FAN_PRESETS_THREE;
    }

    private ClimateCommands() {
    }

    // ---- state -------------------------------------------------------------------------------

    static final class TileState {
        static final TileState UNKNOWN = new TileState(false, false, 0);
        static final TileState ACTION = new TileState(true, false, 0);

        final boolean known;
        final boolean active;
        /** Lit indicator bars. */
        final int level;

        TileState(boolean known, boolean active, int level) {
            this.known = known;
            this.active = active;
            this.level = level;
        }
    }

    static TileState tileState(ClimateFunction function, ClimateState state, CarModel model) {
        if (function.kind == ClimateFunction.Kind.ACTION) {
            return TileState.ACTION;
        }
        Double raw = state.property(function.propertyId(model), function.area);
        if (raw == null) {
            return TileState.UNKNOWN;
        }
        int value = (int) Math.round(raw);
        switch (function.kind) {
            case TOGGLE: {
                boolean on = value == function.onValue;
                return new TileState(true, on, on ? 1 : 0);
            }
            case LEVEL: {
                int index = levelIndex(function, value);
                return new TileState(true, index > 0, index);
            }
            case BLOW: {
                boolean on = (blowBits(value) & function.blowBit) != 0;
                return new TileState(true, on, on ? 1 : 0);
            }
            case SELECT: {
                boolean on = value == function.onValue;
                return new TileState(true, on, function.rank);
            }
            default:
                return TileState.UNKNOWN;
        }
    }

    /** Maps a raw level value, including the short 1..3 and alias encodings, to 0..count. */
    static int levelIndex(ClimateFunction function, int value) {
        int[] levels = function.levels;
        for (int index = 0; index < levels.length; index++) {
            if (levels[index] == value) {
                return index;
            }
        }
        if (value >= 1 && value < levels.length) {
            return value;
        }
        if (value == SEAT_HEAT_MAX_ALIAS || value == SEAT_VENT_MAX_ALIAS) {
            return levels.length - 1;
        }
        return 0;
    }

    static int blowBits(int mode) {
        switch (mode) {
            case Hvac.BLOWING_FACE:
                return BLOW_FACE;
            case Hvac.BLOWING_LEG:
                return BLOW_LEGS;
            case Hvac.BLOWING_FACE_AND_LEG:
                return BLOW_FACE | BLOW_LEGS;
            case Hvac.BLOWING_WINDOW:
                return BLOW_WINDOW;
            case Hvac.BLOWING_FACE_AND_WINDOW:
                return BLOW_FACE | BLOW_WINDOW;
            case Hvac.BLOWING_LEG_AND_WINDOW:
                return BLOW_LEGS | BLOW_WINDOW;
            case Hvac.BLOWING_ALL:
                return BLOW_FACE | BLOW_LEGS | BLOW_WINDOW;
            default:
                return 0;
        }
    }

    static int blowMode(int bits) {
        switch (bits) {
            case BLOW_FACE:
                return Hvac.BLOWING_FACE;
            case BLOW_LEGS:
                return Hvac.BLOWING_LEG;
            case BLOW_FACE | BLOW_LEGS:
                return Hvac.BLOWING_FACE_AND_LEG;
            case BLOW_WINDOW:
                return Hvac.BLOWING_WINDOW;
            case BLOW_FACE | BLOW_WINDOW:
                return Hvac.BLOWING_FACE_AND_WINDOW;
            case BLOW_LEGS | BLOW_WINDOW:
                return Hvac.BLOWING_LEG_AND_WINDOW;
            case BLOW_FACE | BLOW_LEGS | BLOW_WINDOW:
                return Hvac.BLOWING_ALL;
            default:
                return 0;
        }
    }

    static final class TempRange {
        final float min;
        final float max;

        TempRange(float min, float max) {
            this.min = min;
            this.max = max;
        }

        int steps() {
            return Math.round((max - min) / Hvac.TEMP_STEP) + 1;
        }

        float valueAt(int step) {
            return Math.min(max, min + step * Hvac.TEMP_STEP);
        }

        float fraction(float value) {
            return Math.max(0f, Math.min(1f, (value - min) / (max - min)));
        }
    }

    static TempRange tempRange(ClimateState state) {
        Double min = state.property(Hvac.TEMP_MIN, Hvac.ZONE_DRIVER);
        Double max = state.property(Hvac.TEMP_MAX, Hvac.ZONE_DRIVER);
        if (min != null && max != null && min >= 0d && max > min && max - min <= 30d) {
            return new TempRange(roundToStep(min.floatValue()), roundToStep(max.floatValue()));
        }
        return new TempRange(Hvac.DEFAULT_TEMP_MIN, Hvac.DEFAULT_TEMP_MAX);
    }

    static Float temperature(ClimateState state, int zone) {
        Double value = state.property(Hvac.TEMP, zone);
        if (value == null || value < 0d) {
            return null;
        }
        return value.floatValue();
    }

    static final class FanState {
        final boolean known;
        final boolean auto;
        /** Manual level 0..9. */
        final int level;

        FanState(boolean known, boolean auto, int level) {
            this.known = known;
            this.auto = auto;
            this.level = level;
        }
    }

    static FanState fanState(ClimateState state) {
        Double speed = state.property(Hvac.FAN_SPEED, Hvac.ZONE_ROW_1_ALL);
        Double autoFan = state.property(Hvac.AUTO_FAN_SETTING, Hvac.ZONE_ROW_1_ALL);
        boolean auto = autoFan != null && isAutoFan((int) Math.round(autoFan));
        if (speed == null) {
            return new FanState(auto, auto, 0);
        }
        int value = (int) Math.round(speed);
        if (value == Hvac.FAN_SPEED_AUTO) {
            return new FanState(true, true, 0);
        }
        return new FanState(true, auto, fanLevel(value));
    }

    static int fanLevel(int value) {
        int level = value - Hvac.FAN_SPEED_LEVEL_1 + 1;
        if (level >= 1 && level <= Hvac.FAN_SPEED_LEVEL_COUNT) {
            return level;
        }
        if (value >= 1 && value <= Hvac.FAN_SPEED_LEVEL_COUNT) {
            return value;
        }
        return 0;
    }

    private static boolean isAutoFan(int value) {
        return value >= Hvac.AUTO_FAN_SILENT && value <= Hvac.AUTO_FAN_HIGHER;
    }

    // ---- commands ----------------------------------------------------------------------------

    static final class Command {
        enum Type {
            INT,
            FLOAT,
            CAR_FUNCTION
        }

        final Type type;
        final int id;
        final int area;
        final double value;
        final String function;

        private Command(Type type, int id, int area, double value, String function) {
            this.type = type;
            this.id = id;
            this.area = area;
            this.value = value;
            this.function = function;
        }

        static Command setInt(int id, int area, int value) {
            return new Command(Type.INT, id, area, value, null);
        }

        static Command setFloat(int id, int area, float value) {
            return new Command(Type.FLOAT, id, area, value, null);
        }

        static Command carFunction(String function) {
            return new Command(Type.CAR_FUNCTION, 0, 0, 0d, function);
        }

        /** Commands that only nudge the car have no state to preview optimistically. */
        boolean isOptimistic() {
            return type != Type.CAR_FUNCTION && id != FAN_BLOWER;
        }
    }

    static List<Command> press(
            ClimateFunction function,
            ClimateState state,
            CarModel model,
            boolean levelsFromMax
    ) {
        int id = function.propertyId(model);
        Double raw = function.hasProperty() ? state.property(id, function.area) : null;
        int current = raw == null ? Integer.MIN_VALUE : (int) Math.round(raw);
        switch (function.kind) {
            case ACTION:
                return single(Command.carFunction(function.carFunction));
            case TOGGLE: {
                boolean on = current == function.onValue;
                return single(Command.setInt(id, function.area,
                        on ? function.offValue : function.onValue));
            }
            case LEVEL: {
                int count = function.levels.length;
                int index = raw == null ? 0 : levelIndex(function, current);
                int next = levelsFromMax
                        ? (index == 0 ? count - 1 : index - 1)
                        : (index + 1) % count;
                return single(Command.setInt(id, function.area, function.levels[next]));
            }
            case BLOW: {
                int bits = raw == null ? 0 : blowBits(current);
                int nextBits = bits ^ function.blowBit;
                if (nextBits == 0) {
                    return Collections.emptyList();
                }
                return single(Command.setInt(id, function.area, blowMode(nextBits)));
            }
            case SELECT:
                return single(Command.setInt(id, function.area, function.onValue));
            default:
                return Collections.emptyList();
        }
    }

    static List<Command> setTemperature(ClimateState state, int zone, float value) {
        TempRange range = tempRange(state);
        float clamped = Math.max(range.min, Math.min(range.max, roundToStep(value)));
        return single(Command.setFloat(Hvac.TEMP, zone, clamped));
    }

    static List<Command> stepTemperature(ClimateState state, int zone, int direction, float step) {
        TempRange range = tempRange(state);
        Float current = temperature(state, zone);
        float base = current == null ? FALLBACK_TEMPERATURE : current;
        float safeStep = step >= 1f ? 1f : Hvac.TEMP_STEP;
        float next;
        if (safeStep >= 1f) {
            // Whole-degree steps land on whole degrees even from a half-degree value.
            next = direction > 0 ? (float) Math.floor(base) + 1f : (float) Math.ceil(base) - 1f;
        } else {
            next = base + direction * safeStep;
        }
        next = Math.max(range.min, Math.min(range.max, next));
        if (current != null && next == current) {
            return Collections.emptyList();
        }
        return single(Command.setFloat(Hvac.TEMP, zone, next));
    }

    static List<Command> setFan(int level) {
        int bounded = Math.max(1, Math.min(Hvac.FAN_SPEED_LEVEL_COUNT, level));
        return single(Command.setInt(Hvac.FAN_SPEED, Hvac.ZONE_ROW_1_ALL,
                Hvac.FAN_SPEED_LEVEL_1 + bounded - 1));
    }

    static List<Command> setFanPreset(int index, int count) {
        int[] presets = fanPresets(count);
        int bounded = Math.max(0, Math.min(presets.length - 1, index));
        return single(Command.setInt(Hvac.AUTO_FAN_SETTING, Hvac.ZONE_ROW_1_ALL,
                presets[bounded]));
    }

    /** Index of the active auto-fan preset, or -1. */
    static int fanPreset(ClimateState state, int count) {
        Double raw = state.property(Hvac.AUTO_FAN_SETTING, Hvac.ZONE_ROW_1_ALL);
        return raw == null ? -1 : indexOf(fanPresets(count), (int) Math.round(raw));
    }

    static List<Command> stepFan(ClimateState state, CarModel model, int direction,
            int presetCount) {
        int[] cycle = fanPresets(presetCount);
        if (model != CarModel.ATLAS) {
            return single(Command.setInt(FAN_BLOWER, Hvac.ZONE_ROW_1_ALL,
                    direction > 0 ? FAN_BLOWER_UP : FAN_BLOWER_DOWN));
        }
        FanState fan = fanState(state);
        if (fan.auto) {
            Double raw = state.property(Hvac.AUTO_FAN_SETTING, Hvac.ZONE_ROW_1_ALL);
            int middle = cycle.length / 2;
            int current = raw == null ? cycle[middle] : (int) Math.round(raw);
            int index = indexOf(cycle, current);
            // Profiles outside the preset cycle snap to the middle one.
            int next = index < 0
                    ? middle
                    : Math.max(0, Math.min(cycle.length - 1, index + direction));
            if (next == index) {
                return Collections.emptyList();
            }
            return single(Command.setInt(Hvac.AUTO_FAN_SETTING, Hvac.ZONE_ROW_1_ALL,
                    cycle[next]));
        }
        int next = Math.max(1, Math.min(Hvac.FAN_SPEED_LEVEL_COUNT, fan.level + direction));
        if (fan.known && next == fan.level) {
            return Collections.emptyList();
        }
        return setFan(next);
    }

    static float roundToStep(float value) {
        return Math.round(value / Hvac.TEMP_STEP) * Hvac.TEMP_STEP;
    }

    private static int indexOf(int[] values, int value) {
        for (int index = 0; index < values.length; index++) {
            if (values[index] == value) {
                return index;
            }
        }
        return -1;
    }

    private static List<Command> single(Command command) {
        List<Command> commands = new ArrayList<>(1);
        commands.add(command);
        return commands;
    }
}
