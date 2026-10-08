package com.mmwtl.atlasclimatewidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WidgetGeometryTest {
    @Test public void stripsCoverEachCardExactly() {
        WidgetConfig config = new WidgetConfig();
        WidgetGeometry.Plan plan = WidgetGeometry.plan(config, 640f, 0f, 1f, 25);
        float card = 0f;
        for (WidgetGeometry.Strip strip : plan.strips) {
            if (strip.firstInCard) {
                card = 0f;
            }
            assertEquals(card, strip.cardOffset, 0.01f);
            card += strip.height;
            if (strip.lastInCard) {
                assertEquals(strip.cardHeight, card, 0.01f);
            }
        }
        // header, temperature, fan directions, fan speeds and two tile rows
        assertEquals(6, plan.strips.size());
    }

    @Test public void sensorLineIsABlockOfItsOwn() {
        WidgetConfig config = new WidgetConfig();
        config.temperatureEnabled = false;
        config.fanEnabled = false;
        config.tilesEnabled = false;
        WidgetGeometry.Plan plan = WidgetGeometry.plan(config, 640f, 0f, 1f, 25);
        assertEquals(1, plan.strips.size());
        assertEquals(WidgetGeometry.RowKind.HEADER, plan.strips.get(0).row.kind);
        assertEquals(0, plan.strips.get(0).zoneCount);
    }

    @Test public void tileCellsCentreOnTiles() {
        WidgetConfig config = new WidgetConfig();
        WidgetGeometry.Plan plan = WidgetGeometry.plan(config, 640f, 0f, 1f, 25);
        WidgetGeometry.Strip tiles = plan.strips.get(plan.strips.size() - 1);
        float cell = (plan.width - 2f * tiles.zonePadding) / tiles.zoneCount;
        float tileCell = plan.tileCellWidth(config.columns);
        assertEquals(tileCell, cell, 0.01f);
    }

    @Test public void fitsAvailableHeight() {
        WidgetConfig config = new WidgetConfig();
        config.heightMode = WidgetConfig.HeightMode.CONTENT;
        float natural = WidgetGeometry.plan(config, 640f, 0f, 1f, 25).totalHeight();
        WidgetGeometry.Plan fitted = WidgetGeometry.plan(config, 640f, natural * 0.8f, 1f, 25);
        assertEquals(natural * 0.8f * WidgetGeometry.FIT_MARGIN, fitted.totalHeight(),
                natural * 0.02f);
    }

    @Test public void narrowsContentWhenSquashingIsNotEnough() {
        WidgetConfig config = new WidgetConfig();
        WidgetGeometry.Plan plan = WidgetGeometry.plan(config, 264f, 120f, 1f, 25);
        assertTrue(Math.round(plan.totalHeight()) <= 120);
        assertTrue(plan.width < plan.fullWidth);
        assertEquals(264f, plan.fullWidth, 0f);
    }

    @Test public void temperatureCellsMapToSteps() {
        WidgetConfig config = new WidgetConfig();
        config.infoEnabled = false;
        WidgetGeometry.Plan plan = WidgetGeometry.plan(config, 640f, 0f, 1f, 25);
        WidgetGeometry.Strip temp = plan.strips.get(0);
        assertTrue(temp.buttonCells >= 1);
        assertEquals(25 + 2 * temp.buttonCells, temp.zoneCount);
        assertEquals("tempstep/1/-1", WidgetViews.control(config, temp, 0));
        assertEquals("temp/1/0", WidgetViews.control(config, temp, temp.buttonCells));
        assertEquals("temp/1/24", WidgetViews.control(config, temp,
                temp.zoneCount - temp.buttonCells - 1));
        assertEquals("tempstep/1/1", WidgetViews.control(config, temp, temp.zoneCount - 1));
    }

    @Test public void emptyTileCellsAreInert() {
        WidgetConfig config = new WidgetConfig();
        config.functions.remove(ClimateFunction.PASSENGER_VENT);
        WidgetGeometry.Plan plan = WidgetGeometry.plan(config, 640f, 0f, 1f, 25);
        WidgetGeometry.Strip last = plan.strips.get(plan.strips.size() - 1);
        assertNull(WidgetViews.control(config, last, config.columns - 1));
        assertEquals("fn/DRIVER_HEAT", WidgetViews.control(config,
                plan.strips.get(plan.strips.size() - 2), 0));
    }

    @Test public void fanBlockStacksBarAndControlRow() {
        WidgetConfig config = new WidgetConfig();
        config.fanLayout = WidgetConfig.FanLayout.ROWS;
        config.temperatureEnabled = false;
        config.infoEnabled = false;
        config.tilesEnabled = false;
        WidgetGeometry.Plan plan = WidgetGeometry.plan(config, 640f, 0f, 1f, 25);
        assertEquals(2, plan.strips.size());
        WidgetGeometry.Strip bar = plan.strips.get(0);
        WidgetGeometry.Strip controls = plan.strips.get(1);
        assertEquals(WidgetGeometry.RowKind.FAN, bar.row.kind);
        assertEquals(WidgetGeometry.RowKind.FAN_CONTROLS, controls.row.kind);
        assertEquals(6, controls.zoneCount);
        assertEquals("fn/BLOW_WINDOW", WidgetViews.control(config, controls, 0));
        assertEquals("fn/BLOW_LEGS", WidgetViews.control(config, controls, 2));
        assertEquals("fanpreset/0", WidgetViews.control(config, controls, 3));
        assertEquals("fanpreset/2", WidgetViews.control(config, controls, 5));

        config.fanDirections = false;
        config.fanPresetCount = 5;
        WidgetGeometry.Strip five = WidgetGeometry.plan(config, 640f, 0f, 1f, 25).strips.get(1);
        assertEquals(5, five.zoneCount);
        assertEquals("fanpreset/4", WidgetViews.control(config, five, 4));

        config.fanBar = false;
        config.fanPresets = false;
        assertTrue(!config.isEnabled(WidgetConfig.Block.FAN));
    }

    @Test public void fanBarShowsSpeedsOrPresetsByMode() {
        WidgetConfig config = new WidgetConfig();
        config.temperatureEnabled = false;
        config.infoEnabled = false;
        config.tilesEnabled = false;
        WidgetGeometry.Plan plan = WidgetGeometry.plan(config, 640f, 0f, 1f, 25);
        assertEquals("directions above speeds", 2, plan.strips.size());
        WidgetGeometry.Strip top = plan.strips.get(0);
        WidgetGeometry.Strip speeds = plan.strips.get(1);
        assertEquals(WidgetGeometry.RowKind.FAN_TOP, top.row.kind);
        assertEquals(WidgetGeometry.RowKind.FAN, speeds.row.kind);
        assertEquals("both rows share one grid", top.zoneCount, speeds.zoneCount);
        assertEquals(top.autoCells, speeds.autoCells);
        assertTrue(speeds.autoCells > speeds.buttonCells);
        assertEquals(speeds.autoCells + speeds.buttonCells, speeds.stepStart);
        assertEquals(speeds.stepStart + 9 * speeds.stepSpan + speeds.buttonCells,
                speeds.zoneCount);

        // Manual: AUTO on both rows, directions above, −, speeds and + below.
        assertEquals("fn/AUTO", WidgetViews.control(config, top, 0, false));
        assertEquals("fn/AUTO", WidgetViews.control(config, speeds, speeds.autoCells - 1, false));
        assertEquals("fn/BLOW_WINDOW", WidgetViews.control(config, top, top.autoCells, false));
        assertEquals("fn/BLOW_LEGS", WidgetViews.control(config, top, top.zoneCount - 1, false));
        assertEquals("fanstep/-1", WidgetViews.control(config, speeds, speeds.autoCells, false));
        assertEquals("fan/1", WidgetViews.control(config, speeds, speeds.stepStart, false));
        assertEquals("fanstep/1", WidgetViews.control(config, speeds, speeds.zoneCount - 1,
                false));
        // AUTO: the presets take both rows after AUTO.
        assertEquals("fn/AUTO", WidgetViews.control(config, speeds, 0, true));
        for (WidgetGeometry.Strip strip : plan.strips) {
            assertEquals("fanpreset/0", WidgetViews.control(config, strip, strip.autoCells, true));
            assertEquals("fanpreset/2",
                    WidgetViews.control(config, strip, strip.zoneCount - 1, true));
        }

        for (int presets : new int[]{3, 5}) {
            for (boolean auto : new boolean[]{true, false}) {
                for (boolean buttons : new boolean[]{true, false}) {
                    config.fanPresetCount = presets;
                    config.fanAuto = auto;
                    config.fanButtons = buttons;
                    plan = WidgetGeometry.plan(config, 640f, 0f, 1f, 25);
                    WidgetGeometry.Strip strip = plan.strips.get(1);
                    int rest = strip.zoneCount - strip.autoCells;
                    assertTrue("parts split evenly or finely", rest % presets == 0
                            || rest >= WidgetGeometry.FAN_BAR_MIN_CELLS);
                    for (int preset = 0; preset < presets; preset++) {
                        int first = strip.partStart(preset, presets);
                        int last = strip.partStart(preset + 1, presets) - 1;
                        assertTrue(last - first + 1 >= rest / presets);
                        assertEquals("fanpreset/" + preset,
                                WidgetViews.control(config, strip, first, true));
                        assertEquals("fanpreset/" + preset,
                                WidgetViews.control(config, strip, last, true));
                    }
                    for (int level = 1; level <= 9; level++) {
                        int first = strip.stepStart + (level - 1) * strip.stepSpan;
                        assertEquals("fan/" + level,
                                WidgetViews.control(config, strip, first, false));
                        assertEquals("fan/" + level, WidgetViews.control(config, strip,
                                first + strip.stepSpan - 1, false));
                    }
                }
            }
        }
        config.fanAuto = false;
        config.fanDirections = false;
        plan = WidgetGeometry.plan(config, 640f, 0f, 1f, 25);
        assertEquals("without directions only the speed row is left", 1, plan.strips.size());
        assertTrue(config.isEnabled(WidgetConfig.Block.FAN));
    }

    @Test public void onlyBarCellsOpenTheScrubber() {
        assertTrue(WidgetViews.isScrubbable("temp/1/4"));
        assertTrue(WidgetViews.isScrubbable("fan/3"));
        assertTrue(!WidgetViews.isScrubbable("tempstep/1/1"));
        assertTrue(!WidgetViews.isScrubbable("fanstep/-1"));
        assertTrue(!WidgetViews.isScrubbable("fn/AC"));
        assertTrue(!WidgetViews.isScrubbable("fanpreset/2"));
    }

    @Test public void fillStretchesTilesToTheCellBottom() {
        WidgetConfig config = new WidgetConfig();
        float natural = WidgetGeometry.plan(config, 640f, 0f, 1f, 25).totalHeight();
        WidgetGeometry.Plan filled = WidgetGeometry.plan(config, 640f, natural + 100f, 1f, 25);
        assertEquals(natural + 100f, filled.totalHeight(), 0.5f);
        WidgetGeometry.Strip tiles = filled.strips.get(filled.strips.size() - 1);
        float tileWidth = filled.tileCellWidth(config.columns) - filled.gap;
        assertTrue(tiles.contentHeight > tileWidth);
        assertTrue(tiles.contentHeight <= tileWidth * WidgetGeometry.MAX_TILE_ASPECT + 0.01f);
    }

    @Test public void fillSqueezesToTheCellBottom() {
        WidgetConfig config = new WidgetConfig();
        float natural = WidgetGeometry.plan(config, 640f, 0f, 1f, 25).totalHeight();
        float cell = natural * 0.8f;
        WidgetGeometry.Plan fitted = WidgetGeometry.plan(config, 640f, cell, 1f, 25);
        assertEquals(cell, fitted.totalHeight(), 0.5f);
        // Just below the natural height still reaches the bottom instead of the fit margin.
        cell = natural + 1f;
        fitted = WidgetGeometry.plan(config, 640f, cell, 1f, 25);
        assertEquals(cell, fitted.totalHeight(), 0.5f);
    }

    @Test public void snappedStripsAddUpToTheCell() {
        WidgetConfig config = new WidgetConfig();
        for (float cell : new float[] {333f, 517f, 744f, 1001f}) {
            WidgetGeometry.Plan plan = WidgetGeometry.plan(config, 744f, cell, 1.6f, 25);
            int total = 0;
            for (WidgetGeometry.Strip strip : plan.strips) {
                total += plan.pixelHeight(strip);
            }
            assertEquals(Math.round(plan.totalHeight()), total);
            assertTrue(total <= cell);
        }
    }

    @Test public void fillKeepsBarsAndSpreadsRestOverCards() {
        WidgetConfig config = new WidgetConfig();
        config.tilesEnabled = false;
        float natural = WidgetGeometry.plan(config, 640f, 0f, 1f, 25).totalHeight();
        WidgetGeometry.Plan filled = WidgetGeometry.plan(config, 640f, natural + 200f, 1f, 25);
        assertEquals(natural + 200f, filled.totalHeight(), 0.5f);
        for (WidgetGeometry.Strip strip : filled.strips) {
            if (strip.row.kind == WidgetGeometry.RowKind.FAN) {
                assertEquals(WidgetGeometry.FAN_ROW_DP * filled.density, strip.contentHeight,
                        0.01f);
            }
        }
    }

    @Test public void singleCardHoldsAllBlocksWithDividers() {
        WidgetConfig config = new WidgetConfig();
        config.cardLayout = WidgetConfig.CardLayout.SINGLE;
        WidgetGeometry.Plan plan = WidgetGeometry.plan(config, 640f, 0f, 1f, 25);
        assertEquals(6, plan.strips.size());
        float card = 0f;
        int sections = 0;
        for (int index = 0; index < plan.strips.size(); index++) {
            WidgetGeometry.Strip strip = plan.strips.get(index);
            assertEquals(index == 0, strip.firstInCard);
            assertEquals(index == plan.strips.size() - 1, strip.lastInCard);
            assertEquals(0f, strip.trailingGap, 0f);
            assertEquals(card, strip.cardOffset, 0.01f);
            card += strip.height;
            if (strip.row.sectionStart) {
                sections++;
                assertTrue(strip.block != plan.strips.get(index - 1).block);
            }
        }
        assertEquals(plan.strips.get(0).cardHeight, card, 0.01f);
        assertEquals("temperature, fan and tiles start their own sections", 3, sections);
        assertEquals(WidgetConfig.Block.TILES, plan.strips.get(5).block);
    }

    @Test public void singleCardFillsToTheCellBottom() {
        WidgetConfig config = new WidgetConfig();
        config.cardLayout = WidgetConfig.CardLayout.SINGLE;
        config.tilesEnabled = false;
        float natural = WidgetGeometry.plan(config, 640f, 0f, 1f, 25).totalHeight();
        for (float cell : new float[] {natural * 0.8f, natural + 200f}) {
            WidgetGeometry.Plan plan = WidgetGeometry.plan(config, 640f, cell, 1f, 25);
            assertEquals(cell, plan.totalHeight(), 0.5f);
        }
    }

    @Test public void contentModeLeavesFreeSpace() {
        WidgetConfig config = new WidgetConfig();
        config.heightMode = WidgetConfig.HeightMode.CONTENT;
        float natural = WidgetGeometry.plan(config, 640f, 0f, 1f, 25).totalHeight();
        WidgetGeometry.Plan plan = WidgetGeometry.plan(config, 640f, natural + 100f, 1f, 25);
        assertEquals(natural, plan.totalHeight(), 0.01f);
        assertEquals(natural, plan.naturalHeight, 0.01f);
    }

    @Test public void scaleEnlargesBarsButKeepsTileColumns() {
        WidgetConfig config = new WidgetConfig();
        config.infoEnabled = false;
        config.scalePercent = 100;
        WidgetGeometry.Plan normal = WidgetGeometry.plan(config, 640f, 0f, 1f, 25);
        config.scalePercent = 150;
        WidgetGeometry.Plan large = WidgetGeometry.plan(config, 640f, 0f, 1f, 25);
        assertEquals(normal.strips.get(0).contentHeight * 1.5f,
                large.strips.get(0).contentHeight, 0.01f);
        assertEquals(1.5f, large.density, 0f);
        WidgetGeometry.Strip tiles = large.strips.get(large.strips.size() - 1);
        assertEquals(config.columns, tiles.zoneCount);
    }

    @Test public void consoleSplitsTemperatureAndFanButtons() {
        WidgetConfig config = new WidgetConfig();
        config.infoEnabled = false;
        config.style = WidgetConfig.Style.CONSOLE;
        config.fanLayout = WidgetConfig.FanLayout.ROWS;
        config.scalePercent = 100;
        WidgetGeometry.Plan plan = WidgetGeometry.plan(config, 640f, 0f, 1f, 25);
        WidgetGeometry.Strip value = plan.strips.get(0);
        WidgetGeometry.Strip bar = plan.strips.get(1);
        assertEquals(WidgetGeometry.RowKind.TEMP_VALUE, value.row.kind);
        assertEquals(WidgetGeometry.CONSOLE_VALUE_ROW_DP, value.contentHeight, 0.01f);
        assertEquals("− and + take the outer cells", 1, value.buttonCells);
        assertTrue(value.zoneCount >= 3);
        assertEquals(WidgetGeometry.RowKind.TEMPERATURE, bar.row.kind);
        assertEquals("the bar is all steps", 25, bar.zoneCount);
        assertEquals(0, bar.buttonCells);
        WidgetGeometry.Strip directions = plan.strips.get(3);
        WidgetGeometry.Strip presets = plan.strips.get(4);
        assertEquals(WidgetGeometry.RowKind.FAN_CONTROLS, directions.row.kind);
        assertEquals(WidgetConfig.FAN_ROW_DIRECTIONS, directions.row.index);
        assertEquals(WidgetConfig.FAN_DIRECTIONS.length, directions.zoneCount);
        assertEquals(WidgetConfig.FAN_ROW_PRESETS, presets.row.index);
        assertEquals(config.fanControlCells(WidgetConfig.FAN_ROW_PRESETS), presets.zoneCount);
        assertEquals(directions.contentHeight, presets.contentHeight, 0.01f);
        config.fanDirections = false;
        config.temperatureButtons = false;
        plan = WidgetGeometry.plan(config, 640f, 0f, 1f, 25);
        assertEquals("without buttons the value row is inert", 0, plan.strips.get(0).zoneCount);
        assertEquals(WidgetConfig.FAN_ROW_PRESETS, plan.strips.get(3).row.index);
    }

    @Test public void consoleDualZoneStacksValueAndBarPerZone() {
        WidgetConfig config = new WidgetConfig();
        config.infoEnabled = false;
        config.temperatureDual = true;
        config.style = WidgetConfig.Style.CONSOLE;
        WidgetGeometry.Plan plan = WidgetGeometry.plan(config, 640f, 0f, 1f, 25);
        WidgetGeometry.RowKind[] kinds = {WidgetGeometry.RowKind.TEMP_VALUE,
                WidgetGeometry.RowKind.TEMPERATURE, WidgetGeometry.RowKind.TEMP_VALUE,
                WidgetGeometry.RowKind.TEMPERATURE};
        for (int index = 0; index < kinds.length; index++) {
            assertEquals(kinds[index], plan.strips.get(index).row.kind);
            assertEquals(index / 2, plan.strips.get(index).row.index);
        }
    }

    @Test public void consoleFlattensTilesBeforeSquashingControls() {
        WidgetConfig config = new WidgetConfig();
        config.style = WidgetConfig.Style.CONSOLE;
        config.cardLayout = WidgetConfig.CardLayout.SINGLE;
        config.fanLayout = WidgetConfig.FanLayout.ROWS;
        config.columns = 4;
        WidgetGeometry.Plan natural = WidgetGeometry.plan(config, 740f, 0f, 1f, 25);
        float tile = tileHeight(natural);
        float segment = WidgetGeometry.CONSOLE_SEGMENT_ROW_DP * natural.density;
        int rows = config.tileRows().size();

        // A little too tall: only the tiles give up height.
        float cell = natural.totalHeight() - tile * 0.2f * rows;
        WidgetGeometry.Plan tight = WidgetGeometry.plan(config, 740f, cell, 1f, 25);
        assertEquals(cell, tight.totalHeight(), 0.5f);
        assertEquals(1f, tight.verticalScale, 0f);
        assertEquals(tile * 0.8f, tileHeight(tight), 0.5f);
        assertEquals(segment, segmentHeight(tight), 0.01f);

        // Far too tall: tiles stop at the minimum aspect and the rest is squashed evenly.
        cell = natural.totalHeight() - tile * 0.6f * rows;
        WidgetGeometry.Plan squashed = WidgetGeometry.plan(config, 740f, cell, 1f, 25);
        assertEquals(cell, squashed.totalHeight(), 0.5f);
        assertTrue(squashed.verticalScale < 1f);
        assertEquals(tile * WidgetGeometry.CONSOLE_MIN_TILE_ASPECT * squashed.verticalScale,
                tileHeight(squashed), 0.5f);
        assertEquals(segment * squashed.verticalScale, segmentHeight(squashed), 0.01f);
    }

    @Test public void consoleFillKeepsTilesSquare() {
        WidgetConfig config = new WidgetConfig();
        config.style = WidgetConfig.Style.CONSOLE;
        WidgetGeometry.Plan natural = WidgetGeometry.plan(config, 740f, 0f, 1f, 25);
        WidgetGeometry.Plan filled = WidgetGeometry.plan(config, 740f,
                natural.totalHeight() + 300f, 1f, 25);
        assertEquals(natural.totalHeight() + 300f, filled.totalHeight(), 0.5f);
        assertEquals(tileHeight(natural), tileHeight(filled), 0.01f);
    }

    private static float tileHeight(WidgetGeometry.Plan plan) {
        for (WidgetGeometry.Strip strip : plan.strips) {
            if (strip.row.kind == WidgetGeometry.RowKind.TILES) {
                return strip.contentHeight;
            }
        }
        throw new AssertionError("no tiles");
    }

    private static float segmentHeight(WidgetGeometry.Plan plan) {
        for (WidgetGeometry.Strip strip : plan.strips) {
            if (strip.row.kind == WidgetGeometry.RowKind.FAN_CONTROLS) {
                return strip.contentHeight;
            }
        }
        throw new AssertionError("no fan buttons");
    }
}
