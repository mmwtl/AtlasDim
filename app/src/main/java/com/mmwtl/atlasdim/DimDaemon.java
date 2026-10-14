package com.mmwtl.atlasdim;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Запуск/остановка ghudbridgelited-dim на QNX. По образцу
 * DaemonInstaller.kt из ANHUD (https://github.com/g992/ANHUD): бинарник из assets
 * копируется в /tmp через NFS-шару (или набирается через telnet, если шары
 * нет) и запускается в фоне. В системе QNX ничего не меняется; /tmp
 * очищается при перезагрузке — после загрузки ГУ демоны заново кладёт SceneService.
 */
final class DimDaemon {
    private static final String TAG = "AtlasDim";

    /** Имя в assets; на QNX — отдельная копия на каждую роль и дисплей. */
    static final String NAME = "ghudbridgelited-dim";
    private static final int KILL_WAIT_STEPS = 10;
    private static final String SHARED_ANDROID = "/data/vendor/nfs/shared";
    private static final String SHARED_QNX = "/shared";
    private static final int TELNET_LINE_MAX = 200;
    private static final int TELNET_BATCH_LINES = 40;
    private static final long TELNET_BATCH_TIMEOUT_MS = 60000;

    interface Progress {
        void step(String text);
    }

    private DimDaemon() {
    }

    /** Смещения `cmp w8,#<display>` (выбор дисплея, проверка окна) — RESEARCH.md п. 5. */
    private static final int[] DISPLAY_CMP_OFFSETS = {0x4964, 0x5090};

    /**
     * Приводит наши процессы к нужным окнам: демон с той же командной строкой
     * (файл с display, порт, z) и файлом текущей версии остаётся работать,
     * остальные наши процессы останавливаются, недостающие запускаются.
     */
    static void prepare(Context context, QnxShell qnx, SceneConfig.Layer[] layers,
            Progress progress) throws IOException {
        byte[] asset = readAsset(context);
        List<String> wanted = new ArrayList<>();
        for (SceneConfig.Layer layer : layers) {
            // Размер отличается — в APK другая версия демона, прежний процесс не годится.
            if (layer.enabled && sizeOf(qnx, path(layer)) == asset.length) {
                wanted.add(command(layer));
            }
        }
        List<String> kept = new ArrayList<>();
        for (int step = 0; ; step++) {
            kept.clear();
            List<String> pids = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (String[] process : processes(qnx.exec("pidin ar").output)) {
                if (wanted.contains(process[1]) && !kept.contains(process[1])) {
                    kept.add(process[1]);
                } else {
                    pids.add(process[0]);
                    names.add(process[2]);
                }
            }
            if (pids.isEmpty()) {
                break;
            }
            if (step == KILL_WAIT_STEPS) {
                throw new IOException("прежний " + NAME + " не останавливается");
            }
            progress.step("останавливаю " + String.join(", ", names) + "…");
            String signal = step < KILL_WAIT_STEPS / 2 ? "" : "-9 ";
            qnx.exec("kill " + signal + String.join(" ", pids) + "; sleep 1");
        }
        for (SceneConfig.Layer layer : layers) {
            if (!layer.enabled) {
                continue;
            }
            if (kept.contains(command(layer))) {
                progress.step(layer.label() + ": демон dpy-" + layer.display + " z=" + layer.z
                        + " уже работает");
            } else {
                start(asset.clone(), qnx, layer, progress);
            }
        }
    }

    private static String path(SceneConfig.Layer layer) {
        return "/tmp/" + NAME + "-" + layer.id() + layer.display;
    }

    /** Командная строка так, как её показывает `pidin ar`. */
    static String command(SceneConfig.Layer layer) {
        return path(layer) + " --port " + layer.port() + " --z " + layer.z;
    }

    /** Запускает одно окно, не останавливая другие слои. */
    private static void start(byte[] asset, QnxShell qnx, SceneConfig.Layer layer,
            Progress progress) throws IOException {
        String path = path(layer);
        byte[] bin = withDisplay(asset, layer.display);
        if (sizeOf(qnx, path) == bin.length) {
            progress.step(path + " уже на месте");
        } else {
            progress.step("копирую " + path + "…");
            String sharedProblem = checkSharedDirWritable();
            if (sharedProblem == null) {
                copyViaShared(qnx, bin, path);
            } else {
                Log.w(TAG, "daemon: шара: " + sharedProblem + " — копирую через telnet");
                progress.step(sharedProblem + "; передаю через telnet…");
                copyViaTelnet(qnx, bin, path);
            }
        }
        String version = qnx.exec(path + " --version 2>&1").output;
        Log.i(TAG, "daemon: " + version);
        if (!version.contains("ghudbridgelited/")) {
            throw new IOException("на QNX не запускается " + path + ": " + version);
        }
        progress.step(layer.label() + ": запускаю dpy-" + layer.display + " z=" + layer.z + "…");
        must(qnx.exec(command(layer) + " > /tmp/" + NAME + "-" + layer.id() + ".log 2>&1 &"), "запуск");
    }

    /** Останавливает все свои процессы; файлы в /tmp остаются до перезагрузки. */
    static void stop(QnxShell qnx) throws IOException {
        killAll(qnx, text -> { });
    }

    /** slay всех своих процессов и ожидание их выхода; в конце — SIGKILL. */
    private static void killAll(QnxShell qnx, Progress progress) throws IOException {
        for (int step = 0; step < KILL_WAIT_STEPS; step++) {
            List<String> names = runningNames(qnx.exec("pidin ar").output);
            if (names.isEmpty()) {
                return;
            }
            progress.step("останавливаю " + String.join(", ", names) + "…");
            String signal = step < KILL_WAIT_STEPS / 2 ? "" : "-s KILL ";
            StringBuilder cmd = new StringBuilder();
            for (String name : names) {
                cmd.append("slay -f ").append(signal).append(name).append("; ");
            }
            cmd.append("sleep 1");
            qnx.exec(cmd.toString());
        }
        throw new IOException("прежний " + NAME + " не останавливается");
    }

    private static int sizeOf(QnxShell qnx, String path) throws IOException {
        String out = qnx.exec("wc -c < " + path + " 2>/dev/null").output.trim();
        try {
            return Integer.parseInt(out);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Вписывает QNX Screen display id в обе проверки `cmp w8,#imm`. */
    static byte[] withDisplay(byte[] bin, int display) throws IOException {
        if (display < 1 || display > 5) {
            throw new IOException("дисплей QNX должен быть 1…5");
        }
        if (bin.length < DISPLAY_CMP_OFFSETS[1] + 4) {
            throw new IOException("обрезанный бинарник демона");
        }
        for (int off : DISPLAY_CMP_OFFSETS) {
            int ins = (bin[off] & 0xff) | (bin[off + 1] & 0xff) << 8
                    | (bin[off + 2] & 0xff) << 16 | (bin[off + 3] & 0xff) << 24;
            // cmp w8, #imm  ==  subs wzr, w8, #imm: 0x7100001f | imm12 << 10 | 8 << 5
            if ((ins & ~(0xfff << 10)) != 0x7100011f) {
                throw new IOException(String.format("неожиданная инструкция 0x%08x @0x%x", ins, off));
            }
            ins = (ins & ~(0xfff << 10)) | display << 10;
            bin[off] = (byte) ins;
            bin[off + 1] = (byte) (ins >> 8);
            bin[off + 2] = (byte) (ins >> 16);
            bin[off + 3] = (byte) (ins >> 24);
        }
        return bin;
    }

    private static byte[] readAsset(Context context) throws IOException {
        try (InputStream in = context.getAssets().open(NAME)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static void copyViaShared(QnxShell qnx, byte[] bytes, String path)
            throws IOException {
        String name = NAME + "-" + Long.toHexString(System.nanoTime());
        File shared = new File(SHARED_ANDROID, name);
        try (FileOutputStream out = new FileOutputStream(shared)) {
            out.write(bytes);
        }
        try {
            must(qnx.exec("cp " + SHARED_QNX + "/" + name + " " + path + ".new"), "cp");
            must(qnx.exec("chmod 755 " + path + ".new && mv -f " + path + ".new " + path),
                    "chmod/mv");
        } finally {
            try {
                qnx.exec("rm -f " + SHARED_QNX + "/" + name);
            } catch (IOException e) {
                Log.w(TAG, "daemon: очистка шары: " + e);
            }
            //noinspection ResultOfMethodCallIgnored
            shared.delete();
        }
        checkSize(qnx, path, bytes.length);
    }

    /** `print -n '…' >> file` с восьмеричными escape — на QNX нет base64/printf. */
    private static void copyViaTelnet(QnxShell qnx, byte[] bytes, String path)
            throws IOException {
        String tmp = path + ".new";
        must(qnx.exec(": > " + tmp), "создание " + tmp);
        List<String> lines = printLines(bytes, tmp);
        for (int i = 0; i < lines.size(); i += TELNET_BATCH_LINES) {
            qnx.execBatch(lines.subList(i, Math.min(lines.size(), i + TELNET_BATCH_LINES)),
                    TELNET_BATCH_TIMEOUT_MS);
        }
        checkSize(qnx, tmp, bytes.length);
        must(qnx.exec("chmod 755 " + tmp + " && mv -f " + tmp + " " + path), "chmod/mv");
    }

    private static void checkSize(QnxShell qnx, String path, int expected) throws IOException {
        int size = sizeOf(qnx, path);
        if (size != expected) {
            qnx.exec("rm -f " + path);
            throw new IOException("копия " + path + " битая: " + size + " из " + expected + " байт");
        }
    }

    private static List<String> printLines(byte[] bytes, String target) {
        String prefix = "print -n '";
        String suffix = "' >> " + target;
        int budget = TELNET_LINE_MAX - prefix.length() - suffix.length();
        List<String> lines = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        for (byte b : bytes) {
            int c = b & 0xFF;
            String piece = isPlain(c) ? String.valueOf((char) c)
                    : "\\0" + String.format("%03o", c);
            if (body.length() + piece.length() > budget) {
                lines.add(prefix + body + suffix);
                body.setLength(0);
            }
            body.append(piece);
        }
        if (body.length() > 0) {
            lines.add(prefix + body + suffix);
        }
        return lines;
    }

    private static boolean isPlain(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '.' || c == '_' || c == '-' || c == '/' || c == ':';
    }

    private static String checkSharedDirWritable() {
        File dir = new File(SHARED_ANDROID);
        if (!dir.isDirectory()) {
            return "нет папки " + SHARED_ANDROID;
        }
        File probe = new File(dir, ".atlasdim-probe-" + Long.toHexString(System.nanoTime()));
        try (FileOutputStream out = new FileOutputStream(probe)) {
            out.write(0);
            return null;
        } catch (IOException | SecurityException e) {
            return "нет записи в " + SHARED_ANDROID + ": " + e.getMessage();
        } finally {
            //noinspection ResultOfMethodCallIgnored
            probe.delete();
        }
    }

    /** Только наши исполняемые файлы из /tmp, включая старые версии. */
    static List<String> runningNames(String pidinAr) {
        List<String> names = new ArrayList<>();
        for (String[] process : processes(pidinAr)) {
            if (!names.contains(process[2])) {
                names.add(process[2]);
            }
        }
        return names;
    }

    /** Наши процессы: {pid, командная строка с одиночными пробелами, имя файла}. */
    static List<String[]> processes(String pidinAr) {
        List<String[]> processes = new ArrayList<>();
        for (String line : pidinAr.split("\n")) {
            String[] cols = line.trim().split("\\s+");
            if (cols.length < 2 || !cols[0].matches("\\d+")) {
                continue;
            }
            if (!cols[1].startsWith("/tmp/")) {
                continue;
            }
            String exe = cols[1].substring("/tmp/".length());
            if (exe.matches(NAME + "([1-5]?|-(clock|fuel|background)[1-3])")) {
                String cmd = String.join(" ", Arrays.copyOfRange(cols, 1, cols.length));
                processes.add(new String[] {cols[0], cmd, exe});
            }
        }
        return processes;
    }

    private static void must(QnxShell.Result r, String what) throws IOException {
        if (!r.ok()) {
            throw new IOException(what + " на QNX не удался (код " + r.exitCode + "): " + r.output);
        }
    }
}
