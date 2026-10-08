package com.mmwtl.atlasclimatewidget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
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
    /**
     * The OneOS climate dock is a 124 px overlay at the bottom of the 160 dpi screen that reports
     * no insets, so windows are laid out underneath it; the dialog keeps clear of it by hand.
     */
    private static final int CLIMATE_DOCK_DP = 124;
    private static final int SCREEN_GAP_DP = 16;

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
        // Centre in the space above the dock; the scroll view caps the height to that space.
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.gravity = Gravity.CENTER;
        attributes.y = -dockHeight() / 2;
        getWindow().setAttributes(attributes);
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
            Ui.addSegment(blocks, segment);
        }
        card.addView(blocks, fullWrap(8));

        addTitle(card, R.string.style_title);
        LinearLayout styles = segmentRow();
        for (WidgetConfig.Style style : WidgetConfig.Style.values()) {
            TextView segment = Ui.segment(this, style.titleRes);
            segment.setOnClickListener(view -> {
                config.style = style;
                refresh();
            });
            refreshers.add(() -> Ui.setSegmentSelected(this, segment, config.style == style));
            Ui.addSegment(styles, segment);
        }
        card.addView(styles, fullWrap(8));

        addTitle(card, R.string.card_layout_title);
        LinearLayout layouts = segmentRow();
        for (WidgetConfig.CardLayout layout : WidgetConfig.CardLayout.values()) {
            TextView segment = Ui.segment(this, layout.titleRes);
            segment.setOnClickListener(view -> {
                config.cardLayout = layout;
                refresh();
            });
            refreshers.add(() -> Ui.setSegmentSelected(this, segment,
                    config.cardLayout == layout));
            Ui.addSegment(layouts, segment);
        }
        card.addView(layouts, fullWrap(8));

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
            Ui.addSegment(heights, segment);
        }
        card.addView(heights, fullWrap(8));

        addTitle(card, R.string.setup_fan_parts);
        LinearLayout fanLayouts = segmentRow();
        for (WidgetConfig.FanLayout layout : WidgetConfig.FanLayout.values()) {
            TextView segment = Ui.segment(this, layout.titleRes);
            segment.setOnClickListener(view -> {
                config.fanLayout = layout;
                if (!config.hasFanParts()) {
                    config.fanBar = true;
                }
                refresh();
            });
            refreshers.add(() -> {
                Ui.setSegmentSelected(this, segment, config.fanLayout == layout);
                segment.setEnabled(config.fanEnabled);
                segment.setAlpha(config.fanEnabled ? 1f : 0.45f);
            });
            Ui.addSegment(fanLayouts, segment);
        }
        card.addView(fanLayouts, fullWrap(8));

        LinearLayout bar = segmentRow();
        int[] barLabels = {R.string.setup_fan_auto, R.string.setup_fan_directions};
        for (int part = 0; part < barLabels.length; part++) {
            boolean auto = part == 0;
            TextView segment = Ui.segment(this, barLabels[part]);
            segment.setOnClickListener(view -> {
                if (auto) {
                    config.fanAuto = !config.fanAuto;
                } else {
                    config.fanDirections = !config.fanDirections;
                }
                refresh();
            });
            refreshers.add(() -> {
                Ui.setSegmentSelected(this, segment,
                        auto ? config.fanAuto : config.fanDirections);
                segment.setEnabled(config.fanEnabled);
                segment.setAlpha(config.fanEnabled ? 1f : 0.45f);
            });
            Ui.addSegment(bar, segment);
        }
        refreshers.add(() -> bar.setVisibility(
                config.fanLayout == WidgetConfig.FanLayout.BAR ? View.VISIBLE : View.GONE));
        card.addView(bar, fullWrap(8));

        LinearLayout fan = segmentRow();
        int[] partLabels = {R.string.setup_fan_bar, R.string.setup_fan_directions,
                R.string.setup_fan_presets};
        for (int part = 0; part < partLabels.length; part++) {
            int index = part;
            TextView segment = Ui.segment(this, partLabels[part]);
            segment.setOnClickListener(view -> {
                boolean[] parts = {config.fanBar, config.fanDirections, config.fanPresets};
                parts[index] = !parts[index];
                if (!parts[0] && !parts[1] && !parts[2]) {
                    return; // The fan block keeps at least one part.
                }
                config.fanBar = parts[0];
                config.fanDirections = parts[1];
                config.fanPresets = parts[2];
                refresh();
            });
            refreshers.add(() -> {
                boolean[] parts = {config.fanBar, config.fanDirections, config.fanPresets};
                Ui.setSegmentSelected(this, segment, parts[index]);
                segment.setEnabled(config.fanEnabled);
                segment.setAlpha(config.fanEnabled ? 1f : 0.45f);
            });
            Ui.addSegment(fan, segment);
        }
        refreshers.add(() -> fan.setVisibility(
                config.fanLayout == WidgetConfig.FanLayout.ROWS ? View.VISIBLE : View.GONE));
        card.addView(fan, fullWrap(8));

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

        ScrollView scroll = new ScrollView(this) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                // The first pass of a wrap-content window is unbounded; cap it by the screen.
                int maxHeight = maxDialogHeight();
                if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                    maxHeight = Math.min(maxHeight, MeasureSpec.getSize(heightMeasureSpec));
                }
                super.onMeasure(widthMeasureSpec,
                        MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
            }
        };
        scroll.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 16)));
        scroll.setClipToOutline(true);
        scroll.addView(card);
        return scroll;
    }

    private int maxDialogHeight() {
        WindowMetrics metrics = getWindowManager().getCurrentWindowMetrics();
        Insets bars = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        int height = metrics.getBounds().height() - bars.top - bars.bottom - dockHeight()
                - 2 * Ui.dp(this, SCREEN_GAP_DP);
        return Math.max(Ui.dp(this, 200), height);
    }

    /** The dock height in real pixels; it does not follow the app's interface scale. */
    private int dockHeight() {
        return Math.round(CLIMATE_DOCK_DP
                * getApplicationContext().getResources().getDisplayMetrics().density);
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

    private LinearLayout.LayoutParams fullWrap(int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, topMarginDp);
        return params;
    }
}
