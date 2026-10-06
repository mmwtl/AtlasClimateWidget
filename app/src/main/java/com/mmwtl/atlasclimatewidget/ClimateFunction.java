package com.mmwtl.atlasclimatewidget;

/** A climate function that can be placed on the widget as a tile. */
enum ClimateFunction {
    POWER(R.string.fn_power, R.string.fn_power_short, Tone.NEUTRAL,
            Spec.toggle(Hvac.POWER, Gib.AREA_GLOBAL, 1, 0).icon(R.drawable.ic_power)),
    AUTO(R.string.fn_auto, R.string.fn_auto_short, Tone.NEUTRAL,
            Spec.toggle(Hvac.AUTO, Hvac.ZONE_DRIVER, 1, 0).glyph("AUTO")
                    .oem(R.drawable.ic_oem_auto)),
    AC(R.string.fn_ac, R.string.fn_ac_short, Tone.COOL,
            Spec.toggle(Hvac.AC, Gib.AREA_GLOBAL, 1, 0).glyph("A/C").oem(R.drawable.ic_oem_ac)),
    AC_MAX(R.string.fn_ac_max, R.string.fn_ac_max_short, Tone.COOL,
            Spec.toggle(Hvac.AC_MAX, Gib.AREA_GLOBAL, 1, 0).glyph("MAX")
                    .oem(R.drawable.ic_oem_ac_max)),
    RECIRCULATION(R.string.fn_recirculation, R.string.fn_recirculation_short, Tone.NEUTRAL,
            Spec.toggle(Hvac.CIRCULATION, Gib.AREA_GLOBAL,
                    Hvac.CIRCULATION_INNER, Hvac.CIRCULATION_OUTSIDE)
                    .icon(R.drawable.ic_fn_recirculation)
                    .oem(R.drawable.ic_oem_recirculation)),
    WINDSHIELD_HEAT(R.string.fn_windshield_heat, R.string.fn_windshield_heat_short, Tone.WARM,
            Spec.toggle(Spec.MODEL_WINDSHIELD, Gib.AREA_GLOBAL, 1, 0)
                    .icon(R.drawable.ic_fn_front_defrost)
                    .oem(R.drawable.ic_oem_windshield_heat)),
    DEFROST_MAX(R.string.fn_defrost_max, R.string.fn_defrost_max_short, Tone.NEUTRAL,
            Spec.toggle(Hvac.DEFROST_FRONT_MAX, Gib.AREA_GLOBAL, 1, 0)
                    .icon(R.drawable.ic_fn_max_defrost)
                    .oem(R.drawable.ic_oem_front_defrost)),
    REAR_DEFROST(R.string.fn_rear_defrost, R.string.fn_rear_defrost_short, Tone.WARM,
            Spec.toggle(Hvac.DEFROST_REAR, Gib.AREA_GLOBAL, 1, 0)
                    .icon(R.drawable.ic_fn_rear_defrost)
                    .oem(R.drawable.ic_oem_rear_defrost)),
    WHEEL_HEAT(R.string.fn_wheel_heat, R.string.fn_wheel_heat_short, Tone.WARM,
            Spec.level(Hvac.STEERING_WHEEL_HEAT, Gib.AREA_GLOBAL, Hvac.STEERING_HEAT_LEVELS)
                    .icons(R.drawable.ic_fn_wheel_heat_0, R.drawable.ic_fn_wheel_heat_1,
                            R.drawable.ic_fn_wheel_heat_2, R.drawable.ic_fn_wheel_heat_3)
                    .oem(R.drawable.ic_oem_wheel_heat_0, R.drawable.ic_oem_wheel_heat_1,
                            R.drawable.ic_oem_wheel_heat_2, R.drawable.ic_oem_wheel_heat_3)),
    DRIVER_HEAT(R.string.fn_driver_heat, R.string.fn_driver_heat_short, Tone.WARM,
            Spec.level(Hvac.SEAT_HEATING, Hvac.ZONE_DRIVER, Hvac.SEAT_HEAT_LEVELS)
                    .icons(DriverIcons.HEAT)
                    .oem(DriverIcons.OEM_HEAT)),
    PASSENGER_HEAT(R.string.fn_passenger_heat, R.string.fn_passenger_heat_short, Tone.WARM,
            Spec.level(Hvac.SEAT_HEATING, Hvac.ZONE_PASSENGER, Hvac.SEAT_HEAT_LEVELS)
                    .icons(PassengerIcons.HEAT)
                    .oem(PassengerIcons.OEM_HEAT)),
    DRIVER_VENT(R.string.fn_driver_vent, R.string.fn_driver_vent_short, Tone.COOL,
            Spec.level(Hvac.SEAT_VENTILATION, Hvac.ZONE_DRIVER, Hvac.SEAT_VENT_LEVELS)
                    .icons(DriverIcons.VENT)
                    .oem(DriverIcons.OEM_VENT)),
    PASSENGER_VENT(R.string.fn_passenger_vent, R.string.fn_passenger_vent_short, Tone.COOL,
            Spec.level(Hvac.SEAT_VENTILATION, Hvac.ZONE_PASSENGER, Hvac.SEAT_VENT_LEVELS)
                    .icons(PassengerIcons.VENT)
                    .oem(PassengerIcons.OEM_VENT)),
    REAR_LEFT_HEAT(R.string.fn_rear_left_heat, R.string.fn_rear_left_heat_short, Tone.WARM,
            Spec.level(Hvac.SEAT_HEATING, Hvac.ZONE_REAR_LEFT, Hvac.SEAT_HEAT_LEVELS)
                    .icons(DriverIcons.HEAT)
                    .oem(R.drawable.ic_oem_rear_seat_left)),
    REAR_RIGHT_HEAT(R.string.fn_rear_right_heat, R.string.fn_rear_right_heat_short, Tone.WARM,
            Spec.level(Hvac.SEAT_HEATING, Hvac.ZONE_REAR_RIGHT, Hvac.SEAT_HEAT_LEVELS)
                    .icons(PassengerIcons.HEAT)
                    .oem(R.drawable.ic_oem_rear_seat_right)),
    SUNROOF(R.string.fn_sunroof, R.string.fn_sunroof_short, Tone.NEUTRAL,
            Spec.position(Hvac.WINDOW_POS, Hvac.ZONE_SUNROOF, Hvac.SUNROOF_POSITIONS)
                    .icon(R.drawable.ic_fn_sunroof)),
    SUNSHADE(R.string.fn_sunshade, R.string.fn_sunshade_short, Tone.NEUTRAL,
            Spec.position(Hvac.WINDOW_POS, Hvac.ZONE_SUNSHADE, Hvac.SUNSHADE_POSITIONS)
                    .icon(R.drawable.ic_fn_sunshade)),
    BLOW_FACE(R.string.fn_blow_face, R.string.fn_blow_face_short, Tone.NEUTRAL,
            Spec.blow(ClimateCommands.BLOW_FACE).icon(R.drawable.ic_blow_face)
                    .oem(R.drawable.ic_oem_blow_face)),
    BLOW_LEGS(R.string.fn_blow_legs, R.string.fn_blow_legs_short, Tone.NEUTRAL,
            Spec.blow(ClimateCommands.BLOW_LEGS).icon(R.drawable.ic_blow_legs)
                    .oem(R.drawable.ic_oem_blow_legs)),
    BLOW_WINDOW(R.string.fn_blow_window, R.string.fn_blow_window_short, Tone.NEUTRAL,
            Spec.blow(ClimateCommands.BLOW_WINDOW).icon(R.drawable.ic_blow_window)
                    .oem(R.drawable.ic_oem_blow_window)),
    FAN_SOFT(R.string.fn_fan_soft, R.string.fn_fan_soft_short, Tone.NEUTRAL,
            Spec.select(Hvac.AUTO_FAN_SETTING, Hvac.ZONE_ROW_1_ALL, Hvac.AUTO_FAN_QUIETER, 1)
                    .icon(R.drawable.ic_fan).oem(R.drawable.ic_oem_fan)),
    FAN_NORMAL(R.string.fn_fan_normal, R.string.fn_fan_normal_short, Tone.NEUTRAL,
            Spec.select(Hvac.AUTO_FAN_SETTING, Hvac.ZONE_ROW_1_ALL, Hvac.AUTO_FAN_NORMAL, 2)
                    .icon(R.drawable.ic_fan).oem(R.drawable.ic_oem_fan)),
    FAN_STRONG(R.string.fn_fan_strong, R.string.fn_fan_strong_short, Tone.NEUTRAL,
            Spec.select(Hvac.AUTO_FAN_SETTING, Hvac.ZONE_ROW_1_ALL, Hvac.AUTO_FAN_HIGHER, 3)
                    .icon(R.drawable.ic_fan).oem(R.drawable.ic_oem_fan)),
    TEMP_SYNC(R.string.fn_temp_sync, R.string.fn_temp_sync_short, Tone.NEUTRAL,
            Spec.toggle(Hvac.TEMP_DUAL, Gib.AREA_GLOBAL, 1, 0).glyph("SYNC")),
    ECO(R.string.fn_eco, R.string.fn_eco_short, Tone.NEUTRAL,
            Spec.toggle(Hvac.ECO, Gib.AREA_GLOBAL, 1, 0).glyph("ECO").oem(R.drawable.ic_oem_eco)),
    IONIZER(R.string.fn_ionizer, R.string.fn_ionizer_short, Tone.NEUTRAL,
            Spec.toggle(Hvac.IONIZER, Gib.AREA_GLOBAL, 1, 0).glyph("ION")),
    ME_HOT(R.string.fn_me_hot, R.string.fn_me_hot_short, Tone.COOL,
            Spec.action("ME_HOT").icon(R.drawable.ic_fn_me_hot)),
    ME_COLD(R.string.fn_me_cold, R.string.fn_me_cold_short, Tone.WARM,
            Spec.action("ME_COLD").icon(R.drawable.ic_fn_me_cold)),
    CLIMATE_MENU(R.string.fn_climate_menu, R.string.fn_climate_menu_short, Tone.NEUTRAL,
            Spec.action("CLIMATE_MENU").icon(R.drawable.ic_fn_climate));

    enum Kind {
        TOGGLE,
        LEVEL,
        /** A float percentage stepped through {@link #levels}, such as the sunroof. */
        POSITION,
        BLOW,
        SELECT,
        ACTION
    }

    /** Colour family used by the "by meaning" palette. */
    enum Tone {
        NEUTRAL,
        WARM,
        COOL
    }

    final int titleRes;
    final int shortRes;
    final Tone tone;
    final Kind kind;
    final int area;
    final int onValue;
    final int offValue;
    final int[] levels;
    final int blowBit;
    final int rank;
    final String carFunction;
    final String glyph;
    final int iconRes;
    final int[] levelIcons;
    private final int[] oemIcons;
    private final int propertyId;

    ClimateFunction(int titleRes, int shortRes, Tone tone, Spec spec) {
        this.titleRes = titleRes;
        this.shortRes = shortRes;
        this.tone = tone;
        this.kind = spec.kind;
        this.propertyId = spec.propertyId;
        this.area = spec.area;
        this.onValue = spec.onValue;
        this.offValue = spec.offValue;
        this.levels = spec.levels;
        this.blowBit = spec.blowBit;
        this.rank = spec.rank;
        this.carFunction = spec.carFunction;
        this.glyph = spec.glyph;
        this.iconRes = spec.iconRes;
        this.levelIcons = spec.levelIcons;
        this.oemIcons = spec.oemIcons;
    }

    /**
     * Drawable for the tile at {@code level}, or 0 when the tile shows its text glyph. The OEM
     * set falls back to the Atlas icon for functions the head unit has no icon for.
     */
    int icon(WidgetConfig.IconSet set, int level) {
        int[] icons = set == WidgetConfig.IconSet.OEM && oemIcons != null ? oemIcons : levelIcons;
        if (icons == null) {
            return glyph != null ? 0 : iconRes;
        }
        return icons[Math.max(0, Math.min(icons.length - 1, level))];
    }

    int propertyId(CarModel model) {
        return propertyId == Spec.MODEL_WINDSHIELD ? model.windshieldHeatId : propertyId;
    }

    boolean hasProperty() {
        return kind != Kind.ACTION;
    }

    boolean isFloat() {
        return kind == Kind.POSITION;
    }

    /** Number of indicator bars under the glyph. */
    int indicatorCount() {
        switch (kind) {
            case LEVEL:
            case POSITION:
                return levels.length - 1;
            case SELECT:
                return 3;
            case TOGGLE:
            case BLOW:
                return 1;
            default:
                return 0;
        }
    }

    static ClimateFunction fromName(String name) {
        for (ClimateFunction function : values()) {
            if (function.name().equals(name)) {
                return function;
            }
        }
        return null;
    }

    private static final class DriverIcons {
        static final int[] HEAT = {R.drawable.ic_fn_seat_heat_driver_0,
                R.drawable.ic_fn_seat_heat_driver_1, R.drawable.ic_fn_seat_heat_driver_2,
                R.drawable.ic_fn_seat_heat_driver_3};
        static final int[] VENT = {R.drawable.ic_fn_seat_vent_driver_0,
                R.drawable.ic_fn_seat_vent_driver_1, R.drawable.ic_fn_seat_vent_driver_2,
                R.drawable.ic_fn_seat_vent_driver_3};
        static final int[] OEM_HEAT = {R.drawable.ic_oem_seat_heat_driver_0,
                R.drawable.ic_oem_seat_heat_driver_1, R.drawable.ic_oem_seat_heat_driver_2,
                R.drawable.ic_oem_seat_heat_driver_3};
        static final int[] OEM_VENT = {R.drawable.ic_oem_seat_vent_driver_0,
                R.drawable.ic_oem_seat_vent_driver_1, R.drawable.ic_oem_seat_vent_driver_2,
                R.drawable.ic_oem_seat_vent_driver_3};
    }

    private static final class PassengerIcons {
        static final int[] HEAT = {R.drawable.ic_fn_seat_heat_passenger_0,
                R.drawable.ic_fn_seat_heat_passenger_1, R.drawable.ic_fn_seat_heat_passenger_2,
                R.drawable.ic_fn_seat_heat_passenger_3};
        static final int[] VENT = {R.drawable.ic_fn_seat_vent_passenger_0,
                R.drawable.ic_fn_seat_vent_passenger_1, R.drawable.ic_fn_seat_vent_passenger_2,
                R.drawable.ic_fn_seat_vent_passenger_3};
        static final int[] OEM_HEAT = {R.drawable.ic_oem_seat_heat_passenger_0,
                R.drawable.ic_oem_seat_heat_passenger_1, R.drawable.ic_oem_seat_heat_passenger_2,
                R.drawable.ic_oem_seat_heat_passenger_3};
        static final int[] OEM_VENT = {R.drawable.ic_oem_seat_vent_passenger_0,
                R.drawable.ic_oem_seat_vent_passenger_1, R.drawable.ic_oem_seat_vent_passenger_2,
                R.drawable.ic_oem_seat_vent_passenger_3};
    }

    private static final class Spec {
        static final int MODEL_WINDSHIELD = -1;

        Kind kind;
        int propertyId;
        int area = Gib.AREA_GLOBAL;
        int onValue = 1;
        int offValue;
        int[] levels;
        int blowBit;
        int rank;
        String carFunction;
        String glyph;
        int iconRes;
        int[] levelIcons;
        int[] oemIcons;

        static Spec toggle(int propertyId, int area, int onValue, int offValue) {
            Spec spec = new Spec();
            spec.kind = Kind.TOGGLE;
            spec.propertyId = propertyId;
            spec.area = area;
            spec.onValue = onValue;
            spec.offValue = offValue;
            return spec;
        }

        static Spec level(int propertyId, int area, int[] levels) {
            Spec spec = new Spec();
            spec.kind = Kind.LEVEL;
            spec.propertyId = propertyId;
            spec.area = area;
            spec.levels = levels;
            return spec;
        }

        static Spec position(int propertyId, int area, int[] percents) {
            Spec spec = level(propertyId, area, percents);
            spec.kind = Kind.POSITION;
            return spec;
        }

        static Spec blow(int bit) {
            Spec spec = new Spec();
            spec.kind = Kind.BLOW;
            spec.propertyId = Hvac.BLOWING_MODE;
            spec.area = Hvac.ZONE_DRIVER;
            spec.blowBit = bit;
            return spec;
        }

        static Spec select(int propertyId, int area, int value, int rank) {
            Spec spec = new Spec();
            spec.kind = Kind.SELECT;
            spec.propertyId = propertyId;
            spec.area = area;
            spec.onValue = value;
            spec.rank = rank;
            return spec;
        }

        static Spec action(String carFunction) {
            Spec spec = new Spec();
            spec.kind = Kind.ACTION;
            spec.carFunction = carFunction;
            return spec;
        }

        Spec icon(int res) {
            iconRes = res;
            return this;
        }

        Spec icons(int... res) {
            iconRes = res[0];
            levelIcons = res;
            return this;
        }

        /** Stock head-unit icon, one per level for level functions. */
        Spec oem(int... res) {
            oemIcons = res;
            return this;
        }

        Spec glyph(String text) {
            glyph = text;
            return this;
        }
    }
}
