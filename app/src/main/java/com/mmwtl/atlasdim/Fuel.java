package com.mmwtl.atlasdim;

/**
 * Литры в баке по датчику Fuel Percentage из GInputBridge. Скопировано из
 * AtlasClimateWidget ({@code Fuel.java}, как в AtlasAppWidget):
 * {@code литры = значение × множитель + смещение}. Датчик отдаёт 0–100, объём бака —
 * больший конец формулы (54 л по умолчанию); на 0 реальный уровень где-то ниже смещения,
 * поэтому значения становятся границами ({@code <4}/{@code >50}).
 */
final class Fuel {
    static final double MULTIPLIER = 0.5;
    static final double OFFSET = 4;
    static final double API_MIN = 0;
    static final double API_MAX = 100;
    /** Пределы своей формулы — те же, что в AtlasAppWidget. */
    static final double MULTIPLIER_LIMIT = 100;
    static final double OFFSET_LIMIT = 1_000;
    /** Знаков после запятой в множителе и смещении. */
    static final int DECIMALS = 6;

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
    static Fuel of(Double sensorValue, double multiplier, double offset) {
        if (sensorValue == null || !Double.isFinite(sensorValue)
                || !isValid(multiplier, offset)) {
            return null;
        }
        int capacity = capacityLiters(multiplier, offset);
        double value = Math.max(API_MIN, Math.min(API_MAX, sensorValue));
        int liters = (int) Math.max(0, Math.min(capacity, Math.round(value * multiplier + offset)));
        return new Fuel(liters, capacity, value <= API_MIN);
    }

    static boolean isValid(double multiplier, double offset) {
        return Double.isFinite(multiplier) && Double.isFinite(offset)
                && Math.abs(multiplier) <= MULTIPLIER_LIMIT && Math.abs(offset) <= OFFSET_LIMIT;
    }

    /** Больший конец формулы на диапазоне датчика, не меньше нуля. */
    static int capacityLiters(double multiplier, double offset) {
        double atMin = API_MIN * multiplier + offset;
        double atMax = API_MAX * multiplier + offset;
        return (int) Math.max(0, Math.round(Math.max(atMin, atMax)));
    }

    /** Коэффициент без хвостовых нулей: 0.5, 4, -1.25, 0.123456. */
    static String format(double value) {
        if (value == Math.rint(value)) {
            return Long.toString(Math.round(value));
        }
        String text = String.format(java.util.Locale.US, "%." + DECIMALS + "f", value);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    String litersText() {
        return (atFloor ? "<" : "") + liters;
    }

    String freeText() {
        return (atFloor ? ">" : "") + freeLiters;
    }
}
