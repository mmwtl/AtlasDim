package com.mmwtl.atlasdim;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;

import java.util.function.Consumer;

/**
 * Держит композицию на приборке без открытой Activity. Применённые настройки лежат
 * в prefs ({@code scene}, {@code active}); по ним сервис поднимается и после загрузки ГУ.
 * Отказ или потеря связи — повтор с нарастающей паузой, пока композиция нужна; демоны
 * при этом не останавливаются, повтор сначала подключает окна к ним без telnet.
 */
public final class SceneService extends Service {
    private static final String TAG = "AtlasDim";
    static final String ACTION_APPLY = "com.mmwtl.atlasdim.APPLY";
    static final String PREFS = "atlasdim";
    static final String KEY_SCENE = "scene";
    static final String KEY_ACTIVE = "active";
    static final String KEY_BOOT = "boot";
    private static final String CHANNEL = "scene";
    private static final int NOTIFICATION = 1;
    private static final long[] RETRY_MS = {5_000, 10_000, 20_000, 40_000, 60_000};

    private static SceneService instance;
    private static Consumer<String> listener;
    private static String state = "Не подключено";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable retry = this::applySaved;
    private SceneSession session;
    private FuelSensor fuel;
    private int failures;
    /** Сессии, ещё не завершившие очистку (в том числе заменённые). */
    private int running;

    /** Сохраняет применённую композицию и поднимает сервис. Только с главного потока. */
    static void apply(Context context, SceneConfig config) {
        prefs(context).edit().putString(KEY_SCENE, config.toJson())
                .putBoolean(KEY_ACTIVE, true).apply();
        start(context);
    }

    /** Убирает окна и демоны; сервис закончит работу после очистки. Только с главного потока. */
    static void stop(Context context) {
        prefs(context).edit().putBoolean(KEY_ACTIVE, false).apply();
        if (instance != null) {
            instance.halt();
        }
    }

    static void start(Context context) {
        try {
            context.startForegroundService(new Intent(context, SceneService.class)
                    .setAction(ACTION_APPLY));
        } catch (RuntimeException e) {
            Log.w(TAG, "сервис не запущен", e);
        }
    }

    static boolean isRunning() {
        return instance != null;
    }

    /** Композиция включена пользователем (ей место на приборке, даже если связь сейчас рвётся). */
    static boolean isActive(Context context) {
        return prefs(context).getBoolean(KEY_ACTIVE, false);
    }

    /** Получает текущее состояние сразу и все следующие на главном потоке; null — отписка. */
    static void setListener(Consumer<String> next) {
        listener = next;
        if (next != null) {
            next.accept(state);
        }
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        enterForeground();
        main.removeCallbacks(retry);
        failures = 0;
        applySaved();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        instance = null;
        main.removeCallbacks(retry);
        stopFuel();
        if (session != null) {
            session.stop();
            session = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void applySaved() {
        SharedPreferences prefs = prefs(this);
        String json = prefs.getString(KEY_SCENE, null);
        if (!prefs.getBoolean(KEY_ACTIVE, false) || json == null) {
            halt();
            return;
        }
        SceneConfig config;
        try {
            config = SceneConfig.fromJson(json);
            config.validate();
        } catch (JSONException | IllegalArgumentException e) {
            setState("Ошибка: сохранённая композиция не читается — " + e.getMessage());
            prefs.edit().putBoolean(KEY_ACTIVE, false).apply();
            halt();
            return;
        }
        if (config.fuel.enabled) {
            if (fuel == null) {
                fuel = new FuelSensor(this);
                fuel.start();
            }
        } else {
            stopFuel();
        }
        if (session != null) {
            // Только рисунок — кадр уходит в открытые окна, демоны не трогаем.
            if (session.update(config)) {
                setState("Рисунок обновлён без перезапуска");
                return;
            }
            if (session.reconfigure(config)) {
                setState("Меняю набор окон без перезапуска…");
                return;
            }
            SceneSession old = session;
            session = null;
            old.stop(!old.sameQnx(config));
        }
        Watch watch = new Watch();
        session = watch.session = new SceneSession(this, config, watch);
        running++;
        setState("Запускаю композицию…");
        session.start();
    }

    /** События одной сессии; приходят из её потоков и переносятся на главный. */
    private final class Watch implements SceneSession.Listener {
        SceneSession session;

        @Override
        public void onState(String text) {
            main.post(() -> {
                SceneSession current = SceneService.this.session;
                // Итог остановленной пользователем сессии показываем, устаревшей при замене — нет.
                if (current == session || (current == null && !isActive(SceneService.this))) {
                    if (text.startsWith("Отправлено")) {
                        failures = 0;
                    }
                    setState(text);
                }
            });
        }

        @Override
        public void onEnd() {
            main.post(() -> ended(session));
        }
    }

    private void ended(SceneSession ended) {
        running--;
        if (session == ended) {
            session = null;
            if (isActive(this)) {
                long delay = RETRY_MS[Math.min(failures, RETRY_MS.length - 1)];
                failures++;
                setState(state + " · повтор через " + delay / 1000 + " с");
                main.postDelayed(retry, delay);
                return;
            }
        }
        if (session == null && running == 0 && !isActive(this)) {
            stopSelf();
        }
    }

    /** Окна закрыть, демоны остановить; сервис завершится, когда закончится очистка. */
    private void halt() {
        main.removeCallbacks(retry);
        stopFuel();
        if (session != null) {
            session.stop(true);
            session = null;
        } else if (running == 0) {
            setState("Не подключено");
            stopSelf();
        }
    }

    private void stopFuel() {
        if (fuel != null) {
            fuel.stop();
            fuel = null;
        }
    }

    private void setState(String text) {
        state = text;
        Consumer<String> current = listener;
        if (current != null) {
            current.accept(text);
        }
    }

    private void enterForeground() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null && manager.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL,
                    getString(R.string.notification_channel),
                    NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(open)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
        startForeground(NOTIFICATION, notification);
    }
}
