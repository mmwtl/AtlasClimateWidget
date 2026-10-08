package com.mmwtl.atlasclimatewidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.Gravity;
import android.widget.RemoteViews;

import java.util.List;
import java.util.Locale;

/** Builds the RemoteViews tree of one widget. */
final class WidgetViews {
    /** Keeps the RemoteViews bitmap payload well below host and Binder limits. */
    static final long MAX_BITMAP_BYTES = 6L * 1024L * 1024L;
    static final String CONTROL_SCHEME = "atlasclimate";

    private WidgetViews() {
    }

    static final class Size {
        final int widthPx;
        final int heightPx;

        Size(int widthPx, int heightPx) {
            this.widthPx = widthPx;
            this.heightPx = heightPx;
        }
    }

    /**
     * @param interactive whether touch zones get PendingIntents; the settings preview is inert
     * @param quality 1 for full resolution, smaller values reduce the bitmap payload
     */
    static RemoteViews build(Context context, WidgetConfig config, ClimateState state,
            CarModel model, Size size, boolean interactive, float quality) {
        return build(context, AppWidgetManager.INVALID_APPWIDGET_ID, config, state, model, size,
                interactive, quality, null, null);
    }

    /**
     * @param widgetId widget whose bars open the drag scrubber; invalid for the preview
     * @param scrubbedStrip {@link #stripKey} of the bar under an open scrubber, drawn card-only
     * @param rendered receives the strip bitmaps in order, or {@code null}
     */
    static RemoteViews build(Context context, int widgetId, WidgetConfig config,
            ClimateState state, CarModel model, Size size, boolean interactive, float quality,
            String scrubbedStrip, List<Bitmap> rendered) {
        float density = context.getResources().getDisplayMetrics().density;
        int steps = ClimateCommands.tempRange(state).steps();
        WidgetGeometry.Plan full = WidgetGeometry.plan(config, size.widthPx, size.heightPx,
                density, steps);
        float bytes = full.fullWidth * full.totalHeight() * 4f;
        float scale = quality;
        if (bytes * scale * scale > MAX_BITMAP_BYTES) {
            scale = (float) Math.sqrt(MAX_BITMAP_BYTES / bytes);
        }
        // Upscaled strips round once more in the host, up to a pixel each; leave room for it.
        WidgetGeometry.Plan plan = scale >= 0.999f
                ? full
                : WidgetGeometry.plan(config, size.widthPx * scale,
                Math.max(0f, size.heightPx - full.strips.size()) * scale, density * scale,
                steps);

        String packageName = context.getPackageName();
        RemoteViews root = new RemoteViews(packageName, R.layout.widget_root);
        root.removeAllViews(R.id.widget_root);
        root.setInt(R.id.widget_root, "setGravity", gravity(config));
        if (plan.strips.isEmpty()) {
            RemoteViews empty = new RemoteViews(packageName, R.layout.widget_empty);
            if (interactive) {
                empty.setOnClickPendingIntent(R.id.widget_empty, openSettings(context));
            }
            root.addView(R.id.widget_root, empty);
            return root;
        }
        WidgetRenderer renderer = new WidgetRenderer(context, config, state, model, plan)
                .withDraggedBar(scrubbedStrip);
        boolean auto = ClimateCommands.fanState(state).auto;
        for (WidgetGeometry.Strip strip : plan.strips) {
            RemoteViews views = new RemoteViews(packageName, R.layout.widget_strip);
            Bitmap bitmap = renderer.render(strip, true,
                    !stripKey(strip).equals(scrubbedStrip));
            if (rendered != null) {
                rendered.add(bitmap);
            }
            views.setImageViewBitmap(R.id.strip_image, bitmap);
            int padding = Math.round((strip.zonePadding + plan.offsetX()) / scale);
            views.setViewPadding(R.id.strip_zones, padding, 0, padding, 0);
            for (int cell = 0; cell < strip.zoneCount; cell++) {
                String control = interactive ? control(config, strip, cell, auto) : null;
                RemoteViews zone = new RemoteViews(packageName,
                        control == null ? R.layout.widget_zone_blank : R.layout.widget_zone);
                if (control != null) {
                    zone.setOnClickPendingIntent(R.id.zone,
                            widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                                    && isScrubbable(control)
                                    ? scrubIntent(context, widgetId, cell, control)
                                    : controlIntent(context, control));
                }
                views.addView(R.id.strip_zones, zone);
            }
            root.addView(R.id.widget_root, views);
        }
        return root;
    }

    /** A filled layout matches the cell; a content-sized one sits where the user chose. */
    static int gravity(WidgetConfig config) {
        if (config.heightMode == WidgetConfig.HeightMode.FILL) {
            return Gravity.TOP;
        }
        switch (config.verticalAlign) {
            case CENTER:
                return Gravity.CENTER_VERTICAL;
            case BOTTOM:
                return Gravity.BOTTOM;
            default:
                return Gravity.TOP;
        }
    }

    static String control(WidgetConfig config, WidgetGeometry.Strip strip, int cell) {
        return control(config, strip, cell, false);
    }

    /**
     * Control encoded in the touch cell's URI, or {@code null} for an inert cell.
     *
     * @param auto whether climate AUTO is on; the bar fan layout shows presets then
     */
    static String control(WidgetConfig config, WidgetGeometry.Strip strip, int cell,
            boolean auto) {
        int buttons = strip.buttonCells;
        switch (strip.row.kind) {
            case TEMP_VALUE: {
                int zone = strip.row.index == 0 ? Hvac.ZONE_DRIVER : Hvac.ZONE_PASSENGER;
                if (buttons == 0) {
                    return null;
                }
                if (cell == 0) {
                    return "tempstep/" + zone + "/-1";
                }
                return cell == strip.zoneCount - 1 ? "tempstep/" + zone + "/1" : null;
            }
            case TEMPERATURE: {
                int zone = strip.row.index == 0 ? Hvac.ZONE_DRIVER : Hvac.ZONE_PASSENGER;
                if (cell < buttons) {
                    return "tempstep/" + zone + "/-1";
                }
                if (cell >= strip.zoneCount - buttons) {
                    return "tempstep/" + zone + "/1";
                }
                return "temp/" + zone + "/" + (cell - buttons);
            }
            case FAN_TOP:
                return fanBarControl(config, strip, cell, auto);
            case FAN:
                if (config.fanLayout == WidgetConfig.FanLayout.BAR) {
                    return fanBarControl(config, strip, cell, auto);
                }
                if (cell < buttons) {
                    return "fanstep/-1";
                }
                if (cell >= strip.zoneCount - buttons) {
                    return "fanstep/1";
                }
                return "fan/" + (cell - buttons + 1);
            case FAN_CONTROLS: {
                if (config.style == WidgetConfig.Style.CONSOLE) {
                    return strip.row.index == WidgetConfig.FAN_ROW_DIRECTIONS
                            ? "fn/" + WidgetConfig.FAN_DIRECTIONS[cell].name()
                            : "fanpreset/" + cell;
                }
                int directions = config.fanDirections ? WidgetConfig.FAN_DIRECTIONS.length : 0;
                return cell < directions
                        ? "fn/" + WidgetConfig.FAN_DIRECTIONS[cell].name()
                        : "fanpreset/" + (cell - directions);
            }
            case TILES: {
                List<ClimateFunction[]> rows = config.tileRows();
                ClimateFunction function = strip.row.index < rows.size()
                        ? rows.get(strip.row.index)[cell] : null;
                return function == null ? null : "fn/" + function.name();
            }
            default:
                return null;
        }
    }

    /** Both rows of the bar fan layout: AUTO, then directions or speeds, or the presets. */
    private static String fanBarControl(WidgetConfig config, WidgetGeometry.Strip strip,
            int cell, boolean auto) {
        if (cell < strip.autoCells) {
            return "fn/" + ClimateFunction.AUTO.name();
        }
        if (auto) {
            int presets = ClimateCommands.fanPresets(config.fanPresetCount).length;
            return "fanpreset/" + strip.partAt(cell, presets);
        }
        if (strip.row.kind == WidgetGeometry.RowKind.FAN_TOP) {
            int directions = WidgetConfig.FAN_DIRECTIONS.length;
            return "fn/" + WidgetConfig.FAN_DIRECTIONS[strip.partAt(cell, directions)].name();
        }
        if (cell < strip.stepStart) {
            return "fanstep/-1";
        }
        int step = (cell - strip.stepStart) / strip.stepSpan;
        return step < Hvac.FAN_SPEED_LEVEL_COUNT ? "fan/" + (step + 1) : "fanstep/1";
    }

    static String stripKey(WidgetGeometry.Strip strip) {
        return strip.row.kind + ":" + strip.row.index;
    }

    /** Temperature steps and fan levels open the scrubber; −/+ buttons and presets do not. */
    static boolean isScrubbable(String control) {
        return control.startsWith("temp/") || control.startsWith("fan/");
    }

    /**
     * Opens the drag scrubber over the bar. The host adds the tapped cell's screen bounds as a
     * fill-in, which an immutable PendingIntent would drop; the explicit component, data and
     * extras cannot be replaced by the host. The tapped cell places the window, since a step
     * may span several cells.
     */
    static PendingIntent scrubIntent(Context context, int widgetId, int cell, String control) {
        Intent intent = new Intent(context, ScrubActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse(CONTROL_SCHEME + "://scrub/" + widgetId + "/" + cell + "/"
                        + control))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        return PendingIntent.getActivity(context, widgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

    static PendingIntent controlIntent(Context context, String control) {
        Intent intent = new Intent(context, ClimateService.class)
                .setAction(ClimateService.ACTION_CONTROL)
                .setData(Uri.parse(CONTROL_SCHEME + "://control/" + control));
        return PendingIntent.getForegroundService(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent openSettings(Context context) {
        Intent intent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static String describe(Size size, float density) {
        return String.format(Locale.US, "%d×%d dp", Math.round(size.widthPx / density),
                Math.round(size.heightPx / density));
    }
}
