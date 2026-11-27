package com.example.pinspire.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.palette.graphics.Palette;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Color DNA: palette extraction and mapping colors to Pixabay's color filter names. */
public final class Palettes {
    /** Color filters understood by the Pixabay API, paired with a display swatch. */
    public static final String[] FILTER_NAMES = {"red", "orange", "yellow", "green", "turquoise", "blue", "lilac", "pink", "brown", "white", "gray", "black"};
    public static final int[] FILTER_SWATCHES = {0xffe5484d, 0xfff38b3c, 0xfff5d547, 0xff5bb36a, 0xff3fc1c0, 0xff3b82f6, 0xffa78bfa, 0xfff49ac1, 0xff8b5e3c, 0xfff7f7f5, 0xff9a9a9a, 0xff1b1b1b};

    private Palettes() {}

    /** Synchronously extracts up to five dominant colors. Call on a background thread. */
    public static int[] extract(Context c, String uri) {
        if (uri == null || uri.isEmpty()) return new int[0];
        Context app = c.getApplicationContext();
        com.bumptech.glide.request.FutureTarget<Bitmap> target = Glide.with(app).asBitmap().load(ImageModels.of(uri)).submit(120, 120);
        try {
            Bitmap bmp = target.get();
            Palette palette = Palette.from(bmp).maximumColorCount(16).generate();
            List<Palette.Swatch> swatches = new ArrayList<>(palette.getSwatches());
            Collections.sort(swatches, (a, b) -> b.getPopulation() - a.getPopulation());
            ArrayList<Integer> picked = new ArrayList<>();
            for (Palette.Swatch s : swatches) {
                int rgb = s.getRgb() | 0xff000000;
                boolean close = false;
                for (int p : picked) if (distance(p, rgb) < 38) { close = true; break; }
                if (!close) picked.add(rgb);
                if (picked.size() == 5) break;
            }
            int[] out = new int[picked.size()];
            for (int i = 0; i < out.length; i++) out[i] = picked.get(i);
            return out;
        } catch (Exception e) {
            return new int[0];
        } finally {
            Glide.with(app).clear(target);
        }
    }

    private static double distance(int a, int b) {
        int dr = Color.red(a) - Color.red(b), dg = Color.green(a) - Color.green(b), db = Color.blue(a) - Color.blue(b);
        return Math.sqrt(dr * dr * .3 + dg * dg * .59 + db * db * .11);
    }

    public static String hex(int color) {
        return String.format(Locale.US, "#%06X", color & 0xffffff);
    }

    /** Nearest Pixabay color-filter name for any color. */
    public static String nearestFilter(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        float h = hsv[0], s = hsv[1], v = hsv[2];
        if (v < 0.18f) return "black";
        if (s < 0.14f) return v > 0.85f ? "white" : "gray";
        if (h >= 15 && h < 45 && v < 0.62f) return "brown";
        if (h < 12 || h >= 345) return "red";
        if (h < 42) return "orange";
        if (h < 68) return "yellow";
        if (h < 165) return "green";
        if (h < 195) return "turquoise";
        if (h < 255) return "blue";
        if (h < 295) return "lilac";
        return "pink";
    }

    public static int swatchFor(String filter) {
        for (int i = 0; i < FILTER_NAMES.length; i++) if (FILTER_NAMES[i].equals(filter)) return FILTER_SWATCHES[i];
        return 0xff888888;
    }

    /** Whether text drawn on this color should be dark. */
    public static boolean isLight(int color) {
        double l = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return l > 0.62;
    }

    /** Combines the palettes of several pins into one mood strip, most frequent hues first. */
    public static int[] blend(List<Pin> pins, int max) {
        ArrayList<Integer> colors = new ArrayList<>();
        ArrayList<Integer> weight = new ArrayList<>();
        for (Pin p : pins) {
            for (int i = 0; i < p.palette.length; i++) {
                int c = p.palette[i];
                int w = p.palette.length - i;
                int match = -1;
                for (int k = 0; k < colors.size(); k++) if (distance(colors.get(k), c) < 30) { match = k; break; }
                if (match >= 0) weight.set(match, weight.get(match) + w);
                else { colors.add(c); weight.add(w); }
            }
        }
        ArrayList<Integer> order = new ArrayList<>();
        for (int i = 0; i < colors.size(); i++) order.add(i);
        Collections.sort(order, (a, b) -> weight.get(b) - weight.get(a));
        int n = Math.min(max, order.size());
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = colors.get(order.get(i));
        return out;
    }
}
