package com.mmwtl.atlasclimatewidget;

/** Car family; only the heated windscreen property differs between them. */
enum CarModel {
    ATLAS(R.string.car_model_atlas, Hvac.WINDSHIELD_HEAT_ATLAS),
    PREFACE(R.string.car_model_preface, Hvac.WINDSHIELD_HEAT_PREFACE),
    CITYRAY(R.string.car_model_cityray, Hvac.WINDSHIELD_HEAT_CITYRAY);

    final int titleRes;
    final int windshieldHeatId;

    CarModel(int titleRes, int windshieldHeatId) {
        this.titleRes = titleRes;
        this.windshieldHeatId = windshieldHeatId;
    }

    static CarModel fromName(String name) {
        for (CarModel model : values()) {
            if (model.name().equals(name)) {
                return model;
            }
        }
        return ATLAS;
    }
}
