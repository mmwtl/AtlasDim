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

/**
 * Датчик топлива через GInputBridge (ГИБ, {@code com.salat.gbinder}). Контракт и подписка —
 * по образцу AtlasClimateWidget ({@code Gib.java}, {@code ClimateService}): запросы — явные
 * broadcast в пакет моста, ответы и изменения — неявные broadcast с extras id/value, их
 * получает только динамически зарегистрированный приёмник. Значение общее на процесс:
 * его читают и кадры для QNX, и предпросмотр.
 */
final class FuelSensor {
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
    /** Повтор подписки и запроса: мост мог стартовать позже нас (после загрузки ГУ). */
    private static final long POLL_MS = 30_000;
    /** Без ответа дольше — показание считается устаревшим. */
    private static final long STALE_MS = 5 * 60_000;

    private static volatile Double value;
    private static volatile long at;

    private final Context context;
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

    FuelSensor(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Последнее свежее показание 0–100 или null. */
    static Double value() {
        Double current = value;
        return current != null && SystemClock.elapsedRealtime() - at <= STALE_MS ? current : null;
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag") // targetSdk 30: флаг экспорта не нужен.
    void start() {
        if (registered) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(SENSOR_FLOAT_RESULT);
        filter.addAction(SENSOR_FLOAT_CHANGED);
        try {
            context.registerReceiver(receiver, filter);
            registered = true;
        } catch (RuntimeException e) {
            Log.w(TAG, "fuel: приёмник ГИБ не зарегистрирован", e);
            return;
        }
        poll.run();
    }

    void stop() {
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
        send(new Intent(LISTEN_SENSOR_CHANGES));
        send(new Intent(GET_FLOAT_SENSOR));
    }

    private void send(Intent intent) {
        try {
            context.sendBroadcast(intent.setPackage(PACKAGE)
                    .putExtra(EXTRA_ID, SENSOR_FUEL_PERCENTAGE));
        } catch (RuntimeException e) {
            Log.w(TAG, "fuel: не отправлен " + intent.getAction(), e);
        }
    }

    static void accept(Intent intent) {
        if (!SENSOR_FLOAT_RESULT.equals(intent.getAction())
                && !SENSOR_FLOAT_CHANGED.equals(intent.getAction())) {
            return;
        }
        Double id = number(extra(intent, EXTRA_ID));
        Double reading = number(extra(intent, EXTRA_VALUE));
        if (id == null || id != SENSOR_FUEL_PERCENTAGE || reading == null) {
            return;
        }
        value = reading;
        at = SystemClock.elapsedRealtime();
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
