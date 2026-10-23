package com.mmwtl.atlasclimatewidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.Handler;
import android.os.Looper;

import com.ecarx.xui.adaptapi.car.Car;

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
        car = new Car();
        Car.instance = car;
    }

    @After
    public void tearDown() {
        Car.instance = null;
    }

    private Ecarx connect() {
        Ecarx link = Ecarx.connect(RuntimeEnvironment.getApplication(),
                new Handler(Looper.getMainLooper()), sink);
        assertNotNull(link);
        return link;
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
    public void watcherWaitsForTheFirstAnswerAndReregistersAfterAFailure() {
        car.down = true;
        Ecarx link = connect();
        link.listenProperty(SEAT, 1);
        link.requestProperty(SEAT, 1, false);
        assertTrue(car.watchers.isEmpty());

        car.down = false;
        car.values.put(SEAT + "@1", 1);
        link.listenProperty(SEAT, 1);
        link.requestProperty(SEAT, 1, false);
        assertTrue(car.watchers.containsKey(SEAT));

        car.watchers.clear();
        car.down = true;
        link.requestProperty(SEAT, 1, false);
        car.down = false;
        link.requestProperty(SEAT, 1, false);
        assertTrue(car.watchers.containsKey(SEAT));
    }

    @Test
    public void changeRereadsEveryWatchedArea() {
        car.values.put(SEAT + "@1", 0);
        car.values.put(SEAT + "@g", 0);
        Ecarx link = connect();
        link.listenProperty(SEAT, 1);
        link.requestProperty(SEAT, 1, false);
        link.listenProperty(SEAT, Gib.AREA_GLOBAL);
        link.requestProperty(SEAT, Gib.AREA_GLOBAL, false);
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
        link.listenSensor(Hvac.SENSOR_TEMPERATURE_INDOOR);
        link.requestSensor(Hvac.SENSOR_TEMPERATURE_INDOOR);
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
