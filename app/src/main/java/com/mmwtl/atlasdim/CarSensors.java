package com.mmwtl.atlasdim;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Датчики автомобиля: топливо и температуры в салоне и за бортом. Источник — GInputBridge
 * (ГИБ, {@code com.salat.gbinder}) или, если выбрано «без GInputBridge», ECarX API ГУ
 * напрямую ({@link EcarxSensors}); без ECarX API прямой режим откатывается на мост.
 * Контракт моста — по образцу AtlasClimateWidget ({@code Gib.java}, {@code ClimateService},
 * id из {@code Hvac.java}): запросы — явные broadcast в пакет моста по одному id, ответы и
 * изменения — неявные broadcast с extras id/value, их получает только динамически
 * зарегистрированный приёмник. Значения общие на процесс: их читают и кадры для QNX,
 * и предпросмотр.
 */
final class CarSensors {
    private static final String TAG = "AtlasDim";
    static final String PACKAGE = "com.salat.gbinder";
    static final String GET_FLOAT_SENSOR = PACKAGE + ".GET_FLOAT_SENSOR";
    static final String LISTEN_SENSOR_CHANGES = PACKAGE + ".LISTEN_SENSOR_CHANGES";
    static final String SENSOR_FLOAT_RESULT = PACKAGE + ".SENSOR_FLOAT_RESULT";
    static final String SENSOR_FLOAT_CHANGED = PACKAGE + ".SENSOR_FLOAT_CHANGED";
    static final String EXTRA_ID = "id";
    static final String EXTRA_VALUE = "value";
    /** Fuel Percentage, 0–100 (AtlasClimateWidget {@code Hvac.SENSOR_FUEL_PERCENTAGE}). */
    static final int SENSOR_FUEL_PERCENTAGE = 4211968;
    /** Температура за бортом, °C ({@code Hvac.SENSOR_TEMPERATURE_AMBIENT}). */
    static final int SENSOR_TEMPERATURE_AMBIENT = 1051392;
    /** Температура в салоне, °C ({@code Hvac.SENSOR_TEMPERATURE_INDOOR}). */
    static final int SENSOR_TEMPERATURE_INDOOR = 1051648;
    private static final int[] SENSORS = {SENSOR_FUEL_PERCENTAGE, SENSOR_TEMPERATURE_INDOOR,
            SENSOR_TEMPERATURE_AMBIENT};
    /** Повтор подписки и запроса: мост мог стартовать позже нас (после загрузки ГУ). */
    private static final long POLL_MS = 30_000;
    /** Без ответа дольше — показание считается устаревшим. */
    private static final long STALE_MS = 5 * 60_000;

    /** id датчика → {значение, elapsedRealtime ответа}. */
    private static final ConcurrentHashMap<Integer, double[]> READINGS = new ConcurrentHashMap<>();

    enum Source { BRIDGE, DIRECT, DIRECT_MISSING }

    /** Откуда идут показания у последнего запущенного экземпляра; null — ещё не запускали. */
    private static volatile Source source;
    /** Режим, в котором получены показания в {@link #READINGS}. */
    private static volatile Boolean readingsDirect;

    private final Context context;
    final boolean direct;
    private EcarxSensors ecarx;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean registered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            accept(intent);
        }
    };

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            request();
            handler.postDelayed(this, POLL_MS);
        }
    };

    CarSensors(Context context, boolean direct) {
        this.context = context.getApplicationContext();
        this.direct = direct;
    }

    static Source source() {
        return source;
    }

    static void put(int id, double value) {
        READINGS.put(id, new double[] {value, SystemClock.elapsedRealtime()});
    }

    /** Последнее свежее показание датчика или null. */
    static Double value(int id) {
        double[] reading = READINGS.get(id);
        return reading != null && SystemClock.elapsedRealtime() - (long) reading[1] <= STALE_MS
                ? reading[0] : null;
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag") // targetSdk 30: флаг экспорта не нужен.
    void start() {
        if (registered || ecarx != null) {
            return;
        }
        // Показания другого источника не должны сойти за ответ этого.
        if (readingsDirect != null && readingsDirect != direct) {
            READINGS.clear();
        }
        readingsDirect = direct;
        if (direct) {
            ecarx = EcarxSensors.connect(context, SENSORS);
            if (ecarx != null) {
                source = Source.DIRECT;
                ecarx.start();
                return;
            }
            Log.w(TAG, "sensors: прямой режим без ECarX API — через GInputBridge");
        }
        source = direct ? Source.DIRECT_MISSING : Source.BRIDGE;
        IntentFilter filter = new IntentFilter();
        filter.addAction(SENSOR_FLOAT_RESULT);
        filter.addAction(SENSOR_FLOAT_CHANGED);
        try {
            context.registerReceiver(receiver, filter);
            registered = true;
        } catch (RuntimeException e) {
            Log.w(TAG, "gib: приёмник ГИБ не зарегистрирован", e);
            return;
        }
        poll.run();
    }

    void stop() {
        if (ecarx != null) {
            ecarx.stop();
            ecarx = null;
        }
        handler.removeCallbacks(poll);
        if (registered) {
            try {
                context.unregisterReceiver(receiver);
            } catch (IllegalArgumentException ignored) {
            }
            registered = false;
        }
    }

    private void request() {
        for (int id : SENSORS) {
            send(new Intent(LISTEN_SENSOR_CHANGES), id);
            send(new Intent(GET_FLOAT_SENSOR), id);
        }
    }

    private void send(Intent intent, int id) {
        try {
            context.sendBroadcast(intent.setPackage(PACKAGE).putExtra(EXTRA_ID, id));
        } catch (RuntimeException e) {
            Log.w(TAG, "gib: не отправлен " + intent.getAction(), e);
        }
    }

    static void accept(Intent intent) {
        if (!SENSOR_FLOAT_RESULT.equals(intent.getAction())
                && !SENSOR_FLOAT_CHANGED.equals(intent.getAction())) {
            return;
        }
        Double id = number(extra(intent, EXTRA_ID));
        Double reading = number(extra(intent, EXTRA_VALUE));
        if (id == null || reading == null || !known(id)) {
            return;
        }
        put((int) (double) id, reading);
    }

    private static boolean known(double id) {
        for (int sensor : SENSORS) {
            if (id == sensor) {
                return true;
            }
        }
        return false;
    }

    /** Мост шлёт extras строками, старые сборки — числами. */
    @SuppressWarnings("deprecation")
    private static String extra(Intent intent, String name) {
        try {
            Bundle extras = intent.getExtras();
            Object raw = extras == null ? null : extras.get(name);
            return raw == null ? null : raw.toString().trim();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Double number(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(raw);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
