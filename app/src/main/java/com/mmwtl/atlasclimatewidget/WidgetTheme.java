package com.mmwtl.atlasclimatewidget;

import android.content.Context;
import android.content.res.Configuration;

/**
 * Day and night colours of the widget, matching the AtlasLauncher palette. The strips are
 * bitmaps, so resource qualifiers cannot switch them: the renderer picks the theme from the
 * configuration of a {@link #themed} context and {@link ClimateService} redraws when the night
 * mode changes.
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

    /** Theme modes of the settings: the head unit's, or always day or night. */
    static final int AUTO = 0;
    static final int FIXED_DAY = 1;
    static final int FIXED_NIGHT = 2;

    static int validMode(int mode) {
        return mode == FIXED_DAY || mode == FIXED_NIGHT ? mode : AUTO;
    }

    /**
     * The context in the theme chosen in the settings: its resources and {@link #of(Context)}
     * are the fixed theme's, or the head unit's in {@link #AUTO}. The host resolves the widget's
     * own resources in its configuration, so views it inflates get their colours set from code.
     */
    static Context themed(Context context) {
        int mode = new Prefs(context).themeMode();
        if (mode == AUTO) {
            return context;
        }
        // Only the night bits are set; the rest keeps following the system.
        Configuration override = new Configuration();
        override.uiMode = mode == FIXED_NIGHT
                ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        return context.createConfigurationContext(override);
    }

    /** The mode of the context's configuration; see {@link #themed}. */
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
        return forCard(config, of(context));
    }

    /** @param mode the head unit's theme, {@link #DAY} or {@link #NIGHT} */
    static WidgetTheme forCard(WidgetConfig config, WidgetTheme mode) {
        return config.themedCard(mode.night) ? mode : forColor(config.cardColor(mode.night));
    }

    static WidgetTheme forColor(int color) {
        // Black and white text have equal WCAG contrast at this luminance.
        return luminance(color) < 0.179 ? NIGHT : DAY;
    }

    /** Opaque card colour the widget draws in the head unit's theme, before its opacity. */
    static int cardColor(WidgetConfig config, WidgetTheme mode) {
        return config.themedCard(mode.night) ? mode.card : config.cardColor(mode.night);
    }

    static int cardColor(WidgetConfig config, Context context) {
        return cardColor(config, of(context));
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
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
