package com.mmwtl.atlasdim;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

/**
 * Датчики напрямую через ECarX API ГУ ({@code com.ecarx.xui.adaptapi}) — тот же API, что
 * вызывают GInputBridge и FX11: {@code Car.create(context)}, затем {@code ISensor}. API живёт
 * во фреймворке ГУ, поэтому вызывается через рефлексию, в APK ничего из него нет; вне ГУ
 * {@link #connect} возвращает null. Сенсорная часть перенесена из AtlasClimateWidget
 * ({@code Ecarx.java}, режим «Работать без GInputBridge»): слушатель регистрируется только
 * после удачного чтения (зарегистрированный до готовности сервиса автомобиля может молчать)
 * и заново — после сбоя чтения. Вызовы идут в своём потоке, показания — в {@link CarSensors}.
 */
final class EcarxSensors {
    private static final String TAG = "AtlasDim";
    private static final String PACKAGE = "com.ecarx.xui.adaptapi";
    private static final String CAR = PACKAGE + ".car.Car";
    private static final String CAR_INTERFACE = PACKAGE + ".car.ICar";
    private static final String SENSOR = PACKAGE + ".car.sensor.ISensor";
    private static final String LISTENER = SENSOR + "$ISensorListener";
    /** Повтор чтения: сервис автомобиля мог подняться позже нас. */
    private static final long POLL_MS = 30_000;
    private static final long RETRY_MS = 3_000;

    private final Context context;
    private final int[] ids;
    private final Method create;
    private final Method getSensorManager;
    private final Method getSensor;
    private final Method registerListener;
    private final Method unregisterListener;
    private final Object listener;
    private final HandlerThread thread = new HandlerThread("atlasdim-ecarx");
    private Handler worker;

    /** Только в потоке {@link #worker}. */
    private Object car;
    private Object sensors;
    private boolean answered;
    private final Set<Integer> registered = new HashSet<>();

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            boolean ok = readAll();
            worker.postDelayed(this, ok ? POLL_MS : RETRY_MS);
        }
    };

    private EcarxSensors(Context context, int[] ids) throws ReflectiveOperationException {
        this.context = context.getApplicationContext();
        this.ids = ids.clone();
        Class<?> carClass = Class.forName(CAR);
        Class<?> carInterface = Class.forName(CAR_INTERFACE);
        Class<?> sensor = Class.forName(SENSOR);
        Class<?> listenerClass = Class.forName(LISTENER);
        create = carClass.getMethod("create", Context.class);
        getSensorManager = carInterface.getMethod("getSensorManager");
        getSensor = sensor.getMethod("getSensorLatestValue", int.class);
        registerListener = sensor.getMethod("registerListener", listenerClass, int.class);
        unregisterListener = sensor.getMethod("unregisterListener", listenerClass);
        listener = proxy(listenerClass);
    }

    /** null — на этой ГУ (или эмуляторе) нет ECarX API. */
    static EcarxSensors connect(Context context, int[] ids) {
        try {
            return new EcarxSensors(context, ids);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            Log.w(TAG, "ecarx: API недоступен — " + e); // Вне ГУ это норма, стек не нужен.
            return null;
        }
    }

    void start() {
        thread.start();
        worker = new Handler(thread.getLooper());
        worker.post(poll);
    }

    void stop() {
        if (worker == null) {
            return;
        }
        worker.removeCallbacks(poll);
        worker.post(() -> {
            if (sensors != null && !registered.isEmpty()) {
                call(unregisterListener, sensors, listener);
            }
            registered.clear();
            thread.quitSafely();
        });
    }

    /** Читает все датчики и подписывается на те, что ответили. false — автомобиль не готов. */
    private boolean readAll() {
        if (!ensure()) {
            return false;
        }
        for (int id : ids) {
            Object value;
            try {
                value = read(getSensor, sensors, id);
            } catch (UnsupportedOperationException unsupported) {
                answered = true; // Автомобиль ответил, что такого датчика нет.
                continue;
            } catch (ReflectiveOperationException | RuntimeException e) {
                lost(e);
                return false;
            }
            if (value instanceof Float && Float.isFinite((Float) value)) {
                answered = true;
                CarSensors.put(id, (Float) value);
            }
        }
        if (answered) {
            for (int id : ids) {
                if (!registered.contains(id)
                        && Boolean.TRUE.equals(call(registerListener, sensors, listener, id))) {
                    registered.add(id);
                }
            }
        }
        return answered;
    }

    /** Автомобиль и менеджер датчиков появляются, только когда поднят сервис автомобиля. */
    private boolean ensure() {
        try {
            if (car == null) {
                car = create.invoke(null, context);
            }
            if (car != null && sensors == null) {
                sensors = getSensorManager.invoke(car);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(TAG, "ecarx: автомобиль не готов", e);
        }
        return sensors != null;
    }

    /** Чтение не удалось: сервис автомобиля пропал, а с ним и подписки. */
    private void lost(Throwable error) {
        if (answered) {
            Log.w(TAG, "ecarx: автомобиль перестал отвечать", error);
        }
        answered = false;
        registered.clear();
    }

    /** Вызов геттера; собственное исключение API пробрасывается как есть. */
    private static Object read(Method method, Object target, Object... args)
            throws ReflectiveOperationException {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof UnsupportedOperationException) {
                throw (UnsupportedOperationException) e.getCause();
            }
            throw e;
        }
    }

    private static Object call(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Throwable cause = e instanceof InvocationTargetException ? e.getCause() : e;
            Log.w(TAG, "ecarx: " + method.getName() + " не удался", cause);
            return null;
        }
    }

    /** Слушатель датчиков; вызывается в binder-потоках. */
    private Object proxy(Class<?> type) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return "AtlasDim " + type.getSimpleName();
                case "onSensorValueChanged":
                    float value = (Float) args[1];
                    if (Float.isFinite(value)) {
                        CarSensors.put((Integer) args[0], value);
                    }
                    return null;
                default:
                    return null;
            }
        };
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }
}
