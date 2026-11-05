package com.mmwtl.atlasdim;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Свои шрифты пользователя (TTF/OTF): файл копируется в files/fonts приложения,
 * дальше композиция рисуется им без доступа к исходному файлу. Шрифтов в APK нет.
 * Недостающие в шрифте символы (например, кириллицу) Android берёт из системного.
 */
final class UserFonts {
    private static final String TAG = "AtlasDim";
    /** Полный PuHuiTi с иероглифами — около 10 МБ; больше не берём. */
    private static final long MAX_SIZE = 32L * 1024 * 1024;
    private static final Map<String, Typeface> CACHE = new HashMap<>();
    private static File dir;

    private UserFonts() {
    }

    static synchronized void init(Context context) {
        if (dir == null) {
            dir = new File(context.getApplicationContext().getFilesDir(), "fonts");
        }
    }

    static boolean isFont(String name) {
        return name.toLowerCase(Locale.US).matches(".*\\.(ttf|otf)");
    }

    /** Импортированный шрифт или null: файла нет или он не читается. */
    static synchronized Typeface typeface(String name) {
        if (dir == null || name == null || name.isEmpty()) {
            return null;
        }
        Typeface cached = CACHE.get(name);
        if (cached != null) {
            return cached;
        }
        File file = new File(dir, name);
        if (!file.isFile()) {
            return null;
        }
        Typeface typeface = load(file);
        if (typeface != null) {
            CACHE.put(name, typeface);
        }
        return typeface;
    }

    static boolean exists(String name) {
        return dir != null && name != null && !name.isEmpty() && new File(dir, name).isFile();
    }

    /** Typeface.Builder возвращает null на битом файле, createFromFile — молча DEFAULT. */
    private static Typeface load(File file) {
        try {
            return new Typeface.Builder(file).build();
        } catch (RuntimeException e) {
            Log.w(TAG, "шрифт не читается: " + file + ": " + e.getMessage());
            return null;
        }
    }

    static List<File> imported() {
        List<File> files = new ArrayList<>();
        File[] list = dir == null ? null : dir.listFiles();
        if (list != null) {
            for (File file : list) {
                if (file.isFile() && isFont(file.getName())) {
                    files.add(file);
                }
            }
        }
        files.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return files;
    }

    /** Android 11+: без «Доступа ко всем файлам» чужие .ttf в «Загрузках» не видны. */
    static boolean downloadsReadable() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
    }

    /**
     * Шрифты, которые можно импортировать без выбора файлов: «Загрузки» (если
     * Android даёт их прочитать) и Android/data/…/files/fonts (туда кладут по adb).
     */
    static List<File> found(Context context) {
        List<File> files = new ArrayList<>();
        File[] dirs = {Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                context.getExternalFilesDir("fonts")};
        for (File source : dirs) {
            File[] list = source == null ? null : source.listFiles();
            if (list == null) {
                continue;
            }
            for (File file : list) {
                if (file.isFile() && file.canRead() && isFont(file.getName())) {
                    files.add(file);
                }
            }
        }
        files.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return files;
    }

    static String importFile(File source) throws IOException {
        try (InputStream in = new FileInputStream(source)) {
            return importStream(in, source.getName());
        }
    }

    static String importUri(Context context, Uri uri) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        String name = null;
        try (Cursor cursor = resolver.query(uri, new String[] {OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(0);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "имя файла шрифта: " + e);
        }
        if (name == null) {
            name = uri.getLastPathSegment();
        }
        if (name == null || !isFont(name)) {
            throw new IOException("нужен файл .ttf или .otf");
        }
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                throw new IOException("файл не открывается");
            }
            return importStream(in, name);
        }
    }

    /** Копирует шрифт под безопасным именем и проверяет, что Android его читает. */
    private static String importStream(InputStream in, String original) throws IOException {
        if (dir == null || !dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("нет каталога для шрифтов");
        }
        String name = new File(original).getName().replaceAll("[^A-Za-z0-9._ -]", "_");
        if (!isFont(name) || name.startsWith(".")) {
            throw new IOException("нужен файл .ttf или .otf");
        }
        File part = new File(dir, name + ".part");
        long size = 0;
        try (OutputStream out = new FileOutputStream(part)) {
            byte[] buffer = new byte[64 * 1024];
            for (int n; (n = in.read(buffer)) > 0; ) {
                size += n;
                if (size > MAX_SIZE) {
                    throw new IOException("файл больше 32 МБ");
                }
                out.write(buffer, 0, n);
            }
        } catch (IOException e) {
            part.delete();
            throw e;
        }
        if (load(part) == null) {
            part.delete();
            throw new IOException("Android не читает этот шрифт");
        }
        File file = new File(dir, name);
        synchronized (UserFonts.class) {
            if (!part.renameTo(file)) {
                part.delete();
                throw new IOException("не удалось сохранить " + name);
            }
            CACHE.remove(name);
        }
        Log.i(TAG, "свой шрифт сохранён: " + file + " (" + size + " байт)");
        return name;
    }

    static synchronized boolean delete(String name) {
        CACHE.remove(name);
        return dir != null && new File(dir, name).delete();
    }

    /**
     * Список: уже импортированные и найденные в «Загрузках», внизу — системный
     * выбор файлов. picked получает имя импортированного шрифта, файл — для импорта.
     */
    static AlertDialog show(Activity activity, String current, Consumer<String> picked,
            Consumer<File> importFound, Consumer<String> remove, Runnable allowDownloads,
            Runnable systemPicker) {
        Map<String, Runnable> items = new LinkedHashMap<>();
        List<String> importedNames = new ArrayList<>();
        for (File file : imported()) {
            String name = file.getName();
            importedNames.add(name);
            items.put((name.equals(current) ? "✓ " : "") + name, () -> picked.accept(name));
        }
        int found = 0;
        for (File file : found(activity)) {
            if (!importedNames.contains(file.getName())) {
                found++;
                items.put(activity.getString(R.string.font_user_found, file.getName(),
                        file.length() / 1024), () -> importFound.accept(file));
            }
        }
        if (!downloadsReadable()) {
            items.put(activity.getString(R.string.font_user_allow), allowDownloads);
        }
        items.put(activity.getString(R.string.font_user_files), systemPicker);
        String[] labels = items.keySet().toArray(new String[0]);
        Runnable[] actions = items.values().toArray(new Runnable[0]);
        AlertDialog dialog = new AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(importedNames.isEmpty() && found == 0
                        ? R.string.font_user_empty : R.string.font_user_title)
                .setItems(labels, (d, which) -> actions[which].run())
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getListView().setOnItemLongClickListener(
                (parent, view, position, id) -> {
                    if (position >= importedNames.size()) {
                        return false;
                    }
                    dialog.dismiss();
                    remove.accept(importedNames.get(position));
                    return true;
                }));
        dialog.show();
        return dialog;
    }
}
