package com.mmwtl.atlasdim;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * root-shell QNX по telnet. Перенесено на Java из
 * QnxShell.kt из ANHUD (https://github.com/g992/ANHUD).
 * exec() дописывает `; echo <marker>$?` и ждёт маркер с цифрой после него,
 * чтобы не спутать с эхом введённой строки. Telnet-опции отклоняются.
 */
final class QnxShell implements Closeable {
    static final int TELNET_PORT = 23;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final long LOGIN_TIMEOUT_MS = 8000;
    static final long EXEC_TIMEOUT_MS = 20000;
    private static final long QUIET_MS = 300;
    private static final int POLL_MS = 50;

    static final class Result {
        final String output;
        final int exitCode;

        Result(String output, int exitCode) {
            this.output = output;
            this.exitCode = exitCode;
        }

        boolean ok() {
            return exitCode == 0;
        }
    }

    private final Socket socket;
    private final InputStream input;
    private final OutputStream output;
    private final byte[] buf = new byte[4096];
    private int iac;
    private int iacVerb;

    private QnxShell(Socket socket) throws IOException {
        this.socket = socket;
        input = socket.getInputStream();
        output = socket.getOutputStream();
    }

    /** Подключается и входит; IOException с понятным текстом при отказе. */
    static QnxShell open(String host, String user, String password) throws IOException {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, TELNET_PORT), CONNECT_TIMEOUT_MS);
            s.setTcpNoDelay(true);
        } catch (IOException e) {
            s.close();
            throw e;
        }
        QnxShell sh = new QnxShell(s);
        try {
            String banner = sh.readUntil(LOGIN_TIMEOUT_MS, "login:", "Password", "#");
            boolean credentialsSent = false;
            if (banner.contains("login:")) {
                sh.writeLine(user);
                credentialsSent = true;
                banner = sh.readUntil(LOGIN_TIMEOUT_MS, "Password", "#");
            }
            if (banner.contains("Password")) {
                sh.writeLine(password);
                credentialsSent = true;
                banner = sh.readUntil(LOGIN_TIMEOUT_MS, "#", "incorrect", "login:");
            }
            if (!banner.trim().endsWith("#") && !banner.contains("\n#")) {
                if (credentialsSent || banner.contains("incorrect")) {
                    throw new IOException("вход в QNX не удался (проверьте пароль root)");
                }
                throw new IOException("QNX не прислала приглашение telnet");
            }
            return sh;
        } catch (IOException | RuntimeException e) {
            sh.close();
            throw e;
        }
    }

    Result exec(String command) throws IOException {
        return exec(command, EXEC_TIMEOUT_MS);
    }

    Result exec(String command, long timeoutMs) throws IOException {
        String marker = "__DONE_" + System.nanoTime() + "__";
        String cmd = command.trim();
        if (cmd.endsWith("&")) {
            writeLine(cmd);
            writeLine("echo " + marker + "$?");
        } else {
            writeLine(cmd + "; echo " + marker + "$?");
        }
        Result r = parseExecOutput(readUntilMarker(marker, timeoutMs), marker);
        if (r == null) {
            throw new IOException("QNX не ответила на команду: " + cmd);
        }
        return r;
    }

    /** Все строки подряд, один маркер в конце; код возврата — последней строки. */
    Result execBatch(List<String> lines, long timeoutMs) throws IOException {
        String marker = "__DONE_" + System.nanoTime() + "__";
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line.trim()).append("\r\n");
        }
        sb.append("echo ").append(marker).append("$?\r\n");
        output.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        output.flush();
        Result r = parseExecOutput(readUntilMarker(marker, timeoutMs), marker);
        if (r == null) {
            throw new IOException("QNX не ответила на пакет из " + lines.size() + " команд");
        }
        return r;
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private void writeLine(String line) throws IOException {
        output.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private String readUntil(long timeoutMs, String... targets) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        StringBuilder sb = new StringBuilder();
        while (System.currentTimeMillis() < deadline) {
            if (read(sb) < 0) {
                break;
            }
            for (String t : targets) {
                if (sb.indexOf(t) >= 0) {
                    return sb.toString();
                }
            }
        }
        return sb.toString();
    }

    private String readUntilMarker(String marker, long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        long seenAt = -1;
        StringBuilder sb = new StringBuilder();
        while (System.currentTimeMillis() < deadline) {
            if (read(sb) < 0) {
                break;
            }
            if (seenAt < 0 && completedMarker(sb.toString(), marker) >= 0) {
                seenAt = System.currentTimeMillis();
            }
            if (seenAt >= 0 && System.currentTimeMillis() - seenAt > QUIET_MS) {
                break;
            }
        }
        return sb.toString();
    }

    /** Одно короткое чтение: текст в sb (без CR и NUL), telnet-опции отклоняются. -1 — EOF. */
    private int read(StringBuilder sb) throws IOException {
        socket.setSoTimeout(POLL_MS);
        int n;
        try {
            n = input.read(buf);
        } catch (SocketTimeoutException e) {
            return 0;
        }
        if (n < 0) {
            return -1;
        }
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (int i = 0; i < n; i++) {
            int c = buf[i] & 0xff;
            switch (iac) {
                case 0:
                    if (c == 0xFF) {
                        iac = 1;
                    } else if (c != '\r' && c != 0) {
                        data.write(c);
                    }
                    break;
                case 1:
                    if (c == 0xFA) {
                        iac = 3;
                    } else if (c >= 0xFB && c <= 0xFE) {
                        iacVerb = c;
                        iac = 2;
                    } else {
                        iac = 0;
                    }
                    break;
                case 2:
                    int answer = (iacVerb == 0xFD || iacVerb == 0xFE) ? 0xFC : 0xFE;
                    output.write(new byte[] {(byte) 0xFF, (byte) answer, (byte) c});
                    iac = 0;
                    break;
                case 3:
                    if (c == 0xFF) {
                        iac = 4;
                    }
                    break;
                default:
                    iac = c == 0xF0 ? 0 : 3;
                    break;
            }
        }
        output.flush();
        String text = new String(data.toByteArray(), StandardCharsets.UTF_8);
        sb.append(text);
        return text.length();
    }

    /** Позиция маркера, за которым идёт цифра (настоящий echo, не эхо ввода), или -1. */
    static int completedMarker(String text, String marker) {
        int from = 0;
        while (true) {
            int i = text.indexOf(marker, from);
            if (i < 0) {
                return -1;
            }
            int end = i + marker.length();
            if (end < text.length() && Character.isDigit(text.charAt(end))) {
                return i;
            }
            from = i + 1;
        }
    }

    /** Вывод между эхом команды и маркером + код возврата; null, если ответ неполный. */
    static Result parseExecOutput(String raw, String marker) {
        int at = completedMarker(raw, marker);
        if (at < 0) {
            return null;
        }
        int end = at + marker.length();
        int digits = end;
        while (digits < raw.length() && Character.isDigit(raw.charAt(digits))) {
            digits++;
        }
        int exitCode = Integer.parseInt(raw.substring(end, digits));
        String body = raw.substring(0, at);
        int echo = body.lastIndexOf(marker + "$?");
        if (echo >= 0) {
            int nl = body.indexOf('\n', echo);
            body = nl >= 0 ? body.substring(nl + 1) : "";
        }
        return new Result(body.trim(), exitCode);
    }
}
