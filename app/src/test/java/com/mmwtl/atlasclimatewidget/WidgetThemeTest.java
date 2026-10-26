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
        assertSame(WidgetTheme.NIGHT, WidgetTheme.forCard(config, null));
        assertEquals(0xFF102030, WidgetTheme.DAY.cardColor(config));
    }

    @Test public void graphiteCardsFollowTheTheme() throws Exception {
        WidgetConfig config = new WidgetConfig();
        assertTrue(config.themedCard());
        assertEquals(WidgetTheme.DAY.card, WidgetTheme.DAY.cardColor(config));
        assertEquals(WidgetTheme.NIGHT.card, WidgetTheme.NIGHT.cardColor(config));
        // Layouts saved before the themes stored the graphite default.
        JSONObject saved = new JSONObject(config.toJson());
        saved.put("cardColor", 0xFF262626);
        assertTrue(WidgetConfig.fromJson(saved.toString()).themedCard());
        saved.put("cardColor", 0xFF303030);
        assertFalse(WidgetConfig.fromJson(saved.toString()).themedCard());
    }

    @Test public void mixBlendsChannels() {
        assertEquals(0xFF808080, WidgetTheme.mix(0xFF000000, 0xFFFFFFFF, 0.5f));
        assertEquals(0xFF7893A0, WidgetTheme.mix(0xFF7893A0, 0xFF000000, 0f));
    }
}
