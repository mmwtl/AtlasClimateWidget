package com.mmwtl.atlasclimatewidget;

/**
 * ECarX property and sensor identifiers used by the widget.
 *
 * <p>Values mirror {@code CarPropertyKey}/{@code CarFunctionIds} in GInputBridge. Area and value
 * encodings are firmware-specific; they were taken from the bridge's own launcher tiles and must
 * be confirmed on the head unit before generalising them to other models.
 */
final class Hvac {
    static final int ZONE_DRIVER = 1;
    static final int ZONE_PASSENGER = 4;
    static final int ZONE_ROW_1_ALL = 8;
    static final int ZONE_REAR_LEFT = 16;
    static final int ZONE_REAR_RIGHT = 64;
    /** {@link #WINDOW_POS} areas for the sunroof glass and its sunshade, after FX11. */
    static final int ZONE_SUNROOF = 4;
    static final int ZONE_SUNSHADE = 65544;
    /** {@link #DOOR} area of the tailgate, as in GInputBridge and FX11. */
    static final int ZONE_TRUNK = 536870912;
    /** Reading light zones FX11 switches together; it reads the state from the passenger's. */
    static final int[] READING_LIGHT_ZONES = {1, 4, 16, 64};

    static final int POWER = 268501248;
    static final int AUTO = 268501504;
    static final int AC = 268501760;
    static final int AC_MAX = 268502016;
    static final int FAN_SPEED = 268566784;
    static final int AUTO_FAN_SETTING = 268567040;
    static final int CIRCULATION = 268632320;
    static final int DEFROST_FRONT_MAX = 268698112;
    static final int DEFROST_REAR = 268698368;
    static final int SEAT_VENTILATION = 268763392;
    static final int SEAT_HEATING = 268763648;
    static final int TEMP = 268828928;
    static final int TEMP_MAX = 268829184;
    static final int TEMP_MIN = 268829440;
    static final int TEMP_DUAL = 268829952;
    static final int BLOWING_MODE = 268894464;
    static final int ECO = 268960000;
    static final int IONIZER = 268961024;
    static final int STEERING_WHEEL_HEAT = 269025536;

    /** Window, sunroof and sunshade position, a float percentage open (0 = closed). */
    static final int WINDOW_POS = 553845504;
    /** Sunroof airing (tilt), 1 = on; FX11 sets it in {@link #ZONE_SUNROOF} and globally. */
    static final int SUNROOF_TILT = 553845760;

    /** Body functions from GInputBridge's launcher tiles; 1 = open, folded, on. */
    static final int DOOR = 553779456;
    static final int FOLD_REAR_MIRROR = 554041600;
    static final int READING_LIGHT = 553980672;
    static final int WINDSCREEN_SERVICE_POSITION = 537657600;
    /** ESC sport mode: 0 keeps traction control («антибукс») on, 1 switches it off. */
    static final int ESC_SPORT_MODE = 537002752;
    static final int EXTERIOR_LIGHT = 537136640;

    /**
     * Whether every zone of the property holds its own value, so a zoned read never falls back
     * to the global one: that describes another glass, door or light.
     */
    static boolean hasZoneOnlyValue(int id) {
        return id == WINDOW_POS || id == DOOR || id == READING_LIGHT
                || id == WINDSCREEN_SERVICE_POSITION;
    }

    /**
     * Whether the property moves a part for longer than a confirmation takes: the same target
     * sent again while the part moves may stop or reverse it.
     */
    static boolean movesPart(int id) {
        return id == WINDOW_POS || id == DOOR || id == FOLD_REAR_MIRROR;
    }

    static final int WINDSHIELD_HEAT_ATLAS = 269027328;
    static final int WINDSHIELD_HEAT_PREFACE = 269753088;
    static final int WINDSHIELD_HEAT_CITYRAY = 269755136;

    static final int SENSOR_TEMPERATURE_AMBIENT = 1051392;
    static final int SENSOR_TEMPERATURE_INDOOR = 1051648;
    /** Fuel level 0–100; {@link Fuel} turns it into liters. */
    static final int SENSOR_FUEL_PERCENTAGE = 4211968;
    /** Range on fuel, km, as in AtlasAppWidget; 1 and below mean the car has no estimate. */
    static final int SENSOR_RANGE_FUEL = 1054720;

    static final int CIRCULATION_INNER = 268632321;
    static final int CIRCULATION_OUTSIDE = 268632322;

    static final int FAN_SPEED_OFF = 0;
    static final int FAN_SPEED_LEVEL_1 = 268566785;
    static final int FAN_SPEED_LEVEL_COUNT = 9;
    static final int FAN_SPEED_AUTO = 268566794;

    static final int AUTO_FAN_SILENT = 268567041;
    static final int AUTO_FAN_NORMAL = 268567042;
    static final int AUTO_FAN_HIGH = 268567043;
    static final int AUTO_FAN_QUIETER = 268567044;
    static final int AUTO_FAN_HIGHER = 268567045;

    static final int BLOWING_FACE = 268894465;
    static final int BLOWING_LEG = 268894466;
    static final int BLOWING_FACE_AND_LEG = 268894467;
    static final int BLOWING_WINDOW = 268894468;
    static final int BLOWING_FACE_AND_WINDOW = 268894469;
    static final int BLOWING_LEG_AND_WINDOW = 268894470;
    static final int BLOWING_ALL = 268894471;
    /** Reported instead of a direction while AUTO is on. */
    static final int BLOWING_AUTO = 268894472;

    static final int[] SEAT_HEAT_LEVELS = {0, 268763649, 268763650, 268763651};
    static final int[] SEAT_VENT_LEVELS = {0, 268763393, 268763394, 268763395};
    static final int[] STEERING_HEAT_LEVELS = {0, 269025537, 269025538, 269025539};
    /** Headlights: off, parking lights, low beam, automatic. */
    static final int[] EXTERIOR_LIGHT_LEVELS = {0, 537136641, 537136642, 537136643};

    /**
     * Sunroof stops: closed, airing, 20 %, fully open. Airing is sent as {@link #SUNROOF_TILT};
     * 8 % is the position FX11 expects it to report.
     */
    static final int[] SUNROOF_POSITIONS = {0, 8, 20, 100};
    /** Sunshade stops: closed, 20 %, 50 %, fully open. */
    static final int[] SUNSHADE_POSITIONS = {0, 20, 50, 100};

    static final float DEFAULT_TEMP_MIN = 16f;
    static final float DEFAULT_TEMP_MAX = 28f;
    static final float TEMP_STEP = 0.5f;

    private Hvac() {
    }
}
