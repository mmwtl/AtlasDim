package com.mmwtl.atlasdim;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Без внешних тестовых зависимостей: реальные сокеты и Android Canvas на эмуляторе. */
public final class SceneTestRunner extends Instrumentation {
    private final StringBuilder report = new StringBuilder();

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            validationAndOwnership();
            fuelData();
            renderAndCodec();
            previewOrder();
            twoLayersAndSnapshot();
            delayedSecondWindow();
            refusedSecondWindow();
            backgroundOnly();
            liveUpdate();
            repeatSessions();
            fuelWindow();
            activityLifecycle();
            bootAndRetry();
            result.putString("stream", "\n" + report + "ALL 13 CHECKS PASSED\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable e) {
            result.putString("stream", "\n" + report + android.util.Log.getStackTraceString(e));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void validationAndOwnership() throws Exception {
        SceneConfig config = new SceneConfig();
        config.validate();
        config.clock.x = Integer.MAX_VALUE;
        invalid(config, "overflow coordinates");
        config = new SceneConfig();
        config.clock.z = 10;
        invalid(config, "clock over the lamps on dpy-1");
        config = new SceneConfig();
        config.fuel.enabled = true;
        config.fuel.z = 10;
        invalid(config, "fuel over the lamps on dpy-1");
        config = new SceneConfig();
        config.background.enabled = true;
        config.background.display = 1;
        config.background.z = 3;
        invalid(config, "background over the cluster on dpy-1");
        config.background.z = 2;
        config.validate();
        config = new SceneConfig();
        config.background.enabled = true;
        config.clock.display = 2;
        config.clock.z = config.background.z;
        invalid(config, "same z on one display");
        config = new SceneConfig();
        config.fuel.enabled = true;
        config.fuel.z = config.clock.z;
        invalid(config, "clock and fuel with the same z");
        config.fuel.display = 2;
        config.validate();
        config = new SceneConfig();
        config.fuel.enabled = true;
        config.background.enabled = true;
        int[][] zs = {{-98, -99, -100}, {9, 8, 2}, {2, 1, 0}, {100, 50, 10}};
        for (int display = 1; display <= 3; display++) {
            config.clock.display = config.fuel.display = config.background.display = display;
            config.background.w = display == 3 ? 1440 : SceneConfig.WIDTH;
            config.fuel.x = display == 3 ? 1140 : SceneConfig.WIDTH - 300;
            for (int[] z : zs) {
                config.clock.z = z[0];
                config.fuel.z = z[1];
                config.background.z = z[2];
                if (display == 1 && z[0] >= 10) {
                    invalid(config, "upper layers on dpy-1");
                    continue;
                }
                config.validate();
                check(DimDaemon.command(config.background).endsWith(" --z " + z[2])
                        && DimDaemon.command(config.fuel).equals("/tmp/ghudbridgelited-dim-fuel"
                                + display + " --port 50212 --z " + z[1]),
                        "chosen z reaches daemon command");
            }
        }
        config.clock.z = 101;
        invalid(config, "daemon upper z limit");
        config.clock.z = -101;
        invalid(config, "daemon lower z limit");
        config = new SceneConfig();
        config.fuel.enabled = true;
        config.fuelLiters = config.fuelFree = false;
        invalid(config, "fuel without parts");
        check(DimDaemon.runningNames("123 /tmp/ghudbridgelited-dim-clock1 --port 50210\n"
                + "124 /tmp/ghudbridgelited-dim-background2 --port 50211\n"
                + "125 /tmp/ghudbridgelited-dim1\n126 /apps/ghudbridgelited-dim1\n"
                + "127 /tmp/ghudbridgelited\n128 /tmp/ghudbridgelited-dim-clock1\n"
                + "129 /tmp/ghudbridgelited-dim-background2-other\n"
                + "130 /tmp/ghudbridgelited-dim-fuel1 --port 50212\n").equals(Arrays.asList(
                        "ghudbridgelited-dim-clock1", "ghudbridgelited-dim-background2",
                        "ghudbridgelited-dim1", "ghudbridgelited-dim-fuel1")), "process ownership");
        List<String[]> processes = DimDaemon.processes(
                "  123 /tmp/ghudbridgelited-dim-clock1  --port 50210 --z 9 \n");
        check(processes.size() == 1 && processes.get(0)[0].equals("123")
                && processes.get(0)[1].equals(DimDaemon.command(new SceneConfig().clock)),
                "running daemon matches its command line");
        byte[] bin;
        try (java.io.InputStream in = getTargetContext().getAssets().open(DimDaemon.NAME)) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = in.read(chunk)) >= 0) {
                bytes.write(chunk, 0, n);
            }
            bin = bytes.toByteArray();
        }
        byte[] patched = DimDaemon.withDisplay(bin.clone(), 2);
        int differences = 0;
        for (int i = 0; i < bin.length; i++) {
            if (bin[i] != patched[i]) {
                differences++;
                check(i >= 0x4964 && i < 0x4968 || i >= 0x5090 && i < 0x5094,
                        "patch escaped display instructions");
            }
        }
        check(differences > 0, "display patch");
        pass("layer limits restored, display patch and only owned process names");
    }

    private void fuelData() throws Exception {
        Fuel fuel = Fuel.of(50d, Fuel.MULTIPLIER, Fuel.OFFSET);
        check(fuel.liters == 29 && fuel.freeLiters == 25 && fuel.capacityLiters == 54, "default formula");
        check(Fuel.of(0d, Fuel.MULTIPLIER, Fuel.OFFSET).litersText().equals("<4")
                && Fuel.of(0d, Fuel.MULTIPLIER, Fuel.OFFSET).freeText().equals(">50"), "floor bounds");
        check(Fuel.of(null, Fuel.MULTIPLIER, Fuel.OFFSET) == null, "no reading");
        check(Fuel.of(100d, 0.6f, 0).liters == 60, "custom formula");
        SceneConfig config = new SceneConfig();
        check(SceneRenderer.fuel(config, 50d).equals("29 л · своб. 25 л"), "fuel text");
        check(SceneRenderer.fuel(config, null).equals("— л · своб. — л"), "fuel text without data");
        config.fuelFree = false;
        check(SceneRenderer.fuel(config, 50d).equals("29 л"), "liters only");
        // Ответ моста: неявный broadcast со строковыми extras, другой датчик игнорируется.
        android.content.Intent other = new android.content.Intent(FuelSensor.SENSOR_FLOAT_CHANGED)
                .putExtra("id", "123").putExtra("value", "7");
        android.content.Intent result = new android.content.Intent(FuelSensor.SENSOR_FLOAT_RESULT)
                .putExtra("id", String.valueOf(FuelSensor.SENSOR_FUEL_PERCENTAGE))
                .putExtra("value", "75.0");
        FuelSensor.accept(result);
        FuelSensor.accept(other);
        check(Double.valueOf(75).equals(FuelSensor.value()), "sensor value from bridge");
        FuelSensor sensor = new FuelSensor(getTargetContext());
        runOnMainSync(sensor::start);
        try {
            // Если на устройстве стоит ГИБ, он ответит на запрос при start(); ждём этот ответ.
            Thread.sleep(1500);
            getTargetContext().sendBroadcast(new android.content.Intent(FuelSensor.SENSOR_FLOAT_CHANGED)
                    .putExtra("id", FuelSensor.SENSOR_FUEL_PERCENTAGE).putExtra("value", 40f));
            await(() -> Double.valueOf(40).equals(FuelSensor.value()), 3000, "registered receiver");
        } finally {
            runOnMainSync(sensor::stop);
        }
        config = new SceneConfig();
        config.fuel.enabled = true;
        config.fuel.z = 4;
        config.fuelOffset = 2.5f;
        config.fill = Backgrounds.ASSET + "c01-night-highway.webp";
        SceneConfig copy = SceneConfig.fromJson(config.toJson());
        check(copy.toJson().equals(config.toJson()) && copy.fuel.enabled && copy.fuel.z == 4
                && copy.fuelOffset == 2.5f && copy.password.equals(config.password), "JSON round trip");
        pass("fuel formula, text, GInputBridge sensor and saved scene JSON");
    }

    private void renderAndCodec() {
        SceneConfig config = new SceneConfig();
        config.grid = false;
        config.fill = Backgrounds.COLOR;
        Bitmap bitmap = Bitmap.createBitmap(1920, 532, Bitmap.Config.ARGB_8888);
        SceneRenderer.draw(new Canvas(bitmap), config, config.background, "12:34");
        check(bitmap.getPixel(1000, 300) == config.color, "background color");
        byte[] pixels = new byte[1920 * 532 * 4];
        bitmap.copyPixelsToBuffer(ByteBuffer.wrap(pixels));
        byte[] frame = Gcp.frame(pixels, 1920, 532);
        check(Arrays.equals(pixels, decodeFrame(frame)), "full-size RLE round trip");
        check(frame.length < 50000, "solid background compression");
        bitmap.recycle();
        bitmap = Bitmap.createBitmap(300, 100, Bitmap.Config.ARGB_8888);
        config.plate = false;
        SceneRenderer.draw(new Canvas(bitmap), config, config.clock, "12:34");
        check(bitmap.getPixel(0, 0) == 0, "clock transparent corner");
        bitmap.recycle();
        config.alpha = 128;
        bitmap = Bitmap.createBitmap(1920, 532, Bitmap.Config.ARGB_8888);
        SceneRenderer.draw(new Canvas(bitmap), config, config.background, "12:34");
        check((bitmap.getPixel(1000, 300) >>> 24) == 128, "background opacity");
        bitmap.recycle();
        // Картинка-фон: center-crop на всё окно, кадр без потерь и в пределах max_payload демона.
        java.io.File file = new java.io.File(getTargetContext().getCacheDir(), "test-bg.png");
        Bitmap source = Bitmap.createBitmap(400, 100, Bitmap.Config.ARGB_8888);
        new Canvas(source).drawColor(0xffff0000);
        new Canvas(source).drawRect(200, 0, 400, 100, new android.graphics.Paint());
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
            source.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        source.recycle();
        config.alpha = 255;
        config.fill = Backgrounds.FILE + file.getAbsolutePath();
        bitmap = Bitmap.createBitmap(1920, 532, Bitmap.Config.ARGB_8888);
        SceneRenderer.draw(new Canvas(bitmap), config, config.background, "12:34");
        check(bitmap.getPixel(300, 266) == 0xffff0000 && bitmap.getPixel(1700, 266) == 0xff000000,
                "image background cover");
        bitmap.copyPixelsToBuffer(ByteBuffer.wrap(pixels));
        frame = Gcp.frame(pixels, 1920, 532);
        check(Arrays.equals(pixels, decodeFrame(frame)), "image frame round trip");
        check(frame.length <= 1920 * 532 * 4 + (1920 * 532 >> 7) + 0x58, "daemon max_payload");
        bitmap.recycle();
        check(file.delete(), "test image cleanup");
        pass("Canvas color, transparency, image fill and full 1920x532 frame codec");
    }

    private void twoLayersAndSnapshot() throws Exception {
        try (FakeDaemon bg = new FakeDaemon(50211, 0, false);
                FakeDaemon clock = new FakeDaemon(50210, 0, false)) {
            SceneConfig config = local();
            config.seconds = true;
            config.grid = false;
            SceneSession session = new SceneSession(getTargetContext(), config, s -> { });
            int x = config.clock.x;
            config.clock.x = 900; // Сессия обязана сохранить исходную геометрию.
            session.start();
            try {
                await(() -> bg.frames >= 1 && clock.frames >= 3, 7000, "both layers sending");
                check(bg.frames == 1 && bg.pings > 0, "static background sent only once");
                check(intAt(clock.hello, 4) == x, "immutable session coordinates");
                check(intAt(bg.hello, 12) == 1920 && intAt(bg.hello, 16) == 532,
                        "full-size background HELLO");
                check(decodeFrame(bg.frame).length == 1920 * 532 * 4, "received background");
                clock.disconnect();
                check(bg.closed.await(3, TimeUnit.SECONDS), "one failure closes other layer");
            } finally {
                session.stop();
            }
            bg.assertHealthy();
            clock.assertHealthy();
        }
        pass("two ports, frozen config, static background and disconnect rollback");
    }

    private void previewOrder() {
        runOnMainSync(() -> {
            SceneConfig config = new SceneConfig();
            config.grid = false;
            config.fill = Backgrounds.COLOR;
            config.background.enabled = true;
            config.background.z = 1;
            config.clock.display = 2;
            config.clock.z = 0;
            int[] hidden = previewPixels(config);
            config.clock.enabled = false;
            check(Arrays.equals(hidden, previewPixels(config)), "higher z hides clock on same display");
            config.clock.enabled = true;
            config.clock.z = 2;
            check(!Arrays.equals(hidden, previewPixels(config)), "higher clock z becomes visible");
            config.background.display = 1;
            config.background.z = -100;
            config.clock.z = 100;
            check(Arrays.equals(hidden, previewPixels(config)), "display order takes priority over z");
            config.background.display = 3;
            config.clock.display = 3;
            int[] centralOnly = previewPixels(config);
            config.background.enabled = false;
            config.clock.enabled = false;
            check(Arrays.equals(centralOnly, previewPixels(config)), "central display excluded from DIM");
        });
        pass("preview respects z, physical display order and central-screen isolation");
    }

    private int[] previewPixels(SceneConfig config) {
        ScenePreview preview = new ScenePreview(getTargetContext(), false);
        preview.update(config, true);
        preview.layout(0, 0, 1920, 532);
        Bitmap bitmap = Bitmap.createBitmap(1920, 532, Bitmap.Config.ARGB_8888);
        preview.draw(new Canvas(bitmap));
        int[] pixels = new int[1920 * 532];
        bitmap.getPixels(pixels, 0, 1920, 0, 0, 1920, 532);
        bitmap.recycle();
        return pixels;
    }

    private void delayedSecondWindow() throws Exception {
        try (FakeDaemon bg = new FakeDaemon(50211, 0, false);
                FakeDaemon clock = new FakeDaemon(50210, 2800, false)) {
            SceneSession session = new SceneSession(getTargetContext(), local(), s -> { });
            session.start();
            try {
                await(() -> clock.frames > 0, 5000, "delayed second HELLO_ACK");
                check(bg.pings >= 4, "heartbeat while second window opens");
                check(!bg.watchdogExpired, "background watchdog");
            } finally {
                session.stop();
            }
        }
        pass("background stays alive during delayed second handshake");
    }

    private void refusedSecondWindow() throws Exception {
        try (FakeDaemon bg = new FakeDaemon(50211, 0, false);
                FakeDaemon clock = new FakeDaemon(50210, 0, true)) {
            SceneSession session = new SceneSession(getTargetContext(), local(), s -> { });
            session.start();
            try {
                check(bg.closed.await(5, TimeUnit.SECONDS), "refused window rolls back first");
            } finally {
                session.stop();
            }
        }
        pass("second-window refusal closes the first window");
    }

    private void backgroundOnly() throws Exception {
        try (FakeDaemon bg = new FakeDaemon(50211, 0, false)) {
            SceneConfig config = local();
            config.clock.enabled = false;
            SceneSession session = new SceneSession(getTargetContext(), config, s -> { });
            session.start();
            try {
                await(() -> bg.frames == 1 && bg.pings >= 2, 4000, "background without clock daemon");
            } finally {
                session.stop();
            }
            check(bg.closed.await(2, TimeUnit.SECONDS), "explicit stop closes background");
        }
        pass("background-only mode and explicit stop");
    }

    private void liveUpdate() throws Exception {
        try (FakeDaemon bg = new FakeDaemon(50211, 0, false)) {
            SceneConfig config = local();
            config.clock.enabled = false;
            SceneSession session = new SceneSession(getTargetContext(), config, s -> { });
            session.start();
            try {
                await(() -> bg.frames == 1, 4000, "first background frame");
                config.grid = !config.grid;
                config.alpha = 128;
                check(session.update(config), "picture change applied live");
                await(() -> bg.frames == 2, 2000, "new frame in the same connection");
                config.background.x = 10;
                config.background.w = 1900;
                check(!session.update(config), "geometry change needs a new HELLO");
                config = local();
                config.clock.enabled = false;
                config.background.z = 1;
                check(!session.update(config) && !session.sameDaemons(config),
                        "z change needs a daemon restart");
            } finally {
                session.stop();
            }
            check(bg.closed.await(2, TimeUnit.SECONDS), "live update keeps one connection");
        }
        pass("picture-only change goes into the open window without restart");
    }

    private void repeatSessions() throws Exception {
        for (int i = 0; i < 3; i++) {
            try (FakeDaemon clock = new FakeDaemon(50210, 0, false)) {
                SceneConfig config = local();
                config.background.enabled = false;
                SceneSession session = new SceneSession(getTargetContext(), config, s -> { });
                session.start();
                try {
                    await(() -> clock.frames > 0, 4000, "clock restart " + i);
                } finally {
                    session.stop();
                }
                check(clock.closed.await(2, TimeUnit.SECONDS), "clock closes before restart");
            }
        }
        pass("three repeated clock-only sessions");
    }

    private void fuelWindow() throws Exception {
        try (FakeDaemon fuel = new FakeDaemon(50212, 0, false)) {
            SceneConfig config = local();
            config.background.enabled = false;
            config.clock.enabled = false;
            config.fuel.enabled = true;
            FuelSensor.accept(new android.content.Intent(FuelSensor.SENSOR_FLOAT_CHANGED)
                    .putExtra("id", String.valueOf(FuelSensor.SENSOR_FUEL_PERCENTAGE))
                    .putExtra("value", "50"));
            SceneSession session = new SceneSession(getTargetContext(), config, s -> { });
            session.start();
            try {
                await(() -> fuel.frames >= 1, 4000, "fuel frame");
                check(intAt(fuel.hello, 4) == config.fuel.x && intAt(fuel.hello, 12) == 300,
                        "fuel window geometry");
                int frames = fuel.frames;
                FuelSensor.accept(new android.content.Intent(FuelSensor.SENSOR_FLOAT_CHANGED)
                        .putExtra("id", String.valueOf(FuelSensor.SENSOR_FUEL_PERCENTAGE))
                        .putExtra("value", "60"));
                await(() -> fuel.frames > frames, 2000, "new fuel level redraws");
                int pings = fuel.pings;
                await(() -> fuel.pings > pings + 1, 2000, "unchanged level sends heartbeat only");
                check(fuel.frames == frames + 1, "no redraw without change");
            } finally {
                session.stop();
            }
            check(fuel.closed.await(2, TimeUnit.SECONDS), "fuel window closes");
        }
        pass("fuel window on TCP 50212 redraws only on a new level");
    }

    private void activityLifecycle() throws Exception {
        android.content.SharedPreferences prefs = prefs();
        java.util.Map<String, ?> saved = prefs.getAll();
        prefs.edit().clear().commit();
        Activity activity = startActivitySync(new android.content.Intent(getTargetContext(),
                MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        try (FakeDaemon bg = new FakeDaemon(50211, 0, false);
                FakeDaemon clock = new FakeDaemon(50210, 0, false)) {
            runOnMainSync(() -> {
                android.view.View root = activity.getWindow().getDecorView();
                ((android.widget.EditText) find(root, "Хост QNX", true)).setText("127.0.0.1");
                // Имитаторы gcp/1 уже слушают порты; telnet-запуск демонов не нужен.
                ((android.widget.Switch) find(root, "Запускать демоны", true)).setChecked(false);
                ((android.widget.Switch) find(root, "Фон: включить", true)).setChecked(true);
                find(root, "Фон: дисплей 1", true).performClick();
                // Фон на dpy-1 z=3 закрыл бы приборы — не применяется.
                for (int i = 0; i < 3; i++) {
                    find(root, "Фон: z +", true).performClick();
                }
                find(root, "Показать / применить", false).performClick();
                check(!SceneService.isActive(activity), "background z=3 on dpy-1 refused");
                find(root, "Фон: z −", true).performClick();
                find(root, "Показать / применить", false).performClick();
            });
            await(() -> bg.frames > 0 && clock.frames > 0, 5000, "UI shows both layers");
            check(prefs.getString("clock.z", "").equals("9")
                    && prefs.getString("background.z", "").equals("2")
                    && prefs.getString("background.display", "").equals("1")
                    && SceneService.isActive(activity), "UI applies and saves allowed layers");
            runOnMainSync(() -> {
                android.view.View root = activity.getWindow().getDecorView();
                find(root, "Часы: z +", true).performClick(); // z=10 — уровень ламп.
                find(root, "Показать / применить", false).performClick();
                ((android.widget.EditText) find(root, "Ширина", true)).setText("2147483647");
                find(root, "Показать / применить", false).performClick();
            });
            int pings = clock.pings;
            await(() -> clock.pings > pings, 2000, "invalid draft preserves running scene");
            check(SceneConfig.fromJson(prefs.getString(SceneService.KEY_SCENE, "")).clock.z == 9,
                    "refused draft is not saved as the applied scene");
            runOnMainSync(activity::finish);
            int afterExit = clock.pings;
            await(() -> clock.pings > afterExit + 2 && bg.pings > 2, 3000,
                    "windows stay after Activity exit");
            check(bg.closed.getCount() == 1 && clock.closed.getCount() == 1, "sockets kept open");
            runOnMainSync(() -> SceneService.stop(getTargetContext()));
            check(bg.closed.await(2, TimeUnit.SECONDS) && clock.closed.await(2, TimeUnit.SECONDS),
                    "Убрать всё closes both sockets");
            await(() -> !SceneService.isRunning(), 3000, "service ends after stop");
        } finally {
            runOnMainSync(activity::finish);
            runOnMainSync(() -> SceneService.stop(getTargetContext()));
            restore(saved);
        }
        pass("UI apply within limits, refused drafts, background work after exit, stop");
    }

    private void bootAndRetry() throws Exception {
        android.content.SharedPreferences prefs = prefs();
        java.util.Map<String, ?> saved = prefs.getAll();
        SceneConfig config = local();
        config.background.enabled = false;
        android.content.Intent boot = new android.content.Intent(
                android.content.Intent.ACTION_BOOT_COMPLETED);
        try {
            prefs.edit().clear().putString(SceneService.KEY_SCENE, config.toJson())
                    .putBoolean(SceneService.KEY_ACTIVE, false).commit();
            new BootReceiver().onReceive(getTargetContext(), boot);
            prefs.edit().putBoolean(SceneService.KEY_ACTIVE, true)
                    .putBoolean(SceneService.KEY_BOOT, false).commit();
            new BootReceiver().onReceive(getTargetContext(), boot);
            Thread.sleep(1000);
            check(!SceneService.isRunning(), "no boot start when removed or switched off");
            prefs.edit().putBoolean(SceneService.KEY_BOOT, true).commit();
            FakeDaemon clock = new FakeDaemon(50210, 0, false);
            try {
                new BootReceiver().onReceive(getTargetContext(), boot);
                await(() -> clock.frames > 0, 5000, "boot restores the applied scene");
            } finally {
                clock.close(); // Потеря связи: демон пропал.
            }
            try (FakeDaemon again = new FakeDaemon(50210, 0, false)) {
                await(() -> again.frames > 0, 12000, "service reconnects after a lost link");
                runOnMainSync(() -> SceneService.stop(getTargetContext()));
                check(again.closed.await(2, TimeUnit.SECONDS), "stop after retry");
                await(() -> !SceneService.isRunning(), 3000, "service ends");
            }
        } finally {
            runOnMainSync(() -> SceneService.stop(getTargetContext()));
            restore(saved);
        }
        pass("boot start only when active, reconnect after a lost link");
    }

    private android.content.SharedPreferences prefs() {
        return getTargetContext().getSharedPreferences(SceneService.PREFS, 0);
    }

    private void restore(java.util.Map<String, ?> saved) {
        android.content.SharedPreferences.Editor editor = prefs().edit().clear();
        for (java.util.Map.Entry<String, ?> entry : saved.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                editor.putString(entry.getKey(), (String) value);
            } else if (value instanceof Boolean) {
                editor.putBoolean(entry.getKey(), (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(entry.getKey(), (Integer) value);
            }
        }
        editor.commit();
    }

    private static android.view.View find(android.view.View view, String text, boolean description) {
        if (description ? text.contentEquals(view.getContentDescription() == null ? ""
                : view.getContentDescription()) : view instanceof android.widget.TextView
                && text.contentEquals(((android.widget.TextView) view).getText())) {
            return view;
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.view.View match = find(group.getChildAt(i), text, description);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static SceneConfig local() {
        SceneConfig config = new SceneConfig();
        config.host = "127.0.0.1";
        config.password = ""; // Без telnet: имитаторы gcp/1 уже слушают порты.
        config.background.enabled = true; // При установке фон выключен.
        return config;
    }

    private void pass(String name) {
        report.append("PASS ").append(name).append('\n');
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void invalid(SceneConfig config, String name) {
        try {
            config.validate();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("accepted invalid " + name);
    }

    private static void await(BooleanSupplier condition, long timeout, String message)
            throws InterruptedException {
        long end = android.os.SystemClock.elapsedRealtime() + timeout;
        while (!condition.getAsBoolean() && android.os.SystemClock.elapsedRealtime() < end) {
            Thread.sleep(50);
        }
        check(condition.getAsBoolean(), message);
    }

    private static int intAt(byte[] bytes, int offset) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(offset);
    }

    private static byte[] decodeFrame(byte[] frame) {
        check(frame != null && frame.length >= 24, "frame header");
        int length = intAt(frame, 20);
        byte[] out = new byte[length];
        if (frame[0] == Gcp.ENC_RAW) {
            check(frame.length == 24 + length, "raw length");
            System.arraycopy(frame, 24, out, 0, length);
            return out;
        }
        check(frame[0] == Gcp.ENC_RLE32, "encoding");
        int write = 0;
        for (int read = 24; read < frame.length;) {
            int control = frame[read++] & 255;
            int count = (control & 127) + 1;
            for (int i = 0; i < count; i++) {
                System.arraycopy(frame, read + ((control & 128) == 0 ? i * 4 : 0), out, write, 4);
                write += 4;
            }
            read += (control & 128) == 0 ? count * 4 : 4;
        }
        check(write == length, "decoded size");
        return out;
    }

    private static final class FakeDaemon implements AutoCloseable {
        final ServerSocket server;
        final CountDownLatch closed = new CountDownLatch(1);
        volatile Socket socket;
        volatile byte[] hello;
        volatile byte[] frame;
        volatile int frames;
        volatile int pings;
        volatile boolean watchdogExpired;
        volatile Throwable failure;
        volatile boolean closing;
        final Thread thread;

        FakeDaemon(int port, int delay, boolean refuse) throws IOException {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new java.net.InetSocketAddress("127.0.0.1", port));
            thread = new Thread(() -> {
                try (Socket accepted = server.accept()) {
                    socket = accepted;
                    socket.setSoTimeout(2000);
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    int seq = 0;
                    while (true) {
                        byte[] head = new byte[24];
                        in.readFully(head);
                        Gcp.Header header = new Gcp.Header(head);
                        byte[] body = new byte[header.length];
                        in.readFully(body);
                        check(Gcp.crc(body, 0, body.length) == intAt(head, 16), "wire CRC");
                        int reply;
                        if (header.type == Gcp.T_HELLO) {
                            hello = body;
                            Thread.sleep(delay);
                            reply = refuse ? Gcp.T_ERROR : Gcp.T_HELLO_ACK;
                        } else if (header.type == Gcp.T_FRAME) {
                            decodeFrame(body);
                            frame = body;
                            frames++;
                            reply = 0x11;
                        } else {
                            check(header.type == Gcp.T_PING, "unexpected wire type");
                            pings++;
                            reply = 0x04;
                        }
                        socket.getOutputStream().write(Gcp.message(reply, 0, ++seq, new byte[0]));
                    }
                } catch (java.io.EOFException | java.net.SocketException expected) {
                } catch (java.net.SocketTimeoutException e) {
                    watchdogExpired = true;
                    failure = e;
                } catch (Throwable e) {
                    if (!closing) {
                        failure = e;
                    }
                } finally {
                    closed.countDown();
                }
            }, "fake-gcp-" + port);
            thread.start();
        }

        void disconnect() throws IOException {
            if (socket != null) {
                socket.close();
            }
        }

        void assertHealthy() {
            check(failure == null, "fake daemon failed: " + failure);
        }

        @Override
        public void close() throws Exception {
            closing = true;
            disconnect();
            server.close();
            thread.interrupt();
            thread.join(2000);
            assertHealthy();
        }
    }
}
