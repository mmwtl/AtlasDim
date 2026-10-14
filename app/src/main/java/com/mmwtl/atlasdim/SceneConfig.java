package com.mmwtl.atlasdim;

import android.os.Build;

import org.json.JSONException;
import org.json.JSONObject;

/** Настройки одной композиции. При запуске сессия получает отдельную копию. */
final class SceneConfig {
    static final int WIDTH = 1920;
    static final int HEIGHT = 532;
    /**
     * G426_J1 (смежная модель): приборы и фон — одно окно на dpy-2 с z=3, фон под
     * приборами невозможен; на dpy-1 второе окно ламп с z=9. RESEARCH.md п. 9.
     */
    static final boolean SINGLE_LAYER = "G426_J1".equals(Build.MODEL);
    /** z контрольных ламп EarlyTT на dpy-1: наши окна всегда ниже. */
    static final int LAMPS_Z = SINGLE_LAYER ? 9 : 10;
    /** z штатного слоя приборов cluster-hmi на dpy-1: фон всегда ниже. */
    static final int CLUSTER_Z = 3;
    // Штатные для наших машин; пароль приходит из local.properties при сборке.
    String host = BuildConfig.QNX_HOST;
    String password = BuildConfig.QNX_PASSWORD;
    final Layer clock = new Layer(Kind.CLOCK);
    final Layer fuel = new Layer(Kind.FUEL);
    final Layer background = new Layer(Kind.BACKGROUND);
    boolean seconds;
    boolean plate = true;
    boolean grid = true;
    int color = 0xff10243a;
    int alpha = 255;
    /** Заливка фона: id пресета из {@link Backgrounds} или "file:" + путь к картинке. */
    String fill = Backgrounds.COLOR;
    /** Топливо: показывать залитые и свободные литры, подложка, формула датчика. */
    boolean fuelLiters = true;
    boolean fuelFree = true;
    boolean fuelPlate = true;
    float fuelMultiplier = Fuel.MULTIPLIER;
    float fuelOffset = Fuel.OFFSET;

    enum Kind { CLOCK, FUEL, BACKGROUND }

    static final class Layer {
        final Kind kind;
        final boolean background;
        boolean enabled;
        int display;
        int z;
        int x;
        int y;
        int w;
        int h;

        // Умолчания при установке — раскладка, проверенная на ГУ: часы в левом нижнем углу
        // приборов, фон выключен. Место топлива на ГУ ещё не проверено — выключено.
        Layer(Kind kind) {
            this.kind = kind;
            background = kind == Kind.BACKGROUND;
            enabled = kind == Kind.CLOCK;
            display = background ? 2 : 1;
            z = background ? 0 : kind == Kind.CLOCK ? LAMPS_Z - 1 : LAMPS_Z - 2;
            x = kind == Kind.FUEL ? WIDTH - 300 : 0;
            y = background ? 0 : 482;
            w = background ? WIDTH : kind == Kind.CLOCK ? 100 : 300;
            h = background ? HEIGHT : 50;
        }

        // 4921x занят ANHUD (его демон слушает 49210) — берём 5021x.
        int port() {
            return background ? 50211 : kind == Kind.CLOCK ? 50210 : 50212;
        }

        String id() {
            return background ? "background" : kind == Kind.CLOCK ? "clock" : "fuel";
        }

        String label() {
            return background ? "Фон" : kind == Kind.CLOCK ? "Часы" : "Топливо";
        }

        void copyFrom(Layer other) {
            enabled = other.enabled;
            display = other.display;
            z = other.z;
            x = other.x;
            y = other.y;
            w = other.w;
            h = other.h;
        }

        void validate() {
            if (!enabled) {
                return;
            }
            String prefix = label() + ": ";
            if (display < 1 || display > 2 || z < -100 || z > 100) {
                throw new IllegalArgumentException(prefix + "дисплей 1…2, z −100…100");
            }
            if (w <= 0 || w > WIDTH || h <= 0 || h > HEIGHT
                    || x < 0 || x > WIDTH - w || y < 0 || y > HEIGHT - h) {
                throw new IllegalArgumentException(prefix + "окно должно помещаться в 1920×532");
            }
            if (display == 1 && z >= LAMPS_Z) {
                throw new IllegalArgumentException(prefix + "на dpy-1 z должен быть ниже "
                        + LAMPS_Z + " (лампы)");
            }
            if (background && SINGLE_LAYER) {
                throw new IllegalArgumentException("Фон на " + Build.MODEL
                        + " недоступен: приборы и фон штатно рисуются одним слоем");
            }
            if (background && display == 1 && z >= CLUSTER_Z) {
                throw new IllegalArgumentException("Фон на dpy-1: z < 3, чтобы не закрыть штатные приборы");
            }
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("enabled", enabled).put("display", display).put("z", z)
                    .put("x", x).put("y", y).put("w", w).put("h", h);
        }

        void fromJson(JSONObject json) {
            enabled = json.optBoolean("enabled", enabled);
            display = json.optInt("display", display);
            z = json.optInt("z", z);
            x = json.optInt("x", x);
            y = json.optInt("y", y);
            w = json.optInt("w", w);
            h = json.optInt("h", h);
        }
    }

    SceneConfig() {
    }

    SceneConfig(SceneConfig other) {
        host = other.host;
        password = other.password;
        for (Kind kind : Kind.values()) {
            layer(kind).copyFrom(other.layer(kind));
        }
        seconds = other.seconds;
        plate = other.plate;
        grid = other.grid;
        color = other.color;
        alpha = other.alpha;
        fill = other.fill;
        fuelLiters = other.fuelLiters;
        fuelFree = other.fuelFree;
        fuelPlate = other.fuelPlate;
        fuelMultiplier = other.fuelMultiplier;
        fuelOffset = other.fuelOffset;
    }

    Layer layer(Kind kind) {
        return kind == Kind.CLOCK ? clock : kind == Kind.FUEL ? fuel : background;
    }

    /** Снизу вверх по порядку запуска: фон первым, затем окна поверх него. */
    Layer[] layers() {
        return new Layer[] {background, clock, fuel};
    }

    boolean anyEnabled() {
        return clock.enabled || fuel.enabled || background.enabled;
    }

    void validate() {
        Layer[] layers = layers();
        for (Layer layer : layers) {
            layer.validate();
        }
        for (int i = 0; i < layers.length; i++) {
            for (int j = i + 1; j < layers.length; j++) {
                Layer a = layers[i];
                Layer b = layers[j];
                if (a.enabled && b.enabled && a.display == b.display && a.z == b.z) {
                    throw new IllegalArgumentException(a.label() + " и " + b.label()
                            + ": у окон на одном дисплее должны различаться z");
                }
            }
        }
        if (alpha < 0 || alpha > 255) {
            throw new IllegalArgumentException("Прозрачность фона: 0…255");
        }
        if (fuel.enabled && !fuelLiters && !fuelFree) {
            throw new IllegalArgumentException("Топливо: выберите залитые или свободные литры");
        }
        if (fuel.enabled && !Fuel.isValid(fuelMultiplier, fuelOffset)) {
            throw new IllegalArgumentException("Топливо: множитель до ±100, смещение до ±1000");
        }
    }

    /** Применённая композиция: её же поднимает сервис после загрузки ГУ. */
    String toJson() {
        try {
            JSONObject json = new JSONObject().put("host", host).put("password", password)
                    .put("seconds", seconds).put("plate", plate).put("grid", grid)
                    .put("color", color).put("alpha", alpha).put("fill", fill)
                    .put("fuelLiters", fuelLiters).put("fuelFree", fuelFree)
                    .put("fuelPlate", fuelPlate).put("fuelMultiplier", (double) fuelMultiplier)
                    .put("fuelOffset", (double) fuelOffset);
            for (Layer layer : layers()) {
                json.put(layer.id(), layer.toJson());
            }
            return json.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** z на dpy-1 не выше ламп этой модели; иначе — значение по умолчанию. */
    static int allowedZ(Layer layer) {
        return layer.display == 1 && layer.z >= LAMPS_Z ? new Layer(layer.kind).z : layer.z;
    }

    static SceneConfig fromJson(String text) throws JSONException {
        JSONObject json = new JSONObject(text);
        SceneConfig config = new SceneConfig();
        config.host = json.optString("host", config.host);
        config.password = json.optString("password", config.password);
        config.seconds = json.optBoolean("seconds", config.seconds);
        config.plate = json.optBoolean("plate", config.plate);
        config.grid = json.optBoolean("grid", config.grid);
        config.color = json.optInt("color", config.color);
        config.alpha = json.optInt("alpha", config.alpha);
        config.fill = json.optString("fill", config.fill);
        config.fuelLiters = json.optBoolean("fuelLiters", config.fuelLiters);
        config.fuelFree = json.optBoolean("fuelFree", config.fuelFree);
        config.fuelPlate = json.optBoolean("fuelPlate", config.fuelPlate);
        config.fuelMultiplier = (float) json.optDouble("fuelMultiplier", config.fuelMultiplier);
        config.fuelOffset = (float) json.optDouble("fuelOffset", config.fuelOffset);
        for (Layer layer : config.layers()) {
            JSONObject item = json.optJSONObject(layer.id());
            if (item != null) {
                layer.fromJson(item);
            }
        }
        // Композиция, сохранённая до этих проверок, не должна ломать возврат после загрузки.
        if (SINGLE_LAYER) {
            config.background.enabled = false;
            config.clock.z = allowedZ(config.clock);
            config.fuel.z = allowedZ(config.fuel);
        }
        return config;
    }
}
