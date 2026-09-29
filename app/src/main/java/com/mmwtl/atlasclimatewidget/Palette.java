package com.mmwtl.atlasclimatewidget;

/** Colours of an active tile. Dark variants follow the GInputBridge launcher palette. */
enum Palette {
    ATLAS(R.string.palette_atlas, 0xFF7893A0, 0xFF34414A, 0xFFB9CCD6),
    BLUE(R.string.palette_blue, 0xFF1975D0, 0xFF253A54, 0xFFAFD0FF),
    TEAL(R.string.palette_teal, 0xFF12907F, 0xFF1E3D39, 0xFF5FD4C4),
    EMERALD(R.string.palette_emerald, 0xFF159A66, 0xFF1D3A2E, 0xFF5DD3A0),
    VIOLET(R.string.palette_violet, 0xFF6B5BD0, 0xFF302A52, 0xFFB4A6F5),
    /** Heating is warm, cooling is blue, everything else uses the Atlas accent. */
    SEMANTIC(R.string.palette_semantic, 0xFF7893A0, 0xFF34414A, 0xFFB9CCD6);

    static final int WARM_TILE = 0xFFD9772F;
    static final int WARM_SOFT_TILE = 0xFF4A3223;
    static final int WARM_SOFT_CONTENT = 0xFFFFC08F;
    static final int COOL_TILE = 0xFF2F7BD9;
    static final int COOL_SOFT_TILE = 0xFF233A55;
    static final int COOL_SOFT_CONTENT = 0xFFA9CCFF;

    final int titleRes;
    private final int tile;
    private final int softTile;
    private final int softContent;

    Palette(int titleRes, int tile, int softTile, int softContent) {
        this.titleRes = titleRes;
        this.tile = tile;
        this.softTile = softTile;
        this.softContent = softContent;
    }

    int tile(ClimateFunction.Tone tone) {
        if (this == SEMANTIC && tone == ClimateFunction.Tone.WARM) {
            return WARM_TILE;
        }
        if (this == SEMANTIC && tone == ClimateFunction.Tone.COOL) {
            return COOL_TILE;
        }
        return tile;
    }

    int softTile(ClimateFunction.Tone tone) {
        if (this == SEMANTIC && tone == ClimateFunction.Tone.WARM) {
            return WARM_SOFT_TILE;
        }
        if (this == SEMANTIC && tone == ClimateFunction.Tone.COOL) {
            return COOL_SOFT_TILE;
        }
        return softTile;
    }

    int softContent(ClimateFunction.Tone tone) {
        if (this == SEMANTIC && tone == ClimateFunction.Tone.WARM) {
            return WARM_SOFT_CONTENT;
        }
        if (this == SEMANTIC && tone == ClimateFunction.Tone.COOL) {
            return COOL_SOFT_CONTENT;
        }
        return softContent;
    }
}
