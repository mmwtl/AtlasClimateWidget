package com.mmwtl.atlasclimatewidget;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Car properties and sensors the configured widgets need. */
final class WatchList {
    static final class Key {
        final int id;
        final int area;
        final boolean isFloat;

        Key(int id, int area, boolean isFloat) {
            this.id = id;
            this.area = area;
            this.isFloat = isFloat;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key)) {
                return false;
            }
            Key key = (Key) other;
            return id == key.id && area == key.area && isFloat == key.isFloat;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, area, isFloat);
        }
    }

    final Set<Key> properties = new LinkedHashSet<>();
    final Set<Integer> sensors = new LinkedHashSet<>();

    static WatchList of(Collection<WidgetConfig> configs, CarModel model) {
        WatchList list = new WatchList();
        for (WidgetConfig config : configs) {
            list.add(config, model);
        }
        return list;
    }

    private void add(WidgetConfig config, CarModel model) {
        if (config.isEnabled(WidgetConfig.Block.TEMPERATURE) && config.temperatureBar) {
            property(Hvac.TEMP, Hvac.ZONE_DRIVER, true);
            if (config.temperatureDual) {
                property(Hvac.TEMP, Hvac.ZONE_PASSENGER, true);
            }
            property(Hvac.TEMP_MIN, Hvac.ZONE_DRIVER, true);
            property(Hvac.TEMP_MAX, Hvac.ZONE_DRIVER, true);
        }
        if (config.isEnabled(WidgetConfig.Block.TEMPERATURE) && config.temperatureHeader) {
            sensors.add(Hvac.SENSOR_TEMPERATURE_INDOOR);
            sensors.add(Hvac.SENSOR_TEMPERATURE_AMBIENT);
        }
        if (config.isEnabled(WidgetConfig.Block.FAN)) {
            property(Hvac.FAN_SPEED, Hvac.ZONE_ROW_1_ALL, false);
            property(Hvac.AUTO_FAN_SETTING, Hvac.ZONE_ROW_1_ALL, false);
        }
        if (config.isEnabled(WidgetConfig.Block.TILES)) {
            for (ClimateFunction function : config.functions) {
                if (function.hasProperty()) {
                    property(function.propertyId(model), function.area, false);
                }
                if (function.kind == ClimateFunction.Kind.BLOW) {
                    // The bridge forwards a change only for the exact area it was set in, and
                    // the car's own climate screen sets the direction outside the driver zone.
                    // These subscriptions only wake the resync that reads the driver zone.
                    property(Hvac.BLOWING_MODE, Hvac.ZONE_ROW_1_ALL, false);
                    property(Hvac.BLOWING_MODE, Hvac.ZONE_PASSENGER, false);
                }
            }
        }
    }

    /** Adds the zoned key and the global fallback that {@link ClimateStore} reads. */
    private void property(int id, int area, boolean isFloat) {
        properties.add(new Key(id, area, isFloat));
        if (area != Gib.AREA_GLOBAL) {
            properties.add(new Key(id, Gib.AREA_GLOBAL, isFloat));
        }
    }
}
