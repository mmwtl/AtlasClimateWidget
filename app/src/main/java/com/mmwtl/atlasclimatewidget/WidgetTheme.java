package com.mmwtl.atlasclimatewidget;

import android.content.Context;
import android.content.res.Configuration;

/**
 * Day and night colours of the widget, matching the AtlasLauncher palette. The strips are
 * bitmaps, so resource qualifiers cannot switch them: the renderer picks the theme from the
 * process configuration and {@link ClimateService} redraws when the night mode changes.
 * Palette accents stay the same in both themes, with white content on a filled tile.
 */
final class WidgetTheme {
    static final WidgetTheme DAY = new WidgetTheme(false, 0xFFEDEEF0, 0xFFFFFFFF, 0xFFC8CBCF,
            0xFF202124, 0xFF5F6368, 0xFFFFFFFF);
    static final WidgetTheme NIGHT = new WidgetTheme(true, 0xFF232528, 0xFF404347, 0xFF404347,
            0xFFF1F2F4, 0xFFC4C7CA, 0xFF232528);

    /** Share of the accent in a day soft tile laid over white. */
    private static final float DAY_SOFT_TINT = 0.26f;
    /** Darkening of the accent for content on a day soft tile. */
    private static final float DAY_SOFT_SHADE = 0.45f;
    /** Darkening of the accent for a lit icon straight on a day card, for 3:1 contrast. */
    private static final float DAY_ICON_SHADE = 0.18f;

    final boolean night;
    final int card;
    /** Tiles, buttons and pills on the card. */
    final int raised;
    /** Bar tracks and unlit fan levels; white on the day card would vanish. */
    final int track;
    final int text;
    final int textSecondary;
    /** Text on a text-coloured knob or bubble. */
    final int onText;

    private WidgetTheme(boolean night, int card, int raised, int track, int text,
            int textSecondary, int onText) {
        this.night = night;
        this.card = card;
        this.raised = raised;
        this.track = track;
        this.text = text;
        this.textSecondary = textSecondary;
        this.onText = onText;
    }

    /** The head unit's current mode; OneOS switches it with the headlights. */
    static WidgetTheme of(Context context) {
        return of(context.getResources().getConfiguration());
    }

    static WidgetTheme of(Configuration configuration) {
        return (configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES ? NIGHT : DAY;
    }

    /**
     * The theme of the widget's card: the head unit's one for a themed card, otherwise the one
     * that keeps content readable on the chosen colour.
     */
    static WidgetTheme forCard(WidgetConfig config, Context context) {
        return config.themedCard() ? of(context) : forColor(config.cardColor);
    }

    static WidgetTheme forColor(int color) {
        // Above this both texts read alike; dark text wins on lighter cards.
        return luminance(color) > 0.2 ? DAY : NIGHT;
    }

    /** Opaque card colour the widget draws, before its opacity. */
    int cardColor(WidgetConfig config) {
        return config.themedCard() ? card : config.cardColor;
    }

    int softTile(Palette palette, ClimateFunction.Tone tone) {
        return night ? palette.softTile(tone)
                : mix(0xFFFFFFFF, palette.tile(tone), DAY_SOFT_TINT);
    }

    int softContent(Palette palette, ClimateFunction.Tone tone) {
        return night ? palette.softContent(tone)
                : mix(palette.tile(tone), 0xFF000000, DAY_SOFT_SHADE);
    }

    /** A lit icon without a tile under it, as in the icon tile style. */
    int litIcon(Palette palette, ClimateFunction.Tone tone) {
        return night ? palette.tile(tone) : mix(palette.tile(tone), 0xFF000000, DAY_ICON_SHADE);
    }

    static int mix(int from, int to, float amount) {
        int color = 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int a = (from >> shift) & 0xFF;
            int b = (to >> shift) & 0xFF;
            color |= Math.round(a + (b - a) * amount) << shift;
        }
        return color;
    }

    /** WCAG relative luminance of an RGB colour, 0 to 1. */
    static double luminance(int color) {
        return 0.2126 * linear((color >> 16) & 0xFF) + 0.7152 * linear((color >> 8) & 0xFF)
                + 0.0722 * linear(color & 0xFF);
    }

    static double contrast(int first, int second) {
        double a = luminance(first) + 0.05;
        double b = luminance(second) + 0.05;
        return Math.max(a, b) / Math.min(a, b);
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
