package com.mmwtl.atlasdim;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * Схема только наших окон; аппаратную композицию штатного Kanzi не имитирует.
 * С {@link EditListener} окна можно выбирать, перетаскивать и тянуть за угол.
 */
final class ScenePreview extends View {
    static final int CLOCK_COLOR = 0xfff5b84a;
    static final int BACKGROUND_COLOR = 0xff38bdf8;
    static final int FUEL_COLOR = 0xffa78bfa;
    private static final int SNAP = 4;
    private static final int EDGE = 16;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private SceneConfig config = new SceneConfig();
    private boolean valid = true;
    private EditListener listener;
    private SceneConfig.Kind selected = SceneConfig.Kind.CLOCK;
    private SceneConfig.Layer dragging;
    private boolean resizing;
    private float downX;
    private float downY;
    private final int[] start = new int[4];

    interface EditListener {
        void onSelect(SceneConfig.Kind kind);

        /** Геометрия слоя изменена пальцем; done — палец отпущен. */
        void onMoved(SceneConfig.Layer layer, boolean done);
    }

    ScenePreview(Context context) {
        super(context);
        setContentDescription("Предпросмотр приборки 1920 на 532");
    }

    void setEditListener(EditListener listener) {
        this.listener = listener;
    }

    void select(SceneConfig.Kind kind) {
        selected = kind;
        invalidate();
    }

    static int color(SceneConfig.Kind kind) {
        return kind == SceneConfig.Kind.CLOCK ? CLOCK_COLOR
                : kind == SceneConfig.Kind.FUEL ? FUEL_COLOR : BACKGROUND_COLOR;
    }

    void update(SceneConfig config, boolean valid) {
        if (dragging != null) {
            return; // Во время жеста геометрию ведёт палец, а не поля ввода.
        }
        this.config = new SceneConfig(config);
        this.valid = valid;
        invalidate();
    }

    private float scale() {
        return getWidth() / (float) SceneConfig.WIDTH;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(width, Math.round(width * (float) SceneConfig.HEIGHT / SceneConfig.WIDTH));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.scale(scale(), getHeight() / (float) SceneConfig.HEIGHT);
        int width = SceneConfig.WIDTH;
        int height = SceneConfig.HEIGHT;
        paint.setStyle(Paint.Style.FILL);
        paint.setPathEffect(null);
        for (int y = 0; y < height; y += 40) {
            for (int x = 0; x < width; x += 40) {
                paint.setColor(((x + y) / 40) % 2 == 0 ? 0xff1f1f1f : 0xff262626);
                canvas.drawRect(x, y, x + 40, y + 40, paint);
            }
        }
        for (SceneConfig.Layer layer : order()) {
            drawLayer(canvas, layer);
        }
        if (listener != null) {
            // Рамки поверх всего: окно, закрытое другим слоем, всё равно можно схватить.
            for (SceneConfig.Layer layer : config.layers()) {
                if (layer.kind != selected) {
                    drawFrame(canvas, layer, false);
                }
            }
            drawFrame(canvas, config.layer(selected), true);
        }
        if (!valid) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x99aa2020);
            canvas.drawRect(0, 0, width, height, paint);
        }
        canvas.restore();
        if (isShown()) {
            postInvalidateDelayed(1000);
        }
    }

    /** Порядок снизу вверх: z сравнивается только внутри дисплея; dpy-1 физически поверх dpy-2. */
    private SceneConfig.Layer[] order() {
        SceneConfig.Layer[] order = config.layers();
        java.util.Arrays.sort(order, (a, b) -> a.display != b.display
                ? Integer.compare(rank(a.display), rank(b.display)) : Integer.compare(a.z, b.z));
        return order;
    }

    private static int rank(int display) {
        return display == 2 ? 0 : 1;
    }

    private boolean here(SceneConfig.Layer layer) {
        return layer.enabled;
    }

    private void drawLayer(Canvas canvas, SceneConfig.Layer layer) {
        if (!here(layer)) {
            return;
        }
        canvas.save();
        canvas.translate(layer.x, layer.y);
        canvas.clipRect(0, 0, layer.w, layer.h);
        SceneRenderer.draw(canvas, config, layer, SceneRenderer.text(config, layer));
        canvas.restore();
    }

    private void drawFrame(Canvas canvas, SceneConfig.Layer layer, boolean selected) {
        if (!here(layer)) {
            return;
        }
        float px = 1 / scale();
        int color = color(layer.kind);
        float inset = px * (selected ? 1.5f : 1);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        paint.setAlpha(selected ? 255 : 120);
        paint.setStrokeWidth(px * (selected ? 3 : 2));
        paint.setPathEffect(selected ? null : new DashPathEffect(new float[] {10 * px, 6 * px}, 0));
        canvas.drawRect(layer.x + inset, layer.y + inset, layer.x + layer.w - inset,
                layer.y + layer.h - inset, paint);
        paint.setPathEffect(null);
        if (!selected) {
            return;
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        float r = 9 * px;
        canvas.drawCircle(layer.x + layer.w - r * 0.6f, layer.y + layer.h - r * 0.6f, r, paint);
        String text = layer.label() + " · " + layer.w + "×" + layer.h + " @" + layer.x + "," + layer.y;
        paint.setTextSize(12 * px);
        float tw = paint.measureText(text) + 12 * px;
        float th = 20 * px;
        float ty = layer.y >= th ? layer.y - th : layer.y + layer.h;
        if (ty + th > SceneConfig.HEIGHT) {
            ty = layer.y;
        }
        float tx = Math.min(layer.x, SceneConfig.WIDTH - tw);
        canvas.drawRoundRect(new RectF(tx, ty, tx + tw, ty + th), 4 * px, 4 * px, paint);
        paint.setColor(Ui.BACKGROUND);
        canvas.drawText(text, tx + 6 * px, ty + 14.5f * px, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (listener == null) {
            return false;
        }
        float s = scale();
        float x = event.getX() / s;
        float y = event.getY() / s;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = hit(x, y, 24 / s);
                if (dragging == null) {
                    return false;
                }
                SceneConfig.Layer l = dragging;
                resizing = Math.abs(x - l.x - l.w) < 36 / s && Math.abs(y - l.y - l.h) < 36 / s;
                selected = l.kind;
                listener.onSelect(l.kind);
                downX = x;
                downY = y;
                start[0] = l.x;
                start[1] = l.y;
                start[2] = l.w;
                start[3] = l.h;
                getParent().requestDisallowInterceptTouchEvent(true);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging != null) {
                    drag(Math.round(x - downX), Math.round(y - downY));
                    listener.onMoved(dragging, false);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging != null) {
                    SceneConfig.Layer done = dragging;
                    dragging = null;
                    listener.onMoved(done, true);
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                return true;
            default:
                return true;
        }
    }

    /** Угол выбранного слоя — ресайз; иначе меньшее окно под пальцем (часы поверх фона). */
    private SceneConfig.Layer hit(float x, float y, float slop) {
        SceneConfig.Layer current = config.layer(selected);
        if (here(current) && Math.abs(x - current.x - current.w) < slop * 1.5f
                && Math.abs(y - current.y - current.h) < slop * 1.5f) {
            return current;
        }
        SceneConfig.Layer best = null;
        for (SceneConfig.Layer layer : config.layers()) {
            if (here(layer) && x >= layer.x - slop && x <= layer.x + layer.w + slop
                    && y >= layer.y - slop && y <= layer.y + layer.h + slop
                    && (best == null || (long) layer.w * layer.h < (long) best.w * best.h)) {
                best = layer;
            }
        }
        return best;
    }

    private void drag(int dx, int dy) {
        SceneConfig.Layer l = dragging;
        int maxW = SceneConfig.WIDTH;
        int maxH = SceneConfig.HEIGHT;
        if (resizing) {
            l.w = snap(clamp(start[2] + dx, 40, maxW - l.x), maxW - l.x);
            l.h = snap(clamp(start[3] + dy, 24, maxH - l.y), maxH - l.y);
        } else {
            l.x = snap(clamp(start[0] + dx, 0, maxW - l.w), maxW - l.w);
            l.y = snap(clamp(start[1] + dy, 0, maxH - l.h), maxH - l.h);
        }
    }

    /** Шаг 4 px и прилипание к краям экрана. */
    private static int snap(int value, int max) {
        if (value < EDGE) {
            return 0;
        }
        if (value > max - EDGE) {
            return max;
        }
        return Math.min(max, Math.round(value / (float) SNAP) * SNAP);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
