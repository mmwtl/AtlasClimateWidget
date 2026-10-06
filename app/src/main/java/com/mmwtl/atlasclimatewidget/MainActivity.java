package com.mmwtl.atlasclimatewidget;

import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
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

/**
 * Settings editor with Blocks, Tiles, Look and System tabs pinned under the title, like the
 * widgetkit settings of AtlasAppWidget and AtlasMediaWidget. The widget selector and the live
 * preview sit in the pinned header on the three layout tabs.
 */
public final class MainActivity extends ScaledActivity {
    private static final int TEMPLATE = AppWidgetManager.INVALID_APPWIDGET_ID;
    private static final long STATUS_REFRESH_MS = 3_000L;
    private static final String STATE_TAB = "settings_tab";
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

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable statusTask = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            handler.postDelayed(this, STATUS_REFRESH_MS);
        }
    };

    private final LinearLayout[] tabPages = new LinearLayout[TAB_LABELS.length];
    private final TextView[] tabButtons = new TextView[TAB_LABELS.length];
    private int selectedTab;

    private Prefs prefs;
    private int editedWidget = TEMPLATE;
    private WidgetConfig config;
    private int[] widgetIds = new int[0];

    private ScrollView settingsScroll;
    private LinearLayout previewPanel;
    private TextView previewTitle;
    private TextView statusText;
    private Spinner widgetSpinner;
    private TileDragLayer previewHost;
    private TextView previewCaption;
    private TextView heightReport;
    private LinearLayout heightHost;
    private LinearLayout blocksHost;
    private LinearLayout tilesHost;
    private LinearLayout functionsHost;
    private LinearLayout availableHost;
    private LinearLayout appearanceHost;
    private LinearLayout cardsHost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        widgetIds = ClimateService.widgetIds(this);
        editedWidget = requestedWidget(getIntent());
        if (editedWidget == TEMPLATE && widgetIds.length > 0) {
            editedWidget = widgetIds[0];
        }
        config = loadConfig(editedWidget);
        View content = buildContent(savedInstanceState == null
                ? TAB_BLOCKS : savedInstanceState.getInt(STATE_TAB, TAB_BLOCKS));
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
        reloadWidgetList();
        handler.post(statusTask);
        previewHost.post(this::refreshPreview);
    }

    @Override
    protected void onPause() {
        ClimateService.setStateListener(null);
        handler.removeCallbacks(statusTask);
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
        titleRow.addView(Ui.heading(this, R.string.app_name, 24), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        previewTitle = Ui.text(this, R.string.preview_title, 13, Ui.TEXT_SECONDARY);
        titleRow.addView(previewTitle);
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
                int target = position < widgetIds.length ? widgetIds[position] : TEMPLATE;
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
        LinearLayout height = Ui.card(this);
        height.addView(Ui.heading(this, R.string.height_title, 20));
        heightHost = vertical();
        height.addView(heightHost);
        heightReport = hint(R.string.height_report_unknown);
        height.addView(heightReport);
        page.addView(height);

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
    }

    private void buildLookTab(LinearLayout page) {
        LinearLayout appearance = Ui.card(this);
        appearance.addView(Ui.heading(this, R.string.appearance_title, 20));
        appearanceHost = vertical();
        appearance.addView(appearanceHost);
        page.addView(appearance);

        cardsHost = collapsibleCard(page, R.string.cards_title, R.string.cards_summary);
    }

    private void buildSystemTab(LinearLayout page) {
        page.addView(buildStatusCard());
        page.addView(buildBehaviourCard());
        page.addView(buildInterfaceCard());
        page.addView(buildBackupCard());
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
            Toast.makeText(this, R.string.status_refresh_sent, Toast.LENGTH_SHORT).show();
        });
        card.addView(refresh);

        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        if (manager.isRequestPinAppWidgetSupported()) {
            Button pin = Ui.button(this, R.string.pin_widget);
            Ui.topMargin(pin, 8);
            pin.setOnClickListener(view -> manager.requestPinAppWidget(
                    new ComponentName(this, ClimateWidgetProvider.class), null, null));
            card.addView(pin);
        } else {
            TextView pinHint = hint(R.string.pin_widget_manual);
            card.addView(pinHint);
        }
        return card;
    }

    /** Actions on the layout picked in the pinned selector. */
    private LinearLayout buildLayoutCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, R.string.edited_widget_title, 20));
        card.addView(hint(R.string.edited_widget_hint));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Ui.topMargin(actions, 10);
        Button applyAll = Ui.button(this, R.string.apply_to_all);
        applyAll.setOnClickListener(view -> {
            prefs.setTemplate(config);
            for (int id : widgetIds) {
                prefs.setWidget(id, config);
            }
            ClimateService.start(this, ClimateService.ACTION_REFRESH);
            Toast.makeText(this, R.string.applied_to_all, Toast.LENGTH_SHORT).show();
        });
        Button reset = Ui.button(this, R.string.reset_layout);
        reset.setOnClickListener(view -> {
            config = prefs.resolved(new WidgetConfig());
            save();
            rebuildEditor();
            refreshPreview();
        });
        actions.addView(applyAll, weighted(0, 8));
        actions.addView(reset, weighted(0, 0));
        card.addView(actions);
        return card;
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
                    presetsChanged();
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
                    presetsChanged();
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
        rebuildHeight();
        rebuildBlocks();
        rebuildTiles();
        rebuildFunctions();
        rebuildAppearance();
    }

    private void rebuildHeight() {
        heightHost.removeAllViews();
        RadioGroup heights = radioGroup();
        for (WidgetConfig.HeightMode mode : WidgetConfig.HeightMode.values()) {
            RadioButton button = radio(mode.titleRes);
            button.setChecked(config.heightMode == mode);
            button.setOnCheckedChangeListener((view, checked) -> {
                if (checked && config.heightMode != mode) {
                    config.heightMode = mode;
                    changed();
                    view.post(this::rebuildHeight);
                }
            });
            heights.addView(button);
        }
        heightHost.addView(heights);
        if (config.heightMode == WidgetConfig.HeightMode.FILL) {
            heightHost.addView(hint(R.string.height_fill_hint));
            return;
        }
        LinearLayout aligns = new LinearLayout(this);
        aligns.setOrientation(LinearLayout.HORIZONTAL);
        Ui.topMargin(aligns, 8);
        for (WidgetConfig.VerticalAlign align : WidgetConfig.VerticalAlign.values()) {
            TextView segment = Ui.segment(this, align.titleRes);
            Ui.setSegmentSelected(this, segment, config.verticalAlign == align);
            segment.setOnClickListener(view -> {
                config.verticalAlign = align;
                changed();
                rebuildHeight();
            });
            Ui.addSegment(aligns, segment);
        }
        heightHost.addView(aligns);
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
                case TEMPERATURE:
                    options.addView(partSwitch(R.string.temp_header, config.temperatureHeader,
                            value -> config.temperatureHeader = value,
                            config::hasTemperatureParts));
                    if (config.temperatureHeader) {
                        options.addView(headerAligns());
                        options.addView(configSwitch(R.string.temp_header_fuel,
                                config.headerFuel, value -> config.headerFuel = value));
                        options.addView(configSwitch(R.string.temp_header_fuel_free,
                                config.headerFuelFree, value -> config.headerFuelFree = value));
                    }
                    options.addView(partSwitch(R.string.temp_part_bar, config.temperatureBar,
                            value -> config.temperatureBar = value,
                            config::hasTemperatureParts));
                    if (config.temperatureBar) {
                        options.addView(configSwitch(R.string.temp_dual, config.temperatureDual,
                                value -> config.temperatureDual = value));
                        options.addView(configSwitch(R.string.temp_buttons,
                                config.temperatureButtons,
                                value -> config.temperatureButtons = value));
                    }
                    options.addView(hint(R.string.temp_hint));
                    break;
                case FAN:
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
        options.addView(label(R.string.tile_style_title));
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
        options.addView(styles);
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
            if (!enabled.contains(function)) {
                availableHost.addView(functionRow(function, false, false, false));
            }
        }
    }

    private View functionRow(ClimateFunction function, boolean enabled, boolean canUp,
            boolean canDown) {
        LinearLayout row = orderRow(getString(function.titleRes), enabled, canUp, canDown,
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
        appearanceHost.addView(label(R.string.palette_title));
        RadioGroup palettes = radioGroup();
        for (Palette palette : Palette.values()) {
            RadioButton button = radio(palette.titleRes);
            button.setChecked(config.palette == palette);
            button.setOnCheckedChangeListener((view, checked) -> {
                if (checked) {
                    config.palette = palette;
                    changed();
                }
            });
            palettes.addView(button);
        }
        appearanceHost.addView(palettes);
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
        addSlider(appearanceHost, getString(R.string.widget_scale),
                WidgetConfig.SCALE_MIN_PERCENT, WidgetConfig.SCALE_MAX_PERCENT,
                config.scalePercent, value -> value + "%", value -> {
                    config.scalePercent = value;
                    changed();
                }, null);
        appearanceHost.addView(hint(R.string.widget_scale_hint));

        cardsHost.removeAllViews();
        addSlider(cardsHost, getString(R.string.card_opacity), 0, 100,
                config.cardOpacityPercent, value -> value + "%", value -> {
                    config.cardOpacityPercent = value;
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
    }

    // ---- state -------------------------------------------------------------------------------

    private WidgetConfig loadConfig(int widgetId) {
        return widgetId == TEMPLATE ? prefs.template() : prefs.widget(widgetId);
    }

    /** The preset count follows global settings, so the edited layout picks it up again. */
    private void presetsChanged() {
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
        widgetIds = ClimateService.widgetIds(this);
        float density = getApplicationContext().getResources().getDisplayMetrics().density;
        List<String> labels = new ArrayList<>();
        int selection = widgetIds.length;
        for (int index = 0; index < widgetIds.length; index++) {
            WidgetViews.Size size = ClimateService.widgetSize(getApplicationContext(),
                    widgetIds[index]);
            labels.add(getString(R.string.widget_item, index + 1,
                    WidgetViews.describe(size, density)));
            if (widgetIds[index] == editedWidget) {
                selection = index;
            }
        }
        labels.add(getString(R.string.widget_template));
        if (selection == widgetIds.length && editedWidget != TEMPLATE) {
            editedWidget = TEMPLATE;
            config = loadConfig(TEMPLATE);
            rebuildEditor();
        }
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
                    text.setPadding(Ui.dp(MainActivity.this, 14), Ui.dp(MainActivity.this, 10),
                            Ui.dp(MainActivity.this, 14), Ui.dp(MainActivity.this, 10));
                }
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        widgetSpinner.setAdapter(adapter);
        widgetSpinner.setSelection(selection, false);
    }

    private void refreshLive() {
        refreshPreview();
        refreshStatus();
    }

    private void refreshStatus() {
        if (statusText == null) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        StringBuilder text = new StringBuilder();
        text.append(getString(Gib.isInstalled(this)
                ? R.string.status_bridge_installed : R.string.status_bridge_missing));
        text.append('\n');
        long last = ClimateService.STORE.lastResultAt();
        if (last <= 0L) {
            text.append(getString(R.string.status_no_answer));
        } else {
            text.append(getString(R.string.status_answer, Math.max(0, (now - last) / 1000L)));
        }
        text.append('\n');
        text.append(getString(R.string.status_widgets, ClimateService.widgetIds(this).length));
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
        } else {
            size = ClimateService.widgetSize(getApplicationContext(), editedWidget);
        }
        try {
            RemoteViews views = WidgetViews.build(getApplicationContext(), config,
                    demo ? DemoState.INSTANCE : live, prefs.carModel(), size, false, 1f);
            View preview = views.apply(this, previewHost);
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
            previewHost.setLayout(config, plan);
            heightReport.setText(HeightReport.describe(this, config, plan));
        } catch (RuntimeException error) {
            AppLog.warn("Preview failed", error);
        }
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
