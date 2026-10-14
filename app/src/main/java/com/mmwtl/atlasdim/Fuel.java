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

    final double liters;
    final double freeLiters;
    final double capacityLiters;
    /** Знаков после запятой в показаниях, 0…{@link #DECIMALS}. */
    final int digits;
    /** Датчик на нуле: {@link #liters} — верхняя граница, свободный объём — нижняя. */
    final boolean atFloor;

    private Fuel(double liters, double capacityLiters, int digits, boolean atFloor) {
        this.liters = liters;
        this.capacityLiters = capacityLiters;
        this.freeLiters = round(capacityLiters - liters, digits);
        this.digits = digits;
        this.atFloor = atFloor;
    }

    /** null — нет свежего показания или формула непригодна; литры округлены до digits. */
    static Fuel of(Double sensorValue, double multiplier, double offset, int digits) {
        if (sensorValue == null || !Double.isFinite(sensorValue)
                || !isValid(multiplier, offset) || digits < 0 || digits > DECIMALS) {
            return null;
        }
        double capacity = round(capacityLiters(multiplier, offset), digits);
        double value = Math.max(API_MIN, Math.min(API_MAX, sensorValue));
        double liters = Math.max(0, Math.min(capacity, round(value * multiplier + offset, digits)));
        return new Fuel(liters, capacity, digits, value <= API_MIN);
    }

    private static double round(double value, int digits) {
        double scale = Math.pow(10, digits);
        return Math.round(value * scale) / scale;
    }

    static boolean isValid(double multiplier, double offset) {
        return Double.isFinite(multiplier) && Double.isFinite(offset)
                && Math.abs(multiplier) <= MULTIPLIER_LIMIT && Math.abs(offset) <= OFFSET_LIMIT;
    }

    /** Больший конец формулы на диапазоне датчика, не меньше нуля. */
    static double capacityLiters(double multiplier, double offset) {
        double atMin = API_MIN * multiplier + offset;
        double atMax = API_MAX * multiplier + offset;
        return Math.max(0, Math.max(atMin, atMax));
    }

    /** Коэффициент без хвостовых нулей: 0.5, 4, -1.25, 0.123456. */
    static String format(double value) {
        if (value == Math.rint(value)) {
            return Long.toString(Math.round(value));
        }
        String text = String.format(java.util.Locale.US, "%." + DECIMALS + "f", value);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /** Показание с заданным числом знаков; дробная часть через запятую. */
    private String text(double value) {
        return String.format(new java.util.Locale("ru"), "%." + digits + "f", value);
    }

    String litersText() {
        return (atFloor ? "<" : "") + text(liters);
    }

    String freeText() {
        return (atFloor ? ">" : "") + text(freeLiters);
    }
}
