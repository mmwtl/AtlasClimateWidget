package com.mmwtl.atlasclimatewidget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
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

import java.util.ArrayList;
import java.util.List;

/** Single settings screen: bridge status, widget constructor with live preview, behaviour. */
public final class MainActivity extends ScaledActivity {
    private static final int TEMPLATE = AppWidgetManager.INVALID_APPWIDGET_ID;
    private static final long STATUS_REFRESH_MS = 3_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable statusTask = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            handler.postDelayed(this, STATUS_REFRESH_MS);
        }
    };

    private Prefs prefs;
    private int editedWidget = TEMPLATE;
    private WidgetConfig config;
    private int[] widgetIds = new int[0];

    private TextView statusText;
    private Spinner widgetSpinner;
    private FrameLayout previewHost;
    private TextView previewCaption;
    private TextView heightReport;
    private LinearLayout blocksHost;
    private LinearLayout functionsHost;
    private LinearLayout appearanceHost;

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
        setContentView(buildContent());
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        int requested = requestedWidget(intent);
        if (requested != TEMPLATE && requested != editedWidget) {
            editedWidget = requested;
            config = loadConfig(requested);
            rebuildEditor();
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

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BACKGROUND);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 24), Ui.dp(this, 42));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Ui.applySystemBarInsets(scroll);

        content.addView(Ui.heading(this, R.string.app_name, 26));
        TextView subtitle = Ui.text(this, R.string.main_subtitle, 15, Ui.TEXT_SECONDARY);
        subtitle.setLineSpacing(0, 1.12f);
        Ui.topMargin(subtitle, 6);
        content.addView(subtitle);
        spacer(content, 14);

        content.addView(buildStatusCard());
        content.addView(buildWidgetCard());
        content.addView(buildPreviewCard());

        addSectionHeading(content, R.string.section_constructor);
        LinearLayout blocks = Ui.card(this);
        blocks.addView(Ui.heading(this, R.string.blocks_title, 20));
        blocks.addView(hint(R.string.blocks_hint));
        blocksHost = vertical();
        blocks.addView(blocksHost);
        content.addView(blocks);

        LinearLayout functions = Ui.card(this);
        functions.addView(Ui.heading(this, R.string.functions_title, 20));
        functions.addView(hint(R.string.functions_hint));
        functionsHost = vertical();
        functions.addView(functionsHost);
        content.addView(functions);

        LinearLayout appearance = Ui.card(this);
        appearance.addView(Ui.heading(this, R.string.appearance_title, 20));
        appearanceHost = vertical();
        appearance.addView(appearanceHost);
        content.addView(appearance);

        addSectionHeading(content, R.string.section_behaviour);
        content.addView(buildBehaviourCard());
        content.addView(buildInterfaceCard());

        rebuildEditor();
        return scroll;
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

    private LinearLayout buildWidgetCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, R.string.edited_widget_title, 20));
        card.addView(hint(R.string.edited_widget_hint));
        widgetSpinner = new Spinner(this);
        Ui.topMargin(widgetSpinner, 10);
        widgetSpinner.setBackground(Ui.rounded(Ui.SURFACE_RAISED, Ui.dp(this, 8)));
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
        card.addView(widgetSpinner);

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
        });
        actions.addView(applyAll, weighted(0, 8));
        actions.addView(reset, weighted(0, 0));
        card.addView(actions);
        return card;
    }

    private LinearLayout buildPreviewCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.heading(this, R.string.preview_title, 20));
        previewCaption = hint(R.string.preview_live);
        card.addView(previewCaption);
        previewHost = new FrameLayout(this);
        previewHost.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12),
                Ui.dp(this, 12));
        previewHost.setBackground(Ui.rounded(Ui.BACKGROUND, Ui.dp(this, 8)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 10);
        card.addView(previewHost, params);
        heightReport = hint(R.string.height_report_unknown);
        card.addView(heightReport);
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

    // ---- constructor -------------------------------------------------------------------------

    private void rebuildEditor() {
        if (blocksHost == null) {
            return;
        }
        rebuildBlocks();
        rebuildFunctions();
        rebuildAppearance();
    }

    private void rebuildBlocks() {
        blocksHost.removeAllViews();
        blocksHost.addView(label(R.string.height_title));
        RadioGroup heights = radioGroup();
        for (WidgetConfig.HeightMode mode : WidgetConfig.HeightMode.values()) {
            RadioButton button = radio(mode.titleRes);
            button.setChecked(config.heightMode == mode);
            button.setOnCheckedChangeListener((view, checked) -> {
                if (checked && config.heightMode != mode) {
                    config.heightMode = mode;
                    changed();
                    view.post(this::rebuildBlocks);
                }
            });
            heights.addView(button);
        }
        blocksHost.addView(heights);
        if (config.heightMode == WidgetConfig.HeightMode.FILL) {
            blocksHost.addView(hint(R.string.height_fill_hint));
        } else {
            LinearLayout aligns = new LinearLayout(this);
            aligns.setOrientation(LinearLayout.HORIZONTAL);
            Ui.topMargin(aligns, 8);
            for (WidgetConfig.VerticalAlign align : WidgetConfig.VerticalAlign.values()) {
                TextView segment = Ui.segment(this, align.titleRes);
                Ui.setSegmentSelected(this, segment, config.verticalAlign == align);
                segment.setOnClickListener(view -> {
                    config.verticalAlign = align;
                    changed();
                    rebuildBlocks();
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (aligns.getChildCount() > 0) {
                    params.leftMargin = Ui.dp(this, 6);
                }
                aligns.addView(segment, params);
            }
            blocksHost.addView(aligns);
        }
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
                    options.addView(configSwitch(R.string.temp_dual, config.temperatureDual,
                            value -> config.temperatureDual = value));
                    options.addView(configSwitch(R.string.temp_header, config.temperatureHeader,
                            value -> config.temperatureHeader = value));
                    options.addView(configSwitch(R.string.temp_buttons,
                            config.temperatureButtons, value -> config.temperatureButtons = value));
                    options.addView(hint(R.string.temp_hint));
                    break;
                case FAN: {
                    options.addView(fanPartSwitch(R.string.fan_part_bar, config.fanBar,
                            value -> config.fanBar = value));
                    if (config.fanBar) {
                        options.addView(configSwitch(R.string.fan_buttons, config.fanButtons,
                                value -> config.fanButtons = value));
                    }
                    options.addView(fanPartSwitch(R.string.fan_part_directions,
                            config.fanDirections, value -> config.fanDirections = value));
                    options.addView(fanPartSwitch(R.string.fan_part_presets, config.fanPresets,
                            value -> config.fanPresets = value));
                    options.addView(hint(R.string.fan_hint));
                    break;
                }
                default:
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
                    break;
            }
            boolean enabled = block == WidgetConfig.Block.TILES
                    ? config.tilesEnabled : config.isEnabled(block);
            setGroupEnabled(options, enabled);
            options.setAlpha(enabled ? 1f : 0.45f);
            blocksHost.addView(options);
        }
    }

    private void rebuildFunctions() {
        functionsHost.removeAllViews();
        List<ClimateFunction> enabled = new ArrayList<>(config.functions);
        for (int index = 0; index < enabled.size(); index++) {
            ClimateFunction function = enabled.get(index);
            functionsHost.addView(functionRow(function, true, index > 0,
                    index < enabled.size() - 1));
        }
        TextView available = label(R.string.functions_available);
        functionsHost.addView(available);
        for (ClimateFunction function : ClimateFunction.values()) {
            if (!enabled.contains(function)) {
                functionsHost.addView(functionRow(function, false, false, false));
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
        addSlider(appearanceHost, getString(R.string.card_opacity), 0, 100,
                config.cardOpacityPercent, value -> value + "%", value -> {
                    config.cardOpacityPercent = value;
                    changed();
                }, null);
        addSlider(appearanceHost, getString(R.string.card_radius), 0,
                WidgetConfig.CARD_RADIUS_MAX_DP, config.cardRadiusDp,
                value -> getString(R.string.dp_value, value), value -> {
                    config.cardRadiusDp = value;
                    changed();
                }, null);
        addSlider(appearanceHost, getString(R.string.card_padding), WidgetConfig.PADDING_MIN_DP,
                WidgetConfig.PADDING_MAX_DP, config.cardPaddingDp,
                value -> getString(R.string.dp_value, value), value -> {
                    config.cardPaddingDp = value;
                    changed();
                }, null);
        addSlider(appearanceHost, getString(R.string.gap), WidgetConfig.GAP_MIN_DP,
                WidgetConfig.GAP_MAX_DP, config.gapDp,
                value -> getString(R.string.dp_value, value), value -> {
                    config.gapDp = value;
                    changed();
                }, null);
        addSlider(appearanceHost, getString(R.string.tile_radius), 0,
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
            int maxWidth = Math.min(width, Math.round(size.widthPx
                    * getResources().getDisplayMetrics().density
                    / getApplicationContext().getResources().getDisplayMetrics().density));
            previewHost.removeAllViews();
            previewHost.addView(HeightReport.framePreview(this, preview, size, maxWidth));
            WidgetGeometry.Plan plan = WidgetGeometry.plan(config, size.widthPx, size.heightPx,
                    getApplicationContext().getResources().getDisplayMetrics().density,
                    ClimateCommands.tempRange(demo ? DemoState.INSTANCE : live).steps());
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
    private Switch fanPartSwitch(int text, boolean checked, BoolListener listener) {
        Switch view = switchRow(text, checked);
        view.setOnCheckedChangeListener((button, value) -> {
            listener.onValue(value);
            if (!config.hasFanParts()) {
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

    private void addSectionHeading(LinearLayout content, int titleRes) {
        TextView heading = Ui.heading(this, titleRes, 16);
        heading.setTextColor(Ui.ACCENT);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 14);
        params.bottomMargin = Ui.dp(this, 10);
        content.addView(heading, params);
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

    private void spacer(LinearLayout parent, int heightDp) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, heightDp)));
    }
}
