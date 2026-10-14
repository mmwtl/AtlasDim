package com.mmwtl.atlasdim;

import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Диагностический отчёт: связь с QNX и состояние приборки по telnet. Только чтение —
 * на QNX ничего не запускается и не останавливается, к портам демонов не подключаемся
 * (демон держит одну сессию gcp/1). Команды — notes/commands.md.
 */
final class Diagnostics {
    private static final int PROBE_TIMEOUT_MS = 3000;
    private static final long STEP_TIMEOUT_MS = 15000;
    private static final int MAX_LINES = 120;
    private static final int MAX_WINDOW_LINES = 600;
    private static final String SUMMARY = "\n== Итог ==\n";
    private static final String SHARED_ANDROID = "/data/vendor/nfs/shared";

    interface Progress {
        void step(String text);
    }

    /** Что известно о приложении на Android; заполняет MainActivity. */
    static final class Input {
        String version;
        String host;
        String password;
        boolean active;
        boolean serviceRunning;
        String state;
        SceneConfig config;
    }

    private static final String[][] STEPS = {
            {"Система QNX", "uname -a; ls -ld /tmp /shared 2>&1; ls /usr/lib/libscreen* 2>&1"},
            {"Дисплеи QNX Screen (/tmp/graphics.conf)",
                    "grep -v -E '^[[:space:]]*(#|$)' /tmp/graphics.conf 2>&1"},
            {"Окна QNX Screen",
                    "for f in /dev/screen/*/win-*; do "
                    // Служебные framebuffer-окна дисплеев одинаковы и только удлиняют отчёт.
                    + "grep -q '^class = framebuffer' \"$f\" 2>/dev/null && continue; "
                    + "echo \"== $f\"; "
                    + "L=$(grep -E '^(display|status|visible|order|class|id string|pos|size"
                    + "|global alpha|transparency|flags) ' \"$f\" 2>/dev/null); "
                    + "if [ -n \"$L\" ]; then echo \"$L\"; else head -12 \"$f\" 2>&1; fi; done"},
            {"Процессы приборки",
                    "pidin ar | grep -E 'cluster|EarlyTT|hud-hmi|vmwindow|screen|ghudbridgelited'"
                    + " | grep -v grep"},
            {"Порты демонов (netstat)", "netstat -an 2>&1 | grep -E '\\.5021[0-2] '"},
            {"Файлы демонов в /tmp", "ls -l /tmp/ghudbridgelited-dim* 2>&1"},
            {"Логи демонов",
                    "for f in /tmp/ghudbridgelited-dim*.log; do [ -f \"$f\" ] || continue; "
                    + "echo \"== $f\"; tail -8 \"$f\"; done"},
            // Без PrintPropValue: VehicleService пишет CLUSTER_* свойства по нескольку раз в секунду.
            {"Журнал QNX (screen/приборка)",
                    "slog2info 2>/dev/null | grep -i -E 'screen|kanzi|cluster-hmi|earlytt"
                    + "|ghudbridge|error|fail' | grep -v -E 'PrintPropValue|WarningLogOuts'"
                    + " | tail -30"},
    };

    private Diagnostics() {
    }

    /** Собирает отчёт; не бросает — ошибки попадают в текст. Вызывать не с главного потока. */
    static String run(Input app, Progress progress) {
        StringBuilder head = new StringBuilder();
        StringBuilder body = new StringBuilder();
        List<String> summary = new ArrayList<>();
        head.append("Atlas Dim — диагностика\n")
                .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()))
                .append('\n');

        section(body, "Android");
        body.append("Приложение: v").append(app.version).append('\n')
                .append("Android ").append(Build.VERSION.RELEASE).append(" (SDK ")
                .append(Build.VERSION.SDK_INT).append("), ").append(Build.MANUFACTURER)
                .append(' ').append(Build.MODEL).append('\n')
                .append("Композиция: ").append(app.active ? "включена" : "выключена")
                .append(", сервис ").append(app.serviceRunning ? "работает" : "не запущен")
                .append('\n')
                .append("Состояние: ").append(app.state).append('\n');
        if (app.config != null) {
            for (SceneConfig.Layer layer : app.config.layers()) {
                body.append(layer.label()).append(": ")
                        .append(layer.enabled ? "вкл" : "выкл")
                        .append(", dpy-").append(layer.display).append(" z=").append(layer.z)
                        .append(", TCP ").append(layer.port()).append('\n');
            }
        }
        File shared = new File(SHARED_ANDROID);
        body.append("NFS-шара ").append(SHARED_ANDROID).append(": ")
                .append(!shared.isDirectory() ? "не видна"
                        : shared.canWrite() ? "запись есть" : "только чтение")
                .append('\n');

        section(body, "Связь");
        progress.step("Проверяю " + app.host + ":" + QnxShell.TELNET_PORT + "…");
        long started = System.currentTimeMillis();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(app.host, QnxShell.TELNET_PORT), PROBE_TIMEOUT_MS);
            long ms = System.currentTimeMillis() - started;
            body.append("telnet ").append(app.host).append(':').append(QnxShell.TELNET_PORT)
                    .append(" — порт открыт, ").append(ms).append(" мс\n");
        } catch (IOException e) {
            body.append("telnet ").append(app.host).append(':').append(QnxShell.TELNET_PORT)
                    .append(" — недоступен: ").append(e.getMessage()).append('\n');
            summary.add("✗ QNX недоступна по сети (" + app.host + ")");
            return finish(head, summary, body);
        }
        if (app.password.isEmpty()) {
            body.append("Вход root: пароль не задан\n");
            summary.add("✗ Пароль root не задан — данные QNX не сняты");
            return finish(head, summary, body);
        }

        progress.step("Вход в QNX…");
        String pidin = "";
        String screen = "";
        String graphics = "";
        try (QnxShell qnx = QnxShell.open(app.host, "root", app.password)) {
            body.append("Вход root: успешно\n");
            summary.add("✓ QNX на связи (" + app.host + ")");
            for (String[] step : STEPS) {
                progress.step(step[0] + "…");
                section(body, step[0]);
                String out;
                try {
                    out = qnx.exec(step[1], STEP_TIMEOUT_MS).output;
                } catch (IOException e) {
                    out = "ошибка: " + e.getMessage();
                }
                body.append(out.isEmpty() ? "(пусто)"
                        : limit(out, step == STEPS[2] ? MAX_WINDOW_LINES : MAX_LINES))
                        .append('\n');
                if (step == STEPS[1]) {
                    graphics = out;
                } else if (step == STEPS[2]) {
                    screen = out;
                } else if (step == STEPS[3]) {
                    pidin = out;
                }
            }
        } catch (IOException e) {
            body.append("Вход root: ").append(e.getMessage()).append('\n');
            summary.add("✗ Telnet открыт, но вход не удался: " + e.getMessage());
            return finish(head, summary, body);
        }

        List<Window> windows = windows(screen);
        summary.add("• graphics.conf: дисплеев " + count(graphics, "begin display")
                + ", окон QNX Screen " + windows.size());
        summary.add(mark(pidin.contains("cluster-hmi")) + " cluster-hmi (Kanzi приборки)");
        summary.add(mark(pidin.contains("EarlyTT")) + " EarlyTT (контрольные лампы)");
        for (int display = 1; display <= 2; display++) {
            StringBuilder line = new StringBuilder("• dpy-" + display + ":");
            for (Window w : windows) {
                if (w.display == display) {
                    line.append(' ').append(w).append(';');
                }
            }
            summary.add(line.toString());
        }
        List<String[]> ours = DimDaemon.processes(pidin);
        List<String> ourPids = new ArrayList<>();
        for (String[] process : ours) {
            ourPids.add(process[0]);
        }
        if (app.config != null) {
            for (SceneConfig.Layer layer : app.config.layers()) {
                if (!layer.enabled) {
                    continue;
                }
                for (Window w : windows) {
                    if (w.display == layer.display && w.order == layer.z
                            && !ourPids.contains(w.pid)) {
                        summary.add("⚠ " + layer.label() + " dpy-" + layer.display + " z="
                                + layer.z + " совпадает с окном " + w.id);
                    }
                }
            }
        }
        if (ours.isEmpty()) {
            summary.add("• Наших демонов не запущено");
        }
        for (String[] process : ours) {
            summary.add("• Демон " + process[1]);
        }
        return finish(head, summary, body);
    }

    /** Только блок «Итог» отчёта — для карточки на вкладке. */
    static String summary(String report) {
        int from = report.indexOf(SUMMARY);
        int to = report.indexOf("\n\n== ", from);
        return from < 0 || to < 0 ? report : report.substring(from + SUMMARY.length(), to);
    }

    /** Окно QNX Screen из вывода шага «Окна»: id, дисплей, order, статус. */
    static final class Window {
        String id = "?";
        String pid = "";
        int display = -1;
        int order;
        String status = "?";

        @Override
        public String toString() {
            return id + " z=" + order + " " + status;
        }
    }

    static List<Window> windows(String screen) {
        List<Window> windows = new ArrayList<>();
        Window current = null;
        for (String line : screen.split("\n")) {
            line = line.trim();
            if (line.startsWith("== /dev/screen/")) {
                current = new Window();
                String[] path = line.substring("== /dev/screen/".length()).split("/");
                current.pid = path[0];
                windows.add(current);
            } else if (current != null) {
                int eq = line.indexOf(" = ");
                if (eq < 0) {
                    continue;
                }
                String key = line.substring(0, eq);
                String value = line.substring(eq + 3).trim();
                try {
                    if (key.equals("id string")) {
                        current.id = value.isEmpty() ? "?" : value;
                    } else if (key.equals("display")) {
                        current.display = Integer.parseInt(value.replace("dpy-", ""));
                    } else if (key.equals("order")) {
                        current.order = Integer.parseInt(value);
                    } else if (key.equals("status")) {
                        current.status = value;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return windows;
    }

    private static int count(String text, String what) {
        int n = 0;
        for (int i = text.indexOf(what); i >= 0; i = text.indexOf(what, i + 1)) {
            n++;
        }
        return n;
    }

    private static String mark(boolean ok) {
        return ok ? "✓" : "✗";
    }

    private static void section(StringBuilder sb, String title) {
        sb.append("\n== ").append(title).append(" ==\n");
    }

    private static String limit(String text, int max) {
        String[] lines = text.split("\n");
        if (lines.length <= max) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) {
            sb.append(lines[i]).append('\n');
        }
        return sb.append("… ещё ").append(lines.length - max).append(" строк").toString();
    }

    private static String finish(StringBuilder head, List<String> summary, StringBuilder body) {
        head.append(SUMMARY);
        for (String line : summary) {
            head.append(line).append('\n');
        }
        return head.append(body).toString();
    }
}
