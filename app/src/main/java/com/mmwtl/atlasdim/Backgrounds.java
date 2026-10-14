package com.mmwtl.atlasdim;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Заливки фонового окна: встроенные пресеты, картинки из assets и картинки пользователя. */
final class Backgrounds {
    static final String COLOR = "color";
    static final String FILE = "file:";
    static final String ASSET = "asset:";
    // Картинки 1920×532 из output/background-concepts-sides (узкие чёрные полосы по бокам), пережатые в WebP.
    static final String[] PRESETS = {COLOR, "night", "aurora", "sunset", "carbon",
            ASSET + "v01-graphite-road.webp", ASSET + "v02-blue-grid.webp",
            ASSET + "v03-mountain-pass.webp", ASSET + "v04-light-tunnel.webp",
            ASSET + "v05-copper-dawn.webp", ASSET + "c01-night-highway.webp",
            ASSET + "c02-neon-horizon.webp", ASSET + "c03-warp.webp",
            ASSET + "c04-aurora.webp", ASSET + "c05-dusk-forest.webp"};
    static final String[] LABELS = {"Цвет", "Ночь", "Аврора", "Закат", "Карбон",
            "Графит", "Сетка", "Перевал", "Тоннель", "Медь",
            "Трасса", "Неон", "Варп", "Сияние", "Лес"};
    private static AssetManager assets;
    private static String cachedPath;
    private static Bitmap cached;
    private static Bitmap carbonTile;

    private Backgrounds() {
    }

    /** Без этого картинки из assets не рисуются (остаётся цвет). */
    static synchronized void init(Context context) {
        assets = context.getApplicationContext().getAssets();
    }

    static boolean isImage(String fill) {
        return fill != null && (fill.startsWith(FILE) || fill.startsWith(ASSET));
    }

    static String label(String fill) {
        int i = Arrays.asList(PRESETS).indexOf(fill);
        return i >= 0 ? LABELS[i] : fill.startsWith(FILE) ? "Картинка" : fill;
    }

    /** Рисует заливку w×h полностью непрозрачной; прозрачность слоя применяет вызывающий. */
    static void draw(Canvas canvas, String fill, int color, int w, int h) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (isImage(fill)) {
            Bitmap image = image(fill);
            if (image != null) {
                canvas.drawBitmap(image, cover(image.getWidth(), image.getHeight(), w, h),
                        new RectF(0, 0, w, h), paint);
                return;
            }
            fill = COLOR; // Файл удалён — остаётся цвет, а не пустое окно.
        }
        switch (fill == null ? COLOR : fill) {
            case "night":
                paint.setShader(new LinearGradient(0, 0, 0, h, 0xff0d2142, 0xff020610,
                        Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, paint);
                paint.setShader(new RadialGradient(w / 2f, h, Math.max(w, h) * 0.45f,
                        0x553a7bd5, 0x003a7bd5, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, paint);
                break;
            case "aurora":
                paint.setShader(new LinearGradient(0, h, w, 0,
                        new int[] {0xff03211f, 0xff0a1c3a, 0xff25103a}, null, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, paint);
                paint.setShader(new RadialGradient(w * 0.3f, 0, Math.max(w, h) * 0.5f,
                        0x4420d3a0, 0x0020d3a0, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, paint);
                break;
            case "sunset":
                paint.setShader(new LinearGradient(0, 0, 0, h,
                        new int[] {0xff1a0b2e, 0xff4a1838, 0xff8a3a24}, null, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, paint);
                break;
            case "carbon":
                paint.setShader(new BitmapShader(carbon(), Shader.TileMode.REPEAT,
                        Shader.TileMode.REPEAT));
                canvas.drawRect(0, 0, w, h, paint);
                paint.setShader(new LinearGradient(0, 0, 0, h, 0x22ffffff, 0x66000000,
                        Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, paint);
                break;
            default:
                paint.setColor(color | 0xff000000);
                canvas.drawRect(0, 0, w, h, paint);
        }
    }

    /** Часть картинки, заполняющая окно без искажения (center-crop). */
    static Rect cover(int iw, int ih, int w, int h) {
        if ((long) iw * h > (long) ih * w) {
            int cw = (int) ((long) ih * w / h);
            return new Rect((iw - cw) / 2, 0, (iw + cw) / 2, ih);
        }
        int ch = (int) ((long) iw * h / w);
        return new Rect(0, (ih - ch) / 2, iw, (ih + ch) / 2);
    }

    private static synchronized Bitmap carbon() {
        if (carbonTile == null) {
            carbonTile = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(carbonTile);
            Paint p = new Paint();
            c.drawColor(0xff101318);
            p.setColor(0xff1c2028);
            c.drawRect(0, 0, 6, 6, p);
            c.drawRect(6, 6, 12, 12, p);
            p.setColor(0xff252a33);
            c.drawRect(0, 0, 6, 2, p);
            c.drawRect(6, 6, 12, 8, p);
        }
        return carbonTile;
    }

    /** Одна закэшированная картинка (file:… или asset:…): ей пользуются и предпросмотр, и кадр для QNX. */
    static synchronized Bitmap image(String fill) {
        if (!fill.equals(cachedPath)) {
            // Картинки, положенные по adb, могут быть огромными — декодируем с уменьшением.
            cached = decode(fill, SceneConfig.WIDTH, SceneConfig.HEIGHT);
            cachedPath = fill;
        }
        return cached;
    }

    static Bitmap thumbnail(String fill, int maxWidth) {
        return decode(fill, maxWidth, 1);
    }

    private static Bitmap decode(String fill, int minW, int minH) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        decode(fill, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null;
        }
        options.inSampleSize = sample(options.outWidth, options.outHeight, minW, minH);
        options.inJustDecodeBounds = false;
        return decode(fill, options);
    }

    private static Bitmap decode(String fill, BitmapFactory.Options options) {
        if (fill.startsWith(FILE)) {
            return BitmapFactory.decodeFile(fill.substring(FILE.length()), options);
        }
        if (!fill.startsWith(ASSET) || assets == null) {
            return null;
        }
        try (InputStream in = assets.open("backgrounds/" + fill.substring(ASSET.length()))) {
            return BitmapFactory.decodeStream(in, null, options);
        } catch (IOException e) {
            return null;
        }
    }

    static File dir(Context context) {
        File dir = new File(context.getFilesDir(), "backgrounds");
        dir.mkdirs();
        return dir;
    }

    /** Свои картинки: импортированные и положенные по adb в Android/data/…/files/backgrounds. */
    static List<File> userImages(Context context) {
        List<File> files = new ArrayList<>();
        for (File dir : new File[] {dir(context), context.getExternalFilesDir("backgrounds")}) {
            File[] list = dir == null ? null : dir.listFiles();
            if (list == null) {
                continue;
            }
            for (File file : list) {
                String name = file.getName().toLowerCase(java.util.Locale.US);
                if (file.isFile() && name.matches(".*\\.(jpe?g|png|webp)")) {
                    files.add(file);
                }
            }
        }
        files.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return files;
    }

    /**
     * Копирует картинку в приложение, уменьшив до размера, который ещё покрывает
     * окно 1920×532: сессии не нужны права на чужой Uri, а в памяти лежит ≤ ~8 МБ.
     */
    static File importImage(Context context, Uri uri) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, options);
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw new IOException("не удалось прочитать картинку");
        }
        options.inSampleSize = sample(options.outWidth, options.outHeight,
                SceneConfig.WIDTH, SceneConfig.HEIGHT);
        options.inJustDecodeBounds = false;
        Bitmap bitmap;
        try (InputStream in = resolver.openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(in, null, options);
        }
        if (bitmap == null) {
            throw new IOException("не удалось декодировать картинку");
        }
        float scale = Math.max((float) SceneConfig.WIDTH / bitmap.getWidth(),
                (float) SceneConfig.HEIGHT / bitmap.getHeight());
        if (scale < 1) {
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                    Math.round(bitmap.getWidth() * scale), Math.round(bitmap.getHeight() * scale), true);
            bitmap.recycle();
            bitmap = scaled;
        }
        boolean alpha = bitmap.hasAlpha();
        File file = new File(dir(context), "bg-" + System.currentTimeMillis() + (alpha ? ".png" : ".jpg"));
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(alpha ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 92, out);
        } finally {
            bitmap.recycle();
        }
        return file;
    }

    /** Наибольший inSampleSize, при котором картинка ещё не меньше minW×minH. */
    private static int sample(int w, int h, int minW, int minH) {
        int sample = 1;
        while (w / (sample * 2) >= minW && h / (sample * 2) >= minH) {
            sample *= 2;
        }
        return sample;
    }
}
