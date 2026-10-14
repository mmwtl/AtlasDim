package com.mmwtl.atlasdim;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Редактор композиции в оформлении Atlas: под заголовком закреплены вкладки «Часы / Топливо /
 * Фон / Система» и предпросмотр с перетаскиванием, внизу — кнопки применения. На приборку
 * композицию выводит {@link SceneService}; Activity можно закрыть.
 */
public final class MainActivity extends ScaledActivity {
    /** Оранжевый акцент иконок Atlas: несохранённые на приборке правки. */
    private static final int PENDING = 0xFFE8793B;
    private static final int NUMBER = InputType.TYPE_CLASS_NUMBER;
    private static final int PICK_IMAGE = 1;
    private static final int READ_IMAGES = 2;
    private static final int[] SWATCHES = {0xff10243a, 0xff000000, 0xff1b1f27, 0xff0f2a2a,
            0xff2a1838, 0xff3a1414, 0xff1a2f14, 0xff2b2b2b};
    private static final SceneConfig.Kind[] KINDS = {SceneConfig.Kind.CLOCK,
            SceneConfig.Kind.FUEL, SceneConfig.Kind.BACKGROUND};
    private static final int TAB_SYSTEM = 3;
    private static final int KEEP_REPORTS = 20;
    private static final String KEY_UNRESTRICTED = "unrestricted";
    private static final int[] TAB_LABELS = {R.string.tab_clock, R.string.tab_fuel,
            R.string.tab_background, R.string.tab_system};
    private boolean ready;
    private boolean syncing;
    private boolean dirty;
    private SceneConfig previewConfig = new SceneConfig();
    private ScrollView scroll;
    private View stage;
    private ScenePreview preview;
    private TextView previewStatus;
    private TextView status;
    private TextView pill;
    private EditText host;
    private EditText password;
    private Switch boot;
    private Switch unrestricted;
    private TextView backgroundLocked;
    private Button diagButton;
    private TextView diagStatus;
    private LayerEditor clock;
    private LayerEditor fuel;
    private LayerEditor background;
    private final List<TextView> tabs = new ArrayList<>();
    private final List<View> panels = new ArrayList<>();
    private Switch seconds;
    private Switch plate;
    private Switch grid;
    private EditText color;
    private SeekBar alpha;
    private TextView alphaValue;
    private LinearLayout fills;
    private View colorRow;
    private String fill;
    private Switch fuelLiters;
    private Switch fuelFree;
    private Switch fuelPlate;
    private EditText fuelMultiplier;
    private EditText fuelOffset;
    private FuelSensor fuelSensor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Backgrounds.init(this);
        SharedPreferences prefs = prefs();
        fill = prefs.getString("fill", Backgrounds.ASSET + "c02-neon-horizon.webp");

        LinearLayout root = column();
        root.setBackgroundColor(Ui.BACKGROUND);
        root.addView(buildHeader());

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = column();
        content.setPadding(dp(24), dp(8), dp(24), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        buildClockTab(page(content));
        buildFuelTab(page(content));
        buildBackgroundTab(page(content));
        buildSystemTab(page(content));
        root.addView(buildActions());
        setContentView(root);
        Ui.applySystemBarInsets(root);

        ready = true;
        rebuildFills();
        showTab(prefs.getInt("tab", 0));
        refreshPreview();
        dirty = false; // Открытие экрана — не правка; плашку задаст состояние сервиса в onStart.
        setPill(getString(R.string.pill_idle), Ui.TEXT_SECONDARY);
        Log.i("AtlasDim", "AtlasDim v" + version() + " started");
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Окна живут в сервисе; Activity только показывает его состояние.
        SceneService.setListener(this::onSceneState);
        fuelSensor = new FuelSensor(this);
        fuelSensor.start();
    }

    @Override
    protected void onStop() {
        SceneService.setListener(null);
        fuelSensor.stop();
        super.onStop();
    }

    // ---- Разметка ----

    /** Заголовок, вкладки и предпросмотр — закреплены над прокручиваемыми настройками. */
    private View buildHeader() {
        LinearLayout header = column();
        header.setPadding(dp(24), dp(16), dp(24), dp(12));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setBaselineAligned(false);
        titleRow.addView(Ui.heading(this, R.string.app_name, 24));
        TextView versionView = Ui.text(this, "v" + version(), 12, Ui.TEXT_SECONDARY);
        versionView.setPadding(dp(8), dp(6), 0, 0);
        titleRow.addView(versionView, new LinearLayout.LayoutParams(0, -2, 1));
        pill = Ui.text(this, "", 13, Ui.TEXT);
        pill.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        pill.setPadding(dp(12), dp(6), dp(12), dp(6));
        titleRow.addView(pill);
        ImageView donate = new ImageView(this);
        donate.setImageResource(R.drawable.ic_heart);
        donate.setColorFilter(Ui.ACCENT);
        donate.setPadding(dp(8), dp(8), dp(8), dp(8));
        donate.setBackground(Ui.rounded(Ui.SURFACE, dp(8)));
        donate.setContentDescription(getString(R.string.donate_title));
        donate.setOnClickListener(v -> DonateDialog.show(this));
        LinearLayout.LayoutParams heart = new LinearLayout.LayoutParams(dp(40), dp(40));
        heart.leftMargin = dp(12);
        titleRow.addView(donate, heart);
        header.addView(titleRow);

        LinearLayout tabBar = new LinearLayout(this);
        for (int i = 0; i < TAB_LABELS.length; i++) {
            int index = i;
            TextView tab = Ui.segment(this, TAB_LABELS[i]);
            tab.setTextSize(15);
            tab.setOnClickListener(v -> showTab(index));
            Ui.addSegment(tabBar, tab);
            tabs.add(tab);
        }
        Ui.topMargin(tabBar, 12);
        header.addView(tabBar);
        header.addView(buildStage());
        return header;
    }

    /** Предпросмотр: окна выбираются и двигаются пальцем, угол — размер. */
    private View buildStage() {
        LinearLayout box = column();
        Ui.topMargin(box, 12);
        LinearLayout head = new LinearLayout(this);
        head.addView(Ui.text(this, R.string.preview_title, 13, Ui.TEXT_SECONDARY),
                new LinearLayout.LayoutParams(0, -2, 1));
        for (SceneConfig.Kind kind : KINDS) {
            head.addView(legend(kind));
        }
        box.addView(head);
        preview = new ScenePreview(this);
        box.addView(rounded(preview), margin(8));
        previewStatus = Ui.text(this, "", 13, Ui.TEXT_SECONDARY);
        previewStatus.setLineSpacing(0, 1.1f);
        box.addView(previewStatus, margin(6));
        preview.setEditListener(new ScenePreview.EditListener() {
            @Override public void onSelect(SceneConfig.Kind kind) {
                showTab(Arrays.asList(KINDS).indexOf(kind));
            }

            @Override public void onMoved(SceneConfig.Layer layer, boolean done) {
                editor(layer.kind).setGeometry(layer);
            }
        });
        stage = box;
        return box;
    }

    private void buildClockTab(LinearLayout page) {
        SharedPreferences prefs = prefs();
        clock = new LayerEditor(page, previewConfig.clock);
        LinearLayout look = card(page, R.string.clock_look_title);
        seconds = toggle(look, R.string.clock_seconds, prefs.getBoolean("seconds", false));
        plate = toggle(look, R.string.digits_plate, prefs.getBoolean("plate", false));
    }

    private void buildFuelTab(LinearLayout page) {
        SharedPreferences prefs = prefs();
        fuel = new LayerEditor(page, previewConfig.fuel);
        LinearLayout shown = card(page, R.string.fuel_shown_title);
        fuelLiters = toggle(shown, R.string.fuel_liters, prefs.getBoolean("fuel.liters", true));
        fuelFree = toggle(shown, R.string.fuel_free, prefs.getBoolean("fuel.free", true));
        fuelPlate = toggle(shown, R.string.digits_plate, prefs.getBoolean("fuel.plate", false));

        LinearLayout formula = card(page, R.string.fuel_formula_title);
        formula.addView(hint(R.string.fuel_formula_summary));
        LinearLayout cells = new LinearLayout(this);
        fuelMultiplier = formulaCell(cells, R.string.fuel_multiplier,
                prefs.getString("fuel.multiplier", Fuel.format(Fuel.MULTIPLIER)));
        fuelOffset = formulaCell(cells, R.string.fuel_offset,
                prefs.getString("fuel.offset", Fuel.format(Fuel.OFFSET)));
        formula.addView(cells, margin(10));
        formula.addView(hint(R.string.fuel_formula_hint));
    }

    private void buildBackgroundTab(LinearLayout page) {
        SharedPreferences prefs = prefs();
        background = new LayerEditor(page, previewConfig.background);
        backgroundLocked = hint(getString(R.string.background_single_layer, Build.MODEL));
        ((LinearLayout) background.enabled.getParent()).addView(backgroundLocked, 1);
        lockBackground(prefs.getBoolean(KEY_UNRESTRICTED, false));
        LinearLayout picture = card(page, R.string.background_picture_title);
        HorizontalScrollView strip = new HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        fills = new LinearLayout(this);
        strip.addView(fills);
        picture.addView(strip, margin(10));
        LinearLayout swatches = new LinearLayout(this);
        swatches.setGravity(Gravity.CENTER_VERTICAL);
        colorRow = swatches;
        color = new EditText(this);
        for (int swatch : SWATCHES) {
            View dot = new View(this);
            GradientDrawable d = Ui.rounded(swatch, dp(18));
            d.setStroke(dp(1), 0x55ffffff);
            dot.setBackground(d);
            dot.setOnClickListener(v -> color.setText(String.format("#%06X", swatch & 0xffffff)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(36));
            lp.rightMargin = dp(8);
            swatches.addView(dot, lp);
        }
        styleField(color, getString(R.string.background_color_hint),
                prefs.getString("color", "#10243A"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        swatches.addView(color, new LinearLayout.LayoutParams(dp(130), -2));
        picture.addView(swatches, margin(10));

        picture.addView(label(R.string.background_opacity));
        LinearLayout alphaRow = new LinearLayout(this);
        alphaRow.setGravity(Gravity.CENTER_VERTICAL);
        alpha = new SeekBar(this);
        alpha.setMax(255);
        alpha.setContentDescription(getString(R.string.background_opacity));
        alphaValue = Ui.text(this, "", 14, Ui.TEXT_SECONDARY);
        alphaValue.setGravity(Gravity.END);
        alpha.setProgress(Math.max(0, Math.min(255, parse(prefs.getString("alpha", "255"), 255))));
        alpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                refreshPreview();
            }

            @Override public void onStartTrackingTouch(SeekBar bar) { }

            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        alphaRow.addView(alpha, new LinearLayout.LayoutParams(0, -2, 1));
        alphaRow.addView(alphaValue, new LinearLayout.LayoutParams(dp(56), -2));
        picture.addView(alphaRow);
        grid = toggle(picture, R.string.background_grid, prefs.getBoolean("grid", false));
        if (!SceneConfig.SINGLE_LAYER) {
            picture.addView(hint(R.string.background_hint));
        }
    }

    private void buildSystemTab(LinearLayout page) {
        SharedPreferences prefs = prefs();
        LinearLayout link = card(page, R.string.link_title);
        boolean built = !BuildConfig.QNX_PASSWORD.isEmpty();
        link.addView(built ? hint(getString(R.string.link_hint_built, BuildConfig.QNX_HOST))
                : hint(R.string.link_hint_missing));
        boot = toggle(link, R.string.link_boot, prefs.getBoolean(SceneService.KEY_BOOT, true));
        boot.setContentDescription("Автозапуск после загрузки");
        link.addView(hint(R.string.link_background_hint));
        // Хост и пароль под катом: обычно хватает значений из сборки.
        TextView manualHead = label(R.string.link_manual);
        manualHead.setClickable(true);
        manualHead.setPadding(0, dp(8), 0, dp(8));
        link.addView(manualHead);
        LinearLayout manual = column();
        link.addView(manual);
        host = field(manual, R.string.link_host, prefs.getString("host", BuildConfig.QNX_HOST),
                InputType.TYPE_CLASS_TEXT);
        password = field(manual, R.string.link_password, prefs.getString("password", ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setHint(built ? R.string.link_password_built : R.string.link_password_missing);
        manual.addView(hint(R.string.link_ports_hint));
        setExpanded(manualHead, manual, getString(R.string.link_manual), !built);
        manualHead.setOnClickListener(v ->
                setExpanded(manualHead, manual, getString(R.string.link_manual),
                        manual.getVisibility() != View.VISIBLE));

        LinearLayout diag = card(page, R.string.diag_title);
        diag.addView(hint(R.string.diag_hint));
        diagButton = Ui.button(this, R.string.diag_button);
        Ui.topMargin(diagButton, 12);
        diagButton.setOnClickListener(v -> runDiagnostics());
        diag.addView(diagButton);
        diagStatus = hint("");
        diagStatus.setVisibility(View.GONE);
        diag.addView(diagStatus);

        LinearLayout limits = card(page, R.string.limits_title);
        unrestricted = toggle(limits, R.string.limits_off, prefs.getBoolean(KEY_UNRESTRICTED, false));
        unrestricted.setOnCheckedChangeListener((v, value) -> {
            lockBackground(value);
            refreshPreview();
        });
        limits.addView(hint(R.string.limits_hint));

        LinearLayout ui = card(page, R.string.interface_title);
        ui.addView(hint(R.string.interface_hint));
        LinearLayout scaleHead = new LinearLayout(this);
        scaleHead.addView(Ui.text(this, R.string.interface_scale, 14, Ui.TEXT),
                new LinearLayout.LayoutParams(0, -2, 1));
        TextView scaleValue = Ui.text(this, "", 14, Ui.TEXT_SECONDARY);
        scaleHead.addView(scaleValue);
        ui.addView(scaleHead, margin(14));
        SeekBar scale = new SeekBar(this);
        scale.setMin(MIN_SCALE_TENTHS);
        scale.setMax(MAX_SCALE_TENTHS);
        scale.setContentDescription(getString(R.string.interface_scale));
        scale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                scaleValue.setText(scaleText(value));
            }

            @Override public void onStartTrackingTouch(SeekBar bar) { }

            @Override public void onStopTrackingTouch(SeekBar bar) {
                if (bar.getProgress() != configuredScaleTenths(MainActivity.this)) {
                    setScaleTenths(MainActivity.this, bar.getProgress());
                    recreate();
                }
            }
        });
        scale.setProgress(configuredScaleTenths(this));
        scaleValue.setText(scaleText(scale.getProgress()));
        ui.addView(scale, margin(4));

        LinearLayout gstore = card(page, R.string.gstore_title);
        gstore.addView(hint(R.string.gstore_hint));
        Button gstoreButton = Ui.button(this, R.string.gstore_button);
        Ui.topMargin(gstoreButton, 12);
        gstoreButton.setOnClickListener(v -> GStoreDialog.show(this));
        gstore.addView(gstoreButton);

        LinearLayout support = card(page, R.string.donate_title);
        support.addView(hint(R.string.donate_hint));
        Button donate = Ui.button(this, R.string.donate_button);
        Ui.topMargin(donate, 12);
        donate.setOnClickListener(v -> DonateDialog.show(this));
        support.addView(donate);
    }

    private void setExpanded(TextView head, View body, String title, boolean expanded) {
        head.setText(getString(R.string.link_manual_toggle, expanded ? "▾" : "▸", title));
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
    }

    /** Диагностика в фоне: связь с QNX и состояние приборки, только чтение. */
    private void runDiagnostics() {
        Diagnostics.Input input = new Diagnostics.Input();
        input.version = version();
        input.host = host.getText().toString().trim();
        if (input.host.isEmpty()) {
            input.host = BuildConfig.QNX_HOST;
        }
        String typed = password.getText().toString();
        input.password = typed.isEmpty() ? BuildConfig.QNX_PASSWORD : typed;
        input.active = SceneService.isActive(this);
        input.serviceRunning = SceneService.isRunning();
        input.state = status.getText().toString();
        try {
            input.config = readConfig();
        } catch (IllegalArgumentException e) {
            input.config = null; // Черновик с ошибкой — слои в отчёт не попадут.
        }
        diagButton.setEnabled(false);
        diagStatus.setVisibility(View.VISIBLE);
        diagStatus.setText(R.string.diag_running);
        new Thread(() -> {
            String report = Diagnostics.run(input,
                    text -> runOnUiThread(() -> diagStatus.setText(text)));
            Log.i("AtlasDim", report);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                diagButton.setEnabled(true);
                File file = saveReport(report);
                String summary = Diagnostics.summary(report);
                diagStatus.setText(file == null ? summary
                        : summary + getString(R.string.diag_saved, file.getPath()));
                showReport(report, file);
            });
        }, "AtlasDim-diag").start();
    }

    private void showReport(String report, File file) {
        TextView text = Ui.text(this, report, 12, Ui.TEXT);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextIsSelectable(true);
        text.setGravity(Gravity.START | Gravity.TOP);
        text.setPadding(dp(20), dp(8), dp(20), dp(8));
        HorizontalScrollView wide = new HorizontalScrollView(this);
        wide.addView(text);
        ScrollView body = new ScrollView(this);
        body.addView(wide);
        AlertDialog.Builder builder = new AlertDialog.Builder(this,
                android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.diag_title)
                .setView(body)
                .setNeutralButton(R.string.diag_copy, (d, w) -> {
                    ClipboardManager clipboard = getSystemService(ClipboardManager.class);
                    clipboard.setPrimaryClip(ClipData.newPlainText("Atlas Dim", report));
                    Toast.makeText(this, R.string.diag_copied, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.donate_close, null);
        if (file != null) {
            builder.setPositiveButton(R.string.diag_share, (d, w) -> shareReport(file));
        }
        sized(builder.show());
    }

    /** Файл в Android/data/…/files/diagnostics; хранятся последние {@link #KEEP_REPORTS}. */
    private File saveReport(String report) {
        File dir = ReportProvider.dir(this);
        File file = new File(dir, "atlasdim-diag-" + new SimpleDateFormat("yyyyMMdd-HHmmss",
                Locale.US).format(new Date()) + ".txt");
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("нет папки " + dir);
            }
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(report.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return null;
        }
        File[] old = dir.listFiles((d, name) -> name.startsWith("atlasdim-diag-"));
        if (old != null && old.length > KEEP_REPORTS) {
            Arrays.sort(old); // Время в имени: по алфавиту — от старых к новым.
            for (int i = 0; i < old.length - KEEP_REPORTS; i++) {
                //noinspection ResultOfMethodCallIgnored
                old[i].delete();
            }
        }
        return file;
    }

    private void shareReport(File file) {
        Uri uri = ReportProvider.uri(file);
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, file.getName())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // Через ClipData разрешение на чтение получает и системное окно выбора (предпросмотр).
        send.setClipData(ClipData.newRawUri(file.getName(), uri));
        try {
            startActivity(Intent.createChooser(send, getString(R.string.diag_share)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.diag_share_none, Toast.LENGTH_LONG).show();
        }
    }

    private static String scaleText(int tenths) {
        return (tenths / 10) + "." + (tenths % 10) + "×";
    }

    /** Кнопки применения и строка состояния — закреплены под настройками. */
    private View buildActions() {
        LinearLayout bar = column();
        bar.setPadding(dp(24), dp(10), dp(24), dp(14));
        bar.setBackgroundColor(Ui.BACKGROUND);
        LinearLayout buttons = new LinearLayout(this);
        Button hide = Ui.button(this, R.string.action_hide);
        hide.setTextSize(15);
        hide.setOnClickListener(v -> stopScene());
        Button show = Ui.primaryButton(this, R.string.action_show);
        show.setTextSize(15);
        show.setOnClickListener(v -> showScene());
        buttons.addView(hide, new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams showSize = new LinearLayout.LayoutParams(0, dp(52), 2);
        showSize.leftMargin = dp(10);
        buttons.addView(show, showSize);
        bar.addView(buttons);
        status = Ui.text(this, R.string.status_idle, 13, Ui.TEXT_SECONDARY);
        status.setMinLines(2);
        bar.addView(status, margin(8));
        return bar;
    }

    private LinearLayout page(LinearLayout content) {
        LinearLayout page = column();
        content.addView(page, new LinearLayout.LayoutParams(-1, -2));
        panels.add(page);
        return page;
    }

    // ---- Состояние ----

    private LayerEditor editor(SceneConfig.Kind kind) {
        return kind == SceneConfig.Kind.CLOCK ? clock : kind == SceneConfig.Kind.FUEL ? fuel : background;
    }

    private void showTab(int index) {
        index = Math.max(0, Math.min(tabs.size() - 1, index));
        for (int i = 0; i < tabs.size(); i++) {
            Ui.setSegmentSelected(this, tabs.get(i), i == index);
            panels.get(i).setVisibility(i == index ? View.VISIBLE : View.GONE);
        }
        stage.setVisibility(index == TAB_SYSTEM ? View.GONE : View.VISIBLE);
        if (index < KINDS.length) {
            preview.select(KINDS[index]);
        }
        scroll.scrollTo(0, 0);
        prefs().edit().putInt("tab", index).apply();
    }

    /** На G426_J1 фон закрывает всю приборку: выключен, пока ограничения не сняты. */
    private void lockBackground(boolean unrestricted) {
        boolean locked = SceneConfig.SINGLE_LAYER && !unrestricted;
        if (locked) {
            background.enabled.setChecked(false);
        }
        background.enabled.setEnabled(!locked);
        backgroundLocked.setVisibility(locked ? View.VISIBLE : View.GONE);
    }

    private SceneConfig readConfig() {
        SceneConfig config = new SceneConfig();
        config.unrestricted = unrestricted.isChecked();
        config.host = host.getText().toString().trim();
        String typed = password.getText().toString();
        config.password = typed.isEmpty() ? BuildConfig.QNX_PASSWORD : typed;
        clock.read(config.clock);
        fuel.read(config.fuel);
        background.read(config.background);
        config.fuelLiters = fuelLiters.isChecked();
        config.fuelFree = fuelFree.isChecked();
        config.fuelPlate = fuelPlate.isChecked();
        if (config.fuel.enabled) {
            config.fuelMultiplier = decimal(fuelMultiplier, "Топливо: множитель");
            config.fuelOffset = decimal(fuelOffset, "Топливо: смещение");
        }
        config.seconds = seconds.isChecked();
        config.plate = plate.isChecked();
        config.grid = grid.isChecked();
        config.fill = fill;
        config.alpha = alpha.getProgress();
        if (config.background.enabled && Backgrounds.COLOR.equals(fill)) {
            String hex = color.getText().toString().trim();
            if (!hex.matches("#[0-9a-fA-F]{6}")) {
                throw new IllegalArgumentException("Цвет фона: #RRGGBB, например #10243A");
            }
            config.color = Color.parseColor(hex);
        }
        config.validate();
        return config;
    }

    private void refreshPreview() {
        if (!ready || syncing) {
            return;
        }
        alphaValue.setText(Math.round(alpha.getProgress() * 100 / 255f) + "%");
        colorRow.setVisibility(Backgrounds.COLOR.equals(fill) ? View.VISIBLE : View.GONE);
        boolean valid = true;
        try {
            previewConfig = readConfig();
            saveConfig(); // Корректный черновик (позиция после перетаскивания) не теряется.
            previewStatus.setText(getString(R.string.preview_hint, summary(previewConfig.clock),
                    summary(previewConfig.fuel), summary(previewConfig.background)));
            previewStatus.setTextColor(Ui.TEXT_SECONDARY);
        } catch (IllegalArgumentException e) {
            valid = false;
            previewStatus.setText(getString(R.string.preview_invalid, e.getMessage()));
            previewStatus.setTextColor(Ui.ERROR);
        }
        preview.update(previewConfig, valid);
        if (SceneService.isActive(this) && !dirty) {
            dirty = true;
            setPill(getString(R.string.pill_dirty), PENDING);
        }
    }

    private String summary(SceneConfig.Layer layer) {
        return layer.enabled ? getString(R.string.layer_on, layer.label(), layer.display, layer.z)
                : getString(R.string.layer_off, layer.label());
    }

    private void showScene() {
        final SceneConfig config;
        try {
            config = readConfig();
            if (config.host.isEmpty()) {
                throw new IllegalArgumentException(getString(R.string.error_host));
            }
            // /tmp на QNX чистится при загрузке — без telnet демонов не будет.
            if (config.password.isEmpty()) {
                throw new IllegalArgumentException(getString(R.string.error_password));
            }
        } catch (IllegalArgumentException e) {
            status.setText(getString(R.string.status_not_applied, e.getMessage()));
            status.setTextColor(Ui.ERROR);
            return;
        }
        saveConfig();
        if (!config.anyEnabled()) {
            stopScene();
            return;
        }
        dirty = false;
        setPill(getString(R.string.pill_starting), Ui.ACCENT);
        SceneService.apply(this, config);
    }

    private void stopScene() {
        dirty = false;
        SceneService.stop(this);
        status.setText(SceneService.isRunning() ? R.string.status_stopping : R.string.pill_idle);
        status.setTextColor(Ui.TEXT_SECONDARY);
        setPill(getString(R.string.pill_idle), Ui.TEXT_SECONDARY);
    }

    /** Состояние сервиса — на главном потоке. */
    private void onSceneState(String state) {
        status.setText(state);
        boolean failed = state.startsWith("Ошибка") || state.contains("потеряна");
        status.setTextColor(failed ? Ui.ERROR : Ui.TEXT_SECONDARY);
        if (dirty) {
            return;
        }
        if (state.startsWith("Отправлено") || state.startsWith("Рисунок обновлён")) {
            setPill(getString(R.string.pill_shown), Ui.OK);
        } else if (failed) {
            setPill(getString(state.contains("повтор") ? R.string.pill_error_retry
                    : R.string.pill_error), Ui.ERROR);
        } else if (state.startsWith("Остановлено") || state.startsWith("Не подключено")) {
            setPill(getString(R.string.pill_idle), Ui.TEXT_SECONDARY);
        } else {
            setPill(getString(R.string.pill_starting), Ui.ACCENT);
        }
    }

    private void setPill(String text, int color) {
        pill.setText(text);
        pill.setTextColor(color);
        GradientDrawable d = Ui.rounded((color & 0x00ffffff) | 0x22000000, dp(16));
        d.setStroke(dp(1), (color & 0x00ffffff) | 0x66000000);
        pill.setBackground(d);
    }

    private void saveConfig() {
        SharedPreferences.Editor edit = prefs().edit();
        edit.putString("host", host.getText().toString().trim());
        edit.putString("password", password.getText().toString());
        edit.putBoolean(SceneService.KEY_BOOT, boot.isChecked());
        edit.putBoolean(KEY_UNRESTRICTED, unrestricted.isChecked());
        clock.save(edit);
        fuel.save(edit);
        background.save(edit);
        edit.putBoolean("fuel.liters", fuelLiters.isChecked())
                .putBoolean("fuel.free", fuelFree.isChecked())
                .putBoolean("fuel.plate", fuelPlate.isChecked())
                .putString("fuel.multiplier", fuelMultiplier.getText().toString())
                .putString("fuel.offset", fuelOffset.getText().toString());
        edit.putBoolean("seconds", seconds.isChecked()).putBoolean("plate", plate.isChecked())
                .putBoolean("grid", grid.isChecked()).putString("color", color.getText().toString())
                .putString("alpha", String.valueOf(alpha.getProgress())).putString("fill", fill).apply();
    }

    // ---- Фоновые картинки ----

    private void rebuildFills() {
        fills.removeAllViews();
        for (int i = 0; i < Backgrounds.PRESETS.length; i++) {
            String id = Backgrounds.PRESETS[i];
            Bitmap image = Backgrounds.isImage(id) ? Backgrounds.thumbnail(id, dp(150)) : preset(id);
            fills.addView(fillTile(id, Backgrounds.LABELS[i], image, null));
        }
        for (File file : Backgrounds.userImages(this)) {
            String id = Backgrounds.FILE + file.getAbsolutePath();
            fills.addView(fillTile(id, null, Backgrounds.thumbnail(id, dp(150)), file));
        }
        TextView add = Ui.text(this, R.string.background_add, 14, Ui.TEXT);
        add.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        add.setGravity(Gravity.CENTER);
        GradientDrawable d = Ui.rounded(Ui.SURFACE_RAISED, dp(8));
        d.setStroke(dp(1), 0x55ffffff, dp(6), dp(4));
        add.setBackground(d);
        add.setContentDescription(getString(R.string.background_add_description));
        add.setOnClickListener(v -> openGallery());
        fills.addView(add, new LinearLayout.LayoutParams(dp(120), dp(64)));
        if (fill.startsWith(Backgrounds.FILE) && !new File(fill.substring(Backgrounds.FILE.length())).exists()) {
            fill = Backgrounds.COLOR;
        }
        markFill();
    }

    private Bitmap preset(String id) {
        Bitmap bitmap = Bitmap.createBitmap(dp(120), dp(64), Bitmap.Config.ARGB_8888);
        int color = Backgrounds.COLOR.equals(id) ? 0xff10243a : 0;
        try {
            color = Color.parseColor(this.color.getText().toString().trim());
        } catch (IllegalArgumentException ignored) {
        }
        Backgrounds.draw(new Canvas(bitmap), id, color, bitmap.getWidth(), bitmap.getHeight());
        return bitmap;
    }

    private View fillTile(String id, String title, Bitmap image, File file) {
        FrameLayout tile = new FrameLayout(this);
        tile.setTag(id);
        tile.setPadding(dp(3), dp(3), dp(3), dp(3));
        ImageView picture = new ImageView(this);
        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        picture.setImageBitmap(image);
        picture.setClipToOutline(true);
        picture.setBackground(Ui.rounded(Ui.SURFACE_RAISED, dp(6)));
        tile.addView(picture, new FrameLayout.LayoutParams(-1, -1));
        if (title != null) {
            TextView caption = Ui.text(this, title, 12, Ui.TEXT);
            caption.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            caption.setShadowLayer(4, 0, 1, Color.BLACK);
            caption.setPadding(dp(8), 0, 0, dp(5));
            tile.addView(caption, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START));
        }
        tile.setContentDescription(title != null ? getString(R.string.background_preset, title)
                : getString(R.string.background_own));
        tile.setOnClickListener(v -> {
            fill = id;
            markFill();
            refreshPreview();
        });
        if (file != null) {
            tile.setOnLongClickListener(v -> {
                sized(new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                        .setMessage(R.string.background_delete)
                        .setPositiveButton(R.string.background_delete_confirm, (d, w) -> {
                            if (!file.delete()) {
                                status.setText(getString(R.string.background_delete_failed,
                                        file.getName()));
                            }
                            rebuildFills();
                            refreshPreview();
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show());
                return true;
            });
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(120), dp(64));
        lp.rightMargin = dp(8);
        tile.setLayoutParams(lp);
        return tile;
    }

    private void markFill() {
        for (int i = 0; i < fills.getChildCount(); i++) {
            View tile = fills.getChildAt(i);
            boolean on = fill.equals(tile.getTag());
            if (tile instanceof FrameLayout) {
                GradientDrawable d = Ui.rounded(Color.TRANSPARENT, dp(9));
                d.setStroke(dp(on ? 3 : 1), on ? Ui.ACCENT : 0x22ffffff);
                tile.setForeground(d);
            }
        }
    }

    private void openGallery() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE}, READ_IMAGES);
            return;
        }
        ImageGallery.show(this, this::openSystemPicker, this::importImage);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        if (code != READ_IMAGES) {
            return;
        }
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            ImageGallery.show(this, this::openSystemPicker, this::importImage);
        } else {
            openSystemPicker();
        }
    }

    private void openSystemPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
                .addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent,
                    getString(R.string.background_chooser)), PICK_IMAGE);
        } catch (ActivityNotFoundException e) {
            status.setText(getString(R.string.background_no_picker,
                    getExternalFilesDir("backgrounds")));
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == PICK_IMAGE && result == RESULT_OK && data != null && data.getData() != null) {
            importImage(data.getData());
        }
    }

    private void importImage(Uri uri) {
        status.setText(R.string.background_loading);
        new Thread(() -> {
            try {
                File file = Backgrounds.importImage(this, uri);
                runOnUiThread(() -> {
                    fill = Backgrounds.FILE + file.getAbsolutePath();
                    rebuildFills();
                    refreshPreview();
                    status.setText(R.string.background_added);
                });
            } catch (Exception e) {
                Log.w("AtlasDim", "import " + uri, e);
                runOnUiThread(() -> status.setText(getString(R.string.background_failed,
                        e.getMessage())));
            }
        }, "atlasdim-import").start();
    }

    // ---- Редактор слоя ----

    private final class LayerEditor {
        final String key;
        final Switch enabled;
        final TextView[] displays = new TextView[2];
        final TextView zValue;
        final TextView zHint;
        final TextView placementHead;
        final LinearLayout placement;
        final EditText x;
        final EditText y;
        final EditText w;
        final EditText h;
        final SceneConfig.Layer defaults;
        int display;
        int z;

        LayerEditor(LinearLayout parent, SceneConfig.Layer defaults) {
            this.defaults = defaults;
            key = defaults.id() + ".";
            SharedPreferences prefs = prefs();
            LinearLayout card = card(parent, 0);
            enabled = toggle(card, defaults.label(), prefs.getBoolean(key + "enabled", defaults.enabled));
            enabled.setTextSize(20);
            enabled.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            enabled.setPadding(0, 0, 0, 0);
            enabled.setContentDescription(defaults.label() + ": включить");
            TextView port = hint(getString(R.string.layer_port, defaults.port()));
            port.setCompoundDrawablePadding(dp(8));
            port.setCompoundDrawablesRelative(dot(ScenePreview.color(defaults.kind)), null, null, null);
            card.addView(port);
            SceneConfig.Layer saved = new SceneConfig.Layer(defaults.kind);
            saved.display = parse(prefs.getString(key + "display", null), defaults.display);
            saved.z = parse(prefs.getString(key + "z", null), defaults.z);
            if (saved.display < 1 || saved.display > 2 || !prefs.getBoolean(KEY_UNRESTRICTED, false)) {
                SceneConfig.allowPlacement(saved); // Черновик с dpy-3 или прежней раскладкой модели.
            }
            display = saved.display;
            z = saved.z;

            // Экран и z под катом: значения по умолчанию проверены на машине.
            placementHead = label(R.string.layer_display);
            placementHead.setClickable(true);
            placementHead.setPadding(0, dp(8), 0, dp(8));
            card.addView(placementHead);
            placement = column();
            card.addView(placement);
            placementHead.setOnClickListener(v -> paintPlacement(placement.getVisibility() != View.VISIBLE));
            LinearLayout segments = new LinearLayout(MainActivity.this);
            int[] names = {R.string.layer_display_1, R.string.layer_display_2};
            for (int i = 0; i < names.length; i++) {
                int value = i + 1;
                TextView segment = Ui.segment(MainActivity.this, names[i]);
                segment.setContentDescription(defaults.label() + ": дисплей " + value);
                segment.setOnClickListener(v -> {
                    display = value;
                    paintSegments();
                    refreshPreview();
                });
                Ui.addSegment(segments, segment);
                displays[i] = segment;
            }
            placement.addView(segments, margin(6));

            LinearLayout zRow = new LinearLayout(MainActivity.this);
            zRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView zTitle = label(R.string.layer_z);
            zTitle.setLayoutParams(new LinearLayout.LayoutParams(dp(90), -2));
            Ui.topMargin(zTitle, 0);
            zRow.addView(zTitle);
            zValue = Ui.heading(MainActivity.this, "", 18);
            zValue.setGravity(Gravity.CENTER);
            zRow.addView(stepper("−", -1));
            zRow.addView(zValue, new LinearLayout.LayoutParams(dp(64), -2));
            zRow.addView(stepper("+", 1));
            zHint = Ui.text(MainActivity.this, "", 13, Ui.TEXT_SECONDARY);
            zHint.setPadding(dp(12), 0, 0, 0);
            zRow.addView(zHint, new LinearLayout.LayoutParams(0, -2, 1));
            placement.addView(zRow, margin(14));
            zValue.setText(String.valueOf(z));
            paintSegments();
            paintPlacement(display != defaults.display || z != defaults.z);

            card.addView(label(R.string.layer_geometry));
            LinearLayout row1 = new LinearLayout(MainActivity.this);
            x = cell(row1, "x", "X", "x", defaults.x);
            y = cell(row1, "y", "Y", "y", defaults.y);
            w = cell(row1, "Ширина", getString(R.string.layer_width), "w", defaults.w);
            h = cell(row1, "Высота", getString(R.string.layer_height), "h", defaults.h);
            card.addView(row1, margin(6));

            LinearLayout quick = new LinearLayout(MainActivity.this);
            if (defaults.background) {
                Ui.addSegment(quick, chip(R.string.place_full,
                        () -> place(0, 0, SceneConfig.WIDTH, SceneConfig.HEIGHT)));
            } else {
                Ui.addSegment(quick, chip(R.string.place_bottom_left,
                        () -> place(40, SceneConfig.HEIGHT - num(h) - 20, -1, -1)));
                Ui.addSegment(quick, chip(R.string.place_center, () -> place((SceneConfig.WIDTH - num(w)) / 2,
                        (SceneConfig.HEIGHT - num(h)) / 2, -1, -1)));
                Ui.addSegment(quick, chip(R.string.place_top_right,
                        () -> place(SceneConfig.WIDTH - num(w) - 40, 20, -1, -1)));
            }
            Ui.addSegment(quick, chip(R.string.place_reset, this::reset));
            card.addView(quick, margin(10));
        }

        /** Экран, z и геометрия — как при установке; включённость не меняется. */
        private void reset() {
            SceneConfig.Layer initial = new SceneConfig.Layer(defaults.kind);
            display = initial.display;
            z = initial.z;
            paintSegments();
            zValue.setText(String.valueOf(z));
            setGeometry(initial);
        }


        private int num(EditText field) {
            return parse(field.getText().toString(), 0);
        }

        private void place(int px, int py, int pw, int ph) {
            syncing = true;
            if (pw > 0) {
                w.setText(String.valueOf(pw));
                h.setText(String.valueOf(ph));
            }
            x.setText(String.valueOf(Math.max(0, px)));
            y.setText(String.valueOf(Math.max(0, py)));
            syncing = false;
            refreshPreview();
        }

        void setGeometry(SceneConfig.Layer layer) {
            syncing = true;
            x.setText(String.valueOf(layer.x));
            y.setText(String.valueOf(layer.y));
            w.setText(String.valueOf(layer.w));
            h.setText(String.valueOf(layer.h));
            syncing = false;
            refreshPreview();
        }

        /** Заголовок ката показывает текущие экран и z и в свёрнутом виде. */
        private void paintPlacement(boolean expanded) {
            setExpanded(placementHead, placement, getString(R.string.layer_placement, display, z), expanded);
        }

        /** Сегменты экрана и подсказка о штатных z именно на выбранном экране. */
        private void paintSegments() {
            for (int i = 0; i < displays.length; i++) {
                Ui.setSegmentSelected(MainActivity.this, displays[i], display == i + 1);
            }
            paintPlacement(placement.getVisibility() == View.VISIBLE);
            boolean single = SceneConfig.SINGLE_LAYER;
            zHint.setText(display == 2 ? single ? R.string.layer_z_hint_dpy2_single
                            : R.string.layer_z_hint_dpy2
                    : defaults.background ? R.string.layer_z_hint_dpy1_background
                    : single ? R.string.layer_z_hint_dpy1_single
                    : R.string.layer_z_hint_dpy1);
        }

        private Button stepper(String text, int delta) {
            Button b = Ui.button(MainActivity.this, text);
            b.setTextSize(20);
            b.setPadding(0, 0, 0, 0);
            b.setContentDescription(defaults.label() + ": z " + text);
            b.setOnClickListener(v -> {
                z = Math.max(-100, Math.min(100, z + delta));
                zValue.setText(String.valueOf(z));
                paintPlacement(true);
                refreshPreview();
            });
            b.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(44)));
            return b;
        }

        private EditText cell(LinearLayout row, String description, String title, String name, int value) {
            LinearLayout box = column();
            box.addView(Ui.text(MainActivity.this, title, 12, Ui.TEXT_SECONDARY));
            EditText edit = new EditText(MainActivity.this);
            styleField(edit, description, prefs().getString(key + name, String.valueOf(value)), NUMBER);
            box.addView(edit, margin(4));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            if (row.getChildCount() > 0) {
                lp.leftMargin = dp(8);
            }
            row.addView(box, lp);
            return edit;
        }

        void read(SceneConfig.Layer layer) {
            layer.enabled = enabled.isChecked();
            if (!layer.enabled) {
                return;
            }
            layer.display = display;
            layer.z = z;
            layer.x = number(x, layer.label() + ": x");
            layer.y = number(y, layer.label() + ": y");
            layer.w = number(w, layer.label() + ": ширина");
            layer.h = number(h, layer.label() + ": высота");
        }

        void save(SharedPreferences.Editor edit) {
            edit.putBoolean(key + "enabled", enabled.isChecked());
            edit.putString(key + "display", String.valueOf(display));
            edit.putString(key + "z", String.valueOf(z));
            edit.putString(key + "x", x.getText().toString());
            edit.putString(key + "y", y.getText().toString());
            edit.putString(key + "w", w.getText().toString());
            edit.putString(key + "h", h.getText().toString());
        }
    }

    // ---- Общие элементы ----

    private EditText formulaCell(LinearLayout row, int title, String value) {
        LinearLayout box = column();
        box.addView(Ui.text(this, title, 12, Ui.TEXT_SECONDARY));
        EditText edit = new EditText(this);
        styleField(edit, "Топливо: " + getString(title).toLowerCase(Locale.ROOT), value,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        | InputType.TYPE_NUMBER_FLAG_SIGNED);
        box.addView(edit, margin(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        if (row.getChildCount() > 0) {
            lp.leftMargin = dp(8);
        }
        row.addView(box, lp);
        return edit;
    }

    private float decimal(EditText field, String label) {
        try {
            float value = Float.parseFloat(field.getText().toString().trim().replace(',', '.'));
            if (Float.isFinite(value)) {
                return value;
            }
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException(label + ": введите число, например 0.5");
    }

    private int number(EditText field, String label) {
        try {
            return Integer.parseInt(field.getText().toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + ": введите целое число");
        }
    }

    private static int parse(String text, int fallback) {
        try {
            return text == null ? fallback : Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private SharedPreferences prefs() {
        return SceneService.prefs(this);
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    /** Карточка Atlas с заголовком; {@code title} 0 — без заголовка. */
    private LinearLayout card(LinearLayout parent, int title) {
        LinearLayout card = Ui.card(this);
        if (title != 0) {
            card.addView(Ui.heading(this, title, 20));
        }
        parent.addView(card);
        return card;
    }

    /** Скругляет углы предпросмотра. */
    private View rounded(View view) {
        view.setBackground(Ui.rounded(Color.BLACK, dp(8)));
        view.setClipToOutline(true);
        return view;
    }

    private View legend(SceneConfig.Kind kind) {
        TextView view = Ui.text(this, new SceneConfig.Layer(kind).label(), 13, Ui.TEXT_SECONDARY);
        view.setCompoundDrawablePadding(dp(6));
        view.setCompoundDrawablesRelative(dot(ScenePreview.color(kind)), null, null, null);
        view.setPadding(dp(14), 0, 0, 0);
        return view;
    }

    /** Цветная метка слоя — тот же цвет, что у его рамки в предпросмотре. */
    private GradientDrawable dot(int color) {
        GradientDrawable d = Ui.rounded(color, dp(3));
        d.setBounds(0, 0, dp(10), dp(10));
        return d;
    }

    private TextView label(int text) {
        TextView view = Ui.text(this, text, 14, Ui.TEXT_SECONDARY);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        Ui.topMargin(view, 14);
        return view;
    }

    private TextView hint(int text) {
        return hint(getString(text));
    }

    private TextView hint(String text) {
        TextView view = Ui.text(this, text, 13, Ui.TEXT_SECONDARY);
        view.setLineSpacing(0, 1.15f);
        Ui.topMargin(view, 6);
        return view;
    }

    private void styleField(EditText edit, String description, String value, int type) {
        edit.setInputType(type);
        edit.setTypeface(Typeface.DEFAULT); // Пароль иначе получает моноширинный шрифт.
        edit.setText(value);
        edit.setTextColor(Ui.TEXT);
        edit.setHintTextColor(Ui.TEXT_SECONDARY);
        edit.setTextSize(16);
        edit.setSingleLine(true);
        edit.setBackground(Ui.rounded(Ui.SURFACE_RAISED, dp(8)));
        edit.setPadding(dp(12), dp(10), dp(12), dp(10));
        edit.setContentDescription(description);
        edit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshPreview();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private EditText field(LinearLayout parent, int title, String value, int type) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(Ui.text(this, title, 14, Ui.TEXT), new LinearLayout.LayoutParams(dp(130), -2));
        EditText edit = new EditText(this);
        styleField(edit, getString(title), value, type);
        row.addView(edit, new LinearLayout.LayoutParams(0, -2, 1));
        parent.addView(row, margin(8));
        return edit;
    }

    private Switch toggle(LinearLayout parent, int text, boolean checked) {
        return toggle(parent, getString(text), checked);
    }

    private Switch toggle(LinearLayout parent, String text, boolean checked) {
        Switch view = new Switch(this);
        view.setText(text);
        view.setTextColor(Ui.TEXT);
        view.setTextSize(15);
        view.setChecked(checked);
        view.setPadding(0, dp(10), 0, dp(4));
        view.setOnCheckedChangeListener((v, value) -> refreshPreview());
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    /** Быстрое действие; в строку добавляется {@link Ui#addSegment} равной долей. */
    private Button chip(int text, Runnable action) {
        Button view = Ui.button(this, text);
        view.setTextSize(13);
        view.setPadding(dp(6), dp(8), dp(6), dp(8));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private LinearLayout.LayoutParams margin(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top);
        return lp;
    }

    /** Масштаб Activity расширяет диалоги за край экрана. */
    private void sized(AlertDialog dialog) {
        int screen = getResources().getDisplayMetrics().widthPixels;
        dialog.getWindow().setLayout(Math.min(screen - dp(48), dp(640)),
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private int dp(int value) {
        return Ui.dp(this, value);
    }
}
