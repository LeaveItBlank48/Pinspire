package com.example.pinspire.ui;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.pinspire.R;
import com.example.pinspire.data.Pin;
import com.example.pinspire.ui.widget.AbstractArtDrawable;

/** Small view helpers shared by all screens. */
public final class Ui {
    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static int color(Context c, int res) { return ContextCompat.getColor(c, res); }

    /** Loads a pin's image (or placeholder art) into an ImageView, downsampled to the view size. */
    public static void loadPin(ImageView iv, Pin p) {
        loadImage(iv, p.cover(), p);
    }

    public static void loadImage(ImageView iv, String uri, Pin p) {
        int base = p.palette.length > 0 ? (p.palette[0] & 0x00ffffff) | 0x66000000 : p.color;
        if (uri == null || uri.isEmpty()) {
            Glide.with(iv).clear(iv);
            iv.setImageDrawable(new AbstractArtDrawable(p.color));
            return;
        }
        Glide.with(iv)
                .load(com.example.pinspire.data.ImageModels.of(uri))
                .placeholder(new ColorDrawable(p.remote ? color(iv.getContext(), R.color.surface2) : base))
                .error(new AbstractArtDrawable(p.color))
                .transition(DrawableTransitionOptions.withCrossFade(160))
                .into(iv);
    }

    public static View swatch(Context c, int color, int sizeDp) {
        View v = new View(c);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        g.setStroke(dp(c, 1), color(c, R.color.line_strong));
        v.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp));
        lp.setMarginEnd(dp(c, 8));
        v.setLayoutParams(lp);
        return v;
    }

    /** Fills a horizontal LinearLayout with equal-width color blocks; hides it when empty. */
    public static void moodStrip(LinearLayout strip, int[] colors) {
        strip.removeAllViews();
        strip.setVisibility(colors.length == 0 ? View.GONE : View.VISIBLE);
        for (int c : colors) {
            View v = new View(strip.getContext());
            v.setBackgroundColor(c);
            strip.addView(v, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        }
    }

    public static void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    public static String count(int n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    /** Masonry columns: 2 on phones, more on wide screens. */
    public static int spanCount(Context c) {
        float widthDp = c.getResources().getDisplayMetrics().widthPixels / c.getResources().getDisplayMetrics().density;
        return Math.max(2, Math.min(5, (int) (widthDp / 190)));
    }
}
