package com.mmwtl.atlasclimatewidget;

import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RemoteViews;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Settings editor with Blocks, Tiles, Look and System tabs pinned under the title, like the
 * widgetkit settings of AtlasAppWidget and AtlasMediaWidget. The widget selector and the live
 * preview sit in the pinned header on the three layout tabs.
 */
public final class MainActivity extends ScaledActivity {
    private static final int TEMPLATE = AppWidgetManager.INVALID_APPWIDGET_ID;
    private static final long STATUS_REFRESH_MS = 3_000L;
    private static final String STATE_TAB = "settings_tab";
    static final String EXTRA_DISPLAY_SETTINGS = "display_settings";
    static final int TAB_BLOCKS = 0;
    static final int TAB_TILES = 1;
    static final int TAB_LOOK = 2;
    static final int TAB_SYSTEM = 3;
    private static final int[] TAB_LABELS = {R.string.tab_blocks, R.string.tab_tiles,
            R.string.tab_look, R.string.tab_system};
    /** The pinned preview may take this share of the screen; the settings scroll below it. */
    private static final float PREVIEW_MAX_SCREEN_SHARE = 0.28f;
    private static final int REQUEST_IMPORT = 41;
    private static final int BACKUP_MAX_BYTES = 256 * 1024;
    private static final long OVERLAY_INPUT_SAVE_DELAY_MS = 400L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable statusTask = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            handler.postDelayed(this, STATUS_REFRESH_MS);
        }
    };
    private final Runnable accessibilityStateRefresh = () -> handler.post(this::refreshStatus);
    private final Runnable saveOverlayPositionTask = this::saveOverlayPosition;

    private final LinearLayout[] tabPages = new LinearLayout[TAB_LABELS.length];
    private final TextView[] tabButtons = new TextView[TAB_LABELS.length];
    private int selectedTab;

    private Prefs prefs;
    private int editedWidget = TEMPLATE;
    private WidgetConfig config;
    private int[] widgetIds = new int[0];
    /** Layout ids in the selector's order: the placed widgets, the window, the template. */
    private int[] layoutIds = {Prefs.PANEL_ID, TEMPLATE};

    private ScrollView settingsScroll;
    private LinearLayout previewPanel;
    private TextView overlayPermissionsLabel;
    private Button overlayPermission;
    private Button usagePermission;
    private Button windowsPermission;
    private TextView overlayStatusText;
    private Button startOverlayButton;
    private Button stopOverlayButton;
    private LinearLayout overlaySizeCard;
    private SeekBar overlayWidthSlider;
    private EditText overlayWidthInput;
    private Switch overlayAutoHeightSwitch;
    private LinearLayout overlayManualHeightGroup;
    private SeekBar overlayHeightSlider;
    private EditText overlayHeightInput;
    private TextView overlaySizeReport;
    private LinearLayout overlayPositionCard;
    private Switch overlayDragHandleSwitch;
    private Spinner overlayCornerSpinner;
    private EditText overlayPosXInput;
    private EditText overlayPosYInput;
    private OverlayCorner displayedOverlayCorner;
    private boolean updatingOverlayControls;
    private boolean overlayPositionDirty;
    private LinearLayout widgetHeightCard;
    private TextView previewTitle;
    private TextView statusText;
    private Spinner widgetSpinner;
    private TileDragLayer previewHost;
    private TextView previewCaption;
    private TextView heightTitle;
    private TextView heightHint;
    private TextView heightReport;
    private LinearLayout blocksHost;
    private LinearLayout tilesHost;
    private LinearLayout functionsHost;
    private LinearLayout availableHost;
    private LinearLayout levelsHost;
    private LinearLayout offHost;
    private LinearLayout appearanceHost;
    private LinearLayout cardsHost;
    private LinearLayout colorsHost;
    /** The theme whose colours the Look tab edits and the preview shows; not stored. */
    private boolean editNight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        widgetIds = prefs.isWidgetMode() ? ClimateService.widgetIds(this) : new int[0];
        editedWidget = requestedWidget(getIntent());
        if (editedWidget == TEMPLATE) {
            editedWidget = !prefs.isWidgetMode() ? Prefs.OVERLAY_ID
                    : widgetIds.length > 0 ? widgetIds[0] : TEMPLATE;
        }
        config = loadConfig(editedWidget);
        View content = buildContent(savedInstanceState == null
                ? (getIntent().getBooleanExtra(EXTRA_DISPLAY_SETTINGS, false) ? TAB_SYSTEM : TAB_BLOCKS)
                : savedInstanceState.getInt(STATE_TAB, TAB_BLOCKS));
        setContentView(content);
        Ui.applySystemBarInsets(content);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, selectedTab);
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getBooleanExtra(EXTRA_DISPLAY_SETTINGS, false)) {
            selectTab(TAB_SYSTEM);
        }
        int requested = requestedWidget(intent);
        if (requested != TEMPLATE && requested != editedWidget) {
            editedWidget = requested;
            config = loadConfig(requested);
            rebuildEditor();
            reloadWidgetList();
        }
    }

    /** The widget setup dialog opens the full constructor on its own widget. */
    private int requestedWidget(android.content.Intent intent) {
        int requested = intent == null ? TEMPLATE
                : intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, TEMPLATE);
        if (requested == Prefs.PANEL_ID || (requested == Prefs.OVERLAY_ID && !prefs.isWidgetMode())) {
            return requested;
        }
        for (int id : widgetIds) {
            if (id == requested) {
                return id;
            }
        }
        return TEMPLATE;
    }

    @Override
    protected void onResume() {
        super.onResume();
        ClimateService.start(this, ClimateService.ACTION_START);
        ClimateService.setStateListener(this::refreshLive);
        AccessibilityWindowState.setStateListener(accessibilityStateRefresh);
        reloadWidgetList();
        if (editedWidget == Prefs.OVERLAY_ID) {
            refreshOverlayGeometryControls();
        }
        handler.post(statusTask);
        previewHost.post(this::refreshPreview);
    }

    @Override
    protected void onPause() {
        AccessibilityWindowState.clearStateListener(accessibilityStateRefresh);
        ClimateService.setStateListener(null);
        handler.removeCallbacks(statusTask);
        handler.removeCallbacks(saveOverlayPositionTask);
        saveOverlayPosition();
        super.onPause();
    }

    // ---- layout ------------------------------------------------------------------------------

    private View buildContent(int initialTab) {
        LinearLayout root = vertical();
        root.setBackgroundColor(Ui.BACKGROUND);

        LinearLayout stickyHeader = vertical();
        stickyHeader.setClipChildren(false);
        stickyHeader.setPadding(Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 24),
                Ui.dp(this, 12));
        stickyHeader.setBackgroundColor(Ui.BACKGROUND);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        // The version sits on the title's baseline, the pair centred in the row.
        LinearLayout titleText = new LinearLayout(this);
        titleText.setOrientation(LinearLayout.HORIZONTAL);
        titleText.addView(Ui.heading(this, R.string.app_name, 24));
        TextView version = Ui.text(this, versionLabel(), 13, Ui.TEXT_SECONDARY);
        version.setSingleLine(true);
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        versionParams.leftMargin = Ui.dp(this, 10);
        titleText.addView(version, versionParams);
        titleRow.addView(titleText, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        previewTitle = Ui.text(this, R.string.preview_title, 13, Ui.TEXT_SECONDARY);
        titleRow.addView(previewTitle);
        ImageView donate = new ImageView(this);
        donate.setImageResource(R.drawable.ic_heart);
        donate.setColorFilter(Ui.ACCENT);
        int heartPad = Ui.dp(this, 8);
        donate.setPadding(heartPad, heartPad, heartPad, heartPad);
        donate.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 8)));
        donate.setContentDescription(getString(R.string.donate_title));
        donate.setOnClickListener(view -> DonateDialog.show(this));
        LinearLayout.LayoutParams heartParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 40), Ui.dp(this, 40));
        heartParams.leftMargin = Ui.dp(this, 12);
        titleRow.addView(donate, heartParams);
        stickyHeader.addView(titleRow);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        for (int index = 0; index < TAB_LABELS.length; index++) {
            int tab = index;
            TextView button = Ui.segment(this, TAB_LABELS[index]);
            button.setTextSize(15);
            button.setOnClickListener(view -> selectTab(tab));
            tabButtons[index] = button;
            Ui.addSegment(tabs, button);
        }
        // Tabs sit above the preview so they stay put when a tab hides the preview.
        Ui.topMargin(tabs, 12);
        stickyHeader.addView(tabs);
        stickyHeader.addView(buildPreviewPanel());

        settingsScroll = new ScrollView(this);
        settingsScroll.setFillViewport(true);
        settingsScroll.setBackgroundColor(Ui.BACKGROUND);
        LinearLayout content = vertical();
        content.setPadding(Ui.dp(this, 24), Ui.dp(this, 8), Ui.dp(this, 24), Ui.dp(this, 42));
        settingsScroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int index = 0; index < tabPages.length; index++) {
            tabPages[index] = vertical();
            content.addView(tabPages[index], new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        buildBlocksTab(tabPages[TAB_BLOCKS]);
        buildTilesTab(tabPages[TAB_TILES]);
        buildLookTab(tabPages[TAB_LOOK]);
        buildSystemTab(tabPages[TAB_SYSTEM]);
        rebuildEditor();

        root.addView(stickyHeader, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(settingsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        selectTab(initialTab >= 0 && initialTab < tabPages.length ? initialTab : TAB_BLOCKS);
        return root;
    }

    private void selectTab(int tab) {
        selectedTab = tab;
        for (int index = 0; index < tabPages.length; index++) {
            tabPages[index].setVisibility(index == tab ? View.VISIBLE : View.GONE);
            Ui.setSegmentSelected(this, tabButtons[index], index == tab);
        }
        int preview = tab == TAB_SYSTEM ? View.GONE : View.VISIBLE;
        previewPanel.setVisibility(preview);
        previewTitle.setVisibility(preview);
        settingsScroll.scrollTo(0, 0);
        // A hidden preview is not redrawn on changes; catch up once it has a width again.
        previewHost.post(this::refreshPreview);
    }

    /** Widget selector, preview and data caption, pinned under the tabs. */
    private LinearLayout buildPreviewPanel() {
        previewPanel = vertical();
        Ui.topMargin(previewPanel, 10);
        widgetSpinner = new Spinner(this);
        widgetSpinner.setBackground(Ui.rounded(Ui.SURFACE_RAISED, Ui.dp(this, 8)));
        widgetSpinner.setContentDescription(getString(R.string.edited_widget_title));
        widgetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int target = position < layoutIds.length ? layoutIds[position] : TEMPLATE;
                if (target != editedWidget) {
                    editedWidget = target;
                    config = loadConfig(target);
                    rebuildEditor();
                    refreshPreview();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        previewPanel.addView(widgetSpinner);

        previewHost = new TileDragLayer(this);
        previewHost.setListener(new TileDragLayer.Listener() {
            @Override
            public boolean onTileMoved(ClimateFunction function, int row, int column) {
                if (!config.dropTile(function, row, column)) {
                    return false;
                }
                // The order is saved once on release; meanwhile only the preview follows.
                refreshPreview();
                return true;
            }

            @Override
            public void onTileDropped(boolean moved) {
                if (moved) {
                    save();
                    rebuildFunctions();
                }
            }
        });
        previewHost.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8),
                Ui.dp(this, 10));
        previewHost.setBackground(Ui.rounded(Ui.BACKGROUND, Ui.dp(this, 8)));
        previewHost.setContentDescription(getString(R.string.preview_title));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 8);
        previewPanel.addView(previewHost, params);
        previewCaption = Ui.text(this, R.string.preview_live, 13, Ui.TEXT_SECONDARY);
        Ui.topMargin(previewCaption, 6);
        previewPanel.addView(previewCaption);
        return previewPanel;
    }

    private void buildBlocksTab(LinearLayout page) {
        widgetHeightCard = Ui.card(this);
        heightTitle = Ui.heading(this, R.string.height_title, 20);
        heightHint = hint(R.string.height_fill_hint);
        widgetHeightCard.addView(heightTitle);
        widgetHeightCard.addView(heightHint);
        heightReport = hint(R.string.height_report_unknown);
        widgetHeightCard.addView(heightReport);
        page.addView(widgetHeightCard);

        LinearLayout blocks = Ui.card(this);
        blocks.addView(Ui.heading(this, R.string.blocks_title, 20));
        blocks.addView(hint(R.string.blocks_hint));
        blocksHost = vertical();
        blocks.addView(blocksHost);
        page.addView(blocks);

        page.addView(buildLayoutCard());
    }

    private void buildTilesTab(LinearLayout page) {
        LinearLayout tiles = Ui.card(this);
        tiles.addView(Ui.heading(this, R.string.tiles_title, 20));
        tilesHost = vertical();
        tiles.addView(tilesHost);
        page.addView(tiles);

        LinearLayout functions = Ui.card(this);
        functions.addView(Ui.heading(this, R.string.functions_title, 20));
        functions.addView(hint(R.string.functions_hint));
        functionsHost = vertical();
        functions.addView(functionsHost);
        page.addView(functions);

        availableHost = collapsibleCard(page, R.string.functions_available,
                R.string.functions_available_summary);
        levelsHost = collapsibleCard(page, R.string.tile_levels_title,
                R.string.tile_levels_summary);
        offHost = collapsibleCard(page, R.string.off_targets_title,
                R.string.off_targets_summary);
    }

    private void buildLookTab(LinearLayout page) {
        LinearLayout appearance = Ui.card(this);
        appearance.addView(Ui.heading(this, R.string.appearance_title, 20));
        appearanceHost = vertical();
        appearance.addView(appearanceHost);
        page.addView(appearance);

        overlaySizeCard = buildOverlaySizeCard();
        page.addView(overlaySizeCard);
        overlayPositionCard = buildOverlayPositionCard();
        page.addView(overlayPositionCard);

        cardsHost = collapsibleCard(page, R.string.cards_title, R.string.cards_summary);
        editNight = WidgetTheme.of(WidgetTheme.themed(this)).night;
        colorsHost = collapsibleCard(page, R.string.colors_title, R.string.colors_summary);
    }

    private void buildSystemTab(LinearLayout page) {
        page.addView(buildStatusCard());
        page.addView(buildDisplayModeCard());
        if (!prefs.isWidgetMode()) {
            page.addView(buildOverlayControlCard());
            page.addView(buildOverlayVisibilityCard());
        }
        page.addView(buildBehaviourCard());
        page.addView(buildThemeCard());
        page.addView(buildPanelCard());
        page.addView(buildFuelCard());
        page.addView(buildInterfaceCard());
        page.addView(buildBackupCard());
        page.addView(buildGStoreCard());
        page.addView(buildDonateCard());
    }

    private LinearLayout buildDisplayModeCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, R.string.display_mode_title, 20));
        LinearLayout modes = new LinearLayout(this);
        Ui.topMargin(modes, 10);
        TextView widget = Ui.segment(this, R.string.display_mode_widget);
        TextView overlay = Ui.segment(this, R.string.display_mode_overlay);
        Ui.addSegment(modes, widget);
        Ui.addSegment(modes, overlay);
        Ui.setSegmentSelected(this, widget, prefs.isWidgetMode());
        Ui.setSegmentSelected(this, overlay, !prefs.isWidgetMode());
        widget.setOnClickListener(view -> changeDisplayMode(true));
        overlay.setOnClickListener(view -> changeDisplayMode(false));
        card.addView(modes);
        boolean widgetMode = prefs.isWidgetMode();
        card.addView(hint(widgetMode ? R.string.display_mode_widget_hint : R.string.overlay_hint));
        if (widgetMode) {
            Button add = Ui.button(this, R.string.add_android_widget);
            Ui.topMargin(add, 10);
            add.setOnClickListener(view -> {
                AppWidgetManager manager = AppWidgetManager.getInstance(this);
                if (!manager.isRequestPinAppWidgetSupported() || !manager.requestPinAppWidget(
                        new ComponentName(this, ClimateWidgetProvider.class), null, null)) {
                    Toast.makeText(this, R.string.display_mode_widget_hint, Toast.LENGTH_LONG).show();
                }
            });
            card.addView(add);
            return card;
        }
        Button edit = Ui.button(this, R.string.overlay_edit);
        Ui.topMargin(edit, 10);
        edit.setOnClickListener(view -> {
            editedWidget = Prefs.OVERLAY_ID;
            config = loadConfig(editedWidget);
            reloadWidgetList();
            rebuildEditor();
            selectTab(TAB_BLOCKS);
        });
        card.addView(edit);
        return card;
    }

    private LinearLayout buildOverlayControlCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, R.string.overlay_title, 20));
        overlayPermissionsLabel = label(R.string.overlay_permissions_title);
        card.addView(overlayPermissionsLabel);
        overlayPermission = settingsButton(card, R.string.overlay_allow_draw,
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
        usagePermission = settingsButton(card, R.string.overlay_allow_usage,
                Settings.ACTION_USAGE_ACCESS_SETTINGS, null);
        windowsPermission = settingsButton(card, R.string.overlay_allow_windows,
                Settings.ACTION_ACCESSIBILITY_SETTINGS, null);

        overlayStatusText = Ui.text(this, "", 14, Ui.TEXT_SECONDARY);
        Ui.topMargin(overlayStatusText, 12);
        card.addView(overlayStatusText);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Ui.topMargin(actions, 10);
        startOverlayButton = Ui.button(this, R.string.overlay_start);
        startOverlayButton.setOnClickListener(view -> startOverlay());
        stopOverlayButton = Ui.button(this, R.string.overlay_stop);
        stopOverlayButton.setOnClickListener(view -> stopOverlay());
        actions.addView(startOverlayButton, weighted(0, 8));
        actions.addView(stopOverlayButton, weighted(0, 0));
        card.addView(actions);

        Switch autoStart = switchRow(R.string.overlay_auto_start, prefs.overlayAutoStart());
        autoStart.setOnCheckedChangeListener((view, checked) -> {
            prefs.setOverlayAutoStart(checked);
            if (checked && !ClimateOverlay.hasPermissions(this)) {
                Toast.makeText(this, R.string.overlay_auto_start_permission_warning,
                        Toast.LENGTH_LONG).show();
            }
        });
        card.addView(autoStart);

        LinearLayout headUnitNote = collapsible(card, R.string.head_unit_note_title,
                R.string.head_unit_note_summary);
        Ui.topMargin(card.getChildAt(card.getChildCount() - 2), 12);
        headUnitNote.addView(hint(R.string.head_unit_note));
        return card;
    }

    private LinearLayout buildOverlayVisibilityCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, R.string.overlay_visibility_title, 20));
        Switch appList = switchRow(R.string.overlay_app_list, prefs.overlayAppListOnly());
        appList.setOnCheckedChangeListener((view, checked) -> prefs.raw().edit()
                .putBoolean(Prefs.KEY_SHOW_ONLY_IN_APP_LIST, checked).apply());
        card.addView(appList);
        card.addView(hint(R.string.overlay_app_list_hint));
        addSlider(card, getString(R.string.overlay_hide_threshold),
                WindowVisibilityPolicy.MIN_HIDE_THRESHOLD_PERCENT,
                WindowVisibilityPolicy.MAX_HIDE_THRESHOLD_PERCENT, prefs.freeformHideThresholdPercent(),
                value -> value + "%",
                value -> prefs.raw().edit().putInt(Prefs.KEY_FREEFORM_HIDE_THRESHOLD, value).apply(),
                null);
        card.addView(hint(R.string.overlay_hide_threshold_hint));
        return card;
    }

    private LinearLayout buildOverlaySizeCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, R.string.overlay_size, 20));
        overlaySizeReport = hint(R.string.overlay_size);
        card.addView(overlaySizeReport);

        card.addView(label(R.string.overlay_width));
        LinearLayout widthRow = new LinearLayout(this);
        widthRow.setOrientation(LinearLayout.HORIZONTAL);
        widthRow.setGravity(Gravity.CENTER_VERTICAL);
        Ui.topMargin(widthRow, 6);
        overlayWidthSlider = new SeekBar(this);
        overlayWidthSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (updatingOverlayControls || !fromUser) {
                    return;
                }
                overlayWidthInput.clearFocus();
                setOverlayText(overlayWidthInput, value);
                applyOverlayWidth(value);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        widthRow.addView(overlayWidthSlider, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        overlayWidthInput = overlayNumberInput(R.string.overlay_width);
        overlayWidthInput.addTextChangedListener(textWatcher(this::onOverlayWidthTextChanged));
        overlayWidthInput.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus && !updatingOverlayControls) {
                refreshOverlaySizeControls();
            }
        });
        widthRow.addView(overlayWidthInput, new LinearLayout.LayoutParams(
                Ui.dp(this, 78), ViewGroup.LayoutParams.WRAP_CONTENT));
        widthRow.addView(Ui.text(this, "px", 13, Ui.TEXT_SECONDARY));
        card.addView(widthRow);

        overlayAutoHeightSwitch = switchRow(R.string.overlay_auto_height, true);
        overlayAutoHeightSwitch.setOnCheckedChangeListener((view, checked) -> {
            if (updatingOverlayControls) {
                return;
            }
            Rect bounds = ClimateOverlay.displayBounds(this);
            int width = prefs.overlayWidthPx(bounds.width());
            if (checked) {
                prefs.setOverlaySize(width, 0);
            } else {
                ClimateState state = ClimateService.STORE.snapshot(SystemClock.elapsedRealtime());
                WidgetViews.Size current = ClimateOverlay.size(getApplicationContext(), bounds,
                        prefs, config, state);
                int min = Math.min(Prefs.OVERLAY_MIN_HEIGHT_PX, Math.max(1, bounds.height()));
                int nextHeight = Math.max(min, Math.min(bounds.height(), current.heightPx));
                prefs.setOverlaySize(width, nextHeight);
            }
            refreshOverlaySizeControls();
            refreshPreview();
        });
        card.addView(overlayAutoHeightSwitch);

        overlayManualHeightGroup = vertical();
        overlayManualHeightGroup.addView(label(R.string.overlay_height));
        LinearLayout heightRow = new LinearLayout(this);
        heightRow.setOrientation(LinearLayout.HORIZONTAL);
        heightRow.setGravity(Gravity.CENTER_VERTICAL);
        Ui.topMargin(heightRow, 6);
        overlayHeightSlider = new SeekBar(this);
        overlayHeightSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (updatingOverlayControls || !fromUser) {
                    return;
                }
                overlayHeightInput.clearFocus();
                setOverlayText(overlayHeightInput, value);
                applyOverlayHeight(value);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        heightRow.addView(overlayHeightSlider, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        overlayHeightInput = overlayNumberInput(R.string.overlay_height);
        overlayHeightInput.addTextChangedListener(textWatcher(this::onOverlayHeightTextChanged));
        overlayHeightInput.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus && !updatingOverlayControls) {
                refreshOverlaySizeControls();
            }
        });
        heightRow.addView(overlayHeightInput, new LinearLayout.LayoutParams(
                Ui.dp(this, 78), ViewGroup.LayoutParams.WRAP_CONTENT));
        heightRow.addView(Ui.text(this, "px", 13, Ui.TEXT_SECONDARY));
        overlayManualHeightGroup.addView(heightRow);
        card.addView(overlayManualHeightGroup);

        Button resetSize = Ui.button(this, R.string.overlay_reset_size);
        Ui.topMargin(resetSize, 12);
        resetSize.setOnClickListener(view -> {
            Rect bounds = ClimateOverlay.displayBounds(this);
            prefs.resetOverlaySize(bounds.width());
            refreshOverlaySizeControls();
            refreshPreview();
            Toast.makeText(this, R.string.overlay_size_reset, Toast.LENGTH_SHORT).show();
        });
        card.addView(resetSize);
        return card;
    }

    private LinearLayout buildOverlayPositionCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, R.string.overlay_position_title, 20));

        overlayDragHandleSwitch = switchRow(R.string.overlay_drag_handle, prefs.overlayDragHandle());
        overlayDragHandleSwitch.setOnCheckedChangeListener((view, checked) -> {
            if (!updatingOverlayControls) {
                prefs.setOverlayDragHandle(checked);
            }
        });
        card.addView(overlayDragHandleSwitch);
        card.addView(hint(R.string.overlay_drag_handle_hint));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        Ui.topMargin(header, 14);
        header.addView(Ui.text(this, R.string.overlay_corner, 13, Ui.TEXT_SECONDARY),
                positionColumnParams(1.35f, 0));
        header.addView(Ui.text(this, R.string.overlay_pos_x, 13, Ui.TEXT_SECONDARY),
                positionColumnParams(1f, 8));
        header.addView(Ui.text(this, R.string.overlay_pos_y, 13, Ui.TEXT_SECONDARY),
                positionColumnParams(1f, 8));
        card.addView(header);

        OverlayCorner[] choices = OverlayCorner.values();
        List<String> cornerLabels = new ArrayList<>();
        for (OverlayCorner choice : choices) {
            cornerLabels.add(choice.label);
        }
        overlayCornerSpinner = new Spinner(this);
        overlayCornerSpinner.setContentDescription(getString(R.string.overlay_corner));
        overlayCornerSpinner.setAdapter(styledSpinnerAdapter(cornerLabels));
        overlayCornerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (updatingOverlayControls || position < 0 || position >= choices.length) {
                    return;
                }
                OverlayCorner next = choices[position];
                if (next != displayedOverlayCorner) {
                    reanchorOverlayCorner(next);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Ui.topMargin(row, 6);
        row.addView(dropdownField(overlayCornerSpinner), positionColumnParams(1.35f, 0));

        overlayPosXInput = overlayNumberInput(R.string.overlay_pos_x_desc);
        overlayPosXInput.setHint("X");
        row.addView(pxInputCell(overlayPosXInput), positionColumnParams(1f, 8));

        overlayPosYInput = overlayNumberInput(R.string.overlay_pos_y_desc);
        overlayPosYInput.setHint("Y");
        row.addView(pxInputCell(overlayPosYInput), positionColumnParams(1f, 8));
        card.addView(row);

        TextWatcher watcher = textWatcher(this::onOverlayPositionTextChanged);
        overlayPosXInput.addTextChangedListener(watcher);
        overlayPosYInput.addTextChangedListener(watcher);
        View.OnFocusChangeListener focusSaver = (view, hasFocus) -> {
            if (!hasFocus && !updatingOverlayControls) {
                handler.removeCallbacks(saveOverlayPositionTask);
                saveOverlayPosition();
            }
        };
        overlayPosXInput.setOnFocusChangeListener(focusSaver);
        overlayPosYInput.setOnFocusChangeListener(focusSaver);

        card.addView(hint(R.string.overlay_position_hint));

        Button resetPos = Ui.button(this, R.string.overlay_reset_position);
        Ui.topMargin(resetPos, 12);
        resetPos.setOnClickListener(view -> {
            handler.removeCallbacks(saveOverlayPositionTask);
            overlayPositionDirty = false;
            float density = getApplicationContext().getResources().getDisplayMetrics().density;
            prefs.resetOverlayPosition(density);
            refreshOverlayPositionControls();
            Toast.makeText(this, R.string.overlay_position_reset, Toast.LENGTH_SHORT).show();
        });
        card.addView(resetPos);
        return card;
    }

    private LinearLayout pxInputCell(EditText input) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.HORIZONTAL);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        cell.addView(input, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        cell.addView(Ui.text(this, "px", 13, Ui.TEXT_SECONDARY));
        return cell;
    }

    private EditText overlayNumberInput(int descriptionRes) {
        EditText field = new EditText(this);
        field.setContentDescription(getString(descriptionRes));
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setSingleLine(true);
        field.setSelectAllOnFocus(true);
        field.setTextColor(Ui.TEXT);
        field.setHintTextColor(Ui.TEXT_SECONDARY);
        field.setTextSize(15);
        field.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        field.setBackground(Ui.rounded(Ui.SURFACE_RAISED, Ui.dp(this, 8)));
        return field;
    }

    private LinearLayout.LayoutParams positionColumnParams(float weight, int leftMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        params.leftMargin = Ui.dp(this, leftMarginDp);
        return params;
    }

    private FrameLayout dropdownField(Spinner spinner) {
        FrameLayout field = new FrameLayout(this);
        field.setBackground(Ui.rounded(Ui.SURFACE_RAISED, Ui.dp(this, 8)));
        spinner.setBackgroundColor(0x00000000);
        spinner.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 26), 0);
        field.addView(spinner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView arrow = Ui.text(this, "▾", 16, Ui.ACCENT);
        arrow.setGravity(Gravity.CENTER);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams arrowParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        arrowParams.rightMargin = Ui.dp(this, 10);
        field.addView(arrow, arrowParams);
        field.setOnClickListener(view -> spinner.performClick());
        return field;
    }

    private ArrayAdapter<String> styledSpinnerAdapter(List<String> labels) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, labels) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                return style(super.getView(position, convertView, parent));
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return style(super.getDropDownView(position, convertView, parent));
            }

            private View style(View view) {
                if (view instanceof TextView) {
                    TextView text = (TextView) view;
                    text.setTextColor(Ui.TEXT);
                    text.setTextSize(15);
                    text.setPadding(Ui.dp(MainActivity.this, 12), Ui.dp(MainActivity.this, 10),
                            Ui.dp(MainActivity.this, 12), Ui.dp(MainActivity.this, 10));
                }
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private static TextWatcher textWatcher(Runnable onAfterChange) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                onAfterChange.run();
            }
        };
    }

    private void refreshOverlayGeometryControls() {
        refreshOverlaySizeControls();
        refreshOverlayPositionControls();
    }

    private void refreshOverlaySizeControls() {
        if (overlayWidthSlider == null) {
            return;
        }
        Rect bounds = ClimateOverlay.displayBounds(this);
        prefs.migrateOverlaySize(bounds.width(), bounds.height());
        int minWidth = Math.min(Prefs.OVERLAY_MIN_WIDTH_PX, Math.max(1, bounds.width()));
        int maxWidth = Math.max(minWidth, bounds.width());
        int width = prefs.overlayWidthPx(maxWidth);
        int minHeight = Math.min(Prefs.OVERLAY_MIN_HEIGHT_PX, Math.max(1, bounds.height()));
        int maxHeight = Math.max(minHeight, bounds.height());
        int height = prefs.overlayHeightPx(maxHeight);
        boolean autoHeight = height == 0;
        boolean previous = updatingOverlayControls;
        updatingOverlayControls = true;
        try {
            overlayWidthSlider.setMin(minWidth);
            overlayWidthSlider.setMax(maxWidth);
            overlayWidthSlider.setProgress(width);
            if (!overlayWidthInput.hasFocus()) {
                setOverlayText(overlayWidthInput, width);
            }
            overlayAutoHeightSwitch.setChecked(autoHeight);
            overlayManualHeightGroup.setVisibility(autoHeight ? View.GONE : View.VISIBLE);
            if (!autoHeight) {
                overlayHeightSlider.setMin(minHeight);
                overlayHeightSlider.setMax(maxHeight);
                overlayHeightSlider.setProgress(height);
                if (!overlayHeightInput.hasFocus()) {
                    setOverlayText(overlayHeightInput, height);
                }
            }
        } finally {
            updatingOverlayControls = previous;
        }
    }

    private void refreshOverlayPositionControls() {
        if (overlayCornerSpinner == null) {
            return;
        }
        float density = getApplicationContext().getResources().getDisplayMetrics().density;
        prefs.migrateOverlayOffsets(density);
        OverlayCorner corner = prefs.overlayCorner();
        int xPx = prefs.overlayOffsetXPx(density);
        int yPx = prefs.overlayOffsetYPx(density);
        boolean previous = updatingOverlayControls;
        updatingOverlayControls = true;
        try {
            overlayDragHandleSwitch.setChecked(prefs.overlayDragHandle());
            displayedOverlayCorner = corner;
            overlayCornerSpinner.setSelection(corner.ordinal(), false);
            if (!overlayPosXInput.hasFocus()) {
                setOverlayText(overlayPosXInput, xPx);
            }
            if (!overlayPosYInput.hasFocus()) {
                setOverlayText(overlayPosYInput, yPx);
            }
        } finally {
            updatingOverlayControls = previous;
        }
    }

    private void setOverlayText(EditText field, int value) {
        boolean previous = updatingOverlayControls;
        updatingOverlayControls = true;
        try {
            field.setText(Integer.toString(value));
            field.setError(null);
        } finally {
            updatingOverlayControls = previous;
        }
    }

    private void applyOverlayWidth(int widthPx) {
        Rect bounds = ClimateOverlay.displayBounds(this);
        prefs.setOverlaySize(widthPx, prefs.overlayHeightPx(bounds.height()));
        refreshPreview();
    }

    private void applyOverlayHeight(int heightPx) {
        Rect bounds = ClimateOverlay.displayBounds(this);
        prefs.setOverlaySize(prefs.overlayWidthPx(bounds.width()), heightPx);
        refreshPreview();
    }

    private void onOverlayWidthTextChanged() {
        if (updatingOverlayControls) {
            return;
        }
        Rect bounds = ClimateOverlay.displayBounds(this);
        int min = Math.min(Prefs.OVERLAY_MIN_WIDTH_PX, Math.max(1, bounds.width()));
        int max = Math.max(min, bounds.width());
        Integer parsed = parseIntField(overlayWidthInput);
        if (parsed != null && parsed >= min && parsed <= max) {
            overlayWidthInput.setError(null);
            boolean previous = updatingOverlayControls;
            updatingOverlayControls = true;
            overlayWidthSlider.setProgress(parsed);
            updatingOverlayControls = previous;
            applyOverlayWidth(parsed);
        } else if (overlayWidthInput.length() > 0) {
            overlayWidthInput.setError(getString(R.string.overlay_size_range, min, max));
        }
    }

    private void onOverlayHeightTextChanged() {
        if (updatingOverlayControls || overlayAutoHeightSwitch.isChecked()) {
            return;
        }
        Rect bounds = ClimateOverlay.displayBounds(this);
        int min = Math.min(Prefs.OVERLAY_MIN_HEIGHT_PX, Math.max(1, bounds.height()));
        int max = Math.max(min, bounds.height());
        Integer parsed = parseIntField(overlayHeightInput);
        if (parsed != null && parsed >= min && parsed <= max) {
            overlayHeightInput.setError(null);
            boolean previous = updatingOverlayControls;
            updatingOverlayControls = true;
            overlayHeightSlider.setProgress(parsed);
            updatingOverlayControls = previous;
            applyOverlayHeight(parsed);
        } else if (overlayHeightInput.length() > 0) {
            overlayHeightInput.setError(getString(R.string.overlay_size_range, min, max));
        }
    }

    private void onOverlayPositionTextChanged() {
        if (updatingOverlayControls) {
            return;
        }
        overlayPositionDirty = true;
        handler.removeCallbacks(saveOverlayPositionTask);
        handler.postDelayed(saveOverlayPositionTask, OVERLAY_INPUT_SAVE_DELAY_MS);
    }

    private void saveOverlayPosition() {
        if (!overlayPositionDirty || overlayPosXInput == null || overlayPosYInput == null) {
            return;
        }
        Rect bounds = ClimateOverlay.displayBounds(this);
        ClimateState state = ClimateService.STORE.snapshot(SystemClock.elapsedRealtime());
        WidgetViews.Size size = ClimateOverlay.size(getApplicationContext(), bounds, prefs,
                prefs.widget(Prefs.OVERLAY_ID), state);
        int maxX = Math.max(0, bounds.width() - size.widthPx);
        int maxY = Math.max(0, bounds.height() - size.heightPx);
        Integer x = parseIntField(overlayPosXInput);
        Integer y = parseIntField(overlayPosYInput);
        boolean valid = true;
        if (x == null || x < 0 || x > maxX) {
            overlayPosXInput.setError(getString(R.string.overlay_offset_range, maxX));
            valid = false;
        } else {
            overlayPosXInput.setError(null);
        }
        if (y == null || y < 0 || y > maxY) {
            overlayPosYInput.setError(getString(R.string.overlay_offset_range, maxY));
            valid = false;
        } else {
            overlayPosYInput.setError(null);
        }
        if (!valid) {
            return;
        }
        overlayPositionDirty = false;
        OverlayCorner corner = displayedOverlayCorner != null
                ? displayedOverlayCorner : prefs.overlayCorner();
        prefs.setOverlayPosition(corner, x, y);
    }

    private void reanchorOverlayCorner(OverlayCorner nextCorner) {
        handler.removeCallbacks(saveOverlayPositionTask);
        if (overlayPositionDirty) {
            saveOverlayPosition();
        }
        Rect bounds = ClimateOverlay.displayBounds(this);
        float density = getApplicationContext().getResources().getDisplayMetrics().density;
        ClimateState state = ClimateService.STORE.snapshot(SystemClock.elapsedRealtime());
        WidgetViews.Size size = ClimateOverlay.size(getApplicationContext(), bounds, prefs,
                prefs.widget(Prefs.OVERLAY_ID), state);
        OverlayCorner previous = displayedOverlayCorner != null
                ? displayedOverlayCorner : prefs.overlayCorner();
        OverlayGeometry.Position absolute = OverlayGeometry.positionFor(previous,
                bounds.left, bounds.top, bounds.right, bounds.bottom, size.widthPx, size.heightPx,
                prefs.overlayOffsetXPx(density), prefs.overlayOffsetYPx(density));
        OverlayGeometry.Offset offsets = OverlayGeometry.offsetsFor(nextCorner,
                bounds.left, bounds.top, bounds.right, bounds.bottom, size.widthPx, size.heightPx,
                absolute.x(), absolute.y());
        displayedOverlayCorner = nextCorner;
        prefs.setOverlayPosition(nextCorner, offsets.x(), offsets.y());
        overlayPositionDirty = false;
        setOverlayText(overlayPosXInput, offsets.x());
        setOverlayText(overlayPosYInput, offsets.y());
    }

    private static Integer parseIntField(EditText field) {
        String text = field.getText().toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_allow_draw_first, Toast.LENGTH_LONG).show();
            return;
        }
        if (!ForegroundAppDetector.hasUsageAccess(this)) {
            Toast.makeText(this, R.string.overlay_allow_usage_first, Toast.LENGTH_LONG).show();
            return;
        }
        if (!AccessibilityWindowState.isEnabled(this)) {
            Toast.makeText(this, R.string.overlay_allow_windows_first, Toast.LENGTH_LONG).show();
            return;
        }
        prefs.setOverlayEnabled(true);
        ClimateService.start(this, ClimateService.ACTION_REFRESH);
        refreshStatus();
        Toast.makeText(this, R.string.overlay_started, Toast.LENGTH_SHORT).show();
    }

    private void stopOverlay() {
        prefs.setOverlayEnabled(false);
        ClimateService.start(this, ClimateService.ACTION_RENDER);
        refreshStatus();
        Toast.makeText(this, R.string.overlay_stopped, Toast.LENGTH_SHORT).show();
    }

    private Button settingsButton(LinearLayout card, int title, String action, Uri data) {
        Button button = Ui.button(this, title);
        Ui.topMargin(button, 8);
        button.setOnClickListener(view -> {
            try {
                startActivity(new Intent(action, data));
            } catch (ActivityNotFoundException error) {
                Toast.makeText(this, R.string.overlay_settings_unavailable,
                        Toast.LENGTH_LONG).show();
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (ActivityNotFoundException ignored) {
                    // No system settings screen available.
                }
            }
        });
        card.addView(button);
        return button;
    }

    private void changeDisplayMode(boolean widget) {
        if (prefs.isWidgetMode() != widget) {
            prefs.setWidgetMode(widget);
            if (!widget) {
                prefs.setOverlayEnabled(true);
            }
            ClimateService.start(this, ClimateService.ACTION_REFRESH);
            recreate();
        }
    }

    private LinearLayout buildStatusCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, R.string.status_title, 20));
        statusText = Ui.text(this, "", 14, Ui.TEXT_SECONDARY);
        statusText.setLineSpacing(0, 1.2f);
        Ui.topMargin(statusText, 8);
        card.addView(statusText);

        Button refresh = Ui.button(this, R.string.status_refresh);
        Ui.topMargin(refresh, 12);
        refresh.setOnClickListener(view -> {
            ClimateService.start(this, ClimateService.ACTION_REFRESH);
            Toast.makeText(this, ClimateService.isDirect() ? R.string.status_refresh_sent_direct
                    : R.string.status_refresh_sent, Toast.LENGTH_SHORT).show();
        });
        card.addView(refresh);

        Switch direct = switchRow(R.string.direct_car, prefs.directCar());
        direct.setOnCheckedChangeListener((view, checked) -> {
            prefs.setDirectCar(checked);
            ClimateService.start(this, ClimateService.ACTION_REFRESH);
        });
        card.addView(direct);
        card.addView(hint(R.string.direct_car_hint));

        return card;
    }

    /** Actions on the layout picked in the pinned selector. */
    private LinearLayout buildLayoutCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, R.string.edited_widget_title, 20));
        card.addView(hint(prefs.isWidgetMode() ? R.string.edited_widget_hint : R.string.edited_overlay_hint));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Ui.topMargin(actions, 10);
        Button reset = Ui.button(this, R.string.reset_layout);
        reset.setOnClickListener(view -> {
            config = prefs.resolved(new WidgetConfig());
            save();
            rebuildEditor();
            refreshPreview();
        });
        if (prefs.isWidgetMode()) {
            Button applyAll = Ui.button(this, R.string.apply_to_all);
            applyAll.setOnClickListener(view -> {
                prefs.setTemplate(config);
                for (int id : widgetIds) {
                    prefs.setWidget(id, config);
                }
                ClimateService.start(this, ClimateService.ACTION_REFRESH);
                Toast.makeText(this, R.string.applied_to_all, Toast.LENGTH_SHORT).show();
            });
            actions.addView(applyAll, weighted(0, 8));
        }
        actions.addView(reset, weighted(0, 0));
        card.addView(actions);
        return card;
    }

    /** The climate window: how to bind it, its size, place and auto-close. */
    private LinearLayout buildPanelCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, R.string.panel_shortcut, 20));
        card.addView(hint(R.string.panel_hint));

        addSlider(card, getString(R.string.panel_width), Prefs.PANEL_WIDTH_MIN,
                Prefs.PANEL_WIDTH_MAX, prefs.panelWidthPercent(), value -> value + "%",
                prefs::setPanelWidthPercent, null);

        card.addView(label(R.string.panel_position));
        LinearLayout positions = new LinearLayout(this);
        positions.setOrientation(LinearLayout.HORIZONTAL);
        Ui.topMargin(positions, 8);
        TextView center = Ui.segment(this, R.string.panel_center);
        TextView bottom = Ui.segment(this, R.string.panel_bottom);
        Ui.setSegmentSelected(this, center, !prefs.panelBottom());
        Ui.setSegmentSelected(this, bottom, prefs.panelBottom());
        center.setOnClickListener(view -> {
            prefs.setPanelBottom(false);
            Ui.setSegmentSelected(this, center, true);
            Ui.setSegmentSelected(this, bottom, false);
        });
        bottom.setOnClickListener(view -> {
            prefs.setPanelBottom(true);
            Ui.setSegmentSelected(this, center, false);
            Ui.setSegmentSelected(this, bottom, true);
        });
        Ui.addSegment(positions, center);
        Ui.addSegment(positions, bottom);
        card.addView(positions);

        addSlider(card, getString(R.string.panel_close), 0, Prefs.PANEL_CLOSE_MAX,
                prefs.panelCloseSeconds(), value -> value == 0
                        ? getString(R.string.panel_close_never)
                        : getString(R.string.panel_close_seconds, value),
                prefs::setPanelCloseSeconds, null);

        Button edit = Ui.button(this, R.string.panel_edit);
        Ui.topMargin(edit, 12);
        edit.setOnClickListener(view -> {
            if (editedWidget != Prefs.PANEL_ID) {
                editedWidget = Prefs.PANEL_ID;
                config = loadConfig(Prefs.PANEL_ID);
                rebuildEditor();
                reloadWidgetList();
            }
            selectTab(TAB_BLOCKS);
        });
        card.addView(edit);
        Button open = Ui.button(this, R.string.panel_open);
        Ui.topMargin(open, 10);
        open.setOnClickListener(view -> startActivity(ClimatePanelActivity.intent(this)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        card.addView(open);
        return card;
    }

    /** The fuel formula, as in AtlasAppWidget: the default or the user's own coefficients. */
    private LinearLayout buildFuelCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, R.string.fuel_title, 20));
        TextView summary = Ui.text(this, "", 14, Ui.TEXT_SECONDARY);
        summary.setLineSpacing(0, 1.2f);
        Ui.topMargin(summary, 8);
        card.addView(summary);
        Switch custom = switchRow(R.string.fuel_custom_formula, prefs.fuelCustomFormula());
        card.addView(custom);
        Button edit = Ui.button(this, R.string.fuel_change_formula);
        Ui.topMargin(edit, 10);
        card.addView(edit);
        card.addView(hint(R.string.fuel_hint));
        Runnable refresh = () -> {
            summary.setText(getString(prefs.fuelCustomFormula()
                            ? R.string.fuel_formula_custom : R.string.fuel_formula_default,
                    Fuel.format(prefs.fuelMultiplier()), signed(prefs.fuelOffset()),
                    Fuel.capacityLiters(prefs.fuelMultiplier(), prefs.fuelOffset())));
            edit.setVisibility(prefs.fuelCustomFormula() ? View.VISIBLE : View.GONE);
        };
        refresh.run();
        custom.setOnCheckedChangeListener((view, checked) -> {
            prefs.setFuelCustomFormula(checked);
            refresh.run();
            globalChanged();
        });
        edit.setOnClickListener(view -> showFuelFormulaDialog(() -> {
            refresh.run();
            globalChanged();
        }));
        return card;
    }

    private static String signed(float offset) {
        return (offset < 0f ? "− " : "+ ") + Fuel.format(Math.abs(offset));
    }

    private void showFuelFormulaDialog(Runnable onSaved) {
        LinearLayout fields = vertical();
        fields.setPadding(Ui.dp(this, 24), Ui.dp(this, 8), Ui.dp(this, 24), 0);
        fields.addView(hint(R.string.fuel_formula_dialog_hint));
        EditText multiplier = formulaField(fields, R.string.fuel_multiplier,
                prefs.savedFuelMultiplier());
        EditText offset = formulaField(fields, R.string.fuel_offset, prefs.savedFuelOffset());
        AlertDialog dialog = new AlertDialog.Builder(this,
                android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.fuel_formula_dialog_title)
                .setView(fields)
                .setNegativeButton(R.string.setup_cancel, null)
                .setPositiveButton(R.string.setup_done, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    Float parsedMultiplier = formulaValue(multiplier, Fuel.MULTIPLIER_LIMIT,
                            R.string.fuel_multiplier_range);
                    Float parsedOffset = formulaValue(offset, Fuel.OFFSET_LIMIT,
                            R.string.fuel_offset_range);
                    if (parsedMultiplier == null || parsedOffset == null) {
                        return;
                    }
                    prefs.setFuelFormula(parsedMultiplier, parsedOffset);
                    dialog.dismiss();
                    onSaved.run();
                }));
        dialog.show();
        sized(dialog);
    }

    private EditText formulaField(LinearLayout parent, int labelRes, float value) {
        TextView label = Ui.text(this, labelRes, 14, Ui.TEXT);
        Ui.topMargin(label, 12);
        parent.addView(label);
        EditText field = new EditText(this);
        field.setText(Fuel.format(value));
        field.setSelectAllOnFocus(true);
        field.setSingleLine(true);
        field.setTextColor(Ui.TEXT);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        parent.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return field;
    }

    /** Parses a coefficient, accepting a decimal comma; marks the field and returns null if bad. */
    private Float formulaValue(EditText field, float limit, int rangeRes) {
        String text = field.getText().toString().trim().replace(',', '.').replace('−', '-');
        float value;
        try {
            value = Float.parseFloat(text);
        } catch (NumberFormatException error) {
            field.setError(getString(R.string.fuel_formula_invalid));
            return null;
        }
        if (!Float.isFinite(value)) {
            field.setError(getString(R.string.fuel_formula_invalid));
            return null;
        }
        if (Math.abs(value) > limit) {
            field.setError(getString(rangeRes));
            return null;
        }
        return value;
    }

    private LinearLayout buildBehaviourCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, R.string.behaviour_title, 20));

        card.addView(label(R.string.car_model_title));
        RadioGroup models = radioGroup();
        for (CarModel model : CarModel.values()) {
            RadioButton button = radio(model.titleRes);
            button.setChecked(prefs.carModel() == model);
            button.setOnCheckedChangeListener((view, checked) -> {
                if (checked) {
                    prefs.setCarModel(model);
                    rebuildFunctions();
                    globalChanged();
                }
            });
            models.addView(button);
        }
        card.addView(models);
        card.addView(hint(R.string.car_model_hint));

        card.addView(label(R.string.temp_step_title));
        RadioGroup steps = radioGroup();
        RadioButton half = radio(R.string.temp_step_half);
        RadioButton whole = radio(R.string.temp_step_whole);
        steps.addView(half);
        steps.addView(whole);
        (prefs.temperatureStep() >= 1f ? whole : half).setChecked(true);
        half.setOnCheckedChangeListener((view, checked) -> {
            if (checked) {
                prefs.setTemperatureStep(Hvac.TEMP_STEP);
            }
        });
        whole.setOnCheckedChangeListener((view, checked) -> {
            if (checked) {
                prefs.setTemperatureStep(1f);
            }
        });
        card.addView(steps);

        card.addView(label(R.string.fan_presets_title));
        RadioGroup presets = radioGroup();
        for (Prefs.FanPresets value : Prefs.FanPresets.values()) {
            RadioButton button = radio(value.titleRes);
            button.setChecked(prefs.fanPresets() == value);
            button.setOnCheckedChangeListener((view, checked) -> {
                if (checked) {
                    prefs.setFanPresets(value);
                    globalChanged();
                }
            });
            presets.addView(button);
        }
        card.addView(presets);
        card.addView(hint(R.string.fan_presets_hint));

        Switch fromMax = switchRow(R.string.levels_from_max, prefs.levelsFromMax());
        fromMax.setOnCheckedChangeListener((view, checked) -> prefs.setLevelsFromMax(checked));
        card.addView(fromMax);
        card.addView(hint(R.string.levels_from_max_hint));

        Switch keptWhenOff = switchRow(R.string.kept_when_off, prefs.keptWhenOff());
        keptWhenOff.setOnCheckedChangeListener((view, checked) -> {
            prefs.setKeptWhenOff(checked);
            globalChanged();
        });
        card.addView(keptWhenOff);
        card.addView(hint(R.string.kept_when_off_hint));
        return card;
    }

    /** Global: all widgets follow the head unit's day and night mode or keep one theme. */
    private LinearLayout buildThemeCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, R.string.theme_title, 20));
        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        Ui.topMargin(modes, 8);
        int[] titles = {R.string.theme_auto, R.string.colors_day, R.string.colors_night};
        int[] values = {WidgetTheme.AUTO, WidgetTheme.FIXED_DAY, WidgetTheme.FIXED_NIGHT};
        List<TextView> segments = new ArrayList<>();
        for (int index = 0; index < values.length; index++) {
            int mode = values[index];
            TextView segment = Ui.segment(this, titles[index]);
            Ui.setSegmentSelected(this, segment, prefs.themeMode() == mode);
            segment.setOnClickListener(view -> {
                prefs.setThemeMode(mode);
                for (int other = 0; other < segments.size(); other++) {
                    Ui.setSegmentSelected(this, segments.get(other), values[other] == mode);
                }
                // The colours and the preview open in the theme the widgets now show.
                editNight = WidgetTheme.of(WidgetTheme.themed(this)).night;
                rebuildColors();
                globalChanged();
            });
            segments.add(segment);
            Ui.addSegment(modes, segment);
        }
        card.addView(modes);
        card.addView(hint(R.string.theme_hint));
        return card;
    }

    private LinearLayout buildInterfaceCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, R.string.interface_title, 20));
        card.addView(hint(R.string.interface_hint));
        int current = configuredScaleTenths(this);
        addSlider(card, getString(R.string.interface_scale), MIN_SCALE_TENTHS, MAX_SCALE_TENTHS,
                current, value -> (value / 10) + "." + (value % 10) + "×", value -> {
                }, value -> {
                    prefs.setUiScaleTenths(value);
                    recreate();
                });
        return card;
    }

    private LinearLayout buildBackupCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, R.string.backup_title, 20));
        card.addView(hint(R.string.backup_hint));
        Button export = Ui.button(this, R.string.backup_export);
        Ui.topMargin(export, 12);
        export.setOnClickListener(view -> exportBackup());
        card.addView(export);
        Button importButton = Ui.button(this, R.string.backup_import);
        Ui.topMargin(importButton, 10);
        importButton.setOnClickListener(view -> chooseBackup());
        card.addView(importButton);
        return card;
    }

    private LinearLayout buildGStoreCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, R.string.gstore_title, 20));
        card.addView(hint(R.string.gstore_hint));
        Button gstore = Ui.button(this, R.string.gstore_button);
        Ui.topMargin(gstore, 12);
        gstore.setOnClickListener(view -> GStoreDialog.show(this));
        card.addView(gstore);
        return card;
    }

    private LinearLayout buildDonateCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, R.string.donate_title, 20));
        card.addView(hint(R.string.donate_hint));
        Button donate = Ui.button(this, R.string.donate_button);
        Ui.topMargin(donate, 12);
        donate.setOnClickListener(view -> DonateDialog.show(this));
        card.addView(donate);
        return card;
    }

    // ---- backup ------------------------------------------------------------------------------

    /** As GInputBridge shares .gibb: a dated file in the cache, sent through the share sheet. */
    private void exportBackup() {
        String name = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date())
                + "_" + SettingsBackup.FILE_NAME;
        Uri uri;
        try {
            uri = BackupProvider.publish(this, name,
                    prefs.exportBackup(ClimateService.widgetIds(this)).toJson(appVersion()));
        } catch (IOException | RuntimeException error) {
            AppLog.warn("Backup export failed", error);
            Toast.makeText(this, R.string.backup_export_failed, Toast.LENGTH_LONG).show();
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType(SettingsBackup.MIME)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newRawUri(name, uri));
        try {
            startActivity(Intent.createChooser(send, getString(R.string.backup_share_title))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.backup_no_target, Toast.LENGTH_LONG).show();
        }
    }

    /** "1.3.0 (38)" beside the title, as in the other Atlas apps. */
    private String versionLabel() {
        try {
            android.content.pm.PackageInfo info =
                    getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName + " (" + info.getLongVersionCode() + ")";
        } catch (android.content.pm.PackageManager.NameNotFoundException error) {
            return "";
        }
    }

    private String appVersion() {
        try {
            String name = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return name == null ? "" : name;
        } catch (android.content.pm.PackageManager.NameNotFoundException error) {
            return "";
        }
    }

    /** As GInputBridge's import: the system document picker, any type, since JSON has none. */
    @SuppressWarnings("deprecation")
    private void chooseBackup() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{SettingsBackup.MIME,
                        "application/octet-stream", "text/plain", "*/*"});
        try {
            startActivityForResult(picker, REQUEST_IMPORT);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.backup_no_picker, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT && resultCode == RESULT_OK && data != null
                && data.getData() != null) {
            readBackup(data.getData());
        }
    }

    private void readBackup(Uri uri) {
        String text;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("Cannot open " + uri);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > BACKUP_MAX_BYTES) {
                    throw new IOException("Settings file is larger than 256 KB");
                }
            }
            text = new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException error) {
            AppLog.warn("Backup read failed: " + uri, error);
            Toast.makeText(this, R.string.backup_import_failed, Toast.LENGTH_LONG).show();
            return;
        }
        confirmImport(text);
    }

    private void rebuildColors() {
        colorsHost.removeAllViews();
        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        Ui.topMargin(modes, 8);
        for (boolean night : new boolean[] {false, true}) {
            TextView segment = Ui.segment(this, night ? R.string.colors_night : R.string.colors_day);
            Ui.setSegmentSelected(this, segment, editNight == night);
            segment.setOnClickListener(view -> {
                editNight = night;
                rebuildColors();
                refreshPreview();
            });
            Ui.addSegment(modes, segment);
        }
        colorsHost.addView(modes);
        colorsHost.addView(hint(R.string.colors_hint));
        colorsHost.addView(label(R.string.palette_title));
        colorsHost.addView(paletteGroup(config.palette(editNight), palette -> {
            if (editNight) {
                config.palette = palette;
            } else {
                config.dayPalette = palette;
            }
        }));
        colorsHost.addView(cardColorButton(editNight ? WidgetTheme.NIGHT : WidgetTheme.DAY));
    }

    private RadioGroup paletteGroup(Palette checked, Consumer<Palette> listener) {
        RadioGroup palettes = radioGroup();
        for (Palette palette : Palette.values()) {
            RadioButton button = radio(palette.titleRes);
            button.setChecked(checked == palette);
            button.setOnCheckedChangeListener((view, on) -> {
                if (on) {
                    listener.accept(palette);
                    changed();
                }
            });
            palettes.addView(button);
        }
        return palettes;
    }

    /** @param mode the theme whose card colour the button edits */
    private Button cardColorButton(WidgetTheme mode) {
        Button button = Ui.button(this, "");
        Ui.topMargin(button, 12);
        showCardColor(button, mode);
        button.setOnClickListener(view -> {
            int shown = WidgetTheme.cardColor(config, mode);
            boolean themed = config.themedCard(mode.night);
            sized(ColorPickerDialog.show(this, getString(R.string.card_color), shown,
                    WidgetConfig.THEME_CARD_COLOR, color -> {
                        // Applying the theme's colour unchanged keeps the card themed.
                        if (!themed || color != shown) {
                            if (mode.night) {
                                config.cardColor = color;
                            } else {
                                config.dayCardColor = color;
                            }
                        }
                        showCardColor(button, mode);
                        changed();
                    }));
        });
        return button;
    }

    /** The button shows the colour itself, as in AtlasAppWidget. */
    private void showCardColor(Button button, WidgetTheme mode) {
        colorButton(button, getString(R.string.card_color),
                WidgetTheme.cardColor(config, mode),
                config.themedCard(mode.night) ? getString(R.string.card_color_theme) : null);
    }

    /** @param value shown instead of the hex code */
    private void colorButton(Button button, String label, int color, String value) {
        button.setText(value != null ? label + "  " + value
                : String.format("%s  #%06X", label, color & 0xFFFFFF));
        button.setBackground(Ui.rounded(color, Ui.dp(this, 8)));
        double luminance = (0.2126 * Color.red(color)
                + 0.7152 * Color.green(color)
                + 0.0722 * Color.blue(color)) / 255.0;
        button.setTextColor(luminance > 0.62 ? Color.BLACK : Color.WHITE);
    }

    /** The scaled activity density widens default dialogs past the screen edge. */
    private void sized(AlertDialog dialog) {
        int screen = getResources().getDisplayMetrics().widthPixels;
        dialog.getWindow().setLayout(Math.min(screen - Ui.dp(this, 48), Ui.dp(this, 640)),
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    void confirmImport(String text) {
        SettingsBackup backup = SettingsBackup.parse(text);
        if (backup == null) {
            Toast.makeText(this, R.string.backup_import_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        int[] ids = ClimateService.widgetIds(this);
        sized(new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.backup_import_title)
                .setMessage(getString(R.string.backup_import_summary, backup.widgets.size(),
                        getString(CarModel.fromName(backup.carModel).titleRes), ids.length))
                .setPositiveButton(R.string.backup_import_confirm, (button, which) -> {
                    prefs.importBackup(backup, ClimateService.widgetIds(this));
                    ClimateService.start(this, ClimateService.ACTION_REFRESH);
                    Toast.makeText(this, R.string.backup_import_done, Toast.LENGTH_SHORT).show();
                    // The interface scale may have changed too; rebuild everything from prefs.
                    recreate();
                })
                .setNegativeButton(R.string.setup_cancel, null)
                .show());
    }

    // ---- constructor -------------------------------------------------------------------------

    private void rebuildEditor() {
        if (blocksHost == null) {
            return;
        }
        boolean isOverlay = editedWidget == Prefs.OVERLAY_ID;
        widgetHeightCard.setVisibility(
                editedWidget == Prefs.PANEL_ID || isOverlay ? View.GONE : View.VISIBLE);
        if (overlaySizeCard != null) {
            overlaySizeCard.setVisibility(isOverlay ? View.VISIBLE : View.GONE);
        }
        if (overlayPositionCard != null) {
            overlayPositionCard.setVisibility(isOverlay ? View.VISIBLE : View.GONE);
        }
        if (isOverlay) {
            refreshOverlayGeometryControls();
        }
        rebuildBlocks();
        rebuildTiles();
        rebuildFunctions();
        rebuildAppearance();
    }

    private void rebuildBlocks() {
        blocksHost.removeAllViews();
        LinearLayout styles = new LinearLayout(this);
        styles.setOrientation(LinearLayout.HORIZONTAL);
        Ui.topMargin(styles, 10);
        for (WidgetConfig.Style style : WidgetConfig.Style.values()) {
            TextView segment = Ui.segment(this, style.titleRes);
            Ui.setSegmentSelected(this, segment, config.style == style);
            segment.setOnClickListener(view -> {
                config.style = style;
                changed();
                rebuildBlocks();
                rebuildTiles();
            });
            Ui.addSegment(styles, segment);
        }
        blocksHost.addView(styles);
        blocksHost.addView(hint(config.style == WidgetConfig.Style.CONSOLE
                ? R.string.style_console_hint : R.string.style_classic_hint));
        LinearLayout layouts = new LinearLayout(this);
        layouts.setOrientation(LinearLayout.HORIZONTAL);
        Ui.topMargin(layouts, 10);
        for (WidgetConfig.CardLayout layout : WidgetConfig.CardLayout.values()) {
            TextView segment = Ui.segment(this, layout.titleRes);
            Ui.setSegmentSelected(this, segment, config.cardLayout == layout);
            segment.setOnClickListener(view -> {
                config.cardLayout = layout;
                changed();
                rebuildBlocks();
            });
            Ui.addSegment(layouts, segment);
        }
        blocksHost.addView(layouts);
        blocksHost.addView(hint(config.cardLayout == WidgetConfig.CardLayout.SINGLE
                ? R.string.card_layout_single_hint : R.string.card_layout_separate_hint));
        List<WidgetConfig.Block> order = config.blockOrder;
        for (int index = 0; index < order.size(); index++) {
            WidgetConfig.Block block = order.get(index);
            LinearLayout row = orderRow(getString(block.titleRes), config.isEnabled(block)
                            || (block == WidgetConfig.Block.TILES && config.tilesEnabled),
                    index > 0, index < order.size() - 1,
                    checked -> {
                        config.setEnabled(block, checked);
                        changed();
                        rebuildBlocks();
                        rebuildTiles();
                    },
                    direction -> {
                        config.moveBlock(block, direction);
                        changed();
                        rebuildBlocks();
                    });
            blocksHost.addView(row);
            LinearLayout options = settingsGroup();
            switch (block) {
                case INFO:
                    options.addView(headerAligns());
                    options.addView(partSwitch(R.string.info_cabin, config.headerCabin,
                            value -> config.headerCabin = value, config::hasInfoParts));
                    options.addView(partSwitch(R.string.info_outside, config.headerOutside,
                            value -> config.headerOutside = value, config::hasInfoParts));
                    options.addView(partSwitch(R.string.info_fuel, config.headerFuel,
                            value -> config.headerFuel = value, config::hasInfoParts));
                    options.addView(partSwitch(R.string.info_fuel_percent,
                            config.headerFuelPercent, value -> config.headerFuelPercent = value,
                            config::hasInfoParts));
                    options.addView(partSwitch(R.string.info_fuel_free, config.headerFuelFree,
                            value -> config.headerFuelFree = value, config::hasInfoParts));
                    options.addView(partSwitch(R.string.info_range, config.headerRange,
                            value -> config.headerRange = value, config::hasInfoParts));
                    options.addView(hint(R.string.info_hint));
                    break;
                case TEMPERATURE:
                    options.addView(temperatureLayouts());
                    options.addView(configSwitch(R.string.temp_dual, config.temperatureDual,
                            value -> config.temperatureDual = value));
                    if (config.temperatureButtonsOnly()) {
                        options.addView(hint(
                                config.temperatureLayout == WidgetConfig.TempLayout.EDGES
                                        ? R.string.temp_edges_hint : R.string.temp_buttons_hint));
                        break;
                    }
                    options.addView(configSwitch(R.string.temp_buttons, config.temperatureButtons,
                            value -> config.temperatureButtons = value));
                    options.addView(hint(R.string.temp_hint));
                    break;
                case FAN:
                    options.addView(fanLayouts());
                    if (config.fanLayout == WidgetConfig.FanLayout.BAR) {
                        options.addView(configSwitch(R.string.fan_auto_button, config.fanAuto,
                                value -> config.fanAuto = value));
                        options.addView(configSwitch(R.string.fan_part_directions,
                                config.fanDirections, value -> config.fanDirections = value));
                        options.addView(configSwitch(R.string.fan_buttons, config.fanButtons,
                                value -> config.fanButtons = value));
                        options.addView(hint(R.string.fan_bar_hint));
                        break;
                    }
                    options.addView(partSwitch(R.string.fan_part_bar, config.fanBar,
                            value -> config.fanBar = value, config::hasFanParts));
                    if (config.fanBar) {
                        options.addView(configSwitch(R.string.fan_buttons, config.fanButtons,
                                value -> config.fanButtons = value));
                    }
                    options.addView(partSwitch(R.string.fan_part_directions,
                            config.fanDirections, value -> config.fanDirections = value,
                            config::hasFanParts));
                    options.addView(partSwitch(R.string.fan_part_presets, config.fanPresets,
                            value -> config.fanPresets = value, config::hasFanParts));
                    options.addView(hint(R.string.fan_hint));
                    break;
                default:
                    options.addView(hint(R.string.tiles_block_hint));
                    break;
            }
            boolean enabled = block == WidgetConfig.Block.TILES
                    ? config.tilesEnabled : config.isEnabled(block);
            setGroupEnabled(options, enabled);
            options.setAlpha(enabled ? 1f : 0.45f);
            blocksHost.addView(options);
        }
    }

    private LinearLayout temperatureLayouts() {
        LinearLayout layouts = new LinearLayout(this);
        layouts.setOrientation(LinearLayout.HORIZONTAL);
        Ui.topMargin(layouts, 6);
        for (WidgetConfig.TempLayout layout : WidgetConfig.TempLayout.values()) {
            TextView segment = Ui.segment(this, layout.titleRes);
            Ui.setSegmentSelected(this, segment, config.temperatureLayout == layout);
            if (config.temperatureLayout != layout) {
                // The row sits on a nested surface, where the usual segment colour disappears.
                segment.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 8)));
            }
            segment.setOnClickListener(view -> {
                config.temperatureLayout = layout;
                changed();
                rebuildBlocks();
            });
            Ui.addSegment(layouts, segment);
        }
        return layouts;
    }

    private LinearLayout fanLayouts() {
        LinearLayout layouts = new LinearLayout(this);
        layouts.setOrientation(LinearLayout.HORIZONTAL);
        Ui.topMargin(layouts, 6);
        for (WidgetConfig.FanLayout layout : WidgetConfig.FanLayout.values()) {
            TextView segment = Ui.segment(this, layout.titleRes);
            Ui.setSegmentSelected(this, segment, config.fanLayout == layout);
            if (config.fanLayout != layout) {
                // The row sits on a nested surface, where the usual segment colour disappears.
                segment.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 8)));
            }
            segment.setOnClickListener(view -> {
                config.fanLayout = layout;
                if (!config.hasFanParts()) {
                    // Rows need a part; the bar's speeds are the closest one.
                    config.fanBar = true;
                }
                changed();
                rebuildBlocks();
            });
            Ui.addSegment(layouts, segment);
        }
        return layouts;
    }

    private LinearLayout headerAligns() {
        LinearLayout aligns = new LinearLayout(this);
        aligns.setOrientation(LinearLayout.HORIZONTAL);
        Ui.topMargin(aligns, 6);
        for (WidgetConfig.HeaderAlign align : WidgetConfig.HeaderAlign.values()) {
            TextView segment = Ui.segment(this, align.titleRes);
            Ui.setSegmentSelected(this, segment, config.headerAlign == align);
            if (config.headerAlign != align) {
                // The row sits on a nested surface, where the usual segment colour disappears.
                segment.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 8)));
            }
            segment.setOnClickListener(view -> {
                config.headerAlign = align;
                changed();
                rebuildBlocks();
            });
            Ui.addSegment(aligns, segment);
        }
        return aligns;
    }

    private void rebuildTiles() {
        tilesHost.removeAllViews();
        if (!config.tilesEnabled) {
            tilesHost.addView(hint(R.string.tiles_disabled_hint));
        }
        LinearLayout options = vertical();
        addSlider(options, getString(R.string.tile_columns),
                WidgetConfig.COLUMNS_MIN, WidgetConfig.COLUMNS_MAX, config.columns,
                String::valueOf, value -> {
                    config.columns = value;
                    changed();
                }, null);
        setGroupEnabled(options, config.tilesEnabled);
        options.setAlpha(config.tilesEnabled ? 1f : 0.45f);
        tilesHost.addView(options);
    }

    private void rebuildFunctions() {
        functionsHost.removeAllViews();
        availableHost.removeAllViews();
        List<ClimateFunction> enabled = new ArrayList<>(config.functions);
        if (enabled.isEmpty()) {
            functionsHost.addView(hint(R.string.functions_empty));
        }
        for (int index = 0; index < enabled.size(); index++) {
            ClimateFunction function = enabled.get(index);
            functionsHost.addView(functionRow(function, true, index > 0,
                    index < enabled.size() - 1));
        }
        for (ClimateFunction function : ClimateFunction.values()) {
            if (!enabled.contains(function) && function.isAvailableFor(prefs.carModel())) {
                availableHost.addView(functionRow(function, false, false, false));
            }
        }
        rebuildTileLevels();
        rebuildOffTargets();
    }

    private void rebuildTileLevels() {
        levelsHost.removeAllViews();
        List<ClimateFunction> leveled = new ArrayList<>();
        CarModel model = prefs.carModel();
        for (ClimateFunction function : config.functions) {
            if (function.hasLevels() && function.isAvailableFor(model)) {
                leveled.add(function);
            }
        }
        if (leveled.isEmpty()) {
            levelsHost.addView(hint(R.string.tile_levels_empty));
            return;
        }
        levelsHost.addView(hint(R.string.tile_levels_hint));
        for (ClimateFunction function : leveled) {
            String title = getString(function.titleRes);
            levelsHost.addView(label(function.titleRes));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            Ui.topMargin(row, 6);
            int count = function.levelCount();
            for (int level = 1; level <= count; level++) {
                int currentLevel = level;
                String text = levelLabel(function, level);
                TextView segment = Ui.segment(this, text);
                segment.setContentDescription(title + ": " + text);
                Ui.setSegmentSelected(this, segment,
                        config.isTileLevelEnabled(function, level));
                segment.setOnClickListener(view -> {
                    boolean next = !config.isTileLevelEnabled(function, currentLevel);
                    if (!config.setTileLevel(function, currentLevel, next)) {
                        return;
                    }
                    Ui.setSegmentSelected(this, segment, next);
                    changed();
                });
                Ui.addSegment(row, segment);
            }
            levelsHost.addView(row);
        }
    }

    private String levelLabel(ClimateFunction function, int level) {
        int nameRes = function.levelNameRes(level);
        if (nameRes != 0) {
            return getString(nameRes);
        }
        if (function.kind == ClimateFunction.Kind.POSITION) {
            return function.levels[level] + "%";
        }
        return String.valueOf(level);
    }

    private void rebuildOffTargets() {
        offHost.removeAllViews();
        offHost.addView(hint(config.functions.contains(ClimateFunction.ALL_OFF)
                ? R.string.off_targets_hint : R.string.off_targets_no_tile));
        for (ClimateFunction function : ClimateFunction.values()) {
            if (!function.canSwitchOff()) {
                continue;
            }
            CheckBox box = new CheckBox(this);
            box.setText(function.titleRes);
            box.setTextColor(Ui.TEXT);
            box.setTextSize(15);
            box.setChecked(config.offTargets.contains(function));
            box.setOnCheckedChangeListener((view, checked) -> {
                config.setOffTarget(function, checked);
                if (config.offTargets.contains(function) != checked) {
                    // The tile keeps at least one target.
                    view.setChecked(true);
                    return;
                }
                changed();
            });
            Ui.topMargin(box, 6);
            offHost.addView(box);
        }
    }

    private View functionRow(ClimateFunction function, boolean enabled, boolean canUp,
            boolean canDown) {
        String title = getString(function.titleRes);
        if (!function.isAvailableFor(prefs.carModel())) {
            title = getString(R.string.function_unavailable, title);
        }
        LinearLayout row = orderRow(title, enabled, canUp, canDown,
                checked -> {
                    config.setFunctionEnabled(function, checked);
                    changed();
                    rebuildFunctions();
                },
                direction -> {
                    config.moveFunction(function, direction);
                    changed();
                    rebuildFunctions();
                });
        if (!enabled) {
            row.getChildAt(1).setVisibility(View.INVISIBLE);
            row.getChildAt(2).setVisibility(View.INVISIBLE);
        }
        return row;
    }

    private void rebuildAppearance() {
        appearanceHost.removeAllViews();
        appearanceHost.addView(configSwitch(R.string.filled_active, config.filledActive,
                value -> config.filledActive = value));
        appearanceHost.addView(label(R.string.icons_title));
        RadioGroup iconSets = radioGroup();
        for (WidgetConfig.IconSet set : WidgetConfig.IconSet.values()) {
            RadioButton button = radio(set.titleRes);
            button.setChecked(config.iconSet == set);
            button.setOnCheckedChangeListener((view, checked) -> {
                if (checked) {
                    config.iconSet = set;
                    changed();
                }
            });
            iconSets.addView(button);
        }
        appearanceHost.addView(iconSets);
        appearanceHost.addView(hint(R.string.icons_hint));
        // One look for tiles and fan buttons, so it lives here rather than on the Tiles tab.
        appearanceHost.addView(label(R.string.tile_style_title));
        RadioGroup styles = radioGroup();
        for (WidgetConfig.TileStyle style : WidgetConfig.TileStyle.values()) {
            RadioButton button = radio(style.titleRes);
            button.setChecked(config.tileStyle == style);
            button.setOnCheckedChangeListener((view, checked) -> {
                if (checked) {
                    config.tileStyle = style;
                    changed();
                }
            });
            styles.addView(button);
        }
        appearanceHost.addView(styles);
        appearanceHost.addView(hint(R.string.tile_style_hint));
        addSlider(appearanceHost, getString(R.string.widget_scale),
                WidgetConfig.SCALE_MIN_PERCENT, WidgetConfig.SCALE_MAX_PERCENT,
                config.scalePercent, value -> value + "%", value -> {
                    config.scalePercent = value;
                    changed();
                }, null);
        appearanceHost.addView(hint(R.string.widget_scale_hint));

        cardsHost.removeAllViews();
        addSlider(cardsHost, getString(R.string.card_opacity), 0, 20,
                Math.round(config.cardOpacityPercent / 5f), step -> step * 5 + "%", step -> {
                    config.cardOpacityPercent = step * 5;
                    changed();
                }, null);
        addSlider(cardsHost, getString(R.string.content_opacity),
                WidgetConfig.CONTENT_OPACITY_MIN_PERCENT / 5, 20,
                Math.round(config.contentOpacityPercent / 5f), step -> step * 5 + "%", step -> {
                    config.contentOpacityPercent = step * 5;
                    changed();
                }, null);
        addSlider(cardsHost, getString(R.string.card_radius), 0,
                WidgetConfig.CARD_RADIUS_MAX_DP, config.cardRadiusDp,
                value -> getString(R.string.dp_value, value), value -> {
                    config.cardRadiusDp = value;
                    changed();
                }, null);
        addSlider(cardsHost, getString(R.string.card_padding), WidgetConfig.PADDING_MIN_DP,
                WidgetConfig.PADDING_MAX_DP, config.cardPaddingDp,
                value -> getString(R.string.dp_value, value), value -> {
                    config.cardPaddingDp = value;
                    changed();
                }, null);
        addSlider(cardsHost, getString(R.string.gap), WidgetConfig.GAP_MIN_DP,
                WidgetConfig.GAP_MAX_DP, config.gapDp,
                value -> getString(R.string.dp_value, value), value -> {
                    config.gapDp = value;
                    changed();
                }, null);
        addSlider(cardsHost, getString(R.string.tile_radius), 0,
                WidgetConfig.TILE_RADIUS_MAX_PERCENT, config.tileRadiusPercent,
                value -> value + "%", value -> {
                    config.tileRadiusPercent = value;
                    changed();
                }, null);
        rebuildColors();
    }

    // ---- state -------------------------------------------------------------------------------

    private WidgetConfig loadConfig(int widgetId) {
        return widgetId == TEMPLATE ? prefs.template() : prefs.widget(widgetId);
    }

    /** The preset count and fuel formula follow global settings; re-resolve the edited layout. */
    /** A global setting the widgets draw with changed. */
    private void globalChanged() {
        prefs.resolved(config);
        ClimateService.start(this, ClimateService.ACTION_REFRESH);
        refreshPreview();
    }

    private void changed() {
        save();
        refreshPreview();
    }

    private void save() {
        if (editedWidget == TEMPLATE) {
            prefs.setTemplate(config);
        } else {
            prefs.setWidget(editedWidget, config);
        }
        ClimateService.start(this, ClimateService.ACTION_REFRESH);
    }

    private void reloadWidgetList() {
        widgetIds = prefs.isWidgetMode() ? ClimateService.widgetIds(this) : new int[0];
        int overlayCount = prefs.isWidgetMode() ? 0 : 1;
        layoutIds = new int[widgetIds.length + 2 + overlayCount];
        System.arraycopy(widgetIds, 0, layoutIds, 0, widgetIds.length);
        if (overlayCount != 0) {
            layoutIds[widgetIds.length] = Prefs.OVERLAY_ID;
        }
        layoutIds[layoutIds.length - 2] = Prefs.PANEL_ID;
        layoutIds[layoutIds.length - 1] = TEMPLATE;
        float density = getApplicationContext().getResources().getDisplayMetrics().density;
        List<String> labels = new ArrayList<>();
        int selection = -1;
        for (int index = 0; index < widgetIds.length; index++) {
            WidgetViews.Size size = ClimateService.widgetSize(getApplicationContext(),
                    widgetIds[index]);
            labels.add(getString(R.string.widget_item, index + 1,
                    WidgetViews.describe(size, density)));
        }
        if (overlayCount != 0) {
            labels.add(getString(R.string.overlay_title));
        }
        labels.add(getString(R.string.panel_shortcut));
        labels.add(getString(R.string.widget_template));
        for (int index = 0; index < layoutIds.length; index++) {
            if (layoutIds[index] == editedWidget) {
                selection = index;
            }
        }
        if (selection < 0) {
            // The edited widget was removed from the screen.
            selection = overlayCount != 0 ? widgetIds.length : layoutIds.length - 1;
            editedWidget = layoutIds[selection];
            config = loadConfig(editedWidget);
            rebuildEditor();
        }
        widgetSpinner.setAdapter(styledSpinnerAdapter(labels));
        widgetSpinner.setSelection(selection, false);
    }

    private void refreshLive() {
        refreshPreview();
        refreshStatus();
    }

    private void refreshStatus() {
        boolean hasOverlay = Settings.canDrawOverlays(this);
        boolean hasUsage = ForegroundAppDetector.hasUsageAccess(this);
        boolean hasWindows = AccessibilityWindowState.isEnabled(this);
        boolean allPermissions = hasOverlay && hasUsage && hasWindows;
        if (overlayPermission != null) {
            overlayPermission.setVisibility(hasOverlay ? View.GONE : View.VISIBLE);
            usagePermission.setVisibility(hasUsage ? View.GONE : View.VISIBLE);
            windowsPermission.setVisibility(hasWindows ? View.GONE : View.VISIBLE);
            windowsPermission.setText(AccessibilityWindowState.isConfigured(this)
                    ? R.string.overlay_windows_waiting : R.string.overlay_allow_windows);
            if (overlayPermissionsLabel != null) {
                overlayPermissionsLabel.setVisibility(allPermissions ? View.GONE : View.VISIBLE);
            }
        }
        if (overlayStatusText != null) {
            boolean enabled = prefs.overlayEnabled();
            boolean running = enabled && allPermissions;
            overlayStatusText.setText(!enabled ? R.string.overlay_status_stopped
                    : allPermissions ? R.string.overlay_status_running
                    : R.string.overlay_status_waiting);
            if (startOverlayButton != null) {
                Ui.setButtonAccent(this, startOverlayButton, !running);
            }
            if (stopOverlayButton != null) {
                stopOverlayButton.setEnabled(enabled);
                stopOverlayButton.setAlpha(enabled ? 1f : 0.45f);
            }
        }
        if (statusText == null) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        StringBuilder text = new StringBuilder();
        boolean direct = ClimateService.isRunning() && ClimateService.isDirect();
        if (prefs.directCar()) {
            text.append(getString(direct ? R.string.status_link_direct
                    : R.string.status_link_direct_missing));
            text.append('\n');
        }
        text.append(getString(Gib.isInstalled(this)
                ? R.string.status_bridge_installed : R.string.status_bridge_missing));
        text.append('\n');
        long last = ClimateService.STORE.lastResultAt();
        if (last <= 0L) {
            text.append(getString(direct ? R.string.status_no_answer_direct
                    : R.string.status_no_answer));
        } else {
            text.append(getString(direct ? R.string.status_answer_direct
                    : R.string.status_answer, Math.max(0, (now - last) / 1000L)));
        }
        text.append('\n');
        if (prefs.isWidgetMode()) {
            text.append(getString(R.string.status_widgets, ClimateService.widgetIds(this).length));
        } else if (!prefs.overlayEnabled()) {
            text.append(getString(R.string.status_overlay_stopped));
        } else {
            text.append(getString(allPermissions
                    ? R.string.status_overlay_ready : R.string.status_overlay_permissions));
        }
        text.append('\n');
        text.append(getString(ClimateService.isRunning()
                ? R.string.status_service_running : R.string.status_service_stopped));
        statusText.setText(text);
    }

    private void refreshPreview() {
        if (previewHost == null || previewHost.getWidth() == 0) {
            return;
        }
        ClimateState live = ClimateService.STORE.snapshot(SystemClock.elapsedRealtime());
        boolean demo = !live.isConnected() || !hasClimateValues(live);
        previewCaption.setText(!live.isConnected() ? R.string.preview_demo
                : demo ? R.string.preview_demo_no_car : R.string.preview_live);
        WidgetViews.Size size;
        if (editedWidget == TEMPLATE) {
            float density = getApplicationContext().getResources().getDisplayMetrics().density;
            size = new WidgetViews.Size(Math.round(WidgetGeometry.DEFAULT_WIDTH_DP * density), 0);
        } else if (editedWidget == Prefs.PANEL_ID) {
            size = panelSize(config);
        } else if (editedWidget == Prefs.OVERLAY_ID) {
            Context app = getApplicationContext();
            size = ClimateOverlay.size(app, ClimateOverlay.displayBounds(this), prefs, config, live);
        } else {
            size = ClimateService.widgetSize(getApplicationContext(), editedWidget);
        }
        try {
            RemoteViews views = WidgetViews.build(withEditedMode(getApplicationContext()), config,
                    demo ? DemoState.INSTANCE : live, prefs.carModel(), size, false, 1f);
            View preview = views.apply(withEditedMode(this), previewHost);
            int width = previewHost.getWidth() - previewHost.getPaddingLeft()
                    - previewHost.getPaddingRight();
            float toScreen = getResources().getDisplayMetrics().density
                    / getApplicationContext().getResources().getDisplayMetrics().density;
            int maxWidth = Math.min(width, Math.round(size.widthPx * toScreen));
            if (size.heightPx > 0) {
                // Keep the pinned preview short enough for the settings below it.
                int maxHeight = Math.round(getResources().getDisplayMetrics().heightPixels
                        * PREVIEW_MAX_SCREEN_SHARE);
                maxWidth = Math.min(maxWidth, maxHeight * size.widthPx / size.heightPx);
            }
            previewHost.removeAllViews();
            previewHost.addView(HeightReport.framePreview(this, preview, size, maxWidth));
            WidgetGeometry.Plan plan = WidgetGeometry.plan(config, size.widthPx, size.heightPx,
                    getApplicationContext().getResources().getDisplayMetrics().density,
                    ClimateCommands.tempRange(demo ? DemoState.INSTANCE : live).steps());
            previewHost.setLayout(config, plan, editNight ? WidgetTheme.NIGHT : WidgetTheme.DAY);
            if (editedWidget == Prefs.OVERLAY_ID) {
                if (overlaySizeReport != null) {
                    overlaySizeReport.setText(plan.verticalScale < 1f
                            ? getString(R.string.overlay_size_squeezed, size.widthPx, size.heightPx,
                                    HeightReport.describe(this, plan))
                            : getString(R.string.overlay_size_px, size.widthPx, size.heightPx));
                }
            } else {
                heightTitle.setText(R.string.height_title);
                heightHint.setText(R.string.height_fill_hint);
                heightReport.setText(HeightReport.describe(this, plan));
            }
        } catch (RuntimeException error) {
            AppLog.warn("Preview failed", error);
        }
    }

    private WidgetViews.Size panelSize(WidgetConfig layout) {
        return ClimatePanelActivity.size(this, prefs.panelWidthPercent(), layout,
                ClimateService.STORE.snapshot(SystemClock.elapsedRealtime()));
    }

    private Context withEditedMode(Context base) {
        Configuration mode = new Configuration(base.getResources().getConfiguration());
        mode.uiMode = (mode.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (editNight ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return base.createConfigurationContext(mode);
    }

    private boolean hasClimateValues(ClimateState state) {
        if (ClimateCommands.temperature(state, Hvac.ZONE_DRIVER) != null
                || ClimateCommands.fanState(state).known) {
            return true;
        }
        for (ClimateFunction function : config.functions) {
            if (function.hasProperty()
                    && ClimateCommands.tileState(function, state, prefs.carModel()).known) {
                return true;
            }
        }
        return false;
    }

    // ---- small views -------------------------------------------------------------------------

    private interface BoolListener {
        void onValue(boolean value);
    }

    private interface IntListener {
        void onValue(int value);
    }

    private interface Formatter {
        String format(int value);
    }

    private LinearLayout orderRow(String title, boolean checked, boolean canUp, boolean canDown,
            BoolListener onChecked, IntListener onMove) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Ui.topMargin(row, 6);
        CheckBox box = new CheckBox(this);
        box.setText(title);
        box.setTextColor(Ui.TEXT);
        box.setTextSize(15);
        box.setChecked(checked);
        box.setOnCheckedChangeListener((view, value) -> view.post(() -> onChecked.onValue(value)));
        row.addView(box, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(arrow("↑", canUp, () -> onMove.onValue(-1)));
        row.addView(arrow("↓", canDown, () -> onMove.onValue(1)));
        return row;
    }

    private Button arrow(String text, boolean enabled, Runnable action) {
        Button button = Ui.button(this, text);
        button.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.35f);
        button.setOnClickListener(view -> view.post(action));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = Ui.dp(this, 8);
        button.setLayoutParams(params);
        return button;
    }

    /** A fan block part; switching one rebuilds the options that depend on it. */
    /** Switch of a block part; the block keeps at least one part and redraws its options. */
    private Switch partSwitch(int text, boolean checked, BoolListener listener,
            BooleanSupplier hasParts) {
        Switch view = switchRow(text, checked);
        view.setOnCheckedChangeListener((button, value) -> {
            listener.onValue(value);
            if (!hasParts.getAsBoolean()) {
                // The block needs at least one part; keep the one just switched on.
                listener.onValue(true);
                button.setChecked(true);
                return;
            }
            changed();
            button.post(this::rebuildBlocks);
        });
        return view;
    }

    private Switch configSwitch(int text, boolean checked, BoolListener listener) {
        Switch view = switchRow(text, checked);
        view.setOnCheckedChangeListener((button, value) -> {
            listener.onValue(value);
            changed();
        });
        return view;
    }

    private Switch switchRow(int text, boolean checked) {
        Switch view = new Switch(this);
        view.setText(text);
        view.setTextColor(Ui.TEXT);
        view.setTextSize(15);
        view.setChecked(checked);
        view.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 4));
        return view;
    }

    private void addSlider(LinearLayout parent, String title, int min, int max, int current,
            Formatter formatter, IntListener onChange, IntListener onRelease) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        Ui.topMargin(header, 14);
        header.addView(Ui.text(this, title, 14, Ui.TEXT), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView value = Ui.text(this, formatter.format(current), 14, Ui.TEXT_SECONDARY);
        header.addView(value);
        parent.addView(header);
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMin(min);
        seekBar.setMax(max);
        seekBar.setProgress(Math.max(min, Math.min(max, current)));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                value.setText(formatter.format(progress));
                if (fromUser) {
                    onChange.onValue(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                if (onRelease != null) {
                    onRelease.onValue(bar.getProgress());
                }
            }
        });
        parent.addView(seekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private RadioGroup radioGroup() {
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        return group;
    }

    private RadioButton radio(int text) {
        RadioButton button = new RadioButton(this);
        // RadioGroup records a pre-checked child by id while adding it, before it would generate one.
        button.setId(View.generateViewId());
        button.setText(text);
        button.setTextColor(Ui.TEXT);
        button.setTextSize(15);
        return button;
    }

    private TextView label(int text) {
        TextView view = Ui.text(this, text, 14, Ui.TEXT_SECONDARY);
        view.setTypeface(null, android.graphics.Typeface.BOLD);
        Ui.topMargin(view, 14);
        return view;
    }

    private TextView hint(int text) {
        TextView view = Ui.text(this, text, 13, Ui.TEXT_SECONDARY);
        view.setLineSpacing(0, 1.15f);
        Ui.topMargin(view, 6);
        return view;
    }

    /** Adds a card whose body starts collapsed and returns the body for its controls. */
    private LinearLayout collapsibleCard(LinearLayout page, int titleRes, int summaryRes) {
        LinearLayout card = Ui.card(this);
        LinearLayout body = collapsible(card, titleRes, summaryRes);
        page.addView(card);
        return body;
    }

    /** The header stays outside the body, so rebuilding the body keeps it open or closed. */
    private LinearLayout collapsible(LinearLayout parent, int titleRes, int summaryRes) {
        String title = getString(titleRes);
        LinearLayout header = vertical();
        header.setMinimumHeight(Ui.dp(this, 40));
        header.setClickable(true);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(Ui.heading(this, title, 20), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView chevron = Ui.heading(this, "▸", 26);
        chevron.setTextColor(Ui.ACCENT);
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        titleRow.addView(chevron);
        header.addView(titleRow);
        TextView summary = Ui.text(this, summaryRes, 13, Ui.TEXT_SECONDARY);
        summary.setLineSpacing(0, 1.1f);
        header.addView(summary);
        parent.addView(header);

        LinearLayout body = vertical();
        body.setVisibility(View.GONE);
        parent.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        header.setContentDescription(getString(R.string.collapsed_section, title));
        header.setOnClickListener(view -> {
            boolean expand = body.getVisibility() != View.VISIBLE;
            body.setVisibility(expand ? View.VISIBLE : View.GONE);
            chevron.setText(expand ? "▾" : "▸");
            header.setContentDescription(getString(expand
                    ? R.string.expanded_section : R.string.collapsed_section, title));
        });
        return body;
    }

    private LinearLayout settingsGroup() {
        LinearLayout group = vertical();
        group.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 12));
        group.setBackground(Ui.rounded(Ui.SURFACE_RAISED, Ui.dp(this, 8)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 6);
        params.bottomMargin = Ui.dp(this, 6);
        group.setLayoutParams(params);
        return group;
    }

    private void setGroupEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                setGroupEnabled(group.getChildAt(index), enabled);
            }
        }
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout.LayoutParams weighted(int widthDp, int rightMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                widthDp == 0 ? 0 : Ui.dp(this, widthDp), ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.rightMargin = Ui.dp(this, rightMarginDp);
        return params;
    }
}
