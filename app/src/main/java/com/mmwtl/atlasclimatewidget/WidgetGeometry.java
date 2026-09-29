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
    static final float FAN_CONTROLS_ROW_DP = 46f;
    static final float MIN_VERTICAL_SCALE = 0.55f;
    static final float FIT_MARGIN = 0.98f;
    static final float MIN_CONTENT_WIDTH = 0.3f;
    /** Tallest tile relative to its width when tiles stretch to fill the widget. */
    static final float MAX_TILE_ASPECT = 1.6f;
    /** Room for per-strip pixel rounding so a filled widget never overflows its cell. */
    static final float FILL_SAFETY_PX = 4f;

    enum RowKind {
        HEADER,
        TEMPERATURE,
        FAN,
        FAN_CONTROLS,
        TILES
    }

    static final class Row {
        final RowKind kind;
        /** Temperature zone index or tile row index. */
        final int index;
        float height;

        Row(RowKind kind, int index, float height) {
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

        Strip(WidgetConfig.Block block, Row row, int stripIndexInCard, boolean firstInCard,
                boolean lastInCard, float cardOffset, float cardHeight, float height,
                float trailingGap, float contentTop, float contentHeight, float zonePadding,
                int zoneCount, int buttonCells) {
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
        if (natural <= heightPx * FIT_MARGIN) {
            float target = heightPx - FILL_SAFETY_PX;
            if (config.heightMode != WidgetConfig.HeightMode.FILL || natural >= target) {
                return build(config, widthPx, widthPx, density, 1f, temperatureSteps, 0f, 0f)
                        .measured(natural, heightPx);
            }
            return fill(config, widthPx, density, temperatureSteps, target - natural)
                    .measured(natural, heightPx);
        }
        // Fit a little inside the reported height; hosts round cell sizes.
        float target = heightPx * FIT_MARGIN;
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
     * Grows the layout by {@code extra} pixels so the last card ends at the widget's bottom edge.
     * Tile rows take the height first, up to {@link #MAX_TILE_ASPECT}; the rest is shared by the
     * cards as vertical padding, which keeps the temperature and fan bars at their size.
     */
    private static Plan fill(WidgetConfig config, float width, float density,
            int temperatureSteps, float extra) {
        int cards = config.visibleBlocks().size();
        int tileRows = config.isEnabled(WidgetConfig.Block.TILES)
                ? (config.functions.size() + config.columns - 1) / config.columns : 0;
        float tileExtra = 0f;
        if (tileRows > 0) {
            float padding = config.cardPaddingDp * density;
            float gap = config.gapDp * density;
            float tile = (width - 2f * padding + gap) / config.columns - gap;
            tileExtra = Math.min(extra / tileRows, tile * (MAX_TILE_ASPECT - 1f));
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
        List<Strip> strips = new ArrayList<>();
        List<WidgetConfig.Block> blocks = config.visibleBlocks();
        for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
            WidgetConfig.Block block = blocks.get(blockIndex);
            List<Row> rows = new ArrayList<>();
            List<Float> gaps = new ArrayList<>();
            switch (block) {
                case TEMPERATURE: {
                    if (config.temperatureHeader) {
                        rows.add(new Row(RowKind.HEADER, 0, HEADER_HEIGHT_DP * density * scale));
                        gaps.add(HEADER_GAP_DP * density * scale);
                    }
                    int zones = config.temperatureDual ? 2 : 1;
                    float rowHeight = temperatureRowDp(config) * density * scale;
                    for (int zone = 0; zone < zones; zone++) {
                        rows.add(new Row(RowKind.TEMPERATURE, zone, rowHeight));
                        gaps.add(TEMP_ROW_GAP_DP * density * scale);
                    }
                    break;
                }
                case FAN:
                    if (config.fanBar) {
                        rows.add(new Row(RowKind.FAN, 0, FAN_ROW_DP * density * scale));
                        gaps.add(TEMP_ROW_GAP_DP * density * scale);
                    }
                    if (config.fanControlCount() > 0) {
                        rows.add(new Row(RowKind.FAN_CONTROLS, 0,
                                FAN_CONTROLS_ROW_DP * density * scale));
                        gaps.add(0f);
                    }
                    break;
                default: {
                    int columns = config.columns;
                    int rowCount = (config.functions.size() + columns - 1) / columns;
                    float tile = (width - 2f * padding + gap) / columns - gap;
                    for (int row = 0; row < rowCount; row++) {
                        rows.add(new Row(RowKind.TILES, row, tile * scale + tileExtra));
                        gaps.add(gap * scale);
                    }
                    break;
                }
            }
            boolean lastCard = blockIndex == blocks.size() - 1;
            addCard(strips, block, rows, gaps, width, padding, gap, verticalPadding,
                    lastCard ? 0f : cardGap, config, temperatureSteps);
        }
        return new Plan(fullWidth, width, density, scale, padding, gap, temperatureSteps, strips);
    }

    private static void addCard(List<Strip> strips, WidgetConfig.Block block, List<Row> rows,
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
            float zonePadding = padding;
            switch (row.kind) {
                case TEMPERATURE:
                    buttonCells = config.temperatureButtons
                            ? buttonCells(width - 2f * padding, temperatureSteps,
                            row.height * TEMP_ROW_DP / temperatureRowDp(config))
                            : 0;
                    zoneCount = temperatureSteps + 2 * buttonCells;
                    break;
                case FAN:
                    buttonCells = config.fanButtons
                            ? buttonCells(width - 2f * padding, Hvac.FAN_SPEED_LEVEL_COUNT,
                            row.height)
                            : 0;
                    zoneCount = Hvac.FAN_SPEED_LEVEL_COUNT + 2 * buttonCells;
                    break;
                case FAN_CONTROLS:
                    zoneCount = config.fanControlCount();
                    break;
                case TILES:
                    zoneCount = config.columns;
                    // Cells include half a gap on each side so they centre on the tiles.
                    zonePadding = Math.max(0f, padding - gap / 2f);
                    break;
                default:
                    break;
            }
            strips.add(new Strip(block, row, index, index == 0, last, stripTop, cardHeight,
                    height, last ? trailingGap : 0f, rowTop - stripTop, row.height,
                    zonePadding, zoneCount, buttonCells));
            if (!last) {
                rowTop += row.height + gaps.get(index);
            }
            stripTop = stripBottom;
        }
    }

    static float temperatureRowDp(WidgetConfig config) {
        return TEMP_ROW_DP + (config.temperatureDual ? TEMP_LABEL_DP : 0f);
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
