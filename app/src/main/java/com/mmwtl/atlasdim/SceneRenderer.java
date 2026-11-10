package com.mmwtl.atlasdim;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Один и тот же рисунок используется в предпросмотре и в кадрах для QNX. */
final class SceneRenderer {
    /** Разделитель показателей в тексте окна. */
    static final char PARTS = '\n';
    private static final String DOT = "·";

    private SceneRenderer() {
    }

    static String time(SceneConfig config) {
        return new SimpleDateFormat(config.seconds ? "HH:mm:ss" : "HH:mm", Locale.US)
                .format(new Date());
    }

    /**
     * Литры по датчику ГИБ, как строка датчиков AtlasClimateWidget; без показания — «—».
     * Показатели разделены {@link #PARTS}: раскладку и разбежку задаёт {@link #draw}.
     */
    static String fuel(SceneConfig config, Double sensor) {
        Fuel fuel = Fuel.of(sensor, config.fuelMultiplier, config.fuelOffset, config.fuelDigits);
        String liters = config.fuelLiters ? labeled(config, config.fuelLabelLiters,
                fuel == null ? "—" : fuel.litersText()) : null;
        String free = config.fuelFree ? labeled(config, config.fuelLabelFree,
                fuel == null ? "—" : fuel.freeText()) : null;
        // Друг над другом свободный объём сверху, в строку — залитый первым.
        String first = config.fuelStacked ? free : liters;
        String second = config.fuelStacked ? liters : free;
        if (first == null || second == null) {
            return first != null ? first : second != null ? second : "";
        }
        return first + PARTS + second;
    }

    private static String labeled(SceneConfig config, String label, String value) {
        return (label.isEmpty() ? "" : label + " ") + value + (config.fuelUnits ? " л" : "");
    }

    /**
     * Температуры ГИБ целыми градусами: салон, затем улица (что включено), через
     * {@link #PARTS}; без показания — «—». Иконки к ним добавляет {@link #draw}.
     */
    static String temperature(SceneConfig config, Double cabin, Double outside) {
        String first = config.tempCabin ? degrees(cabin) : null;
        String second = config.tempOutside ? degrees(outside) : null;
        if (first == null || second == null) {
            return first != null ? first : second != null ? second : "";
        }
        return first + PARTS + second;
    }

    private static String degrees(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return "—°";
        }
        long whole = Math.round(value);
        return (whole == 0 ? 0 : whole) + "°"; // Без «-0».
    }

    /** Текст окна на этот момент; фон текста не имеет. */
    static String text(SceneConfig config, SceneConfig.Layer layer) {
        switch (layer.kind) {
            case CLOCK:
                return time(config);
            case FUEL:
                return fuel(config, CarSensors.value(CarSensors.SENSOR_FUEL_PERCENTAGE));
            case TEMP:
                return temperature(config, CarSensors.value(CarSensors.SENSOR_TEMPERATURE_INDOOR),
                        CarSensors.value(CarSensors.SENSOR_TEMPERATURE_AMBIENT));
            default:
                return "";
        }
    }

    static void draw(Canvas canvas, SceneConfig config, SceneConfig.Layer layer, String text) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (layer.background) {
            // Прозрачность всего слоя: и для цвета, и для градиентов, и для картинки.
            canvas.saveLayerAlpha(0, 0, layer.w, layer.h, config.alpha);
            Backgrounds.draw(canvas, config.fill, config.color, layer.w, layer.h);
            if (config.grid) {
                paint.setColor(Color.WHITE);
                paint.setAlpha(85);
                paint.setStrokeWidth(2);
                for (int x = 0; x < layer.w; x += 100) {
                    canvas.drawLine(x, 0, x, layer.h, paint);
                }
                for (int y = 0; y < layer.h; y += 100) {
                    canvas.drawLine(0, y, layer.w, y, paint);
                }
                paint.setTextSize(24);
                canvas.drawText("AtlasDim · " + layer.w + "×" + layer.h + " · сетка 100 px",
                        16, 34, paint);
            }
            canvas.restore();
            return;
        }
        boolean isFuel = layer.kind == SceneConfig.Kind.FUEL;
        boolean isTemp = layer.kind == SceneConfig.Kind.TEMP;
        if (isFuel ? config.fuelPlate : isTemp ? config.tempPlate : config.plate) {
            // Температура ставится поверх штатной — подложка её полностью закрывает.
            paint.setColor(Color.argb(isTemp ? 255 : 150, 0, 0, 0));
            float radius = layer.h * 0.2f;
            canvas.drawRoundRect(0, 0, layer.w, layer.h, radius, radius, paint);
        }
        paint.setColor(Color.WHITE);
        int font = isFuel ? config.fuelFont : isTemp ? config.tempFont : config.clockFont;
        Typeface cluster = font == ClusterFont.USER
                ? UserFonts.typeface(isFuel ? config.fuelFontFile
                        : isTemp ? config.tempFontFile : config.clockFontFile)
                : ClusterFont.typeface(font);
        paint.setTypeface(cluster != null ? cluster
                : Typeface.create("sans-serif-medium", Typeface.NORMAL));
        String[] parts = text.split(String.valueOf(PARTS));
        if (isTemp) {
            if (config.tempIcons) {
                drawWithIcons(canvas, paint, layer, parts, icons(config, parts.length));
            } else {
                drawRow(canvas, paint, layer, parts, TEMP_GAP);
            }
        } else if (parts.length > 1 && config.fuelStacked) {
            drawStacked(canvas, paint, layer, parts, config.fuelGapV);
        } else if (parts.length > 1) {
            drawRow(canvas, paint, layer, parts, config.fuelGapH);
        } else {
            drawRow(canvas, paint, layer, parts, 0);
        }
    }

    /** Иконки к частям {@link #temperature}: салон — сиденье, улица — облако с солнцем (MDI). */
    private static Path[] icons(SceneConfig config, int count) {
        Path[] icons = new Path[count];
        int i = 0;
        if (config.tempCabin && i < count) {
            icons[i++] = MdiIcons.cabin();
        }
        if (config.tempOutside && i < count) {
            icons[i] = MdiIcons.outside();
        }
        return icons;
    }

    /**
     * В строку: иконка, число; между показателями — пустое место. Иконка чуть выше цифры
     * и по центру её высоты; всё вместе уменьшается, если не влезает в 90% ширины окна.
     */
    private static void drawWithIcons(Canvas canvas, Paint paint, SceneConfig.Layer layer,
            String[] parts, Path[] icons) {
        float size = layer.h * 0.7f;
        paint.setTextSize(size);
        Rect digit = new Rect();
        paint.getTextBounds("0", 0, 1, digit);
        float unit = digit.height();
        float total = 0;
        float[] widths = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            widths[i] = (icons[i] == null ? 0 : iconWidth(icons[i]) * unit * ICON_SIZE + unit * ICON_GAP)
                    + paint.measureText(parts[i]);
            total += widths[i] + (i > 0 ? unit * GROUP_GAP : 0);
        }
        float scale = total > layer.w * 0.9f && total > 0 ? layer.w * 0.9f / total : 1;
        paint.setTextSize(size * scale);
        unit *= scale;
        total *= scale;
        Paint.FontMetrics fm = paint.getFontMetrics();
        float y = (layer.h - fm.ascent - fm.descent) / 2f;
        float x = (layer.w - total) / 2f;
        Matrix matrix = new Matrix();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                x += unit * GROUP_GAP;
            }
            if (icons[i] != null) {
                Path icon = new Path();
                float side = unit * ICON_SIZE;
                matrix.setScale(side, side);
                matrix.postTranslate(x, y - unit / 2 - side / 2);
                icons[i].transform(matrix, icon);
                canvas.drawPath(icon, paint);
                x += iconWidth(icons[i]) * side + unit * ICON_GAP;
            }
            canvas.drawText(parts[i], x, y, paint);
            x += paint.measureText(parts[i]);
        }
    }

    /** Разбежка вокруг «·» без иконок, px; с иконками — {@link #GROUP_GAP} высот цифры. */
    private static final int TEMP_GAP = 10;
    /** Высота иконки в высотах цифры. */
    private static final float ICON_SIZE = 1.25f;
    private static final float ICON_GAP = 0.3f;
    private static final float GROUP_GAP = 0.9f;

    private static float iconWidth(Path icon) {
        android.graphics.RectF bounds = new android.graphics.RectF();
        icon.computeBounds(bounds, true);
        return bounds.right;
    }

    /** В строку: показатели через «·», разбежка gap px по обе стороны точки. */
    private static void drawRow(Canvas canvas, Paint paint, SceneConfig.Layer layer,
            String[] parts, int gap) {
        paint.setTextSize(layer.h * 0.7f);
        float text = 0;
        for (int i = 0; i < parts.length; i++) {
            text += paint.measureText(parts[i]) + (i > 0 ? paint.measureText(DOT) : 0);
        }
        float gaps = 2f * gap * (parts.length - 1);
        float maxWidth = layer.w * 0.9f - gaps;
        if (text > maxWidth && maxWidth > 0) {
            paint.setTextSize(paint.getTextSize() * maxWidth / text);
            text = maxWidth;
        }
        Paint.FontMetrics fm = paint.getFontMetrics();
        float y = (layer.h - fm.ascent - fm.descent) / 2f;
        float x = (layer.w - text - gaps) / 2f;
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                x += gap;
                canvas.drawText(DOT, x, y, paint);
                x += paint.measureText(DOT) + gap;
            }
            canvas.drawText(parts[i], x, y, paint);
            x += paint.measureText(parts[i]);
        }
    }

    /** Друг над другом по центру, gap px между строками. */
    private static void drawStacked(Canvas canvas, Paint paint, SceneConfig.Layer layer,
            String[] parts, int gap) {
        int n = parts.length;
        float lineHeight = Math.max(1, (layer.h * 0.9f - gap * (n - 1)) / n);
        paint.setTextSize(lineHeight * 0.85f);
        float widest = 0;
        for (String part : parts) {
            widest = Math.max(widest, paint.measureText(part));
        }
        float maxWidth = layer.w * 0.9f;
        if (widest > maxWidth) {
            paint.setTextSize(paint.getTextSize() * maxWidth / widest);
        }
        Paint.FontMetrics fm = paint.getFontMetrics();
        float line = fm.descent - fm.ascent;
        float y = (layer.h - line * n - gap * (n - 1)) / 2f - fm.ascent;
        for (String part : parts) {
            canvas.drawText(part, (layer.w - paint.measureText(part)) / 2f, y, paint);
            y += line + gap;
        }
    }
}
