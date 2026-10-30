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
        config.dayCardColor = 0xFF102030;
        assertSame(WidgetTheme.NIGHT, WidgetTheme.forCard(config, WidgetTheme.DAY));
        assertEquals(0xFF102030, WidgetTheme.cardColor(config, WidgetTheme.DAY));
        config.cardColor = 0xFFF0E0D0;
        assertSame(WidgetTheme.DAY, WidgetTheme.forCard(config, WidgetTheme.NIGHT));
    }

    @Test public void newCardsFollowTheTheme() {
        WidgetConfig config = new WidgetConfig();
        assertTrue(config.themedCard(true));
        assertTrue(config.themedCard(false));
        assertEquals(WidgetTheme.DAY.card, WidgetTheme.cardColor(config, WidgetTheme.DAY));
        assertEquals(WidgetTheme.NIGHT.card, WidgetTheme.cardColor(config, WidgetTheme.NIGHT));
    }

    @Test public void colorsOfEachThemeSurviveSaving() {
        WidgetConfig config = new WidgetConfig();
        config.palette = Palette.VIOLET;
        config.cardColor = 0xFF102030;
        config.dayPalette = Palette.TEAL;
        config.dayCardColor = WidgetConfig.THEME_CARD_COLOR;

        WidgetConfig copy = WidgetConfig.fromJson(config.toJson());
        assertSame(Palette.VIOLET, copy.palette(true));
        assertSame(Palette.TEAL, copy.palette(false));
        assertEquals(0xFF102030, copy.cardColor(true));
        assertTrue(copy.themedCard(false));
    }

    @Test public void oldLayoutsMakeTheGraphiteDefaultFollowTheTheme() throws Exception {
        JSONObject saved = legacyLayout();
        saved.put("cardColor", 0xFF262626);
        WidgetConfig config = WidgetConfig.fromJson(saved.toString());
        assertTrue(config.themedCard(true));
        assertTrue(config.themedCard(false));
    }

    @Test public void oldLayoutsKeepAPickedColourInBothThemes() throws Exception {
        JSONObject saved = legacyLayout();
        saved.put("cardColor", 0xFF303030);
        saved.put("palette", Palette.VIOLET.name());
        WidgetConfig config = WidgetConfig.fromJson(saved.toString());
        assertEquals(0xFF303030, config.cardColor(true));
        assertEquals(0xFF303030, config.cardColor(false));
        assertSame(Palette.VIOLET, config.palette(false));
    }

    @Test public void graphitePickedInACurrentLayoutStaysPicked() {
        WidgetConfig config = new WidgetConfig();
        config.cardColor = 0xFF262626;
        assertFalse(WidgetConfig.fromJson(config.toJson()).themedCard(true));
    }

    private static JSONObject legacyLayout() throws Exception {
        JSONObject saved = new JSONObject(new WidgetConfig().toJson());
        saved.put("version", 4);
        saved.remove("dayPalette");
        saved.remove("dayCardColor");
        return saved;
    }

    @Test public void mixBlendsChannels() {
        assertEquals(0xFF808080, WidgetTheme.mix(0xFF000000, 0xFFFFFFFF, 0.5f));
        assertEquals(0xFF7893A0, WidgetTheme.mix(0xFF7893A0, 0xFF000000, 0f));
    }
}
