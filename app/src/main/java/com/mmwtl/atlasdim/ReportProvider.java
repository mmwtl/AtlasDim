package com.mmwtl.atlasdim;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Отдаёт отчёты диагностики другим приложениям для «Поделиться» — только чтение и только
 * файлы из {@link #dir}. Свой минимальный провайдер вместо FileProvider: без androidx.
 */
public final class ReportProvider extends ContentProvider {
    static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".reports";

    static File dir(Context context) {
        return new File(context.getExternalFilesDir(null), "diagnostics");
    }

    static Uri uri(File file) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY)
                .appendPath(file.getName()).build();
    }

    private File file(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || name.startsWith(".") || name.contains("/")) {
            throw new FileNotFoundException(String.valueOf(uri));
        }
        File file = new File(dir(getContext()), name);
        if (!file.isFile()) {
            throw new FileNotFoundException(String.valueOf(uri));
        }
        return file;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("только чтение");
        }
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /** Имя и размер: по ним мессенджеры подписывают вложение. */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args,
            String sortOrder) {
        File file;
        try {
            file = file(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        MatrixCursor cursor = new MatrixCursor(
                new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[] {file.getName(), file.length()});
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "text/plain";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
