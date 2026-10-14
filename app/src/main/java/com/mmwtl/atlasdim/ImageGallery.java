package com.mmwtl.atlasdim;

import android.app.Activity;
import android.app.Dialog;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Сетка картинок устройства из MediaStore. Системного выбора файлов на OneOS
 * может не быть, поэтому список строится в приложении.
 */
final class ImageGallery {
    private ImageGallery() {
    }

    static void show(Activity activity, Runnable systemPicker, Consumer<Uri> picked) {
        List<Uri> images = new ArrayList<>();
        String[] projection = {MediaStore.Images.Media._ID};
        try (Cursor cursor = activity.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null,
                MediaStore.Images.Media.DATE_MODIFIED + " DESC")) {
            while (cursor != null && cursor.moveToNext() && images.size() < 500) {
                images.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(0)));
            }
        } catch (RuntimeException e) {
            android.util.Log.w("AtlasDim", "MediaStore: " + e);
        }
        float density = activity.getResources().getDisplayMetrics().density;
        int cell = Math.round(160 * density);
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_Dialog_NoActionBar);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * density);
        root.setPadding(pad, pad, pad, pad);
        root.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(activity, 8)));
        root.addView(Ui.heading(activity, images.isEmpty() ? R.string.gallery_empty
                : R.string.gallery_title, 20));
        TextView files = Ui.text(activity, R.string.gallery_files, 16, Ui.ACCENT);
        files.setPadding(0, pad / 2, 0, pad / 2);
        files.setOnClickListener(v -> {
            dialog.dismiss();
            systemPicker.run();
        });
        root.addView(files);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        LruCache<Uri, Bitmap> cache = new LruCache<>(60);
        Handler main = new Handler(Looper.getMainLooper());
        GridView grid = new GridView(activity);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(cell);
        grid.setVerticalSpacing(pad / 2);
        grid.setHorizontalSpacing(pad / 2);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setAdapter(new BaseAdapter() {
            @Override public int getCount() {
                return images.size();
            }

            @Override public Object getItem(int position) {
                return images.get(position);
            }

            @Override public long getItemId(int position) {
                return position;
            }

            @Override public View getView(int position, View convert, ViewGroup parent) {
                ImageView view = convert instanceof ImageView ? (ImageView) convert : new ImageView(activity);
                view.setLayoutParams(new GridView.LayoutParams(-1, Math.round(cell * 0.6f)));
                view.setScaleType(ImageView.ScaleType.CENTER_CROP);
                view.setBackgroundColor(Ui.SURFACE_RAISED);
                Uri uri = images.get(position);
                view.setTag(uri);
                Bitmap bitmap = cache.get(uri);
                view.setImageBitmap(bitmap);
                if (bitmap == null) {
                    executor.execute(() -> {
                        Bitmap thumb = thumbnail(activity, uri, cell);
                        main.post(() -> {
                            if (thumb != null) {
                                cache.put(uri, thumb);
                            }
                            if (uri.equals(view.getTag())) {
                                view.setImageBitmap(thumb);
                            }
                        });
                    });
                }
                return view;
            }
        });
        grid.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            picked.accept(images.get(position));
        });
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> executor.shutdownNow());
        dialog.show();
        dialog.getWindow().setLayout(Math.round(activity.getResources().getDisplayMetrics().widthPixels * 0.9f),
                Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.75f));
        dialog.getWindow().setGravity(Gravity.CENTER);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0));
    }

    private static Bitmap thumbnail(Activity activity, Uri uri, int size) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, options);
            }
            options.inSampleSize = 1;
            while (options.outWidth / (options.inSampleSize * 2) >= size
                    && options.outHeight / (options.inSampleSize * 2) >= size / 2) {
                options.inSampleSize *= 2;
            }
            options.inJustDecodeBounds = false;
            try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, options);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
