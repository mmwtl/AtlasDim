package com.mmwtl.atlasdim;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.Log;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.CopyOnWriteArrayList;

/** Независимые окна gcp/1 (фон, часы, топливо) с общим запуском, остановкой и откатом при ошибке. */
final class SceneSession {
    private static final String TAG = "AtlasDim";
    private static final Object SESSION_LOCK = new Object();
    private final Context context;
    /** Заменяется целиком в {@link #update}; окна читают его заново на каждом такте. */
    private volatile SceneConfig config;
    private final Listener listener;
    private final CopyOnWriteArrayList<Link> links = new CopyOnWriteArrayList<>();
    private volatile boolean running;
    /** Все окна открыты, композиция работает. */
    private volatile boolean live;
    private volatile String lostReason;
    private volatile QnxShell shell;
    /** Сессия заменяется новой: демоны оставить, новая сессия сама их сверит. */
    private volatile boolean keepDaemons;
    /** Демоны прежней сессии подходят как есть — сначала подключиться без telnet. */
    private final boolean reuse;
    private Thread worker;

    interface Listener {
        void onState(String state);

        /** Сессия завершилась (остановка, отказ или потеря связи), демоны уже приведены в порядок. */
        default void onEnd() {
        }
    }

    SceneSession(Context context, SceneConfig config, Listener listener) {
        this(context, config, false, listener);
    }

    SceneSession(Context context, SceneConfig config, boolean reuse, Listener listener) {
        this.context = context.getApplicationContext();
        Backgrounds.init(context);
        this.config = new SceneConfig(config);
        this.reuse = reuse;
        this.listener = listener;
    }

    void start() {
        running = true;
        worker = new Thread(() -> {
            state("Жду завершения прежней сессии…");
            synchronized (SESSION_LOCK) {
                if (running) {
                    session();
                } else {
                    state("Остановлено");
                }
            }
        }, "atlasdim-scene");
        worker.start();
    }

    /**
     * Меняет только рисунок в уже открытых окнах, без telnet и переподключения.
     * Возвращает false, если изменилось то, что требует нового запуска: хост,
     * набор окон, дисплей, z (параметры процесса демона) или геометрия (HELLO).
     */
    boolean update(SceneConfig next) {
        SceneConfig current = config;
        if (!running || !live || !next.host.equals(current.host)
                || !next.password.equals(current.password)) {
            return false;
        }
        for (SceneConfig.Kind kind : SceneConfig.Kind.values()) {
            if (!sameWindow(next.layer(kind), current.layer(kind))) {
                return false;
            }
        }
        // Окна заметят новый объект на ближайшем такте (≤ 500 мс) и пришлют кадр.
        config = new SceneConfig(next);
        return true;
    }

    /**
     * Процессы демонов (хост, набор окон, display, z) подходят для next;
     * окна можно переоткрыть с новой геометрией без telnet.
     */
    boolean sameDaemons(SceneConfig next) {
        SceneConfig current = config;
        if (!running || !live || !sameQnx(next)) {
            return false;
        }
        for (SceneConfig.Kind kind : SceneConfig.Kind.values()) {
            if (!sameDaemon(next.layer(kind), current.layer(kind))) {
                return false;
            }
        }
        return true;
    }

    /** Тот же QNX и пароль: новая сессия сама сверит демоны, гасить их заранее не нужно. */
    boolean sameQnx(SceneConfig next) {
        SceneConfig current = config;
        return !next.password.isEmpty() && next.host.equals(current.host)
                && next.password.equals(current.password);
    }

    private static boolean sameDaemon(SceneConfig.Layer a, SceneConfig.Layer b) {
        return a.enabled == b.enabled
                && (!a.enabled || (a.display == b.display && a.z == b.z));
    }

    private static boolean sameWindow(SceneConfig.Layer a, SceneConfig.Layer b) {
        if (a.enabled != b.enabled) {
            return false;
        }
        return !a.enabled || (a.display == b.display && a.z == b.z
                && a.x == b.x && a.y == b.y && a.w == b.w && a.h == b.h);
    }

    /** Закрытие сокетов прерывает также зависшее чтение/запись, не только sleep. */
    void stop() {
        stop(false);
    }

    /** keepDaemons — сессию сменит новая: окна закрыть, процессы на QNX не трогать. */
    void stop(boolean keepDaemons) {
        if (keepDaemons) {
            this.keepDaemons = true;
        }
        running = false;
        live = false;
        for (Link link : links) {
            link.close();
        }
        QnxShell current = shell;
        if (current != null) {
            current.close();
        }
        if (worker != null) {
            worker.interrupt();
        }
    }

    private void checkRunning() throws InterruptedException {
        if (!running) {
            throw new InterruptedException();
        }
    }

    private void session() {
        String why = "Остановлено";
        // С паролем на QNX могут остаться наши демоны (в том числе от прежней сессии).
        boolean managed = !config.password.isEmpty();
        try {
            config.validate();
            boolean opened = false;
            if (reuse && managed) {
                try {
                    openLinks();
                    opened = true;
                } catch (IOException e) {
                    checkRunning();
                    Log.w(TAG, "прежние демоны не приняли окна: " + e.getMessage());
                    closeLinks();
                }
            }
            if (!opened) {
                if (managed) {
                    state("Подключение к QNX по telnet…");
                    try (QnxShell qnx = QnxShell.open(config.host, "root", config.password)) {
                        shell = qnx;
                        checkRunning();
                        DimDaemon.prepare(context, qnx, layers(), this::state);
                    } finally {
                        shell = null;
                    }
                }
                openLinks();
            }
            live = true;
            while (running) {
                StringBuilder text = new StringBuilder("Отправлено: ");
                for (Link link : links) {
                    if (text.length() > 12) {
                        text.append(" · ");
                    }
                    text.append(link.layer.label()).append(" dpy-")
                            .append(link.layer.display).append(" z=").append(link.layer.z)
                            .append(" (кадров ").append(link.frames).append(")");
                }
                state(text.toString());
                Thread.sleep(500);
            }
        } catch (InterruptedException e) {
            why = "Остановлено";
        } catch (IOException | RuntimeException e) {
            if (running) {
                why = "Ошибка: " + e.getMessage();
                Log.e(TAG, why, e);
            }
        } finally {
            running = false;
            live = false;
            closeLinks();
            // Остановка рабочих сокетов уже выполнена; очистка демонов не отменяется interrupt.
            Thread.interrupted();
            if (lostReason != null) {
                why = lostReason;
            }
            if (keepDaemons) {
                why += " · демоны оставлены новой сессии";
            } else if (managed) {
                state("Окна отключены; останавливаю наши демоны…");
                try (QnxShell qnx = QnxShell.open(config.host, "root", config.password)) {
                    DimDaemon.stop(qnx);
                    why += " · демоны остановлены";
                } catch (IOException e) {
                    why += " · остановка демонов не подтверждена: " + e.getMessage();
                    Log.w(TAG, why);
                }
            }
            state(why);
            listener.onEnd();
        }
    }

    private void openLinks() throws IOException, InterruptedException {
        for (SceneConfig.Layer layer : layers()) {
            if (layer.enabled) {
                Link link = new Link(layer);
                links.add(link);
                checkRunning();
                link.open();
            }
        }
    }

    private void closeLinks() {
        for (Link link : links) {
            link.close();
        }
        links.clear();
    }

    private SceneConfig.Layer[] layers() {
        return config.layers();
    }

    private void state(String text) {
        listener.onState(text);
    }

    private final class Link {
        private final SceneConfig.Layer layer;
        private volatile Socket socket;
        private OutputStream out;
        private Bitmap bitmap;
        private byte[] rgba;
        private String shown;
        private SceneConfig drawn;
        private int sequence;
        private volatile int frames;
        private volatile Thread ticker;
        /** Закрыто нами: обрыв этого сокета — не потеря связи. */
        private volatile boolean closed;

        Link(SceneConfig.Layer layer) {
            this.layer = layer;
        }

        /**
         * Подключение и HELLO с повтором: демон ещё не слушает или ещё держит
         * прежнюю сессию (ERR_BUSY, пока не заметит закрытие сокета или watchdog).
         */
        void open() throws IOException, InterruptedException {
            long deadline = android.os.SystemClock.elapsedRealtime() + 10000;
            byte[][] payload = new byte[1][];
            DataInputStream in;
            while (true) {
                checkRunning();
                state(layer.label() + ": подключение " + config.host + ":" + layer.port() + "…");
                Socket s = new Socket();
                socket = s;
                IOException failure;
                boolean retry = true;
                try {
                    checkRunning();
                    s.connect(new InetSocketAddress(config.host, layer.port()), 3000);
                    s.setTcpNoDelay(true);
                    s.setSoTimeout(5000);
                    out = s.getOutputStream();
                    in = new DataInputStream(s.getInputStream());
                    send(Gcp.T_HELLO, 0, Gcp.hello("AtlasDim " + layer.id(),
                            layer.x, layer.y, layer.w, layer.h, 2));
                    int type = read(in, payload);
                    if (type == Gcp.T_HELLO_ACK) {
                        break;
                    }
                    failure = new IOException(type == Gcp.T_ERROR
                            ? Gcp.describeError(payload[0]) : "нет HELLO_ACK");
                    // Отказ, кроме «занято», повтором не исправить.
                    retry = type == Gcp.T_ERROR && Gcp.errorCode(payload[0]) == Gcp.ERR_BUSY;
                } catch (IOException e) {
                    failure = e;
                }
                try {
                    s.close();
                } catch (IOException ignored) {
                }
                if (!retry || !running || android.os.SystemClock.elapsedRealtime() >= deadline) {
                    throw new IOException(layer.label() + ": " + failure.getMessage(), failure);
                }
                Thread.sleep(500);
            }
            checkRunning();
            DataInputStream reader = in;
            Log.i(TAG, layer.id() + ": HELLO_ACK dpy=" + layer.display + " z=" + layer.z
                    + " " + layer.w + "x" + layer.h + " @" + layer.x + "," + layer.y);
            new Thread(() -> {
                try {
                    while (running && !closed) {
                        int reply = read(reader, payload);
                        if (reply == Gcp.T_ERROR || reply == Gcp.T_BYE) {
                            throw new IOException(reply == Gcp.T_ERROR
                                    ? Gcp.describeError(payload[0]) : "демон закрыл окно");
                        }
                    }
                } catch (IOException | IllegalArgumentException e) {
                    if (running && !closed) {
                        lostReason = layer.label() + ": связь потеряна — " + e.getMessage();
                        stop();
                    }
                }
            }, "atlasdim-" + layer.id() + "-read").start();
            bitmap = Bitmap.createBitmap(layer.w, layer.h, Bitmap.Config.ARGB_8888);
            rgba = new byte[layer.w * layer.h * 4];
            // Каждое окно посылает heartbeat уже во время подключения следующего.
            ticker = new Thread(() -> {
                try {
                    while (running && !closed) {
                        tick(config);
                        Thread.sleep(500);
                    }
                } catch (InterruptedException ignored) {
                } catch (IOException | RuntimeException e) {
                    if (running && !closed) {
                        lostReason = layer.label() + ": ошибка отправки — " + e.getMessage();
                        stop();
                    }
                } finally {
                    bitmap.recycle();
                }
            }, "atlasdim-" + layer.id() + "-send");
            ticker.start();
        }

        void tick(SceneConfig config) throws IOException {
            String text = SceneRenderer.text(config, layer);
            String content = layer.background ? "background" : text;
            if (!content.equals(shown) || config != drawn) {
                bitmap.eraseColor(Color.TRANSPARENT);
                SceneRenderer.draw(new Canvas(bitmap), config, layer, text);
                bitmap.copyPixelsToBuffer(ByteBuffer.wrap(rgba));
                send(Gcp.T_FRAME, Gcp.FRAME_LAST, Gcp.frame(rgba, layer.w, layer.h));
                shown = content;
                drawn = config;
                frames++;
            } else {
                send(Gcp.T_PING, 0, new byte[0]);
            }
        }

        void send(int type, int flags, byte[] payload) throws IOException {
            out.write(Gcp.message(type, flags, ++sequence, payload));
            out.flush();
        }

        void close() {
            closed = true;
            Socket s = socket;
            if (s != null) {
                try {
                    s.close();
                } catch (IOException ignored) {
                }
            }
            Thread sender = ticker;
            if (sender != null) {
                sender.interrupt();
            }
        }
    }

    private static int read(DataInputStream in, byte[][] payload) throws IOException {
        byte[] bytes = new byte[Gcp.HEADER_SIZE];
        in.readFully(bytes);
        Gcp.Header header = new Gcp.Header(bytes);
        // Ответы демона малы (ACK/PONG/ERROR); не выделяем память по произвольной длине.
        if (header.length < 0 || header.length > 4096) {
            throw new IOException("неверная длина ответа gcp/1: " + header.length);
        }
        payload[0] = new byte[header.length];
        in.readFully(payload[0]);
        if (Gcp.crc(payload[0], 0, header.length)
                != ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(16)) {
            throw new IOException("неверный CRC ответа gcp/1");
        }
        return header.type;
    }
}
