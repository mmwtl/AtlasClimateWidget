package com.mmwtl.atlasclimatewidget;

/** Read-only view of the climate values used to draw the widget. */
interface ClimateState {
    /**
     * Returns the value of a car property for the requested area, falling back to the global area.
     * Returns {@code null} when the bridge has no fresh value.
     */
    Double property(int id, int area);

    /** Returns a fresh sensor value or {@code null}. */
    Double sensor(int id);

    /** Whether the bridge answered recently. */
    boolean isConnected();

    ClimateState EMPTY = new ClimateState() {
        @Override
        public Double property(int id, int area) {
            return null;
        }

        @Override
        public Double sensor(int id) {
            return null;
        }

        @Override
        public boolean isConnected() {
            return false;
        }
    };
}
