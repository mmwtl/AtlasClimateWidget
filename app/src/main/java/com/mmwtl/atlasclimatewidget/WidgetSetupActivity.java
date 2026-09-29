package com.mmwtl.atlasclimatewidget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * ACTION_APPWIDGET_CONFIGURE as a short dialog over HOME, like AtlasMediaWidget: the choices that
 * matter when placing the widget, then confirm. The launcher also opens it from the ⚙ of a placed
 * widget. MainActivity stays the full constructor.
 */
public final class WidgetSetupActivity extends ScaledActivity {
    private static final WidgetConfig.Block[] BLOCKS = WidgetConfig.Block.values();
    private static final Palette[] PALETTES = {Palette.ATLAS, Palette.BLUE, Palette.SEMANTIC};
    private static final int[] PALETTE_LABELS = {R.string.palette_atlas, R.string.palette_blue,
            R.string.setup_palette_semantic};

    private final List<Runnable> refreshers = new ArrayList<>();
    private Prefs prefs;
    private int widgetId;
    private boolean reconfigure;
    private WidgetConfig config;
    private FrameLayout previewHost;
    private TextView previewCaption;
    private TextView heightReport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        setResult(RESULT_CANCELED, resultIntent());
        if (!owns(widgetId)) {
            finish();
            return;
        }
        prefs = new Prefs(this);
        reconfigure = prefs.hasWidget(widgetId) || isPlaced(widgetId);
        // A new widget starts from the template; nothing is saved until it is confirmed.
        config = prefs.widget(widgetId);
        setContentView(buildContent());
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        getWindow().setLayout(Math.min(screenWidth - Ui.dp(this, 32), Ui.dp(this, 620)),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        refresh();
    }

    private boolean owns(int id) {
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return false;
        }
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
        return info != null && new ComponentName(this, ClimateWidgetProvider.class)
                .equals(info.provider);
    }

    /**
     * Launchers report a widget's size only after placing it, and the add flow opens this dialog
     * before that; a widget with a size is therefore being reconfigured.
     */
    private boolean isPlaced(int id) {
        Bundle options = AppWidgetManager.getInstance(this).getAppWidgetOptions(id);
        return options != null
                && options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) > 0;
    }

    private ScrollView buildContent() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 16)));
        card.setPadding(Ui.dp(this, 24), Ui.dp(this, 18), Ui.dp(this, 24), Ui.dp(this, 22));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.heading(this, reconfigure ? R.string.setup_title_reconfigure
                : R.string.setup_title_add, 20), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = Ui.text(this, "✕", 22, Ui.TEXT_SECONDARY);
        close.setGravity(Gravity.CENTER);
        close.setMinWidth(Ui.dp(this, 48));
        close.setMinHeight(Ui.dp(this, 48));
        close.setContentDescription(getString(R.string.setup_cancel));
        close.setOnClickListener(view -> finish());
        header.addView(close);
        card.addView(header, fullWrap(0));

        previewHost = new FrameLayout(this);
        previewHost.setBackground(Ui.rounded(Ui.BACKGROUND, Ui.dp(this, 8)));
        previewHost.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10));
        card.addView(previewHost, fullWrap(4));
        previewCaption = Ui.text(this, "", 12, Ui.TEXT_SECONDARY);
        previewCaption.setGravity(Gravity.CENTER);
        card.addView(previewCaption, fullWrap(6));
        heightReport = Ui.text(this, "", 12, Ui.TEXT_SECONDARY);
        heightReport.setGravity(Gravity.CENTER);
        card.addView(heightReport, fullWrap(2));

        addTitle(card, R.string.setup_blocks);
        LinearLayout blocks = segmentRow();
        for (WidgetConfig.Block block : BLOCKS) {
            TextView segment = Ui.segment(this, block.titleShortRes);
            segment.setOnClickListener(view -> {
                boolean enabled = !isShown(block);
                if (!enabled && visibleCount() <= 1) {
                    return; // An empty widget is only useful from the full constructor.
                }
                config.setEnabled(block, enabled);
                if (enabled && block == WidgetConfig.Block.TILES && config.functions.isEmpty()) {
                    config.functions.addAll(WidgetConfig.DEFAULT_FUNCTIONS);
                }
                refresh();
            });
            refreshers.add(() -> Ui.setSegmentSelected(this, segment, isShown(block)));
            addSegment(blocks, segment);
        }
        card.addView(blocks, fullWrap(8));

        addTitle(card, R.string.height_title);
        LinearLayout heights = segmentRow();
        for (WidgetConfig.HeightMode mode : WidgetConfig.HeightMode.values()) {
            TextView segment = Ui.segment(this, mode == WidgetConfig.HeightMode.FILL
                    ? R.string.setup_height_fill : R.string.setup_height_content);
            segment.setOnClickListener(view -> {
                config.heightMode = mode;
                refresh();
            });
            refreshers.add(() -> Ui.setSegmentSelected(this, segment, config.heightMode == mode));
            addSegment(heights, segment);
        }
        card.addView(heights, fullWrap(8));

        addTitle(card, R.string.fan_style_title);
        LinearLayout fan = segmentRow();
        for (WidgetConfig.FanStyle style : WidgetConfig.FanStyle.values()) {
            TextView segment = Ui.segment(this, style == WidgetConfig.FanStyle.LEVELS
                    ? R.string.setup_fan_levels : R.string.setup_fan_presets);
            segment.setOnClickListener(view -> {
                config.fanStyle = style;
                refresh();
            });
            refreshers.add(() -> {
                Ui.setSegmentSelected(this, segment, config.fanStyle == style);
                segment.setEnabled(config.fanEnabled);
                segment.setAlpha(config.fanEnabled ? 1f : 0.45f);
            });
            addSegment(fan, segment);
        }
        card.addView(fan, fullWrap(8));

        addTitle(card, R.string.tile_style_title);
        LinearLayout tiles = segmentRow();
        for (WidgetConfig.TileStyle style : WidgetConfig.TileStyle.values()) {
            TextView segment = Ui.segment(this, style.titleRes);
            segment.setOnClickListener(view -> {
                config.tileStyle = style;
                refresh();
            });
            refreshers.add(() -> {
                Ui.setSegmentSelected(this, segment, config.tileStyle == style);
                segment.setEnabled(config.tilesEnabled);
                segment.setAlpha(config.tilesEnabled ? 1f : 0.45f);
            });
            addSegment(tiles, segment);
        }
        card.addView(tiles, fullWrap(8));

        addTitle(card, R.string.palette_title);
        LinearLayout palettes = segmentRow();
        for (int index = 0; index < PALETTES.length; index++) {
            Palette palette = PALETTES[index];
            TextView segment = Ui.segment(this, PALETTE_LABELS[index]);
            segment.setOnClickListener(view -> {
                config.palette = palette;
                refresh();
            });
            refreshers.add(() -> Ui.setSegmentSelected(this, segment, config.palette == palette));
            addSegment(palettes, segment);
        }
        card.addView(palettes, fullWrap(8));

        TextView hint = Ui.text(this, R.string.setup_hint, 13, Ui.TEXT_SECONDARY);
        hint.setLineSpacing(0, 1.12f);
        card.addView(hint, fullWrap(16));

        Button confirm = Ui.button(this, reconfigure ? R.string.setup_done : R.string.setup_add);
        confirm.setTextSize(17);
        confirm.setTypeface(Typeface.DEFAULT_BOLD);
        confirm.setTextColor(Ui.ON_ACCENT);
        confirm.setBackground(Ui.rounded(Ui.ACCENT, Ui.dp(this, 10)));
        confirm.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        confirm.setOnClickListener(view -> confirm(false));
        card.addView(confirm, fullWrap(20));
        Button confirmAndOpen = Ui.button(this, reconfigure ? R.string.setup_open_settings
                : R.string.setup_add_and_open);
        confirmAndOpen.setOnClickListener(view -> confirm(true));
        card.addView(confirmAndOpen, fullWrap(10));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(card);
        return scroll;
    }

    private boolean isShown(WidgetConfig.Block block) {
        return block == WidgetConfig.Block.TILES ? config.tilesEnabled : config.isEnabled(block);
    }

    private int visibleCount() {
        int count = 0;
        for (WidgetConfig.Block block : BLOCKS) {
            if (isShown(block)) {
                count++;
            }
        }
        return count;
    }

    private void confirm(boolean openSettings) {
        if (!owns(widgetId)) {
            finish();
            return;
        }
        prefs.setWidget(widgetId, config);
        setResult(RESULT_OK, resultIntent());
        ClimateService.start(this, ClimateService.ACTION_REFRESH);
        if (openSettings) {
            startActivity(new Intent(this, MainActivity.class)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        }
        finish();
    }

    private void refresh() {
        for (Runnable refresher : refreshers) {
            refresher.run();
        }
        renderPreview();
    }

    private void renderPreview() {
        if (previewHost.getWidth() <= 0) {
            previewHost.post(this::renderPreview);
            return;
        }
        ClimateState live = ClimateService.STORE.snapshot(SystemClock.elapsedRealtime());
        boolean demo = !live.isConnected()
                || ClimateCommands.temperature(live, Hvac.ZONE_DRIVER) == null;
        previewCaption.setText(demo ? R.string.setup_preview_demo : R.string.setup_preview_live);
        WidgetViews.Size size = ClimateService.widgetSize(getApplicationContext(), widgetId);
        try {
            RemoteViews views = WidgetViews.build(getApplicationContext(), config,
                    demo ? DemoState.INSTANCE : live, prefs.carModel(), size, false, 1f);
            View preview = views.apply(this, previewHost);
            int width = previewHost.getWidth() - previewHost.getPaddingLeft()
                    - previewHost.getPaddingRight();
            // Keep the preview short enough that the choices stay on screen.
            float appDensity = getApplicationContext().getResources().getDisplayMetrics().density;
            int maxHeight = Math.round(getResources().getDisplayMetrics().heightPixels * .3f);
            WidgetGeometry.Plan plan = WidgetGeometry.plan(config, size.widthPx, size.heightPx,
                    appDensity, ClimateCommands.tempRange(DemoState.INSTANCE).steps());
            float cellHeight = size.heightPx > 0 ? size.heightPx : plan.totalHeight();
            int fitWidth = cellHeight <= 0f ? width
                    : Math.round(maxHeight * size.widthPx / cellHeight);
            previewHost.removeAllViews();
            previewHost.addView(HeightReport.framePreview(this, preview, size,
                    Math.min(width, fitWidth)));
            heightReport.setText(HeightReport.describe(this, config, plan));
        } catch (RuntimeException error) {
            AppLog.warn("Setup preview failed", error);
        }
    }

    private Intent resultIntent() {
        return new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
    }

    private void addTitle(LinearLayout card, int text) {
        TextView title = Ui.text(this, text, 15, Ui.TEXT_SECONDARY);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(title, fullWrap(16));
    }

    private LinearLayout segmentRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private void addSegment(LinearLayout row, TextView segment) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (row.getChildCount() > 0) {
            params.leftMargin = Ui.dp(this, 6);
        }
        row.addView(segment, params);
    }

    private LinearLayout.LayoutParams fullWrap(int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, topMarginDp);
        return params;
    }
}
