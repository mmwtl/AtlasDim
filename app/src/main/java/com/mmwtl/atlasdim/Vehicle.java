package com.mmwtl.atlasdim;

import android.content.Context;
import android.os.Build;

/**
 * Модель ГУ: от неё зависят раскладка слоёв приборки (RESEARCH.md п. 9) и смещения
 * шрифтов в resource.kzb (п. 10). По умолчанию — по {@code Build.MODEL}, пользователь
 * может выбрать явно на вкладке «Система».
 */
final class Vehicle {
    /** Основная ГУ G636: приборы и фон — два слоя, лампы на dpy-1 с z=10. */
    static final String G636 = "g636";
    /** Ситирей G426_J1: приборы и фон — одно окно на dpy-2 с z=3, лампы с z=9. */
    static final String CITYRAY = "cityray";
    static final String KEY = "vehicle";
    static final String[] ALL = {G636, CITYRAY};

    private static volatile String current = detect();

    private Vehicle() {
    }

    /** Модель этой ГУ по прошивке. */
    static String detect() {
        return "G426_J1".equals(Build.MODEL) ? CITYRAY : G636;
    }

    /** Известная модель или та, что определена по прошивке. */
    static String valid(String value) {
        return G636.equals(value) || CITYRAY.equals(value) ? value : detect();
    }

    static synchronized void init(Context context) {
        current = valid(SceneService.prefs(context).getString(KEY, null));
    }

    static String current() {
        return current;
    }

    static void set(Context context, String value) {
        current = valid(value);
        SceneService.prefs(context).edit().putString(KEY, current).commit();
    }

    static boolean cityray(String value) {
        return CITYRAY.equals(value);
    }

    static String label(String value) {
        return cityray(value) ? "Cityray (G426_J1)" : "G636";
    }
}
