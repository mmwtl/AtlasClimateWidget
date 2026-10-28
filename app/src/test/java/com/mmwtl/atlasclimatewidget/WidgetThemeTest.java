package com.mmwtl.atlasclimatewidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public final class WidgetThemeTest {
    private static final WidgetTheme[] THEMES = {WidgetTheme.DAY, WidgetTheme.NIGHT};

    @Test public void textReadsOnCardsAndTiles() {
        for (WidgetTheme theme : THEMES) {
            assertTrue(WidgetTheme.contrast(theme.text, theme.card) >= 7);
            assertTrue(WidgetTheme.contrast(theme.text, theme.raised) >= 7);
            assertTrue(WidgetTheme.contrast(theme.textSecondary, theme.card) >= 4.5);
            assertTrue(WidgetTheme.contrast(theme.onText, theme.text) >= 7);
        }
    }

    @Test public void softTilesKeepTheirContentReadable() {
        for (WidgetTheme theme : THEMES) {
            for (Palette palette : Palette.values()) {
                for (ClimateFunction.Tone tone : ClimateFunction.Tone.values()) {
                    assertTrue(palette + " " + tone, WidgetTheme.contrast(
                            theme.softContent(palette, tone), theme.softTile(palette, tone))
                            >= 4.5);
                }
            }
        }
    }

    @Test public void dayIconsStandOutOnTheCard() {
        for (Palette palette : Palette.values()) {
            for (ClimateFunction.Tone tone : ClimateFunction.Tone.values()) {
                assertTrue(palette + " " + tone, WidgetTheme.contrast(
                        WidgetTheme.DAY.litIcon(palette, tone), WidgetTheme.DAY.card) >= 3);
            }
        }
        assertTrue(WidgetTheme.contrast(WidgetTheme.DAY.track, WidgetTheme.DAY.card) > 1.3);
    }

    @Test public void customCardsPickTheReadableTheme() {
        assertSame(WidgetTheme.NIGHT, WidgetTheme.forColor(0xFF101010));
        assertSame(WidgetTheme.NIGHT, WidgetTheme.forColor(0xFF34414A));
        assertSame(WidgetTheme.DAY, WidgetTheme.forColor(0xFFFFFFFF));
        assertSame(WidgetTheme.DAY, WidgetTheme.forColor(0xFFB9CCD6));
        WidgetConfig config = new WidgetConfig();
        config.cardColor = 0xFF102030;
        assertSame(WidgetTheme.NIGHT, WidgetTheme.forCard(config, WidgetTheme.DAY));
        assertEquals(0xFF102030, WidgetTheme.cardColor(config, WidgetTheme.DAY));
    }

    @Test public void graphiteCardsFollowTheTheme() throws Exception {
        WidgetConfig config = new WidgetConfig();
        assertTrue(config.themedCard(true));
        assertEquals(WidgetTheme.DAY.card, WidgetTheme.cardColor(config, WidgetTheme.DAY));
        assertEquals(WidgetTheme.NIGHT.card, WidgetTheme.cardColor(config, WidgetTheme.NIGHT));
        // Layouts saved before the themes stored the graphite default.
        JSONObject saved = new JSONObject(config.toJson());
        saved.put("cardColor", 0xFF262626);
        assertTrue(WidgetConfig.fromJson(saved.toString()).themedCard(true));
        saved.put("cardColor", 0xFF303030);
        assertFalse(WidgetConfig.fromJson(saved.toString()).themedCard(true));
    }

    @Test public void dayColorsApplyOnlyByDayWhenOn() throws Exception {
        WidgetConfig config = new WidgetConfig();
        config.palette = Palette.VIOLET;
        config.cardColor = 0xFF102030;
        config.dayPalette = Palette.TEAL;
        config.dayCardColor = 0xFFF0E0D0;
        // Off by default: both themes share the colours, as before.
        assertSame(Palette.VIOLET, config.palette(false));
        assertEquals(0xFF102030, WidgetTheme.cardColor(config, WidgetTheme.DAY));
        assertSame(WidgetTheme.NIGHT, WidgetTheme.forCard(config, WidgetTheme.DAY));

        config.dayColors = true;
        assertSame(Palette.VIOLET, config.palette(true));
        assertSame(Palette.TEAL, config.palette(false));
        assertEquals(0xFF102030, WidgetTheme.cardColor(config, WidgetTheme.NIGHT));
        assertEquals(0xFFF0E0D0, WidgetTheme.cardColor(config, WidgetTheme.DAY));
        assertSame(WidgetTheme.DAY, WidgetTheme.forCard(config, WidgetTheme.DAY));
        config.dayCardColor = WidgetConfig.THEME_CARD_COLOR;
        assertEquals(WidgetTheme.DAY.card, WidgetTheme.cardColor(config, WidgetTheme.DAY));

        WidgetConfig copy = WidgetConfig.fromJson(config.toJson());
        assertTrue(copy.dayColors);
        assertSame(Palette.TEAL, copy.dayPalette);
        assertTrue(copy.themedCard(false));
        JSONObject old = new JSONObject(config.toJson());
        old.remove("dayColors");
        old.remove("dayPalette");
        old.remove("dayCardColor");
        assertFalse(WidgetConfig.fromJson(old.toString()).dayColors);
    }

    @Test public void mixBlendsChannels() {
        assertEquals(0xFF808080, WidgetTheme.mix(0xFF000000, 0xFFFFFFFF, 0.5f));
        assertEquals(0xFF7893A0, WidgetTheme.mix(0xFF7893A0, 0xFF000000, 0f));
    }
}
