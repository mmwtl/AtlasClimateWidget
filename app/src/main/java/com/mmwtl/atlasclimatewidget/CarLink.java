package com.mmwtl.atlasclimatewidget;

import android.content.Context;

/**
 * Where the service reads and writes car properties: GInputBridge broadcasts or, experimentally,
 * the ECarX API directly ({@link Ecarx}). Areas follow the bridge: {@link Gib#AREA_GLOBAL} is
 * the global zone.
 */
interface CarLink {
    /** Receives the car's answers on the service's worker thread. */
    interface Sink {
        void onProperty(int id, int area, double value);

        void onSensor(int id, float value);
    }

    boolean isDirect();

    void listenProperty(int id, int area);

    void requestProperty(int id, int area, boolean isFloat);

    void listenSensor(int id);

    void requestSensor(int id);

    void setInt(int id, int area, int value);

    void setFloat(int id, int area, float value);

    void close();

    /** The bridge answers with broadcasts, which the service's own receiver gets. */
    static CarLink bridge(Context context) {
        return new CarLink() {
            @Override
            public boolean isDirect() {
                return false;
            }

            @Override
            public void listenProperty(int id, int area) {
                Gib.listenProperty(context, id, area);
            }

            @Override
            public void requestProperty(int id, int area, boolean isFloat) {
                if (isFloat) {
                    Gib.getFloat(context, id, area);
                } else {
                    Gib.getInt(context, id, area);
                }
            }

            @Override
            public void listenSensor(int id) {
                Gib.listenSensor(context, id);
            }

            @Override
            public void requestSensor(int id) {
                Gib.getFloatSensor(context, id);
            }

            @Override
            public void setInt(int id, int area, int value) {
                Gib.setInt(context, id, area, value);
            }

            @Override
            public void setFloat(int id, int area, float value) {
                Gib.setFloat(context, id, area, value);
            }

            @Override
            public void close() {
            }
        };
    }
}
