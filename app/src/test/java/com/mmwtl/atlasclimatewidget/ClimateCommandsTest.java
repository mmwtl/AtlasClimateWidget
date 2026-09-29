package com.mmwtl.atlasclimatewidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public final class ClimateCommandsTest {
    private static ClimateStore store(long now) {
        return new ClimateStore();
    }

    @Test public void togglesFromKnownState() {
        ClimateStore store = store(0);
        store.putProperty(Hvac.AC, Gib.AREA_GLOBAL, 1, 0);
        List<ClimateCommands.Command> commands = ClimateCommands.press(ClimateFunction.AC,
                store.snapshot(0), CarModel.ATLAS, false);
        assertEquals(1, commands.size());
        assertEquals(0d, commands.get(0).value, 0d);
    }

    @Test public void recirculationUsesEncodedValues() {
        ClimateStore store = store(0);
        store.putProperty(Hvac.CIRCULATION, Gib.AREA_GLOBAL, Hvac.CIRCULATION_OUTSIDE, 0);
        ClimateCommands.Command command = ClimateCommands.press(ClimateFunction.RECIRCULATION,
                store.snapshot(0), CarModel.ATLAS, false).get(0);
        assertEquals(Hvac.CIRCULATION_INNER, (int) command.value);
    }

    @Test public void cyclesSeatHeatForwardAndFromMax() {
        ClimateStore store = store(0);
        store.putProperty(Hvac.SEAT_HEATING, Hvac.ZONE_DRIVER, Hvac.SEAT_HEAT_LEVELS[3], 0);
        ClimateState state = store.snapshot(0);
        assertEquals(0, (int) ClimateCommands.press(ClimateFunction.DRIVER_HEAT, state,
                CarModel.ATLAS, false).get(0).value);
        assertEquals(Hvac.SEAT_HEAT_LEVELS[2], (int) ClimateCommands.press(
                ClimateFunction.DRIVER_HEAT, state, CarModel.ATLAS, true).get(0).value);
        ClimateStore off = store(0);
        off.putProperty(Hvac.SEAT_HEATING, Hvac.ZONE_DRIVER, 0, 0);
        assertEquals(Hvac.SEAT_HEAT_LEVELS[3], (int) ClimateCommands.press(
                ClimateFunction.DRIVER_HEAT, off.snapshot(0), CarModel.ATLAS, true).get(0).value);
    }

    @Test public void levelIndexAcceptsShortAndAliasValues() {
        assertEquals(2, ClimateCommands.levelIndex(ClimateFunction.DRIVER_HEAT, 2));
        assertEquals(3, ClimateCommands.levelIndex(ClimateFunction.DRIVER_HEAT, 0x1005020F));
        assertEquals(1, ClimateCommands.levelIndex(ClimateFunction.WHEEL_HEAT,
                Hvac.STEERING_HEAT_LEVELS[1]));
        assertEquals(0, ClimateCommands.levelIndex(ClimateFunction.WHEEL_HEAT, 777));
    }

    @Test public void blowModesCombineAndNeverTurnEverythingOff() {
        ClimateStore store = store(0);
        store.putProperty(Hvac.BLOWING_MODE, Hvac.ZONE_DRIVER, Hvac.BLOWING_FACE, 0);
        ClimateState state = store.snapshot(0);
        assertEquals(Hvac.BLOWING_FACE_AND_LEG, (int) ClimateCommands.press(
                ClimateFunction.BLOW_LEGS, state, CarModel.ATLAS, false).get(0).value);
        assertTrue(ClimateCommands.press(ClimateFunction.BLOW_FACE, state, CarModel.ATLAS,
                false).isEmpty());
        for (int bits = 1; bits <= 7; bits++) {
            assertEquals(bits, ClimateCommands.blowBits(ClimateCommands.blowMode(bits)));
        }
    }

    @Test public void windshieldIdDependsOnModel() {
        assertEquals(Hvac.WINDSHIELD_HEAT_ATLAS,
                ClimateFunction.WINDSHIELD_HEAT.propertyId(CarModel.ATLAS));
        assertEquals(Hvac.WINDSHIELD_HEAT_PREFACE,
                ClimateFunction.WINDSHIELD_HEAT.propertyId(CarModel.PREFACE));
    }

    @Test public void temperatureStepsClampAndSnap() {
        ClimateStore store = store(0);
        store.putProperty(Hvac.TEMP, Hvac.ZONE_DRIVER, 21.5, 0);
        ClimateState state = store.snapshot(0);
        assertEquals(22f, (float) ClimateCommands.stepTemperature(state, Hvac.ZONE_DRIVER, 1,
                0.5f).get(0).value, 0.001f);
        assertEquals(22f, (float) ClimateCommands.stepTemperature(state, Hvac.ZONE_DRIVER, 1,
                1f).get(0).value, 0.001f);
        assertEquals(21f, (float) ClimateCommands.stepTemperature(state, Hvac.ZONE_DRIVER, -1,
                1f).get(0).value, 0.001f);
        ClimateStore max = store(0);
        max.putProperty(Hvac.TEMP, Hvac.ZONE_DRIVER, 28, 0);
        assertTrue(ClimateCommands.stepTemperature(max.snapshot(0), Hvac.ZONE_DRIVER, 1, 0.5f)
                .isEmpty());
        assertEquals(16f, (float) ClimateCommands.setTemperature(state, Hvac.ZONE_DRIVER, 3f)
                .get(0).value, 0.001f);
    }

    @Test public void temperatureRangeUsesCarLimitsWhenValid() {
        ClimateStore store = store(0);
        store.putProperty(Hvac.TEMP_MIN, Gib.AREA_GLOBAL, 15.5, 0);
        store.putProperty(Hvac.TEMP_MAX, Gib.AREA_GLOBAL, 28.5, 0);
        ClimateCommands.TempRange range = ClimateCommands.tempRange(store.snapshot(0));
        assertEquals(15.5f, range.min, 0f);
        assertEquals(27, range.steps());
        assertEquals(ClimateCommands.TempRange.class, range.getClass());
        assertEquals(25, ClimateCommands.tempRange(ClimateState.EMPTY).steps());
    }

    @Test public void fanStepsManualAndAutoProfiles() {
        ClimateStore store = store(0);
        store.putProperty(Hvac.FAN_SPEED, Hvac.ZONE_ROW_1_ALL, Hvac.FAN_SPEED_LEVEL_1 + 8, 0);
        store.putProperty(Hvac.AUTO_FAN_SETTING, Hvac.ZONE_ROW_1_ALL, 0, 0);
        assertTrue(ClimateCommands.stepFan(store.snapshot(0), CarModel.ATLAS, 1, 3).isEmpty());
        assertEquals(Hvac.FAN_SPEED_LEVEL_1 + 7, (int) ClimateCommands.stepFan(
                store.snapshot(0), CarModel.ATLAS, -1, 3).get(0).value);

        ClimateStore auto = store(0);
        auto.putProperty(Hvac.AUTO_FAN_SETTING, Hvac.ZONE_ROW_1_ALL, Hvac.AUTO_FAN_NORMAL, 0);
        ClimateCommands.FanState fan = ClimateCommands.fanState(auto.snapshot(0));
        assertTrue(fan.auto);
        assertEquals(Hvac.AUTO_FAN_HIGHER, (int) ClimateCommands.stepFan(auto.snapshot(0),
                CarModel.ATLAS, 1, 3).get(0).value);
        assertEquals(Hvac.AUTO_FAN_HIGH, (int) ClimateCommands.stepFan(auto.snapshot(0),
                CarModel.ATLAS, 1, 5).get(0).value);
        assertEquals(ClimateCommands.FAN_BLOWER_UP, (int) ClimateCommands.stepFan(
                auto.snapshot(0), CarModel.PREFACE, 1, 3).get(0).value);
    }

    @Test public void fanPresetsMapToAutoFanProfiles() {
        assertEquals(Hvac.AUTO_FAN_QUIETER,
                (int) ClimateCommands.setFanPreset(0, 3).get(0).value);
        assertEquals(Hvac.AUTO_FAN_HIGHER,
                (int) ClimateCommands.setFanPreset(9, 3).get(0).value);
        assertEquals(Hvac.AUTO_FAN_SILENT,
                (int) ClimateCommands.setFanPreset(1, 5).get(0).value);
        assertEquals(Hvac.AUTO_FAN_HIGH,
                (int) ClimateCommands.setFanPreset(3, 5).get(0).value);
        ClimateStore store = new ClimateStore();
        store.putProperty(Hvac.AUTO_FAN_SETTING, Hvac.ZONE_ROW_1_ALL, Hvac.AUTO_FAN_NORMAL, 0);
        assertEquals(1, ClimateCommands.fanPreset(store.snapshot(0), 3));
        assertEquals(2, ClimateCommands.fanPreset(store.snapshot(0), 5));
        assertEquals(-1, ClimateCommands.fanPreset(ClimateState.EMPTY, 3));
    }

    @Test public void unknownStateIsReported() {
        ClimateCommands.TileState state = ClimateCommands.tileState(ClimateFunction.AC,
                ClimateState.EMPTY, CarModel.ATLAS);
        assertFalse(state.known);
        assertTrue(ClimateCommands.tileState(ClimateFunction.ME_HOT, ClimateState.EMPTY,
                CarModel.ATLAS).known);
    }
}
