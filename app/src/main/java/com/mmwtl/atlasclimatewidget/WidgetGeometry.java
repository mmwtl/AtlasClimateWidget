package com.mmwtl.atlasclimatewidget;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits the widget into horizontal strips.
 *
 * <p>RemoteViews on Android 11 cannot position views freely, so every block is drawn as bitmaps
 * of full widget width. Each strip is one bitmap plus an overlay row of equally weighted touch
 * cells; strips stack vertically with {@code adjustViewBounds}. The geometry therefore puts every
 * touch target on an equal-width grid: temperature steps, fan levels and tile columns.
 */
final class WidgetGeometry {
    static final int DEFAULT_WIDTH_DP = 640;
    static final float HEADER_HEIGHT_DP = 20f;
    static final float HEADER_GAP_DP = 6f;
    static final float TEMP_ROW_DP = 50f;
    static final float TEMP_LABEL_DP = 18f;
    static final float TEMP_ROW_GAP_DP = 8f;
    static final float FAN_ROW_DP = 42f;
    static final float FAN_CONTROLS_ROW_DP = 56f;
    /** Width of the bar fan layout's AUTO column relative to the speed row's height. */
    static final float FAN_AUTO_WIDTH = 1.4f;
    /**
     * Cells the bar fan layout's directions share at least when they cannot split the row
     * evenly, so whole cells leave no visible difference between their widths.
     */
    static final int FAN_BAR_MIN_CELLS = 40;
    /** Console temperature: a row with −, the large value and +, then a thin full-width bar. */
    static final float CONSOLE_VALUE_ROW_DP = 40f;
    static final float CONSOLE_ZONE_LABEL_DP = 14f;
    static final float CONSOLE_BAR_ROW_DP = 24f;
    static final float CONSOLE_VALUE_GAP_DP = 2f;
    /** Diameter of every round console button, so the −/+ of all bars line up. */
    static final float CONSOLE_BUTTON_DP = 36f;
    static final float CONSOLE_SEGMENT_ROW_DP = 48f;
    /**
     * Console tiles flatten down to this height/width ratio before anything else shrinks, so a
     * tight cell keeps the bars and fan buttons at size; filling never grows them past square.
     */
    static final float CONSOLE_MIN_TILE_ASPECT = 0.6f;
    static final float CONSOLE_MAX_TILE_ASPECT = 1f;
    static final float MIN_VERTICAL_SCALE = 0.55f;
    static final float FIT_MARGIN = 0.98f;
    static final float MIN_CONTENT_WIDTH = 0.3f;
    /** Tallest tile relative to its width when tiles stretch to fill the widget. */
    static final float MAX_TILE_ASPECT = 1.6f;

    enum RowKind {
        HEADER,
        /** Console only: −, the zone's value and +; the zone's bar follows as TEMPERATURE. */
        TEMP_VALUE,
        TEMPERATURE,
        FAN,
        /** Bar fan layout: the directions above the speeds, or the top half of the presets. */
        FAN_TOP,
        FAN_CONTROLS,
        TILES
    }

    static final class Row {
        final WidgetConfig.Block block;
        final RowKind kind;
        /** Temperature zone index or tile row index. */
        final int index;
        float height;
        /** First row of a block that shares its card with the block above, a section gap below it. */
        boolean sectionStart;

        Row(WidgetConfig.Block block, RowKind kind, int index, float height) {
            this.block = block;
            this.kind = kind;
            this.index = index;
            this.height = height;
        }
    }

    /** One bitmap of the widget. Coordinates are pixels. */
    static final class Strip {
        final WidgetConfig.Block block;
        final Row row;
        final int stripIndexInCard;
        final boolean firstInCard;
        final boolean lastInCard;
        /** Offset of this strip inside its card. */
        final float cardOffset;
        final float cardHeight;
        final float height;
        /** Transparent spacing below the card, drawn only in the card's last strip. */
        final float trailingGap;
        /** Row content position inside the strip. */
        final float contentTop;
        final float contentHeight;
        /** Horizontal padding of the touch overlay. */
        final float zonePadding;
        /** Equal-width touch cells; zero means the strip is not interactive. */
        final int zoneCount;
        /** Cells occupied by each −/+ button of a bar row. */
        final int buttonCells;
        /** First cell of a bar's steps and the cells each step spans. */
        final int stepStart;
        final int stepSpan;
        /**
         * Bar fan layout: cells of the AUTO column at the start of both rows. The directions
         * split the cells after it, see {@link #partStart}; in AUTO the preset levels split the
         * speeds' cells evenly, see {@link #levelStart}.
         */
        final int autoCells;

        Strip(WidgetConfig.Block block, Row row, int stripIndexInCard, boolean firstInCard,
                boolean lastInCard, float cardOffset, float cardHeight, float height,
                float trailingGap, float contentTop, float contentHeight, float zonePadding,
                int zoneCount, int buttonCells, int stepStart, int stepSpan, int autoCells) {
            this.block = block;
            this.row = row;
            this.stripIndexInCard = stripIndexInCard;
            this.firstInCard = firstInCard;
            this.lastInCard = lastInCard;
            this.cardOffset = cardOffset;
            this.cardHeight = cardHeight;
            this.height = height;
            this.trailingGap = trailingGap;
            this.contentTop = contentTop;
            this.contentHeight = contentHeight;
            this.zonePadding = zonePadding;
            this.zoneCount = zoneCount;
            this.buttonCells = buttonCells;
            this.stepStart = stepStart;
            this.stepSpan = stepSpan;
            this.autoCells = autoCells;
        }

        /** First cell of part {@code part} of {@code parts} sharing the cells after AUTO. */
        int partStart(int part, int parts) {
            return autoCells + part * (zoneCount - autoCells) / parts;
        }

        /** First cell of level {@code level} (0-based) of a bar of {@code levels} levels. */
        int levelStart(int level, int levels) {
            return stepStart + level * Hvac.FAN_SPEED_LEVEL_COUNT * stepSpan / levels;
        }

        /** Part of {@code parts} under the cell, see {@link #partStart}. */
        int partAt(int cell, int parts) {
            int part = 0;
            while (part < parts - 1 && cell >= partStart(part + 1, parts)) {
                part++;
            }
            return part;
        }

        float totalHeight() {
            return height + trailingGap;
        }
    }

    static final class Plan {
        /** Bitmap width. */
        final float fullWidth;
        /** Content width, centred inside the bitmap. */
        final float width;
        final float density;
        final float verticalScale;
        final float padding;
        final float gap;
        final int temperatureSteps;
        final List<Strip> strips;

        Plan(float fullWidth, float width, float density, float verticalScale, float padding,
                float gap, int temperatureSteps, List<Strip> strips) {
            this.fullWidth = fullWidth;
            this.width = width;
            this.density = density;
            this.verticalScale = verticalScale;
            this.padding = padding;
            this.gap = gap;
            this.temperatureSteps = temperatureSteps;
            this.strips = strips;
        }

        float totalHeight() {
            float total = 0f;
            for (Strip strip : strips) {
                total += strip.totalHeight();
            }
            return total;
        }

        /** Distance from the widget top to the strip. */
        float stripTop(Strip strip) {
            float top = 0f;
            for (Strip candidate : strips) {
                if (candidate == strip) {
                    break;
                }
                top += candidate.totalHeight();
            }
            return top;
        }

        /**
         * Bitmap height of a strip. Strip edges snap to whole pixels of the widget, so the
         * bitmaps add up to the rounded layout height instead of accumulating rounding errors.
         */
        int pixelHeight(Strip strip) {
            float top = stripTop(strip);
            return Math.max(1, Math.round(top + strip.totalHeight()) - Math.round(top));
        }

        /** Content height the layout needs at full size, before fitting. */
        float naturalHeight;
        /** Height reported by the launcher, or 0. */
        float availableHeight;

        Plan measured(float natural, float available) {
            naturalHeight = natural;
            availableHeight = available;
            return this;
        }

        float offsetX() {
            return (fullWidth - width) / 2f;
        }

        float tileCellWidth(int columns) {
            return (width - 2f * padding + gap) / columns;
        }
    }

    private WidgetGeometry() {
    }

    /**
     * @param widthPx widget width in pixels
     * @param heightPx available height in pixels, or 0 when the host did not report it
     * @param density pixels per dp; the widget's own scale is applied on top
     * @param temperatureSteps number of 0.5 °C steps of the temperature range
     */
    static Plan plan(WidgetConfig config, float widthPx, float heightPx, float density,
            int temperatureSteps) {
        density *= config.scalePercent / 100f;
        float natural = layoutHeight(config, widthPx, density, 1f, temperatureSteps);
        if (heightPx <= 0f) {
            return build(config, widthPx, widthPx, density, 1f, temperatureSteps, 0f, 0f)
                    .measured(natural, heightPx);
        }
        // A filled layout must end at the cell bottom whether it grows or shrinks; a content-sized
        // one fits a little inside the reported height because hosts round cell sizes.
        boolean filled = config.heightMode == WidgetConfig.HeightMode.FILL;
        float target = filled ? heightPx : heightPx * FIT_MARGIN;
        if (natural <= target) {
            if (!filled || natural >= target) {
                return build(config, widthPx, widthPx, density, 1f, temperatureSteps, 0f, 0f)
                        .measured(natural, heightPx);
            }
            return fill(config, widthPx, density, temperatureSteps, target - natural)
                    .measured(natural, heightPx);
        }
        if (config.style == WidgetConfig.Style.CONSOLE) {
            Plan flat = flattenTiles(config, widthPx, density, temperatureSteps, natural,
                    target);
            if (flat != null) {
                return flat.measured(natural, heightPx);
            }
        }
        float scale = target / natural;
        if (scale >= MIN_VERTICAL_SCALE) {
            return build(config, widthPx, widthPx, density, scale, temperatureSteps, 0f, 0f)
                    .measured(natural, heightPx);
        }
        // Squashing further would distort the controls, so narrow and centre the content.
        float low = widthPx * MIN_CONTENT_WIDTH;
        float high = widthPx;
        for (int iteration = 0; iteration < 16; iteration++) {
            float middle = (low + high) / 2f;
            if (layoutHeight(config, middle, density, MIN_VERTICAL_SCALE, temperatureSteps)
                    > target) {
                high = middle;
            } else {
                low = middle;
            }
        }
        float height = layoutHeight(config, low, density, MIN_VERTICAL_SCALE, temperatureSteps);
        // Heights are linear in the vertical scale, so a tiny widget is squashed as a last resort.
        float lastResort = height > target ? MIN_VERTICAL_SCALE * target / height
                : MIN_VERTICAL_SCALE;
        return build(config, widthPx, low, density, lastResort, temperatureSteps, 0f, 0f)
                .measured(natural, heightPx);
    }

    /**
     * Shrinks a console layout that is too tall by flattening its tile rows first, down to
     * {@link #CONSOLE_MIN_TILE_ASPECT}; only what is still missing then squashes every row.
     * Returns {@code null} when even that would squash below {@link #MIN_VERTICAL_SCALE}.
     */
    private static Plan flattenTiles(WidgetConfig config, float width, float density,
            int temperatureSteps, float natural, float target) {
        int tileRows = config.isEnabled(WidgetConfig.Block.TILES)
                ? config.tileRows().size() : 0;
        if (tileRows == 0) {
            return null;
        }
        float cut = tileSize(config, width, density) * (1f - CONSOLE_MIN_TILE_ASPECT);
        float deficit = natural - target;
        if (deficit <= cut * tileRows) {
            return build(config, width, width, density, 1f, temperatureSteps,
                    -deficit / tileRows, 0f);
        }
        // Heights stay linear in the scale when the cut is scaled with the rows.
        float scale = target / (natural - cut * tileRows);
        if (scale < MIN_VERTICAL_SCALE) {
            return null;
        }
        return build(config, width, width, density, scale, temperatureSteps, -cut * scale, 0f);
    }

    private static float tileSize(WidgetConfig config, float width, float density) {
        float padding = config.cardPaddingDp * density;
        float gap = config.gapDp * density;
        return (width - 2f * padding + gap) / config.columns - gap;
    }

    /**
     * Grows the layout by {@code extra} pixels so the last card ends at the widget's bottom edge.
     * Tile rows take the height first, up to {@link #MAX_TILE_ASPECT}; the rest is shared by the
     * cards as vertical padding (in one card, by its padding and the gaps between blocks), which
     * keeps the temperature and fan bars at their size.
     */
    private static Plan fill(WidgetConfig config, float width, float density,
            int temperatureSteps, float extra) {
        int cards = config.visibleBlocks().size();
        int tileRows = config.isEnabled(WidgetConfig.Block.TILES)
                ? config.tileRows().size() : 0;
        float tileExtra = 0f;
        if (tileRows > 0) {
            float aspect = config.style == WidgetConfig.Style.CONSOLE
                    ? CONSOLE_MAX_TILE_ASPECT : MAX_TILE_ASPECT;
            tileExtra = Math.min(extra / tileRows,
                    tileSize(config, width, density) * (aspect - 1f));
        }
        float cardExtra = cards == 0 ? 0f : (extra - tileExtra * tileRows) / cards;
        return build(config, width, width, density, 1f, temperatureSteps, tileExtra, cardExtra);
    }

    private static float layoutHeight(WidgetConfig config, float width, float density,
            float scale, int temperatureSteps) {
        return build(config, width, width, density, scale, temperatureSteps, 0f, 0f)
                .totalHeight();
    }

    private static Plan build(WidgetConfig config, float fullWidth, float width, float density,
            float scale, int temperatureSteps, float tileExtra, float cardExtra) {
        float padding = config.cardPaddingDp * density;
        float gap = config.gapDp * density;
        float verticalPadding = padding * scale + cardExtra / 2f;
        float cardGap = gap * scale;
        boolean single = config.cardLayout == WidgetConfig.CardLayout.SINGLE;
        // In one card, blocks are a card padding apart and take the fill share of a card each.
        float sectionGap = padding * scale + cardExtra;
        List<Strip> strips = new ArrayList<>();
        List<Row> cardRows = new ArrayList<>();
        List<Float> cardGaps = new ArrayList<>();
        List<WidgetConfig.Block> blocks = config.visibleBlocks();
        for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
            List<Row> rows = new ArrayList<>();
            List<Float> gaps = new ArrayList<>();
            addBlockRows(blocks.get(blockIndex), rows, gaps, config, width, density, scale,
                    padding, gap, tileExtra);
            if (rows.isEmpty()) {
                continue;
            }
            if (!single) {
                boolean lastCard = blockIndex == blocks.size() - 1;
                addCard(strips, rows, gaps, width, padding, gap, verticalPadding,
                        lastCard ? 0f : cardGap, config, temperatureSteps);
                continue;
            }
            if (!cardRows.isEmpty()) {
                // The gap after a card's last row is unused, so it becomes the section gap.
                cardGaps.set(cardGaps.size() - 1, sectionGap);
                rows.get(0).sectionStart = true;
            }
            cardRows.addAll(rows);
            cardGaps.addAll(gaps);
        }
        if (single) {
            addCard(strips, cardRows, cardGaps, width, padding, gap, verticalPadding, 0f, config,
                    temperatureSteps);
        }
        return new Plan(fullWidth, width, density, scale, padding, gap, temperatureSteps, strips);
    }

    private static void addBlockRows(WidgetConfig.Block block, List<Row> rows, List<Float> gaps,
            WidgetConfig config, float width, float density, float scale, float padding,
            float gap, float tileExtra) {
        switch (block) {
            case INFO:
                rows.add(new Row(block, RowKind.HEADER, 0, HEADER_HEIGHT_DP * density * scale));
                gaps.add(HEADER_GAP_DP * density * scale);
                break;
            case TEMPERATURE: {
                int zones = config.temperatureDual ? 2 : 1;
                if (config.style == WidgetConfig.Style.CONSOLE) {
                    for (int zone = 0; zone < zones; zone++) {
                        rows.add(new Row(block, RowKind.TEMP_VALUE, zone,
                                consoleValueRowDp(config) * density * scale));
                        gaps.add(CONSOLE_VALUE_GAP_DP * density * scale);
                        rows.add(new Row(block, RowKind.TEMPERATURE, zone,
                                CONSOLE_BAR_ROW_DP * density * scale));
                        gaps.add(TEMP_ROW_GAP_DP * density * scale);
                    }
                    break;
                }
                float rowHeight = temperatureRowDp(config) * density * scale;
                for (int zone = 0; zone < zones; zone++) {
                    rows.add(new Row(block, RowKind.TEMPERATURE, zone, rowHeight));
                    gaps.add(TEMP_ROW_GAP_DP * density * scale);
                }
                break;
            }
            case FAN:
                if (config.fanLayout == WidgetConfig.FanLayout.BAR) {
                    if (config.fanDirections) {
                        rows.add(new Row(block, RowKind.FAN_TOP, 0,
                                fanControlsRowDp(config) * density * scale));
                        gaps.add(TEMP_ROW_GAP_DP * density * scale);
                    }
                    rows.add(new Row(block, RowKind.FAN, 0, FAN_ROW_DP * density * scale));
                    gaps.add(TEMP_ROW_GAP_DP * density * scale);
                    break;
                }
                if (config.fanBar) {
                    rows.add(new Row(block, RowKind.FAN, 0, FAN_ROW_DP * density * scale));
                    gaps.add(TEMP_ROW_GAP_DP * density * scale);
                }
                for (int index : config.fanControlRows()) {
                    rows.add(new Row(block, RowKind.FAN_CONTROLS, index,
                            fanControlsRowDp(config) * density * scale));
                    gaps.add(TEMP_ROW_GAP_DP * density * scale);
                }
                break;
            default: {
                int columns = config.columns;
                int rowCount = config.tileRows().size();
                float tile = (width - 2f * padding + gap) / columns - gap;
                for (int row = 0; row < rowCount; row++) {
                    rows.add(new Row(block, RowKind.TILES, row, tile * scale + tileExtra));
                    gaps.add(gap * scale);
                }
                break;
            }
        }
    }

    private static void addCard(List<Strip> strips, List<Row> rows,
            List<Float> gaps, float width, float padding, float gap, float verticalPadding,
            float trailingGap, WidgetConfig config, int temperatureSteps) {
        if (rows.isEmpty()) {
            return;
        }
        float cardHeight = 2f * verticalPadding;
        for (int index = 0; index < rows.size(); index++) {
            cardHeight += rows.get(index).height;
            if (index < rows.size() - 1) {
                cardHeight += gaps.get(index);
            }
        }
        // Both rows of the bar fan layout share one grid, sized by the speed row.
        int[] barCells = null;
        for (Row row : rows) {
            if (row.kind == RowKind.FAN && config.fanLayout == WidgetConfig.FanLayout.BAR) {
                barCells = fanBarCells(config, width - 2f * padding, row.height);
            }
        }
        float rowTop = verticalPadding;
        float stripTop = 0f;
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            boolean last = index == rows.size() - 1;
            float stripBottom = last
                    ? cardHeight
                    : rowTop + row.height + gaps.get(index) / 2f;
            float height = stripBottom - stripTop;
            int zoneCount = 0;
            int buttonCells = 0;
            int stepStart = 0;
            int stepSpan = 1;
            int autoCells = 0;
            float zonePadding = padding;
            boolean console = config.style == WidgetConfig.Style.CONSOLE;
            switch (row.kind) {
                case TEMP_VALUE: {
                    // Square-ish cells: the outer two hold − and +, the value between is inert.
                    float valueHeight = row.height * CONSOLE_VALUE_ROW_DP
                            / consoleValueRowDp(config);
                    buttonCells = config.temperatureButtons ? 1 : 0;
                    zoneCount = config.temperatureButtons
                            ? Math.max(3, Math.round((width - 2f * padding) / valueHeight)) : 0;
                    break;
                }
                case TEMPERATURE:
                    if (console) {
                        zoneCount = temperatureSteps;
                        break;
                    }
                    buttonCells = config.temperatureButtons
                            ? buttonCells(width - 2f * padding, temperatureSteps,
                            row.height * TEMP_ROW_DP / temperatureRowDp(config))
                            : 0;
                    zoneCount = temperatureSteps + 2 * buttonCells;
                    stepStart = buttonCells;
                    break;
                case FAN_TOP:
                case FAN:
                    if (barCells != null) {
                        autoCells = barCells[0];
                        buttonCells = barCells[1];
                        stepSpan = barCells[2];
                        zoneCount = barCells[3];
                        stepStart = autoCells + buttonCells;
                        break;
                    }
                    buttonCells = config.fanButtons
                            ? buttonCells(width - 2f * padding, Hvac.FAN_SPEED_LEVEL_COUNT,
                            row.height)
                            : 0;
                    zoneCount = Hvac.FAN_SPEED_LEVEL_COUNT + 2 * buttonCells;
                    stepStart = buttonCells;
                    break;
                case FAN_CONTROLS:
                    zoneCount = config.fanControlCells(row.index);
                    break;
                case TILES:
                    zoneCount = config.columns;
                    // Cells include half a gap on each side so they centre on the tiles.
                    zonePadding = Math.max(0f, padding - gap / 2f);
                    break;
                default:
                    break;
            }
            strips.add(new Strip(row.block, row, index, index == 0, last, stripTop, cardHeight,
                    height, last ? trailingGap : 0f, rowTop - stripTop, row.height,
                    zonePadding, zoneCount, buttonCells, stepStart, stepSpan, autoCells));
            if (!last) {
                rowTop += row.height + gaps.get(index);
            }
            stripTop = stripBottom;
        }
    }

    static float temperatureRowDp(WidgetConfig config) {
        return TEMP_ROW_DP + (config.temperatureDual ? TEMP_LABEL_DP : 0f);
    }

    static float consoleValueRowDp(WidgetConfig config) {
        return CONSOLE_VALUE_ROW_DP + (config.temperatureDual ? CONSOLE_ZONE_LABEL_DP : 0f);
    }

    static float fanControlsRowDp(WidgetConfig config) {
        return config.style == WidgetConfig.Style.CONSOLE
                ? CONSOLE_SEGMENT_ROW_DP : FAN_CONTROLS_ROW_DP;
    }

    /**
     * Cells of the bar fan layout: the AUTO column, each −/+ button, each speed and the whole
     * row. AUTO is {@link #FAN_AUTO_WIDTH} speed rows wide and the buttons stay roughly square.
     * The grid is multiplied up so the preset levels split the speeds' cells evenly, and the
     * directions the row after AUTO evenly or into at least {@link #FAN_BAR_MIN_CELLS} cells.
     */
    static int[] fanBarCells(WidgetConfig config, float innerWidth, float rowHeight) {
        int auto = config.fanAuto ? 1 : 0;
        int button = config.fanButtons ? 1 : 0;
        for (int iteration = 0; iteration < 4; iteration++) {
            float cell = innerWidth / (auto + 2 * button + Hvac.FAN_SPEED_LEVEL_COUNT);
            if (config.fanAuto) {
                auto = Math.max(1, Math.round(rowHeight * FAN_AUTO_WIDTH / cell));
            }
            if (config.fanButtons) {
                button = Math.max(1, Math.round(rowHeight / cell));
            }
        }
        int rest = 2 * button + Hvac.FAN_SPEED_LEVEL_COUNT;
        int presets = ClimateCommands.fanPresets(config.fanPresetCount).length;
        int unit = presets / gcd(Hvac.FAN_SPEED_LEVEL_COUNT, presets);
        int span = unit;
        boolean uneven = config.fanDirections
                && rest * span % WidgetConfig.FAN_DIRECTIONS.length != 0;
        while (uneven && rest * span < FAN_BAR_MIN_CELLS) {
            span += unit;
        }
        return new int[]{auto * span, button * span, span, (auto + rest) * span};
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    /** Chooses how many equal cells a −/+ button spans so that it stays roughly square. */
    static int buttonCells(float innerWidth, int steps, float rowHeight) {
        int cells = 1;
        for (int iteration = 0; iteration < 4; iteration++) {
            float cell = innerWidth / (steps + 2 * cells);
            int next = Math.max(1, Math.round(rowHeight / cell));
            if (next == cells) {
                break;
            }
            cells = next;
        }
        return cells;
    }
}
