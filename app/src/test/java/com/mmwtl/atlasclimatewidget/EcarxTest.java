package com.mmwtl.atlasclimatewidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.Handler;
import android.os.Looper;

import com.ecarx.xui.adaptapi.FunctionStatus;
import com.ecarx.xui.adaptapi.car.Car;
import com.ecarx.xui.adaptapi.car.CarImpl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public final class EcarxTest {
    private static final int SEAT = Hvac.SEAT_HEATING;
    private static final int TEMP = Hvac.TEMP;

    private final List<String> answers = new ArrayList<>();
    private final CarLink.Sink sink = new CarLink.Sink() {
        @Override
        public void onProperty(int id, int area, double value) {
            answers.add(id + "@" + area + "=" + value);
        }

        @Override
        public void onSensor(int id, float value) {
            answers.add("s" + id + "=" + value);
        }
    };
    private Car car;

    @Before
    public void setUp() {
        Car.createError = null;
        CarImpl.createError = null;
        CarImpl.creations = 0;
        car = new Car();
        Car.instance = car;
    }

    @After
    public void tearDown() {
        Car.instance = null;
        Car.createError = null;
        CarImpl.createError = null;
        CarImpl.creations = 0;
    }

    private Ecarx connect() {
        Ecarx link = Ecarx.connect(RuntimeEnvironment.getApplication(),
                new Handler(Looper.getMainLooper()), sink);
        assertNotNull(link);
        return link;
    }


    private void noLegacyWindshield() {
        car.support.put(Hvac.WINDSHIELD_HEAT_ATLAS, FunctionStatus.notavailable);
        car.support.put(Hvac.WINDSHIELD_HEAT_PREFACE, FunctionStatus.notavailable);
        car.support.put(Hvac.WINDSHIELD_HEAT_CITYRAY, FunctionStatus.notavailable);
    }

    @Test
    public void windshield2026MapsReadsWritesAndCallbacksToTheLogicalId() {
        noLegacyWindshield();
        int logical = Hvac.WINDSHIELD_HEAT_ATLAS;
        int actual = Hvac.WINDSHIELD_HEAT_2026;
        car.support.put(actual, FunctionStatus.notactive);
        car.values.put(actual + "@g", 0);
        Ecarx link = connect();
        link.requestProperty(logical, Gib.AREA_GLOBAL, false);
        link.listenProperty(logical, Gib.AREA_GLOBAL, false);
        link.setInt(logical, Gib.AREA_GLOBAL, 1);
        assertEquals(List.of(actual + "@g=1"), car.sets);
        assertTrue(car.watchers.containsKey(actual));
        assertFalse(car.watchers.containsKey(logical));
        car.values.put(actual + "@g", 1);
        car.watchers.get(actual).onFunctionValueChanged(actual, 0, 1);
        ShadowLooper.idleMainLooper();
        assertEquals(List.of(logical + "@" + Gib.AREA_GLOBAL + "=0.0",
                logical + "@" + Gib.AREA_GLOBAL + "=1.0"), answers);
        link.close();
        assertTrue(car.watchers.isEmpty());
    }

    @Test
    public void anotherLegacyWindshieldWinsOverOrdinaryDefrostWithoutModelSelection() {
        noLegacyWindshield();
        car.support.put(Hvac.WINDSHIELD_HEAT_CITYRAY, FunctionStatus.active);
        car.support.put(Hvac.WINDSHIELD_HEAT_2026, FunctionStatus.active);
        Ecarx link = connect();
        link.setInt(Hvac.WINDSHIELD_HEAT_ATLAS, Gib.AREA_GLOBAL, 1);
        assertEquals(List.of(Hvac.WINDSHIELD_HEAT_CITYRAY + "@g=1"), car.sets);
    }

    @Test
    public void unknownSupportDoesNotSelect2026AndIsRetriedLater() {
        int logical = Hvac.WINDSHIELD_HEAT_ATLAS;
        car.support.put(logical, FunctionStatus.error);
        car.support.put(Hvac.WINDSHIELD_HEAT_2026, FunctionStatus.active);
        Ecarx link = connect();
        link.setInt(logical, Gib.AREA_GLOBAL, 1);
        assertEquals(List.of(logical + "@g=1"), car.sets);
        noLegacyWindshield();
        link.setInt(logical, Gib.AREA_GLOBAL, 0);
        assertEquals(Hvac.WINDSHIELD_HEAT_2026 + "@g=0", car.sets.get(1));
    }

    @Test
    public void sunshadeUsesNewAreaForReadsWritesAndCallbackRereads() {
        int id = Hvac.WINDOW_POS;
        car.zonedSupport.put(id + "@" + Hvac.ZONE_SUNSHADE, FunctionStatus.notavailable);
        car.zonedSupport.put(id + "@8", FunctionStatus.notactive);
        car.values.put(id + "@8", 20f);
        car.values.put(id + "@4", 0f);
        Ecarx link = connect();
        link.requestProperty(id, Hvac.ZONE_SUNSHADE, true);
        link.requestProperty(id, Hvac.ZONE_SUNROOF, true);
        link.listenProperty(id, Hvac.ZONE_SUNSHADE, true);
        link.listenProperty(id, Hvac.ZONE_SUNROOF, true);
        link.setFloat(id, Hvac.ZONE_SUNSHADE, 50f);
        assertEquals(List.of(id + "@8=50.0"), car.sets);
        answers.clear();
        car.values.put(id + "@8", 50f);
        car.watchers.get(id).onCustomizeFunctionValueChanged(id, 8, 50f);
        ShadowLooper.idleMainLooper();
        assertTrue(answers.contains(id + "@" + Hvac.ZONE_SUNSHADE + "=50.0"));
        assertTrue(answers.contains(id + "@4=0.0"));
    }

    @Test
    public void sunshadeKeepsLegacyAreaWhenSupportedOrUnknown() {
        Ecarx link = connect();
        car.zonedSupport.put(Hvac.WINDOW_POS + "@8", FunctionStatus.active);
        link.setFloat(Hvac.WINDOW_POS, Hvac.ZONE_SUNSHADE, 20f);
        car.zonedSupport.put(Hvac.WINDOW_POS + "@" + Hvac.ZONE_SUNSHADE,
                FunctionStatus.notactive);
        link.setFloat(Hvac.WINDOW_POS, Hvac.ZONE_SUNSHADE, 50f);
        assertEquals(List.of(Hvac.WINDOW_POS + "@" + Hvac.ZONE_SUNSHADE + "=20.0",
                Hvac.WINDOW_POS + "@" + Hvac.ZONE_SUNSHADE + "=50.0"), car.sets);
    }

    @Test
    public void tailgateReadsSeparateStateButWritesDoorAndDoesNotInventUnknownState() {
        int logical = Hvac.DOOR;
        int actual = Hvac.TAILGATE_STATE;
        int zone = Hvac.ZONE_TRUNK;
        car.zonedSupport.put(actual + "@" + zone, FunctionStatus.active);
        car.values.put(actual + "@" + zone, Hvac.TAILGATE_CLOSED);
        Ecarx link = connect();
        link.requestProperty(logical, zone, false);
        link.listenProperty(logical, zone, false);
        link.setInt(logical, zone, 1);
        assertEquals(List.of(logical + "@" + zone + "=1"), car.sets);
        assertTrue(car.watchers.containsKey(actual));
        car.values.put(actual + "@" + zone, 738330118);
        car.watchers.get(actual).onFunctionValueChanged(actual, zone, 738330118);
        ShadowLooper.idleMainLooper();
        car.values.put(actual + "@" + zone, 738330113);
        link.requestProperty(logical, zone, false);
        link.setInt(logical, zone, 0);
        assertEquals(1, car.sets.size());
        assertEquals(List.of(logical + "@" + zone + "=0.0",
                logical + "@" + zone + "=1.0", logical + "@" + zone + "=-1.0"), answers);
    }

    @Test
    public void fanBlowerUsesSupportRatherThanModelAndUnknownKeepsTheDefault() {
        Ecarx link = connect();
        assertTrue(link.fanBlower(true));
        assertFalse(link.fanBlower(false));
        String key = ClimateCommands.FAN_BLOWER + "@8";
        car.zonedSupport.put(key, FunctionStatus.notactive);
        assertTrue(link.fanBlower(false));
        car.zonedSupport.put(key, FunctionStatus.notavailable);
        assertFalse(link.fanBlower(true));
        car.zonedSupport.put(key, FunctionStatus.error);
        assertTrue(link.fanBlower(true));
    }

    @Test
    public void threeAutoProfilesNormalizeBothDirectionsAndWatcherValues() {
        int id = Hvac.AUTO_FAN_SETTING;
        car.supportedValues.put(id + "@8", new int[]{id + 3, id + 1, id + 2});
        car.values.put(id + "@8", id + 1);
        Ecarx link = connect();
        link.requestProperty(id, 8, false);
        assertEquals(3, link.fanPresetCount());
        assertEquals(List.of(id + "@8=" + (double) Hvac.AUTO_FAN_QUIETER), answers);
        link.setInt(id, 8, Hvac.AUTO_FAN_HIGHER);
        assertEquals(List.of(id + "@8=" + (id + 3)), car.sets);
        link.listenProperty(id, 8, false);
        answers.clear();
        car.values.put(id + "@8", id + 3);
        car.watchers.get(id).onFunctionValueChanged(id, 8, id + 3);
        ShadowLooper.idleMainLooper();
        assertEquals(List.of(id + "@8=" + (double) Hvac.AUTO_FAN_HIGHER), answers);
    }

    @Test
    public void fiveAutoProfilesKeepTheirActualValues() {
        int id = Hvac.AUTO_FAN_SETTING;
        car.supportedValues.put(id + "@8", ClimateCommands.FAN_PRESETS_FIVE.clone());
        car.values.put(id + "@8", Hvac.AUTO_FAN_SILENT);
        Ecarx link = connect();
        link.requestProperty(id, 8, false);
        link.setInt(id, 8, Hvac.AUTO_FAN_HIGHER);
        assertEquals(5, link.fanPresetCount());
        assertEquals(List.of(id + "@8=" + (double) Hvac.AUTO_FAN_SILENT), answers);
        assertEquals(List.of(id + "@8=" + Hvac.AUTO_FAN_HIGHER), car.sets);
    }

    @Test
    public void unknownAutoProfilesAreNotGuessedOrCached() {
        int id = Hvac.AUTO_FAN_SETTING;
        Ecarx link = connect();
        link.setInt(id, 8, Hvac.AUTO_FAN_HIGHER);
        assertEquals(0, link.fanPresetCount());
        car.supportedValues.put(id + "@8", new int[]{id + 1, id + 2, id + 5});
        link.setInt(id, 8, Hvac.AUTO_FAN_QUIETER);
        assertEquals(3, link.fanPresetCount());
        assertEquals(List.of(id + "@8=" + Hvac.AUTO_FAN_HIGHER,
                id + "@8=" + Hvac.AUTO_FAN_SILENT), car.sets);
    }


    @Test
    public void movingTailgateUsesPauseInsteadOfReversing() {
        int zone = Hvac.ZONE_TRUNK;
        car.zonedSupport.put(Hvac.TAILGATE_STATE + "@" + zone, FunctionStatus.active);
        car.values.put(Hvac.TAILGATE_STATE + "@" + zone, 738330115);
        Ecarx link = connect();
        link.setInt(Hvac.DOOR, zone, 0);
        assertEquals(List.of(Hvac.DOOR + "@" + zone + "=" + Hvac.TAILGATE_PAUSE), car.sets);
    }

    @Test
    public void legacyTailgateKeepsItsReadWriteProperty() {
        int id = Hvac.DOOR;
        int zone = Hvac.ZONE_TRUNK;
        car.zonedSupport.put(Hvac.TAILGATE_STATE + "@" + zone, FunctionStatus.notavailable);
        car.values.put(id + "@" + zone, 1);
        Ecarx link = connect();
        link.requestProperty(id, zone, false);
        link.listenProperty(id, zone, false);
        link.setInt(id, zone, 0);
        assertEquals(List.of(id + "@" + zone + "=1.0"), answers);
        assertEquals(List.of(id + "@" + zone + "=0"), car.sets);
        assertTrue(car.watchers.containsKey(id));
    }

    @Test
    public void knownThreeProfilesLimitTheWidgetWithoutChangingTheSavedPreference()
            throws Exception {
        Prefs prefs = new Prefs(RuntimeEnvironment.getApplication());
        prefs.setFanPresets(Prefs.FanPresets.FIVE);
        java.lang.reflect.Field running = ClimateService.class.getDeclaredField("running");
        java.lang.reflect.Field active = ClimateService.class.getDeclaredField("directActive");
        java.lang.reflect.Field count = ClimateService.class.getDeclaredField("directFanPresetCount");
        running.setAccessible(true);
        active.setAccessible(true);
        count.setAccessible(true);
        boolean wasRunning = running.getBoolean(null);
        boolean wasActive = active.getBoolean(null);
        int wasCount = count.getInt(null);
        try {
            running.setBoolean(null, true);
            active.setBoolean(null, true);
            count.setInt(null, 3);
            assertEquals(3, prefs.resolved(new WidgetConfig()).fanPresetCount);
            assertEquals(Prefs.FanPresets.FIVE, prefs.fanPresets());
            active.setBoolean(null, false);
            assertEquals(5, prefs.resolved(new WidgetConfig()).fanPresetCount);
        } finally {
            running.setBoolean(null, wasRunning);
            active.setBoolean(null, wasActive);
            count.setInt(null, wasCount);
        }
    }

    @Test
    public void serviceLossRechecksThePreviouslySelectedWindshieldVariant() {
        noLegacyWindshield();
        int logical = Hvac.WINDSHIELD_HEAT_ATLAS;
        car.support.put(Hvac.WINDSHIELD_HEAT_2026, FunctionStatus.active);
        car.values.put(Hvac.WINDSHIELD_HEAT_2026 + "@g", 1);
        Ecarx link = connect();
        link.requestProperty(logical, Gib.AREA_GLOBAL, false);
        car.down = true;
        link.requestProperty(logical, Gib.AREA_GLOBAL, false);
        car.down = false;
        car.support.put(logical, FunctionStatus.notactive);
        link.setInt(logical, Gib.AREA_GLOBAL, 0);
        assertEquals(List.of(logical + "@g=0"), car.sets);
    }

    private void newSeatMemory() {
        car.zonedSupport.put(Hvac.SEAT_MEMORY_RESTORE + "@1", FunctionStatus.notavailable);
        car.zonedSupport.put(Hvac.SEAT_POSITION_SET + "@1", FunctionStatus.active);
        car.zonedSupport.put(Hvac.SEAT_POSITION_BUTTON + "@1", FunctionStatus.active);
    }

    @Test
    public void newSeatMemoryAlternatesSlotsAndSendsRestoreAfterSelection() {
        newSeatMemory();
        Ecarx link = connect();
        assertTrue(link.carFunction("SEAT_MEMORY"));
        assertTrue(link.carFunction("SEAT_MEMORY"));
        assertEquals(List.of(Hvac.SEAT_POSITION_SET + "@1=1",
                Hvac.SEAT_POSITION_BUTTON + "@1=2", Hvac.SEAT_POSITION_SET + "@1=2",
                Hvac.SEAT_POSITION_BUTTON + "@1=2"), car.sets);
    }

    @Test
    public void seatMemoryDoesNotRestoreIfSelectionFailedOrFallBackAfterPartialCommand() {
        newSeatMemory();
        car.rejectedFunction = Hvac.SEAT_POSITION_SET;
        Ecarx link = connect();
        assertTrue(link.carFunction("SEAT_MEMORY"));
        assertEquals(List.of(Hvac.SEAT_POSITION_SET + "@1=1"), car.sets);
        car.rejectedFunction = 0;
        link.carFunction("SEAT_MEMORY");
        assertEquals(Hvac.SEAT_POSITION_SET + "@1=1", car.sets.get(1));
    }

    @Test
    public void legacySeatMemoryUsesReportedSlotAndUnknownSupportKeepsBridgeAction() {
        Ecarx link = connect();
        assertFalse(link.carFunction("SEAT_MEMORY"));
        assertFalse(link.carFunction("CLIMATE_MENU"));
        assertTrue(car.sets.isEmpty());
        car.zonedSupport.put(Hvac.SEAT_MEMORY_RESTORE + "@1", FunctionStatus.active);
        car.values.put(Hvac.SEAT_MEMORY_RESTORE + "@1", Hvac.SEAT_MEMORY_MODE_1);
        assertTrue(link.carFunction("SEAT_MEMORY"));
        assertEquals(List.of(Hvac.SEAT_MEMORY_RESTORE + "@1=" + Hvac.SEAT_MEMORY_MODE_2),
                car.sets);
    }

    @Test
    public void signatureRejectionUsesCarImplForReadsWritesAndWatchers() {
        Car.createError = new SecurityException("signature rejected");
        car.values.put(SEAT + "@1", 2);
        car.sensors.put(Hvac.SENSOR_TEMPERATURE_INDOOR, 21.4f);
        Ecarx link = connect();
        link.requestProperty(SEAT, 1, false);
        link.requestSensor(Hvac.SENSOR_TEMPERATURE_INDOOR);
        link.setInt(SEAT, 1, 3);
        link.listenProperty(SEAT, 1, false);
        link.listenSensor(Hvac.SENSOR_TEMPERATURE_INDOOR);
        assertEquals(List.of(SEAT + "@1=2.0",
                "s" + Hvac.SENSOR_TEMPERATURE_INDOOR + "=21.4"), answers);
        assertEquals(List.of(SEAT + "@1=3"), car.sets);
        assertTrue(car.watchers.containsKey(SEAT));
        assertTrue(car.listeners.containsKey(Hvac.SENSOR_TEMPERATURE_INDOOR));
        assertEquals(1, CarImpl.creations);
    }

    @Test
    public void normalFactoryDoesNotUseCarImpl() {
        connect();
        assertEquals(0, CarImpl.creations);
    }

    @Test
    public void otherFactoryErrorsDoNotUseCarImpl() {
        Car.createError = new IllegalStateException("car service is down");
        Ecarx link = connect();
        link.requestProperty(SEAT, 1, false);
        assertTrue(answers.isEmpty());
        assertEquals(0, CarImpl.creations);
    }

    @Test
    public void failedCarImplCreationCanBeRetried() {
        Car.createError = new SecurityException("signature rejected");
        CarImpl.createError = new IllegalStateException("car service is down");
        Ecarx link = connect();
        assertTrue(answers.isEmpty());
        CarImpl.createError = null;
        car.values.put(SEAT + "@1", 2);
        link.requestProperty(SEAT, 1, false);
        assertEquals(List.of(SEAT + "@1=2.0"), answers);
        assertEquals(2, CarImpl.creations);
    }

    @Test
    public void carThatComesUpLaterIsAskedForAgain() {
        Car.instance = null;
        Ecarx link = connect();
        link.requestProperty(SEAT, 1, false);
        assertTrue(answers.isEmpty());

        Car.instance = car;
        car.values.put(SEAT + "@1", 2);
        link.requestProperty(SEAT, 1, false);
        assertEquals(List.of(SEAT + "@1=2.0"), answers);
    }

    @Test
    public void globalAreaUsesZonelessCallsAndZonesTheZonedOnes() {
        car.values.put(SEAT + "@g", 2);
        car.values.put(SEAT + "@1", 3);
        car.values.put(TEMP + "@1", 22.5f);
        Ecarx link = connect();
        link.requestProperty(SEAT, Gib.AREA_GLOBAL, false);
        link.requestProperty(SEAT, 1, false);
        link.requestProperty(TEMP, 1, true);
        assertEquals(List.of(SEAT + "@" + Gib.AREA_GLOBAL + "=2.0", SEAT + "@1=3.0",
                TEMP + "@1=22.5"), answers);

        link.setInt(SEAT, Gib.AREA_GLOBAL, 1);
        link.setFloat(TEMP, 4, 21f);
        assertEquals(List.of(SEAT + "@g=1", TEMP + "@4=21.0"), car.sets);
    }

    @Test
    public void unsupportedFunctionAnswersLikeTheBridge() {
        Ecarx link = connect();
        link.requestProperty(SEAT, 1, false);
        assertEquals(List.of(SEAT + "@1=-1.0"), answers);
    }

    @Test
    public void watcherWaitsForAnAnswerAndReregistersAfterAFailure() {
        car.down = true;
        Ecarx link = connect();
        link.listenProperty(SEAT, 1, false);
        link.requestProperty(SEAT, 1, false);
        assertTrue(car.watchers.isEmpty());

        car.down = false;
        car.values.put(SEAT + "@1", 1);
        link.requestProperty(SEAT, 1, false);
        assertTrue(car.watchers.isEmpty());
        link.listenProperty(SEAT, 1, false);
        assertTrue(car.watchers.containsKey(SEAT));

        car.watchers.clear();
        link.listenProperty(SEAT, 1, false);
        assertTrue(car.watchers.isEmpty());
        car.down = true;
        link.requestProperty(SEAT, 1, false);
        car.down = false;
        link.requestProperty(SEAT, 1, false);
        link.listenProperty(SEAT, 1, false);
        assertTrue(car.watchers.containsKey(SEAT));
    }

    @Test
    public void changeRereadsEveryWatchedArea() {
        car.values.put(SEAT + "@1", 0);
        car.values.put(SEAT + "@g", 0);
        Ecarx link = connect();
        link.requestProperty(SEAT, 1, false);
        link.requestProperty(SEAT, Gib.AREA_GLOBAL, false);
        link.listenProperty(SEAT, 1, false);
        link.listenProperty(SEAT, Gib.AREA_GLOBAL, false);
        answers.clear();

        car.values.put(SEAT + "@1", 3);
        car.values.put(SEAT + "@g", 3);
        // A late callback of an earlier change must not win over the car's current value.
        car.watchers.get(SEAT).onFunctionValueChanged(SEAT, 1, 1);
        ShadowLooper.idleMainLooper();
        assertTrue(answers.toString(), answers.contains(SEAT + "@1=3.0"));
        assertTrue(answers.toString(), answers.contains(SEAT + "@" + Gib.AREA_GLOBAL + "=3.0"));
        assertEquals(2, answers.size());
    }

    @Test
    public void sensorsAreReadAndWatched() {
        car.sensors.put(Hvac.SENSOR_TEMPERATURE_INDOOR, 21.4f);
        Ecarx link = connect();
        link.requestSensor(Hvac.SENSOR_TEMPERATURE_INDOOR);
        link.listenSensor(Hvac.SENSOR_TEMPERATURE_INDOOR);
        assertEquals(List.of("s" + Hvac.SENSOR_TEMPERATURE_INDOOR + "=21.4"), answers);
        assertTrue(car.listeners.containsKey(Hvac.SENSOR_TEMPERATURE_INDOOR));

        car.listeners.get(Hvac.SENSOR_TEMPERATURE_INDOOR)
                .onSensorValueChanged(Hvac.SENSOR_TEMPERATURE_INDOOR, 22f);
        ShadowLooper.idleMainLooper();
        assertEquals("s" + Hvac.SENSOR_TEMPERATURE_INDOOR + "=22.0", answers.get(1));

        link.close();
        assertTrue(car.listeners.isEmpty());
        assertFalse(car.watchers.containsKey(Hvac.SENSOR_TEMPERATURE_INDOOR));
    }
}
