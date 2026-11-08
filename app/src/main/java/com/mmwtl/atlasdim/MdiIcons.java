package com.mmwtl.atlasdim;

import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Значки температуры из Material Design Icons (Pictogrammers, https://pictogrammers.com,
 * пакет @mdi/svg 7.4.47, Apache License 2.0): {@code car-seat} и
 * {@code weather-partly-cloudy}. Контуры те же, сетка 24×24; S/H/V/A заранее переведены
 * в абсолютные M/L/C/Z, чтобы собрать {@link Path} без парсера SVG.
 */
final class MdiIcons {
    /** mdi car-seat — салон. */
    private static final String CAR_SEAT = "M 7 18 C 7 18 4 10 4 6 C 4 2 6 2 6 2 L 7 2"
            + " C 7 2 8 2 8 3 C 8 4 7 4 7 6 C 7 8 10 10 10 13 C 10 16 7 18 7 18 Z"
            + " M 12 17 C 11 17 8 19.5 8 19.5 C 7.7 19.7 7.8 20 8 20.3 C 8 20.3 9 22.1 11 22.1"
            + " L 17 22.1 C 18.1 22.1 19 21.2 19 20.1 L 19 19.1 C 19 18 18.1 17.1 17 17.1"
            + " L 12 17.1 Z";

    /** mdi weather-partly-cloudy — улица. */
    private static final String PARTLY_CLOUDY = "M 12.74 5.47 C 15.1 6.5 16.35 9.03 15.92 11.46"
            + " C 17.19 12.56 18 14.19 18 16 L 18 16.17 C 18.31 16.06 18.65 16 19 16"
            + " C 20.657 16 22 17.343 22 19 C 22 20.657 20.657 22 19 22 L 6 22"
            + " C 3.791 22 2 20.209 2 18 C 2 15.791 3.791 14 6 14 L 6.27 14"
            + " C 5 12.45 4.6 10.24 5.5 8.26 C 6.72 5.5 9.97 4.24 12.74 5.47 Z"
            + " M 11.93 7.3 C 10.16 6.5 8.09 7.31 7.31 9.07 C 6.85 10.09 6.93 11.22 7.41 12.13"
            + " C 8.5 10.83 10.16 10 12 10 C 12.7 10 13.38 10.12 14 10.34"
            + " C 13.94 9.06 13.18 7.86 11.93 7.3 Z"
            + " M 13.55 3.64 C 13 3.4 12.45 3.23 11.88 3.12 L 14.37 1.82 L 15.27 4.71"
            + " C 14.76 4.29 14.19 3.93 13.55 3.64 Z"
            + " M 6.09 4.44 C 5.6 4.79 5.17 5.19 4.8 5.63 L 4.91 2.82 L 7.87 3.5"
            + " C 7.25 3.71 6.65 4.03 6.09 4.44 Z"
            + " M 18 9.71 C 17.91 9.12 17.78 8.55 17.59 8 L 19.97 9.5 L 17.92 11.73"
            + " C 18.03 11.08 18.05 10.4 18 9.71 Z"
            + " M 3.04 11.3 C 3.11 11.9 3.24 12.47 3.43 13 L 1.06 11.5 L 3.1 9.28"
            + " C 3 9.93 2.97 10.61 3.04 11.3 Z"
            + " M 19 18 L 16 18 L 16 16 C 16 13.791 14.209 12 12 12 C 9.791 12 8 13.791 8 16"
            + " L 6 16 C 4.895 16 4 16.895 4 18 C 4 19.105 4.895 20 6 20 L 19 20"
            + " C 19.552 20 20 19.552 20 19 C 20 18.448 19.552 18 19 18 Z";

    /** Готовые контуры; рисуются через {@link Path#transform(Matrix, Path)} и не меняются. */
    private static final Path CABIN = normalized(CAR_SEAT);
    private static final Path OUTSIDE = normalized(PARTLY_CLOUDY);

    private MdiIcons() {
    }

    static Path cabin() {
        return CABIN;
    }

    static Path outside() {
        return OUTSIDE;
    }

    /** Контур сдвинут в (0, 0) и масштабирован до высоты 1; ширина — {@code bounds.right}. */
    private static Path normalized(String data) {
        Path path = parse(data);
        RectF bounds = new RectF();
        path.computeBounds(bounds, true);
        float scale = 1 / bounds.height();
        Matrix matrix = new Matrix();
        matrix.setTranslate(-bounds.left, -bounds.top);
        matrix.postScale(scale, scale);
        path.transform(matrix);
        return path;
    }

    /** Только абсолютные M, L, C, Z через пробел — так записаны константы выше. */
    static Path parse(String data) {
        String[] t = data.trim().split("\\s+");
        Path path = new Path();
        int i = 0;
        while (i < t.length) {
            switch (t[i++]) {
                case "M":
                    path.moveTo(f(t[i++]), f(t[i++]));
                    break;
                case "L":
                    path.lineTo(f(t[i++]), f(t[i++]));
                    break;
                case "C":
                    path.cubicTo(f(t[i++]), f(t[i++]), f(t[i++]), f(t[i++]), f(t[i++]), f(t[i++]));
                    break;
                case "Z":
                    path.close();
                    break;
                default:
                    throw new IllegalArgumentException("команда пути: " + t[i - 1]);
            }
        }
        return path;
    }

    private static float f(String value) {
        return Float.parseFloat(value);
    }
}
