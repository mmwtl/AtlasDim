package com.mmwtl.atlasdim;

import android.content.Context;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Atlas graphite visual system shared with AtlasAppWidget, AtlasMediaWidget and
 * AtlasClimateWidget; copied from AtlasClimateWidget {@code Ui}.
 */
final class Ui {
    static final int BACKGROUND = 0xFF171717;
    static final int SURFACE = 0xFF262626;
    static final int SURFACE_RAISED = 0xFF333333;
    static final int TEXT = 0xFFF5F5F5;
    static final int TEXT_SECONDARY = 0xFFD4D4D4;
    static final int ACCENT = 0xFF7893A0;
    static final int ON_ACCENT = 0xFF071014;
    static final int OK = 0xFF8FBF8F;
    static final int ERROR = 0xFFE88A7A;

    private Ui() {
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context context, String value, float sizeSp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        return view;
    }

    static TextView text(Context context, int value, float sizeSp, int color) {
        return text(context, context.getString(value), sizeSp, color);
    }

    static TextView heading(Context context, String value, float sizeSp) {
        TextView view = text(context, value, sizeSp, TEXT);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    static TextView heading(Context context, int value, float sizeSp) {
        return heading(context, context.getString(value), sizeSp);
    }

    static Button button(Context context, String value) {
        Button button = new Button(context);
        button.setText(value);
        button.setTextColor(TEXT);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10));
        button.setBackground(rounded(SURFACE_RAISED, dp(context, 8)));
        return button;
    }

    static Button button(Context context, int value) {
        return button(context, context.getString(value));
    }

    /** The screen's main action: accent fill, as the selected segment. */
    static Button primaryButton(Context context, int value) {
        Button button = button(context, value);
        button.setTextColor(ON_ACCENT);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(rounded(ACCENT, dp(context, 8)));
        return button;
    }

    static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 20), dp(context, 18), dp(context, 20), dp(context, 18));
        card.setBackground(rounded(SURFACE, dp(context, 8)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(context, 12));
        card.setLayoutParams(params);
        return card;
    }

    /** Choice chip of a segmented row, as in AtlasMediaWidget's widget setup. */
    static TextView segment(Context context, int value) {
        return segment(context, context.getString(value));
    }

    static TextView segment(Context context, String value) {
        TextView segment = text(context, value, 14, TEXT);
        segment.setGravity(Gravity.CENTER);
        segment.setMaxLines(1);
        segment.setPadding(dp(context, 8), dp(context, 11), dp(context, 8), dp(context, 11));
        segment.setClickable(true);
        setSegmentSelected(context, segment, false);
        return segment;
    }

    static void setSegmentSelected(Context context, TextView segment, boolean selected) {
        segment.setSelected(selected);
        segment.setBackground(rounded(selected ? ACCENT : SURFACE_RAISED, dp(context, 8)));
        segment.setTextColor(selected ? ON_ACCENT : TEXT);
    }

    /** Adds a segment with an equal share of the row. */
    static void addSegment(LinearLayout row, TextView segment) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (row.getChildCount() > 0) {
            params.leftMargin = dp(row.getContext(), 6);
        }
        row.addView(segment, params);
    }

    static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radiusPx);
        return background;
    }

    static void topMargin(View view, int marginDp) {
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        LinearLayout.LayoutParams params;
        if (raw instanceof LinearLayout.LayoutParams) {
            params = (LinearLayout.LayoutParams) raw;
        } else {
            params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
        params.topMargin = dp(view.getContext(), marginDp);
        view.setLayoutParams(params);
    }

    static void applySystemBarInsets(View view) {
        if (Build.VERSION.SDK_INT < 35) {
            return;
        }
        int left = view.getPaddingLeft();
        int top = view.getPaddingTop();
        int right = view.getPaddingRight();
        int bottom = view.getPaddingBottom();
        view.setOnApplyWindowInsetsListener((target, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            target.setPadding(
                    left + bars.left,
                    top + bars.top,
                    right + bars.right,
                    bottom + bars.bottom
            );
            return windowInsets;
        });
        view.requestApplyInsets();
    }
}
