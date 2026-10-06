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

    static final int WINDSHIELD_HEAT_ATLAS = 269027328;
    static final int WINDSHIELD_HEAT_PREFACE = 269753088;
    static final int WINDSHIELD_HEAT_CITYRAY = 269755136;

    static final int SENSOR_TEMPERATURE_AMBIENT = 1051392;
    static final int SENSOR_TEMPERATURE_INDOOR = 1051648;
    /** Fuel level 0–100; {@link Fuel} turns it into liters. */
    static final int SENSOR_FUEL_PERCENTAGE = 4211968;

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

    /** Sunroof and sunshade stops: closed, 20 %, 50 %, fully open. */
    static final int[] ROOF_POSITIONS = {0, 20, 50, 100};

    static final float DEFAULT_TEMP_MIN = 16f;
    static final float DEFAULT_TEMP_MAX = 28f;
    static final float TEMP_STEP = 0.5f;

    private Hvac() {
    }
}
