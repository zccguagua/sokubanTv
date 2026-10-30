package com.sokbantv.game;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

/** Small helpers for the dark, D-pad friendly TV look. */
public final class Ui {

    public static final int TEXT = 0xFFE9EEF9;
    public static final int TEXT_DIM = 0xFF93A0BC;
    public static final int ACCENT = 0xFF35D0BA;
    public static final int WARN = 0xFFFFB74D;
    public static final int CARD = 0xFF1B2440;
    public static final int CARD_BORDER = 0xFF2C3A5E;
    public static final int CARD_FOCUS = 0xFF26395F;

    private Ui() {
    }

    public static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, context.getResources().getDisplayMetrics()));
    }

    private static GradientDrawable rounded(int fill, int stroke, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fill);
        drawable.setCornerRadius(radiusDp);
        if (Color.alpha(stroke) != 0) {
            drawable.setStroke(Math.max(1, Math.round(radiusDp / 10f)), stroke);
        }
        return drawable;
    }

    /** Card background that lights up when a remote focus lands on it. */
    public static StateListDrawable focusableCard(Context context) {
        float radius = dp(context, 18);
        StateListDrawable states = new StateListDrawable();

        GradientDrawable focused = rounded(CARD_FOCUS, ACCENT, radius);
        focused.setStroke(dp(context, 2), ACCENT);

        GradientDrawable pressed = rounded(0xFF2E4675, ACCENT, radius);
        GradientDrawable normal = rounded(CARD, CARD_BORDER, radius);

        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{}, normal);
        return states;
    }

    /** Screen background: deep vertical gradient. */
    public static Drawable screenBackground() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF101A2E, 0xFF080C18});
        return drawable;
    }

    public static TextView label(Context context, CharSequence text, float sizeSp,
                                 int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        }
        view.setShadowLayer(dp(context, 6), 0, dp(context, 2), 0x66000000);
        view.setIncludeFontPadding(false);
        return view;
    }

    public static TextView button(Context context, CharSequence text) {
        TextView view = label(context, text, 15, TEXT, false);
        int padX = dp(context, 18);
        int padY = dp(context, 10);
        view.setPadding(padX, padY, padX, padY);
        view.setGravity(Gravity.CENTER);
        view.setBackground(focusableCard(context));
        view.setFocusable(true);
        view.setClickable(true);
        return view;
    }

    /** Turns any view into something the remote can focus and select. */
    public static void makeFocusable(final android.view.View view) {
        view.setFocusable(true);
        view.setClickable(true);
        view.setOnFocusChangeListener(new android.view.View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(android.view.View v, boolean hasFocus) {
                v.animate().scaleX(hasFocus ? 1.06f : 1f)
                        .scaleY(hasFocus ? 1.06f : 1f)
                        .setDuration(120)
                        .start();
            }
        });
    }
}
