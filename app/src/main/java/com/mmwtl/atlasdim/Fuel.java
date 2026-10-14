package com.mmwtl.atlasdim;

/**
 * Литры в баке по датчику Fuel Percentage из GInputBridge. Скопировано из
 * AtlasClimateWidget ({@code Fuel.java}, как в AtlasAppWidget):
 * {@code литры = значение × множитель + смещение}. Датчик отдаёт 0–100, объём бака —
 * больший конец формулы (54 л по умолчанию); на 0 реальный уровень где-то ниже смещения,
 * поэтому значения становятся границами ({@code <4}/{@code >50}).
 */
final class Fuel {
    static final float MULTIPLIER = 0.5f;
    static final float OFFSET = 4f;
    static final float API_MIN = 0f;
    static final float API_MAX = 100f;
    /** Пределы своей формулы — те же, что в AtlasAppWidget. */
    static final float MULTIPLIER_LIMIT = 100f;
    static final float OFFSET_LIMIT = 1_000f;

    final int liters;
    final int freeLiters;
    final int capacityLiters;
    /** Датчик на нуле: {@link #liters} — верхняя граница, свободный объём — нижняя. */
    final boolean atFloor;

    private Fuel(int liters, int capacityLiters, boolean atFloor) {
        this.liters = liters;
        this.capacityLiters = capacityLiters;
        this.freeLiters = capacityLiters - liters;
        this.atFloor = atFloor;
    }

    /** null — нет свежего показания или формула непригодна. */
    static Fuel of(Double sensorValue, float multiplier, float offset) {
        if (sensorValue == null || !Double.isFinite(sensorValue)
                || !isValid(multiplier, offset)) {
            return null;
        }
        int capacity = capacityLiters(multiplier, offset);
        float value = Math.max(API_MIN, Math.min(API_MAX, sensorValue.floatValue()));
        int liters = Math.max(0, Math.min(capacity, Math.round(value * multiplier + offset)));
        return new Fuel(liters, capacity, value <= API_MIN);
    }

    static boolean isValid(float multiplier, float offset) {
        return Float.isFinite(multiplier) && Float.isFinite(offset)
                && Math.abs(multiplier) <= MULTIPLIER_LIMIT && Math.abs(offset) <= OFFSET_LIMIT;
    }

    /** Больший конец формулы на диапазоне датчика, не меньше нуля. */
    static int capacityLiters(float multiplier, float offset) {
        float atMin = API_MIN * multiplier + offset;
        float atMax = API_MAX * multiplier + offset;
        return Math.max(0, Math.round(Math.max(atMin, atMax)));
    }

    /** Коэффициент без хвостовых нулей: 0.5, 4, -1.25. */
    static String format(float value) {
        if (value == Math.rint(value)) {
            return Integer.toString(Math.round(value));
        }
        String text = String.format(java.util.Locale.US, "%.3f", value);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    String litersText() {
        return (atFloor ? "<" : "") + liters;
    }

    String freeText() {
        return (atFloor ? ">" : "") + freeLiters;
    }
}
