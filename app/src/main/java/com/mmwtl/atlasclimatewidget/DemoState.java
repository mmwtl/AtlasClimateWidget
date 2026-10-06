package com.mmwtl.atlasclimatewidget;

import java.util.HashMap;
import java.util.Map;

/** Sample values for the settings preview while the bridge does not answer. */
final class DemoState implements ClimateState {
    static final DemoState INSTANCE = new DemoState();

    private final Map<String, Double> values = new HashMap<>();

    private DemoState() {
        put(Hvac.TEMP, Hvac.ZONE_DRIVER, 21.5);
        put(Hvac.TEMP, Hvac.ZONE_PASSENGER, 23);
        put(Hvac.FAN_SPEED, Hvac.ZONE_ROW_1_ALL, Hvac.FAN_SPEED_LEVEL_1 + 2);
        put(Hvac.AUTO_FAN_SETTING, Hvac.ZONE_ROW_1_ALL, 0);
        put(Hvac.SEAT_HEATING, Hvac.ZONE_DRIVER, Hvac.SEAT_HEAT_LEVELS[2]);
        put(Hvac.SEAT_HEATING, Hvac.ZONE_PASSENGER, 0);
        put(Hvac.SEAT_HEATING, Hvac.ZONE_REAR_LEFT, 0);
        put(Hvac.SEAT_HEATING, Hvac.ZONE_REAR_RIGHT, 0);
        put(Hvac.SEAT_VENTILATION, Hvac.ZONE_DRIVER, 0);
        put(Hvac.SEAT_VENTILATION, Hvac.ZONE_PASSENGER, Hvac.SEAT_VENT_LEVELS[1]);
        put(Hvac.STEERING_WHEEL_HEAT, Gib.AREA_GLOBAL, Hvac.STEERING_HEAT_LEVELS[1]);
        put(Hvac.WINDOW_POS, Hvac.ZONE_SUNROOF, 0);
        put(Hvac.WINDOW_POS, Hvac.ZONE_SUNSHADE, 50);
        put(Hvac.SUNROOF_TILT, Hvac.ZONE_SUNROOF, 0);
        put(Hvac.WINDSHIELD_HEAT_ATLAS, Gib.AREA_GLOBAL, 1);
        put(Hvac.WINDSHIELD_HEAT_PREFACE, Gib.AREA_GLOBAL, 1);
        put(Hvac.WINDSHIELD_HEAT_CITYRAY, Gib.AREA_GLOBAL, 1);
        put(Hvac.DEFROST_FRONT_MAX, Gib.AREA_GLOBAL, 0);
        put(Hvac.DEFROST_REAR, Gib.AREA_GLOBAL, 0);
        put(Hvac.CIRCULATION, Gib.AREA_GLOBAL, Hvac.CIRCULATION_OUTSIDE);
        put(Hvac.AC, Gib.AREA_GLOBAL, 1);
        put(Hvac.AC_MAX, Gib.AREA_GLOBAL, 0);
        put(Hvac.AUTO, Hvac.ZONE_DRIVER, 1);
        put(Hvac.POWER, Gib.AREA_GLOBAL, 1);
        put(Hvac.BLOWING_MODE, Hvac.ZONE_DRIVER, Hvac.BLOWING_FACE_AND_LEG);
        put(Hvac.TEMP_DUAL, Gib.AREA_GLOBAL, 0);
        put(Hvac.ECO, Gib.AREA_GLOBAL, 0);
        put(Hvac.IONIZER, Gib.AREA_GLOBAL, 0);
    }

    private void put(int id, int area, double value) {
        values.put(ClimateStore.propertyKey(id, area), value);
    }

    @Override
    public Double property(int id, int area) {
        Double value = values.get(ClimateStore.propertyKey(id, area));
        return value != null ? value : values.get(ClimateStore.propertyKey(id, Gib.AREA_GLOBAL));
    }

    @Override
    public Double sensor(int id) {
        if (id == Hvac.SENSOR_TEMPERATURE_INDOOR) {
            return 24d;
        }
        if (id == Hvac.SENSOR_FUEL_PERCENTAGE) {
            return 56d;
        }
        return id == Hvac.SENSOR_TEMPERATURE_AMBIENT ? 13d : null;
    }

    @Override
    public boolean isConnected() {
        return true;
    }
}
