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
    /** The car answers AUTO with its kept profile in about 300–450 ms. */
    static final long AUTO_SETTLE_MS = 900L;
    static final int FAN_BLOWER = 269752064;
    static final int FAN_BLOWER_UP = 269752065;
    static final int FAN_BLOWER_DOWN = 269752066;

    /** Percent a reported roof position may miss a stop by, as the motor stops near it. */
    static final double POSITION_TOLERANCE = 2d;
    /** Sunroof stop sent as {@link Hvac#SUNROOF_TILT} rather than a position. */
    static final int SUNROOF_AIRING = 1;

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
        /**
         * The car keeps the value but ignores it in the current mode: directions in AUTO,
         * auto-fan presets outside it. {@link #active} then marks the kept choice.
         */
        final boolean dormant;

        TileState(boolean known, boolean active, int level) {
            this(known, active, level, false);
        }

        TileState(boolean known, boolean active, int level, boolean dormant) {
            this.known = known;
            this.active = active;
            this.level = level;
            this.dormant = dormant;
        }
    }

    /** Whether climate AUTO is on, or null while unknown. */
    static Boolean autoMode(ClimateState state) {
        Double raw = state.property(Hvac.AUTO, Hvac.ZONE_DRIVER);
        return raw == null ? null : (int) Math.round(raw) == ClimateFunction.AUTO.onValue;
    }

    /** Blowing directions only apply in manual mode. */
    static boolean directionsDormant(ClimateState state) {
        return Boolean.TRUE.equals(autoMode(state));
    }

    /** Auto-fan presets only apply in AUTO. */
    static boolean presetsDormant(ClimateState state) {
        return Boolean.FALSE.equals(autoMode(state));
    }

    static TileState tileState(ClimateFunction function, ClimateState state, CarModel model) {
        if (function.kind == ClimateFunction.Kind.ACTION) {
            return TileState.ACTION;
        }
        if (function == ClimateFunction.SUNROOF) {
            Integer index = sunroofIndex(state);
            return index == null ? TileState.UNKNOWN : new TileState(true, index > 0, index);
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
            case POSITION: {
                int index = positionIndex(function, raw);
                return new TileState(true, index > 0, index);
            }
            case BLOW: {
                boolean on = (blowBits(value) & function.blowBit) != 0;
                return new TileState(true, on, on ? 1 : 0, directionsDormant(state));
            }
            case SELECT: {
                boolean on = value == function.onValue;
                boolean dormant = function.propertyId(model) == Hvac.AUTO_FAN_SETTING
                        && presetsDormant(state);
                return new TileState(true, on, function.rank, dormant);
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

    /**
     * Maps a reported percentage to the highest stop it has reached; any opening counts as
     * the first stop, so a roof moving between stops never shows as closed.
     */
    static int positionIndex(ClimateFunction function, double percent) {
        if (percent <= POSITION_TOLERANCE) {
            return 0;
        }
        int[] levels = function.levels;
        int index = 1;
        while (index + 1 < levels.length && levels[index + 1] <= percent + POSITION_TOLERANCE) {
            index++;
        }
        return index;
    }

    /** Sunroof stop from its position and airing flag, or null while both are unknown. */
    static Integer sunroofIndex(ClimateState state) {
        Double position = state.property(Hvac.WINDOW_POS, Hvac.ZONE_SUNROOF);
        boolean airing = sunroofAiring(state);
        if (position == null && state.property(Hvac.SUNROOF_TILT, Hvac.ZONE_SUNROOF) == null) {
            return null;
        }
        int index = position == null ? 0 : positionIndex(ClimateFunction.SUNROOF, position);
        return airing ? Math.max(index, SUNROOF_AIRING) : index;
    }

    private static boolean sunroofAiring(ClimateState state) {
        Double tilt = state.property(Hvac.SUNROOF_TILT, Hvac.ZONE_SUNROOF);
        return tilt != null && Math.round(tilt) == 1;
    }

    /** Whether the sunroof is open or airing, or false while unknown. */
    static boolean sunroofOpen(ClimateState state) {
        Integer index = sunroofIndex(state);
        return index != null && index > 0;
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
        Boolean mode = autoMode(state);
        boolean auto = mode != null ? mode
                : autoFan != null && isAutoFan((int) Math.round(autoFan));
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
        /** Wait after the previous command of the same press, see {@link #after}. */
        final long delayMs;

        private Command(Type type, int id, int area, double value, String function) {
            this(type, id, area, value, function, 0L);
        }

        private Command(Type type, int id, int area, double value, String function,
                long delayMs) {
            this.type = type;
            this.id = id;
            this.area = area;
            this.value = value;
            this.function = function;
            this.delayMs = delayMs;
        }

        Command after(long delayMs) {
            return new Command(type, id, area, value, function, delayMs);
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
            case POSITION:
                return roofPress(function, state, id, raw);
            case BLOW: {
                // In AUTO the kept directions are not in use, so a tap picks only this one.
                int bits = raw == null || directionsDormant(state) ? 0 : blowBits(current);
                int nextBits = bits ^ function.blowBit;
                if (nextBits == 0) {
                    return Collections.emptyList();
                }
                return single(Command.setInt(id, function.area, blowMode(nextBits)));
            }
            case SELECT: {
                Command select = Command.setInt(id, function.area, function.onValue);
                return id == Hvac.AUTO_FAN_SETTING ? withAuto(state, select) : single(select);
            }
            default:
                return Collections.emptyList();
        }
    }

    /**
     * Steps the sunroof or sunshade upwards like a heating level; the «from the maximum» order
     * never applies, so a first tap only opens a little. The sunshade cannot close under an
     * open sunroof: its cycle skips «closed» then, and opening the sunroof first opens a closed
     * sunshade to its lowest stop that uncovers the opening.
     */
    private static List<Command> roofPress(ClimateFunction function, ClimateState state,
            int id, Double raw) {
        int[] levels = function.levels;
        int lowest = function == ClimateFunction.SUNSHADE && sunroofOpen(state) ? 1 : 0;
        int index;
        if (function == ClimateFunction.SUNROOF) {
            Integer roof = sunroofIndex(state);
            index = roof == null ? 0 : roof;
        } else {
            index = raw == null ? 0 : positionIndex(function, raw);
        }
        int next;
        if (index < lowest) {
            next = lowest;
        } else {
            next = index + 1 < levels.length ? index + 1 : lowest;
        }
        List<Command> commands = new ArrayList<>(3);
        if (function == ClimateFunction.SUNROOF && levels[next] > 0) {
            Double shade = state.property(Hvac.WINDOW_POS, Hvac.ZONE_SUNSHADE);
            if (shade != null && shade <= POSITION_TOLERANCE) {
                commands.add(Command.setFloat(Hvac.WINDOW_POS, Hvac.ZONE_SUNSHADE,
                        stopAtLeast(Hvac.SUNSHADE_POSITIONS, levels[next])));
            }
        }
        if (function != ClimateFunction.SUNROOF) {
            commands.add(Command.setFloat(id, function.area, levels[next]));
        } else if (next == SUNROOF_AIRING) {
            addTilt(commands, 1);
        } else if (next > 0) {
            commands.add(Command.setFloat(id, function.area, levels[next]));
        } else {
            // Close what is open: the airing flag, the slid position or both.
            if (sunroofAiring(state)) {
                addTilt(commands, 0);
            }
            if (raw == null || raw > POSITION_TOLERANCE || !sunroofAiring(state)) {
                commands.add(Command.setFloat(id, function.area, 0f));
            }
        }
        return commands;
    }

    /** FX11 sets the airing flag both in the sunroof zone and globally. */
    private static void addTilt(List<Command> commands, int value) {
        commands.add(Command.setInt(Hvac.SUNROOF_TILT, Hvac.ZONE_SUNROOF, value));
        commands.add(Command.setInt(Hvac.SUNROOF_TILT, Gib.AREA_GLOBAL, value));
    }

    private static int stopAtLeast(int[] stops, int percent) {
        for (int stop : stops) {
            if (stop >= percent) {
                return stop;
            }
        }
        return stops[stops.length - 1];
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

    static List<Command> setFan(ClimateState state, int level) {
        return withoutAuto(state, setFan(level).get(0));
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

    static List<Command> setFanPreset(ClimateState state, int index, int count) {
        return withAuto(state, setFanPreset(index, count).get(0));
    }

    /** Presets only apply in AUTO, so a preset tap in manual mode turns AUTO on first. */
    static List<Command> withAuto(ClimateState state, Command preset) {
        return presetsDormant(state) ? switchAuto(true, preset) : single(preset);
    }

    /** The car ignores a manual fan speed in AUTO, so a speed tap there leaves AUTO first. */
    static List<Command> withoutAuto(ClimateState state, Command speed) {
        return directionsDormant(state) ? switchAuto(false, speed) : single(speed);
    }

    private static List<Command> switchAuto(boolean on, Command then) {
        List<Command> commands = new ArrayList<>(2);
        commands.add(Command.setInt(Hvac.AUTO, ClimateFunction.AUTO.area,
                on ? ClimateFunction.AUTO.onValue : ClimateFunction.AUTO.offValue));
        // The mode switch restores the kept profile and speed, which would override an
        // earlier set.
        commands.add(then.after(AUTO_SETTLE_MS));
        return commands;
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
