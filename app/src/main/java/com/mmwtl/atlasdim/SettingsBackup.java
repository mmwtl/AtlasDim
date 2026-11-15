package com.mmwtl.atlasdim;

import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Все настройки экрана одним JSON. Обёртка (format, schemaVersion, appVersion) — как у
 * AtlasClimateWidget и AtlasAppWidget; внутри — черновик из prefs как есть, с типами JSON.
 * Не переносятся: применённая композиция и её состояние, пароль QNX, модель ГУ и снятие
 * ограничений — они относятся к конкретной машине.
 */
final class SettingsBackup {
    static final String FORMAT = "atlas-dim-settings";
    static final int SCHEMA_VERSION = 1;
    static final String MIME = "application/json";
    static final String PREFIX = "atlasdim-settings-";
    static final int MAX_BYTES = 256 * 1024;
    private static final Set<String> LOCAL = new HashSet<>(Arrays.asList(
            SceneService.KEY_SCENE, SceneService.KEY_ACTIVE, "password", Vehicle.KEY,
            "unrestricted", "tab"));
    private static final String[] LAYERS = {"clock", "fuel", "temp", "background"};

    /** Модель ГУ, с которой сделан файл: раскладка слоёв переносится только на такую же. */
    final String vehicle;
    final JSONObject settings;

    private SettingsBackup(String vehicle, JSONObject settings) {
        this.vehicle = vehicle;
        this.settings = settings;
    }

    static String export(SharedPreferences prefs, String appVersion) {
        try {
            JSONObject settings = new JSONObject();
            for (Map.Entry<String, ?> entry : new TreeMap<>(prefs.getAll()).entrySet()) {
                Object value = entry.getValue();
                if (!LOCAL.contains(entry.getKey()) && (value instanceof Boolean
                        || value instanceof Integer || value instanceof String)) {
                    settings.put(entry.getKey(), value);
                }
            }
            return new JSONObject().put("format", FORMAT).put("schemaVersion", SCHEMA_VERSION)
                    .put("appVersion", appVersion).put("vehicle", Vehicle.current())
                    .put("settings", settings).toString(2);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Файл настроек или null, если это не наш JSON. */
    static SettingsBackup parse(String text) {
        try {
            JSONObject json = new JSONObject(text.trim());
            JSONObject settings = json.optJSONObject("settings");
            if (!FORMAT.equals(json.optString("format")) || settings == null) {
                return null;
            }
            return new SettingsBackup(Vehicle.valid(json.optString("vehicle")), settings);
        } catch (JSONException e) {
            return null;
        }
    }

    int size() {
        return settings.length();
    }

    /** Экран и z слоёв другой модели ГУ здесь недопустимы — тогда по умолчанию этой модели. */
    boolean layoutKept() {
        return vehicle.equals(Vehicle.current());
    }

    /** Заменяет переносимые настройки; локальные ключи файла и неизвестные типы пропускаются. */
    void apply(SharedPreferences prefs) {
        SharedPreferences.Editor edit = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (!LOCAL.contains(key)) {
                edit.remove(key);
            }
        }
        Iterator<String> keys = settings.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = settings.opt(key);
            if (LOCAL.contains(key) || !layoutKept() && placement(key)) {
                continue;
            }
            if (value instanceof Boolean) {
                edit.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                edit.putInt(key, (Integer) value);
            } else if (value instanceof String) {
                edit.putString(key, (String) value);
            }
        }
        edit.commit();
    }

    private static boolean placement(String key) {
        for (String layer : LAYERS) {
            if (key.equals(layer + ".display") || key.equals(layer + ".z")) {
                return true;
            }
        }
        return false;
    }
}
