package com.mmwtl.atlasdim;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Один и тот же рисунок используется в предпросмотре и в кадрах для QNX. */
final class SceneRenderer {
    private SceneRenderer() {
    }

    static String time(SceneConfig config) {
        return new SimpleDateFormat(config.seconds ? "HH:mm:ss" : "HH:mm", Locale.US)
                .format(new Date());
    }

    /** Литры по датчику ГИБ, как строка датчиков AtlasClimateWidget; без показания — «—». */
    static String fuel(SceneConfig config, Double sensor) {
        Fuel fuel = Fuel.of(sensor, config.fuelMultiplier, config.fuelOffset);
        StringBuilder text = new StringBuilder();
        if (config.fuelLiters) {
            text.append(fuel == null ? "—" : fuel.litersText()).append(" л");
        }
        if (config.fuelFree) {
            if (text.length() > 0) {
                text.append(" · ");
            }
            text.append("своб. ").append(fuel == null ? "—" : fuel.freeText()).append(" л");
        }
        return text.toString();
    }

    /** Текст окна на этот момент; фон текста не имеет. */
    static String text(SceneConfig config, SceneConfig.Layer layer) {
        switch (layer.kind) {
            case CLOCK:
                return time(config);
            case FUEL:
                return fuel(config, FuelSensor.value());
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
        if (layer.kind == SceneConfig.Kind.FUEL ? config.fuelPlate : config.plate) {
            paint.setColor(Color.argb(150, 0, 0, 0));
            float radius = layer.h * 0.2f;
            canvas.drawRoundRect(0, 0, layer.w, layer.h, radius, radius, paint);
        }
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextSize(layer.h * 0.7f);
        float width = paint.measureText(text);
        float maxWidth = layer.w * 0.9f;
        if (width > maxWidth) {
            paint.setTextSize(paint.getTextSize() * maxWidth / width);
        }
        Paint.FontMetrics fm = paint.getFontMetrics();
        canvas.drawText(text, (layer.w - paint.measureText(text)) / 2f,
                (layer.h - fm.ascent - fm.descent) / 2f, paint);
    }
}
