package com.mmwtl.atlasclimatewidget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;

import java.util.List;
import java.util.Locale;

/** Draws the widget strips planned by {@link WidgetGeometry}. */
final class WidgetRenderer {
    static final int TEMP_COLD = 0xFF4E86F2;
    static final int TEMP_WARM = 0xFFE8793B;

    private static final float GLYPH_RATIO = 44f / 86f;
    private static final float GAP_RATIO = 7f / 86f;
    private static final float BAR_HEIGHT_RATIO = 4f / 86f;
    private static final float BAR_WIDTH_RATIO = 14f / 86f;
    private static final float BAR_SPACING_RATIO = 5f / 86f;
    private static final float TOGGLE_WIDTH_RATIO = 36f / 86f;
    private static final float UNKNOWN_ALPHA = 0.4f;

    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium",
            Typeface.NORMAL);

    private final Context context;
    private final WidgetConfig config;
    private final ClimateState state;
    private final CarModel model;
    private final WidgetGeometry.Plan plan;
    private final ClimateCommands.TempRange range;
    private final float dp;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Rect bounds = new Rect();
    /** Console zone whose bar is being dragged; its large value gives way to the drag bubble. */
    private int draggedZone = -1;

    WidgetRenderer(Context context, WidgetConfig config, ClimateState state, CarModel model,
            WidgetGeometry.Plan plan) {
        this.context = context;
        this.config = config;
        this.state = state;
        this.model = model;
        this.plan = plan;
        this.range = ClimateCommands.tempRange(state);
        this.dp = plan.density;
    }

    /** Hides the console value of the zone whose bar is under the scrubber. */
    WidgetRenderer withDraggedBar(String stripKey) {
        for (int zone = 0; zone < 2; zone++) {
            if ((WidgetGeometry.RowKind.TEMPERATURE + ":" + zone).equals(stripKey)) {
                draggedZone = zone;
            }
        }
        return this;
    }

    Bitmap render(WidgetGeometry.Strip strip) {
        return render(strip, true, true);
    }

    /**
     * While a bar is dragged, the widget keeps only the strip's card and the scrubber window
     * draws only its content, so a translucent card is never drawn twice.
     */
    Bitmap render(WidgetGeometry.Strip strip, boolean card, boolean content) {
        int width = Math.max(1, Math.round(plan.fullWidth));
        int height = plan.pixelHeight(strip);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        // The bitmap starts on a whole pixel; draw at the strip's exact position inside it.
        float top = plan.stripTop(strip);
        canvas.translate(plan.offsetX(), top - Math.round(top));
        if (card) {
            drawCard(canvas, strip);
            drawDivider(canvas, strip);
        }
        if (!content) {
            return bitmap;
        }
        canvas.save();
        canvas.translate(0f, strip.contentTop);
        switch (strip.row.kind) {
            case HEADER:
                drawHeader(canvas, strip);
                break;
            case TEMP_VALUE:
                drawConsoleValue(canvas, strip);
                break;
            case TEMPERATURE:
                drawTemperature(canvas, strip);
                break;
            case FAN:
                drawFan(canvas, strip);
                break;
            case FAN_CONTROLS:
                drawFanControls(canvas, strip);
                break;
            case TILES:
                drawTiles(canvas, strip);
                break;
            default:
                break;
        }
        canvas.restore();
        return bitmap;
    }

    // ---- card --------------------------------------------------------------------------------

    private void drawCard(Canvas canvas, WidgetGeometry.Strip strip) {
        int alpha = Math.round(config.cardOpacityPercent * 2.55f);
        if (alpha <= 0) {
            return;
        }
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(config.cardColor, alpha));
        float radius = config.cardRadiusDp * dp;
        rect.set(0f, -strip.cardOffset, plan.width, strip.cardHeight - strip.cardOffset);
        canvas.save();
        // Inside a card the fill runs to the bitmap edge so snapped strips never leave a seam.
        canvas.clipRect(0f, -1f, plan.width,
                strip.lastInCard ? strip.height : strip.height + 1f);
        canvas.drawRoundRect(rect, radius, radius, paint);
        canvas.restore();
    }

    /** Hairline between blocks sharing one card, centred in the gap between them. */
    private void drawDivider(Canvas canvas, WidgetGeometry.Strip strip) {
        if (!strip.row.sectionStart) {
            return;
        }
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(Ui.TEXT, 22));
        float thickness = Math.max(1f, dp);
        canvas.drawRect(plan.padding, 0f, plan.width - plan.padding, thickness, paint);
    }

    // ---- temperature -------------------------------------------------------------------------

    private void drawHeader(Canvas canvas, WidgetGeometry.Strip strip) {
        float height = strip.contentHeight;
        float baseline = height * 0.78f;
        float size = Math.min(14f * dp, height * 0.72f);
        float x = plan.padding;
        x = drawLabelValue(canvas, context.getString(R.string.header_inside),
                formatWhole(state.sensor(Hvac.SENSOR_TEMPERATURE_INDOOR)), x, baseline, size);
        textPaint.setTypeface(Typeface.DEFAULT);
        textPaint.setColor(withAlpha(Ui.TEXT_SECONDARY, 150));
        String separator = "  •  ";
        canvas.drawText(separator, x, baseline, textPaint);
        x += textPaint.measureText(separator);
        drawLabelValue(canvas, context.getString(R.string.header_outside),
                formatWhole(state.sensor(Hvac.SENSOR_TEMPERATURE_AMBIENT)), x, baseline, size);
        if (!state.isConnected()) {
            textPaint.setTypeface(Typeface.DEFAULT);
            textPaint.setTextSize(size * 0.9f);
            textPaint.setColor(Ui.TEXT_SECONDARY);
            String status = context.getString(R.string.header_no_bridge);
            float statusWidth = textPaint.measureText(status);
            canvas.drawText(status, plan.width - plan.padding - statusWidth, baseline, textPaint);
        }
    }

    private float drawLabelValue(Canvas canvas, String label, String value, float x,
            float baseline, float size) {
        textPaint.setTextSize(size);
        textPaint.setTypeface(Typeface.DEFAULT);
        textPaint.setColor(Ui.TEXT_SECONDARY);
        String prefix = label + " ";
        canvas.drawText(prefix, x, baseline, textPaint);
        x += textPaint.measureText(prefix);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setColor(Ui.TEXT);
        canvas.drawText(value, x, baseline, textPaint);
        return x + textPaint.measureText(value);
    }

    private void drawTemperature(Canvas canvas, WidgetGeometry.Strip strip) {
        int zone = strip.row.index == 0 ? Hvac.ZONE_DRIVER : Hvac.ZONE_PASSENGER;
        if (config.style == WidgetConfig.Style.CONSOLE) {
            drawConsoleBar(canvas, strip, zone);
            return;
        }
        float top = 0f;
        float barHeight = strip.contentHeight;
        if (config.temperatureDual) {
            float labelHeight = strip.contentHeight * WidgetGeometry.TEMP_LABEL_DP
                    / WidgetGeometry.temperatureRowDp(config);
            textPaint.setTypeface(Typeface.DEFAULT);
            textPaint.setTextSize(Math.min(12f * dp, labelHeight * 0.75f));
            textPaint.setColor(Ui.TEXT_SECONDARY);
            canvas.drawText(context.getString(strip.row.index == 0
                            ? R.string.zone_driver : R.string.zone_passenger),
                    plan.padding, labelHeight * 0.72f, textPaint);
            top = labelHeight;
            barHeight -= labelHeight;
        }
        float inner = plan.width - 2f * plan.padding;
        float cell = inner / strip.zoneCount;
        float centerY = top + barHeight / 2f;
        if (strip.buttonCells > 0) {
            float buttonWidth = cell * strip.buttonCells;
            drawRoundButton(canvas, plan.padding + buttonWidth / 2f, centerY,
                    Math.min(buttonWidth, barHeight), false);
            drawRoundButton(canvas, plan.width - plan.padding - buttonWidth / 2f, centerY,
                    Math.min(buttonWidth, barHeight), true);
        }
        float x0 = plan.padding + cell * strip.buttonCells + cell / 2f;
        float x1 = plan.width - plan.padding - cell * strip.buttonCells - cell / 2f;
        float thickness = Math.max(4f * dp, barHeight * 0.2f);
        paint.setShader(null);
        paint.setColor(Ui.SURFACE_RAISED);
        rect.set(x0 - thickness / 2f, centerY - thickness / 2f,
                x1 + thickness / 2f, centerY + thickness / 2f);
        canvas.drawRoundRect(rect, thickness / 2f, thickness / 2f, paint);

        Float value = ClimateCommands.temperature(state, zone);
        float knobX = (x0 + x1) / 2f;
        String label = "--°";
        int knobColor = Ui.SURFACE_RAISED;
        int knobText = Ui.TEXT_SECONDARY;
        if (value != null) {
            float fraction = range.fraction(value);
            knobX = x0 + fraction * (x1 - x0);
            paint.setShader(new LinearGradient(x0, 0f, x1, 0f,
                    new int[]{TEMP_COLD, Ui.ACCENT, TEMP_WARM}, null, Shader.TileMode.CLAMP));
            rect.set(x0 - thickness / 2f, centerY - thickness / 2f, knobX,
                    centerY + thickness / 2f);
            canvas.drawRoundRect(rect, thickness / 2f, thickness / 2f, paint);
            paint.setShader(null);
            label = formatTemperature(value);
            knobColor = Ui.TEXT;
            knobText = Ui.BACKGROUND;
        }
        float knobHeight = Math.min(barHeight * 0.78f, 40f * dp * plan.verticalScale + 8f);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextSize(knobHeight * 0.42f);
        float knobWidth = Math.max(knobHeight, textPaint.measureText(label) + knobHeight * 0.7f);
        float left = Math.max(x0 - thickness, Math.min(x1 + thickness - knobWidth,
                knobX - knobWidth / 2f));
        paint.setColor(knobColor);
        rect.set(left, centerY - knobHeight / 2f, left + knobWidth, centerY + knobHeight / 2f);
        canvas.drawRoundRect(rect, knobHeight / 2f, knobHeight / 2f, paint);
        textPaint.setColor(knobText);
        drawCentered(canvas, label, rect.centerX(), centerY);
    }

    /**
     * Console value row: −, the zone's set value centred in large type, +. The buttons sit on
     * the card's content edges, like every other console control.
     */
    private void drawConsoleValue(Canvas canvas, WidgetGeometry.Strip strip) {
        int zone = strip.row.index == 0 ? Hvac.ZONE_DRIVER : Hvac.ZONE_PASSENGER;
        float top = 0f;
        float height = strip.contentHeight;
        float valueHeight = height * WidgetGeometry.CONSOLE_VALUE_ROW_DP
                / WidgetGeometry.consoleValueRowDp(config);
        float cx = plan.width / 2f;
        if (config.temperatureDual) {
            float labelHeight = height - valueHeight;
            textPaint.setTypeface(MEDIUM);
            textPaint.setTextSize(Math.min(11f * dp, labelHeight * 0.8f));
            textPaint.setColor(Ui.TEXT_SECONDARY);
            drawCenteredLine(canvas, context.getString(strip.row.index == 0
                    ? R.string.zone_driver : R.string.zone_passenger), cx, labelHeight / 2f);
            top = labelHeight;
        }
        float cy = top + valueHeight / 2f;
        if (strip.buttonCells > 0) {
            float diameter = consoleButton();
            drawCircleButton(canvas, plan.padding + diameter / 2f, cy, diameter, false);
            drawCircleButton(canvas, plan.width - plan.padding - diameter / 2f, cy, diameter,
                    true);
        }
        if (strip.row.index == draggedZone) {
            return;
        }
        Float value = ClimateCommands.temperature(state, zone);
        textPaint.setTypeface(MEDIUM);
        textPaint.setTextSize(valueHeight * 0.74f);
        textPaint.setColor(value == null ? Ui.TEXT_SECONDARY : Ui.TEXT);
        String label = value == null ? "--°" : formatTemperature(value);
        // Centre the digits; the degree sign hangs to the right so the number stays put.
        String digits = label.endsWith("°") ? label.substring(0, label.length() - 1) : label;
        float width = textPaint.measureText(digits);
        textPaint.getTextBounds("0", 0, 1, bounds);
        float baseline = cy - bounds.exactCenterY();
        canvas.drawText(label, cx - width / 2f, baseline, textPaint);
    }

    /** Console bar row: a thin track across the content width with a round knob. */
    private void drawConsoleBar(Canvas canvas, WidgetGeometry.Strip strip, int zone) {
        float height = strip.contentHeight;
        float cy = height / 2f;
        float thickness = Math.max(3f * dp * plan.verticalScale, height * 0.2f);
        float knob = Math.min(height * 0.8f, thickness * 3f);
        // The knob stays inside the content edges at both ends of the range.
        float x0 = plan.padding + knob / 2f;
        float x1 = plan.width - plan.padding - knob / 2f;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Ui.SURFACE_RAISED);
        rect.set(plan.padding, cy - thickness / 2f, plan.width - plan.padding,
                cy + thickness / 2f);
        canvas.drawRoundRect(rect, thickness / 2f, thickness / 2f, paint);
        Float value = ClimateCommands.temperature(state, zone);
        if (value == null) {
            return;
        }
        float knobX = x0 + range.fraction(value) * (x1 - x0);
        paint.setShader(new LinearGradient(plan.padding, 0f, plan.width - plan.padding, 0f,
                new int[]{TEMP_COLD, Ui.ACCENT, TEMP_WARM}, null, Shader.TileMode.CLAMP));
        rect.set(plan.padding, cy - thickness / 2f, knobX, cy + thickness / 2f);
        canvas.drawRoundRect(rect, thickness / 2f, thickness / 2f, paint);
        paint.setShader(null);
        paint.setColor(Ui.TEXT);
        canvas.drawCircle(knobX, cy, knob / 2f, paint);
    }

    /**
     * Diameter shared by all round console buttons: the design size, unless a value row or the
     * fan bar is too low or its button cell too narrow for it.
     */
    private float consoleButton() {
        float diameter = WidgetGeometry.CONSOLE_BUTTON_DP * dp * plan.verticalScale;
        float inner = plan.width - 2f * plan.padding;
        for (WidgetGeometry.Strip strip : plan.strips) {
            boolean value = strip.row.kind == WidgetGeometry.RowKind.TEMP_VALUE;
            if (strip.buttonCells == 0
                    || !value && strip.row.kind != WidgetGeometry.RowKind.FAN) {
                continue;
            }
            float height = value ? strip.contentHeight * WidgetGeometry.CONSOLE_VALUE_ROW_DP
                    / WidgetGeometry.consoleValueRowDp(config) : strip.contentHeight;
            diameter = Math.min(diameter, Math.min(height,
                    inner / strip.zoneCount * strip.buttonCells));
        }
        return diameter;
    }

    private void drawRoundButton(Canvas canvas, float cx, float cy, float size, boolean plus) {
        drawCircleButton(canvas, cx, cy, size * 0.8f, plus);
    }

    private void drawCircleButton(Canvas canvas, float cx, float cy, float diameter,
            boolean plus) {
        float radius = diameter / 2f;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Ui.SURFACE_RAISED);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setColor(Ui.TEXT);
        float arm = radius * 0.42f;
        float stroke = Math.max(2f, radius * 0.13f);
        rect.set(cx - arm, cy - stroke / 2f, cx + arm, cy + stroke / 2f);
        canvas.drawRoundRect(rect, stroke / 2f, stroke / 2f, paint);
        if (plus) {
            rect.set(cx - stroke / 2f, cy - arm, cx + stroke / 2f, cy + arm);
            canvas.drawRoundRect(rect, stroke / 2f, stroke / 2f, paint);
        }
    }

    // ---- fan ---------------------------------------------------------------------------------

    private void drawFan(Canvas canvas, WidgetGeometry.Strip strip) {
        float height = strip.contentHeight;
        float inner = plan.width - 2f * plan.padding;
        float cell = inner / strip.zoneCount;
        float centerY = height / 2f;
        if (strip.buttonCells > 0 && config.style == WidgetConfig.Style.CONSOLE) {
            float diameter = consoleButton();
            drawFanButton(canvas, plan.padding + diameter / 2f, centerY, diameter / 0.8f, 0.5f);
            drawFanButton(canvas, plan.width - plan.padding - diameter / 2f, centerY,
                    diameter / 0.8f, 0.72f);
        } else if (strip.buttonCells > 0) {
            float buttonWidth = cell * strip.buttonCells;
            float size = Math.min(buttonWidth, height);
            drawFanButton(canvas, plan.padding + buttonWidth / 2f, centerY, size, 0.5f);
            drawFanButton(canvas, plan.width - plan.padding - buttonWidth / 2f, centerY, size,
                    0.72f);
        }
        ClimateCommands.FanState fan = ClimateCommands.fanState(state);
        int lit = config.palette.tile(ClimateFunction.Tone.NEUTRAL);
        float segmentHeight = Math.max(4f * dp, height * 0.26f);
        float segmentGap = Math.max(2f, 3f * dp);
        float start = plan.padding + cell * strip.buttonCells;
        for (int level = 1; level <= Hvac.FAN_SPEED_LEVEL_COUNT; level++) {
            float left = start + (level - 1) * cell + segmentGap / 2f;
            float right = start + level * cell - segmentGap / 2f;
            boolean on = fan.known && !fan.auto && level <= fan.level;
            int color = on ? lit : Ui.SURFACE_RAISED;
            if (fan.auto) {
                color = withAlpha(lit, 90);
            }
            paint.setShader(null);
            paint.setColor(color);
            rect.set(left, centerY - segmentHeight / 2f, right, centerY + segmentHeight / 2f);
            canvas.drawRoundRect(rect, segmentHeight / 2f, segmentHeight / 2f, paint);
        }
        if (fan.auto) {
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextSize(Math.min(13f * dp, height * 0.36f));
            String text = "AUTO";
            float badgeHeight = textPaint.getTextSize() * 1.7f;
            float badgeWidth = textPaint.measureText(text) + badgeHeight;
            float cx = start + cell * Hvac.FAN_SPEED_LEVEL_COUNT / 2f;
            paint.setColor(lit);
            rect.set(cx - badgeWidth / 2f, centerY - badgeHeight / 2f, cx + badgeWidth / 2f,
                    centerY + badgeHeight / 2f);
            canvas.drawRoundRect(rect, badgeHeight / 2f, badgeHeight / 2f, paint);
            textPaint.setColor(Color.WHITE);
            drawCentered(canvas, text, cx, centerY);
        }
    }

    /**
     * FX11-style button row of the fan block: blowing directions, then auto-fan presets. Beside
     * the directions the presets are fans of growing size; alone they also carry their names.
     */
    private void drawFanControls(Canvas canvas, WidgetGeometry.Strip strip) {
        if (config.style == WidgetConfig.Style.CONSOLE) {
            drawSegmented(canvas, strip, strip.row.index == WidgetConfig.FAN_ROW_DIRECTIONS);
            return;
        }
        int directions = config.fanDirections ? WidgetConfig.FAN_DIRECTIONS.length : 0;
        int presets = strip.zoneCount - directions;
        int[] labels = presetLabels();
        int activePreset = ClimateCommands.fanPreset(state, config.fanPresetCount);
        boolean presetsKnown = state.property(Hvac.AUTO_FAN_SETTING, Hvac.ZONE_ROW_1_ALL) != null;
        boolean withLabels = directions == 0;

        float height = strip.contentHeight;
        float inner = plan.width - 2f * plan.padding;
        float cell = inner / strip.zoneCount;
        float gap = Math.max(4f, 8f * dp);
        float pillHeight = height * 0.9f;
        float top = (height - pillHeight) / 2f;
        float radius = Math.min(pillHeight / 2f,
                Math.min(cell, pillHeight) * config.tileRadiusPercent / 100f * 1.4f);
        for (int index = 0; index < strip.zoneCount; index++) {
            boolean direction = index < directions;
            boolean on;
            boolean known;
            if (direction) {
                ClimateCommands.TileState tileState = ClimateCommands.tileState(
                        WidgetConfig.FAN_DIRECTIONS[index], state, model);
                on = tileState.active;
                known = tileState.known;
            } else {
                on = index - directions == activePreset;
                known = presetsKnown;
            }
            float left = plan.padding + index * cell + (index == 0 ? 0f : gap / 2f);
            float right = plan.padding + (index + 1) * cell
                    - (index == strip.zoneCount - 1 ? 0f : gap / 2f);
            int background;
            int content;
            if (on && config.filledActive) {
                background = config.palette.tile(ClimateFunction.Tone.NEUTRAL);
                content = Color.WHITE;
            } else if (on) {
                background = config.palette.softTile(ClimateFunction.Tone.NEUTRAL);
                content = config.palette.softContent(ClimateFunction.Tone.NEUTRAL);
            } else {
                background = Ui.SURFACE_RAISED;
                content = Ui.TEXT;
            }
            if (!known) {
                content = withAlpha(content, Math.round(255 * UNKNOWN_ALPHA));
            }
            paint.setShader(null);
            paint.setColor(background);
            rect.set(left, top, right, top + pillHeight);
            canvas.drawRoundRect(rect, radius, radius, paint);
            float cx = (left + right) / 2f;
            float cy = top + pillHeight / 2f;
            float iconMax = Math.min(right - left, pillHeight);
            if (direction) {
                drawIcon(canvas, WidgetConfig.FAN_DIRECTIONS[index].iconRes, cx, cy,
                        iconMax * 0.92f, content);
                continue;
            }
            int preset = index - directions;
            float grow = presets <= 1 ? 1f : preset / (float) (presets - 1);
            float icon = iconMax * (0.52f + 0.34f * grow);
            if (!withLabels) {
                drawIcon(canvas, R.drawable.ic_fan, cx, cy, icon, content);
                continue;
            }
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextSize(Math.min(15f * dp, pillHeight * 0.34f));
            textPaint.setColor(content);
            String label = context.getString(labels[Math.min(preset, labels.length - 1)]);
            float iconSlot = pillHeight * 0.66f;
            float iconGap = pillHeight * 0.14f;
            float textWidth = textPaint.measureText(label);
            if (iconSlot + iconGap + textWidth > (right - left) * 0.9f) {
                // Narrow cells keep only the label, shrunk to fit if needed.
                float room = (right - left) * 0.9f;
                if (textWidth > room) {
                    textPaint.setTextSize(textPaint.getTextSize() * room / textWidth);
                }
                drawCentered(canvas, label, cx, cy);
                continue;
            }
            float start = cx - (iconSlot + iconGap + textWidth) / 2f;
            drawIcon(canvas, R.drawable.ic_fan, start + iconSlot / 2f, cy,
                    pillHeight * (0.44f + 0.22f * grow), content);
            canvas.drawText(label, start + iconSlot + iconGap,
                    cy - textBounds(label).exactCenterY(), textPaint);
        }
    }

    private int[] presetLabels() {
        return config.fanPresetCount >= ClimateCommands.FAN_PRESETS_FIVE.length
                ? new int[]{R.string.fan_preset_quiet, R.string.fn_fan_soft_short,
                R.string.fn_fan_normal_short, R.string.fan_preset_strong,
                R.string.fan_preset_max}
                : new int[]{R.string.fn_fan_soft_short, R.string.fn_fan_normal_short,
                R.string.fn_fan_strong_short};
    }

    /**
     * Console fan row: one pill split into segments, the blowing directions with icon and name,
     * or the auto-fan presets by name so they never read as fan speeds.
     */
    private void drawSegmented(Canvas canvas, WidgetGeometry.Strip strip, boolean directions) {
        int[] labels = presetLabels();
        int activePreset = ClimateCommands.fanPreset(state, config.fanPresetCount);
        boolean presetsKnown = state.property(Hvac.AUTO_FAN_SETTING, Hvac.ZONE_ROW_1_ALL) != null;
        float height = strip.contentHeight;
        float inner = plan.width - 2f * plan.padding;
        float cell = inner / strip.zoneCount;
        float radius = Math.min(height / 2f,
                height * config.tileRadiusPercent / 100f * 1.4f);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Ui.SURFACE_RAISED);
        rect.set(plan.padding, 0f, plan.width - plan.padding, height);
        canvas.drawRoundRect(rect, radius, radius, paint);

        float inset = Math.max(2f, 3f * dp);
        // One text size for the whole row, and direction names on all segments or on none.
        String[] names = new String[strip.zoneCount];
        for (int index = 0; index < names.length; index++) {
            names[index] = context.getString(directions
                    ? WidgetConfig.FAN_DIRECTIONS[index].shortRes
                    : labels[Math.min(index, labels.length - 1)]);
        }
        float icon = directions ? height * 0.62f : 0f;
        float iconGap = directions ? height * 0.14f : 0f;
        float room = cell - 2f * inset - height * 0.3f - icon - iconGap;
        textPaint.setTypeface(MEDIUM);
        textPaint.setTextSize(Math.min(14f * dp * plan.verticalScale, height * 0.36f));
        float widest = 0f;
        for (String name : names) {
            widest = Math.max(widest, textPaint.measureText(name));
        }
        boolean withNames = true;
        if (widest > room && directions) {
            withNames = textPaint.getTextSize() * room / widest >= 10f * dp * plan.verticalScale;
        }
        if (widest > room) {
            textPaint.setTextSize(textPaint.getTextSize() * room / widest);
        }
        boolean previousOn = false;
        for (int index = 0; index < strip.zoneCount; index++) {
            boolean on;
            boolean known;
            if (directions) {
                ClimateCommands.TileState tileState = ClimateCommands.tileState(
                        WidgetConfig.FAN_DIRECTIONS[index], state, model);
                on = tileState.active;
                known = tileState.known;
            } else {
                on = index == activePreset;
                known = presetsKnown;
            }
            float left = plan.padding + index * cell;
            float right = left + cell;
            int content = Ui.TEXT;
            if (on) {
                paint.setColor(config.filledActive
                        ? config.palette.tile(ClimateFunction.Tone.NEUTRAL)
                        : config.palette.softTile(ClimateFunction.Tone.NEUTRAL));
                content = config.filledActive ? Color.WHITE
                        : config.palette.softContent(ClimateFunction.Tone.NEUTRAL);
                float innerRadius = Math.max(0f, radius - inset);
                rect.set(left + inset, inset, right - inset, height - inset);
                canvas.drawRoundRect(rect, innerRadius, innerRadius, paint);
            } else if (index > 0 && !previousOn) {
                paint.setColor(withAlpha(Ui.TEXT, 22));
                float thickness = Math.max(1f, dp);
                canvas.drawRect(left - thickness / 2f, height * 0.28f, left + thickness / 2f,
                        height * 0.72f, paint);
            }
            previousOn = on;
            if (!known) {
                content = withAlpha(content, Math.round(255 * UNKNOWN_ALPHA));
            }
            float cx = (left + right) / 2f;
            float cy = height / 2f;
            textPaint.setColor(content);
            if (!directions) {
                drawCenteredLine(canvas, names[index], cx, cy);
                continue;
            }
            int iconRes = WidgetConfig.FAN_DIRECTIONS[index].iconRes;
            if (!withNames) {
                drawIcon(canvas, iconRes, cx, cy, icon, content);
                continue;
            }
            float width = textPaint.measureText(names[index]);
            float start = cx - (icon + iconGap + width) / 2f;
            drawIcon(canvas, iconRes, start + icon / 2f, cy, icon, content);
            drawCenteredLine(canvas, names[index], start + icon + iconGap + width / 2f, cy);
        }
    }

    private Rect textBounds(String text) {
        textPaint.getTextBounds(text, 0, text.length(), bounds);
        return bounds;
    }

    private void drawFanButton(Canvas canvas, float cx, float cy, float size, float iconRatio) {
        paint.setShader(null);
        paint.setColor(Ui.SURFACE_RAISED);
        canvas.drawCircle(cx, cy, size * 0.4f, paint);
        drawIcon(canvas, R.drawable.ic_fan, cx, cy, size * iconRatio, Ui.TEXT);
    }

    // ---- tiles -------------------------------------------------------------------------------

    private void drawTiles(Canvas canvas, WidgetGeometry.Strip strip) {
        int columns = config.columns;
        float cellWidth = plan.tileCellWidth(columns);
        float tileWidth = cellWidth - plan.gap;
        List<ClimateFunction[]> rows = config.tileRows();
        if (strip.row.index >= rows.size()) {
            return;
        }
        ClimateFunction[] row = rows.get(strip.row.index);
        for (int column = 0; column < columns; column++) {
            ClimateFunction function = row[column];
            if (function == null) {
                continue;
            }
            float left = plan.padding + column * cellWidth;
            rect.set(left, 0f, left + tileWidth, strip.contentHeight);
            drawTile(canvas, new RectF(rect), function,
                    ClimateCommands.tileState(function, state, model));
        }
    }

    private void drawTile(Canvas canvas, RectF tile, ClimateFunction function,
            ClimateCommands.TileState tileState) {
        boolean iconStyle = config.tileStyle == WidgetConfig.TileStyle.ICON;
        boolean withLabel = config.tileStyle == WidgetConfig.TileStyle.TILE_LABEL;
        boolean active = tileState.active;
        float size = Math.min(tile.width(), tile.height());
        // Flattened console tiles have spare width; the glyph stack still fits their height.
        float glyphSize = config.style == WidgetConfig.Style.CONSOLE
                ? Math.min(tile.width(), tile.height() * 1.2f) : size;
        int content;
        int unlit;
        if (iconStyle) {
            content = active ? config.palette.tile(function.tone) : Ui.TEXT;
            unlit = withAlpha(Ui.TEXT, 56);
        } else {
            int background;
            if (active && config.filledActive) {
                background = config.palette.tile(function.tone);
                content = Color.WHITE;
                unlit = withAlpha(Color.WHITE, 90);
            } else if (active) {
                background = config.palette.softTile(function.tone);
                content = config.palette.softContent(function.tone);
                unlit = withAlpha(Color.WHITE, 56);
            } else {
                background = Ui.SURFACE_RAISED;
                content = Ui.TEXT;
                unlit = withAlpha(Color.WHITE, 56);
            }
            float radius = size * config.tileRadiusPercent / 100f;
            paint.setShader(null);
            paint.setColor(background);
            canvas.drawRoundRect(tile, radius, radius, paint);
        }
        if (!tileState.known) {
            content = withAlpha(content, Math.round(255 * UNKNOWN_ALPHA));
        }

        boolean indicator = function.indicatorCount() > 0;
        float glyph = glyphSize * GLYPH_RATIO * (withLabel ? 0.82f : 1f);
        float gap = glyphSize * GAP_RATIO;
        float barHeight = Math.max(2f * dp * 0.75f, glyphSize * BAR_HEIGHT_RATIO);
        float labelSize = Math.max(9f * dp, Math.min(13f * dp, size * 0.13f));
        float labelGap = size * 0.06f;
        float total = glyph + (indicator ? gap + barHeight : 0f)
                + (withLabel ? labelGap + labelSize : 0f);
        float top = tile.centerY() - total / 2f;
        float cx = tile.centerX();
        float glyphCy = top + glyph / 2f;

        if (function.glyph != null) {
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setColor(content);
            // The console sets every text glyph in one size, so MAX and AUTO match.
            boolean small = function.glyph.length() > 3
                    || config.style == WidgetConfig.Style.CONSOLE;
            textPaint.setTextSize(glyph * (small ? 0.36f : 0.44f));
            float maxWidth = tile.width() * 0.84f;
            float measured = textPaint.measureText(function.glyph);
            if (measured > maxWidth) {
                textPaint.setTextSize(textPaint.getTextSize() * maxWidth / measured);
            }
            drawCentered(canvas, function.glyph, cx, glyphCy);
        } else {
            int icon = function.iconRes;
            if (function.levelIcons != null) {
                icon = function.levelIcons[Math.max(0,
                        Math.min(function.levelIcons.length - 1, tileState.level))];
            }
            drawIcon(canvas, icon, cx, glyphCy, glyph, content);
        }

        float y = top + glyph;
        if (indicator) {
            y += gap;
            drawIndicator(canvas, function, tileState, cx, y, barHeight, glyphSize, content,
                    unlit);
            y += barHeight;
        }
        if (withLabel) {
            y += labelGap;
            textPaint.setTypeface(Typeface.DEFAULT);
            textPaint.setTextSize(labelSize);
            textPaint.setColor(active && !iconStyle ? content
                    : withAlpha(Ui.TEXT_SECONDARY, tileState.known ? 255 : 110));
            String label = TextUtils.ellipsize(context.getString(function.shortRes), textPaint,
                    tile.width() * 0.92f, TextUtils.TruncateAt.END).toString();
            float width = textPaint.measureText(label);
            canvas.drawText(label, cx - width / 2f, y + labelSize * 0.8f, textPaint);
        }
    }

    private void drawIndicator(Canvas canvas, ClimateFunction function,
            ClimateCommands.TileState tileState, float cx, float top, float barHeight,
            float size, int lit, int unlit) {
        paint.setShader(null);
        if (function.kind == ClimateFunction.Kind.TOGGLE
                || function.kind == ClimateFunction.Kind.BLOW) {
            float width = size * TOGGLE_WIDTH_RATIO;
            paint.setColor(tileState.active ? lit : unlit);
            rect.set(cx - width / 2f, top, cx + width / 2f, top + barHeight);
            canvas.drawRoundRect(rect, barHeight / 2f, barHeight / 2f, paint);
            return;
        }
        int count = function.indicatorCount();
        float width = size * BAR_WIDTH_RATIO;
        float spacing = size * BAR_SPACING_RATIO;
        float total = count * width + (count - 1) * spacing;
        float left = cx - total / 2f;
        for (int index = 0; index < count; index++) {
            boolean on = index < tileState.level;
            int color = unlit;
            if (on && function.kind == ClimateFunction.Kind.SELECT) {
                color = tileState.active ? lit : withAlpha(lit, 120);
            } else if (on) {
                color = lit;
            }
            paint.setColor(color);
            rect.set(left, top, left + width, top + barHeight);
            canvas.drawRoundRect(rect, barHeight / 2f, barHeight / 2f, paint);
            left += width + spacing;
        }
    }

    // ---- helpers -----------------------------------------------------------------------------

    private void drawIcon(Canvas canvas, int res, float cx, float cy, float size, int color) {
        Drawable drawable = context.getDrawable(res);
        if (drawable == null) {
            return;
        }
        drawable = drawable.mutate();
        drawable.setTint(color);
        int half = Math.round(size / 2f);
        drawable.setBounds(Math.round(cx) - half, Math.round(cy) - half,
                Math.round(cx) + half, Math.round(cy) + half);
        drawable.draw(canvas);
    }

    /** Centres a word by the font's metrics, so labels with and without descenders align. */
    private void drawCenteredLine(Canvas canvas, String text, float cx, float cy) {
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float width = textPaint.measureText(text);
        canvas.drawText(text, cx - width / 2f, cy - (metrics.ascent + metrics.descent) / 2f,
                textPaint);
    }

    private void drawCentered(Canvas canvas, String text, float cx, float cy) {
        textPaint.getTextBounds(text, 0, text.length(), bounds);
        float width = textPaint.measureText(text);
        canvas.drawText(text, cx - width / 2f, cy - bounds.exactCenterY(), textPaint);
    }

    String formatTemperature(float value) {
        return formatTemperature(value, range);
    }

    static String formatTemperature(float value, ClimateCommands.TempRange range) {
        if (value <= range.min) {
            return "LO";
        }
        if (value >= range.max) {
            return "HI";
        }
        return String.format(Locale.US, "%.1f°", value);
    }

    private static String formatWhole(Double value) {
        return value == null ? "—" : Math.round(value) + "°";
    }

    static int withAlpha(int color, int alpha) {
        int base = Color.alpha(color) * Math.max(0, Math.min(255, alpha)) / 255;
        return (color & 0x00FFFFFF) | (base << 24);
    }
}
