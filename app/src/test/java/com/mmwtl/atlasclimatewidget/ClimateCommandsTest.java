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

    @Test public void sunroofStepsThroughStopsAsFloats() {
        ClimateStore store = store(0);
        store.putProperty(Hvac.WINDOW_POS, Hvac.ZONE_SUNROOF, 20, 0);
        store.putProperty(Hvac.WINDOW_POS, Hvac.ZONE_SUNSHADE, 100, 0);
        ClimateState state = store.snapshot(0);
        List<ClimateCommands.Command> commands = ClimateCommands.press(ClimateFunction.SUNROOF,
                state, CarModel.ATLAS, false);
        assertEquals(1, commands.size());
        assertEquals(ClimateCommands.Command.Type.FLOAT, commands.get(0).type);
        assertEquals(Hvac.ZONE_SUNROOF, commands.get(0).area);
        assertEquals(100d, commands.get(0).value, 0d);
        assertTilt(ClimateCommands.press(ClimateFunction.SUNROOF, state, CarModel.ATLAS, true),
                1);
    }

    @Test public void sunroofAiringIsSentAsTiltAndClosedByIt() {
        ClimateStore store = store(0);
        store.putProperty(Hvac.WINDOW_POS, Hvac.ZONE_SUNROOF, 0, 0);
        store.putProperty(Hvac.WINDOW_POS, Hvac.ZONE_SUNSHADE, 100, 0);
        List<ClimateCommands.Command> commands = ClimateCommands.press(ClimateFunction.SUNROOF,
                store.snapshot(0), CarModel.ATLAS, false);
        assertTilt(commands, 1);

        store.putProperty(Hvac.SUNROOF_TILT, Hvac.ZONE_SUNROOF, 1, 0);
        ClimateState airing = store.snapshot(0);
        ClimateCommands.TileState tile = ClimateCommands.tileState(ClimateFunction.SUNROOF,
                airing, CarModel.ATLAS);
        assertTrue(tile.active);
        assertEquals(ClimateCommands.SUNROOF_AIRING, tile.level);
        commands = ClimateCommands.press(ClimateFunction.SUNROOF, airing, CarModel.ATLAS, false);
        assertEquals(1, commands.size());
        assertEquals(Hvac.WINDOW_POS, commands.get(0).id);
        assertEquals(20d, commands.get(0).value, 0d);
        assertTilt(ClimateCommands.press(ClimateFunction.SUNROOF, airing, CarModel.ATLAS, true),
                0);
    }

    @Test public void closingFullyOpenSunroofSetsPositionOnly() {
        ClimateStore store = store(0);
        store.putProperty(Hvac.WINDOW_POS, Hvac.ZONE_SUNROOF, 100, 0);
        store.putProperty(Hvac.SUNROOF_TILT, Hvac.ZONE_SUNROOF, 0, 0);
        List<ClimateCommands.Command> commands = ClimateCommands.press(ClimateFunction.SUNROOF,
                store.snapshot(0), CarModel.ATLAS, false);
        assertEquals(1, commands.size());
        assertEquals(Hvac.WINDOW_POS, commands.get(0).id);
        assertEquals(0d, commands.get(0).value, 0d);
    }

    private static void assertTilt(List<ClimateCommands.Command> commands, int value) {
        assertEquals(2, commands.size());
        for (ClimateCommands.Command command : commands) {
            assertEquals(Hvac.SUNROOF_TILT, command.id);
            assertEquals(ClimateCommands.Command.Type.INT, command.type);
            assertEquals(value, (int) command.value);
        }
        assertEquals(Hvac.ZONE_SUNROOF, commands.get(0).area);
        assertEquals(Gib.AREA_GLOBAL, commands.get(1).area);
    }

    @Test public void positionIndexCountsAnyOpeningAndNearStops() {
        assertEquals(0, ClimateCommands.positionIndex(ClimateFunction.SUNROOF, 1));
        assertEquals(1, ClimateCommands.positionIndex(ClimateFunction.SUNROOF, 5));
        assertEquals(1, ClimateCommands.positionIndex(ClimateFunction.SUNROOF, 8));
        assertEquals(2, ClimateCommands.positionIndex(ClimateFunction.SUNROOF, 19));
        assertEquals(2, ClimateCommands.positionIndex(ClimateFunction.SUNROOF, 80));
        assertEquals(3, ClimateCommands.positionIndex(ClimateFunction.SUNROOF, 100));
        assertEquals(2, ClimateCommands.positionIndex(ClimateFunction.SUNSHADE, 49));
    }

    @Test public void sunshadeCannotCloseUnderOpenSunroof() {
        ClimateStore store = store(0);
        store.putProperty(Hvac.WINDOW_POS, Hvac.ZONE_SUNROOF, 50, 0);
        store.putProperty(Hvac.WINDOW_POS, Hvac.ZONE_SUNSHADE, 100, 0);
        ClimateState state = store.snapshot(0);
        assertEquals(20d, ClimateCommands.press(ClimateFunction.SUNSHADE, state,
                CarModel.ATLAS, false).get(0).value, 0d);
        ClimateStore lowest = store(0);
        lowest.putProperty(Hvac.WINDOW_POS, Hvac.ZONE_SUNROOF, 50, 0);
        lowest.putProperty(Hvac.WINDOW_POS, Hvac.ZONE_SUNSHADE, 20, 0);
        assertEquals(100d, ClimateCommands.press(ClimateFunction.SUNSHADE, lowest.snapshot(0),
                CarModel.ATLAS, true).get(0).value, 0d);

        ClimateStore closed = store(0);
        closed.putProperty(Hvac.WINDOW_POS, Hvac.ZONE_SUNROOF, 0, 0);
        closed.putProperty(Hvac.WINDOW_POS, Hvac.ZONE_SUNSHADE, 100, 0);
        assertEquals(0d, ClimateCommands.press(ClimateFunction.SUNSHADE, closed.snapshot(0),
                CarModel.ATLAS, false).get(0).value, 0d);
    }

    @Test public void openingSunroofOpensClosedSunshadeFirst() {
        ClimateStore store = store(0);
        store.putProperty(Hvac.WINDOW_POS, Hvac.ZONE_SUNROOF, 0, 0);
        store.putProperty(Hvac.WINDOW_POS, Hvac.ZONE_SUNSHADE, 0, 0);
        List<ClimateCommands.Command> commands = ClimateCommands.press(ClimateFunction.SUNROOF,
                store.snapshot(0), CarModel.ATLAS, false);
        assertEquals(3, commands.size());
        assertEquals(Hvac.ZONE_SUNSHADE, commands.get(0).area);
        assertEquals(20d, commands.get(0).value, 0d);
        assertTilt(commands.subList(1, 3), 1);

        store.putProperty(Hvac.WINDOW_POS, Hvac.ZONE_SUNROOF, 20, 0);
        commands = ClimateCommands.press(ClimateFunction.SUNROOF, store.snapshot(0),
                CarModel.ATLAS, false);
        assertEquals(100d, commands.get(0).value, 0d);
        assertEquals(100d, commands.get(1).value, 0d);
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

    @Test public void directionsRestInAutoAndPresetsOutsideIt() {
        ClimateStore store = store(0);
        store.putProperty(Hvac.BLOWING_MODE, Hvac.ZONE_DRIVER, Hvac.BLOWING_FACE_AND_LEG, 0);
        store.putProperty(Hvac.AUTO_FAN_SETTING, Hvac.ZONE_ROW_1_ALL, Hvac.AUTO_FAN_NORMAL, 0);
        ClimateState unknown = store.snapshot(0);
        assertFalse(ClimateCommands.tileState(ClimateFunction.BLOW_LEGS, unknown,
                CarModel.ATLAS).dormant);
        assertFalse(ClimateCommands.tileState(ClimateFunction.FAN_NORMAL, unknown,
                CarModel.ATLAS).dormant);

        store.putProperty(Hvac.AUTO, Hvac.ZONE_DRIVER, 1, 0);
        ClimateState auto = store.snapshot(0);
        ClimateCommands.TileState legs = ClimateCommands.tileState(ClimateFunction.BLOW_LEGS,
                auto, CarModel.ATLAS);
        assertTrue(legs.dormant);
        assertTrue(legs.active);
        // The Atlas reports no direction at all in AUTO.
        store.putProperty(Hvac.BLOWING_MODE, Hvac.ZONE_DRIVER, Hvac.BLOWING_AUTO, 0);
        assertFalse(ClimateCommands.tileState(ClimateFunction.BLOW_LEGS, store.snapshot(0),
                CarModel.ATLAS).active);
        store.putProperty(Hvac.BLOWING_MODE, Hvac.ZONE_DRIVER, Hvac.BLOWING_FACE_AND_LEG, 0);
        assertFalse(ClimateCommands.tileState(ClimateFunction.FAN_NORMAL, auto,
                CarModel.ATLAS).dormant);
        assertTrue(ClimateCommands.fanState(auto).auto);
        // In AUTO a tap picks only the tapped direction instead of toggling the kept ones.
        assertEquals(Hvac.BLOWING_LEG, (int) ClimateCommands.press(ClimateFunction.BLOW_LEGS,
                auto, CarModel.ATLAS, false).get(0).value);

        store.putProperty(Hvac.AUTO, Hvac.ZONE_DRIVER, 0, 0);
        ClimateState manual = store.snapshot(0);
        assertFalse(ClimateCommands.tileState(ClimateFunction.BLOW_LEGS, manual,
                CarModel.ATLAS).dormant);
        assertTrue(ClimateCommands.tileState(ClimateFunction.FAN_NORMAL, manual,
                CarModel.ATLAS).dormant);
    }

    @Test public void presetInManualModeTurnsAutoOnFirst() {
        ClimateStore store = store(0);
        store.putProperty(Hvac.AUTO, Hvac.ZONE_DRIVER, 0, 0);
        List<ClimateCommands.Command> commands = ClimateCommands.setFanPreset(
                store.snapshot(0), 2, 3);
        assertEquals(2, commands.size());
        assertEquals(Hvac.AUTO, commands.get(0).id);
        assertEquals(1, (int) commands.get(0).value);
        assertEquals(0L, commands.get(0).delayMs);
        assertEquals(Hvac.AUTO_FAN_HIGHER, (int) commands.get(1).value);
        assertTrue(commands.get(1).delayMs > 0L);
        assertEquals(2, ClimateCommands.press(ClimateFunction.FAN_SOFT, store.snapshot(0),
                CarModel.ATLAS, false).size());

        store.putProperty(Hvac.AUTO, Hvac.ZONE_DRIVER, 1, 0);
        assertEquals(1, ClimateCommands.setFanPreset(store.snapshot(0), 2, 3).size());
    }

    @Test public void fanSpeedInAutoLeavesAutoFirst() {
        ClimateStore store = store(0);
        store.putProperty(Hvac.AUTO, Hvac.ZONE_DRIVER, 1, 0);
        store.putProperty(Hvac.AUTO_FAN_SETTING, Hvac.ZONE_ROW_1_ALL, Hvac.AUTO_FAN_NORMAL, 0);
        List<ClimateCommands.Command> commands = ClimateCommands.setFan(store.snapshot(0), 4);
        assertEquals(2, commands.size());
        assertEquals(Hvac.AUTO, commands.get(0).id);
        assertEquals(0, (int) commands.get(0).value);
        assertEquals(Hvac.FAN_SPEED_LEVEL_1 + 3, (int) commands.get(1).value);
        assertTrue(commands.get(1).delayMs > 0L);

        // The kept profile does not mean AUTO once the car reports AUTO off.
        store.putProperty(Hvac.AUTO, Hvac.ZONE_DRIVER, 0, 0);
        store.putProperty(Hvac.FAN_SPEED, Hvac.ZONE_ROW_1_ALL, Hvac.FAN_SPEED_LEVEL_1 + 3, 0);
        assertFalse(ClimateCommands.fanState(store.snapshot(0)).auto);
        assertEquals(1, ClimateCommands.setFan(store.snapshot(0), 5).size());
    }
}
