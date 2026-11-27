package com.example.pinspire.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.pinspire.R;
import com.example.pinspire.data.Palettes;

/** Row of color dots that filters a feed by Pixabay color. "" means any color. */
public final class ColorRow {
    public interface Listener { void picked(String color); }

    private final LinearLayout row;
    private final Listener listener;
    private String selected = "";

    public ColorRow(LinearLayout row, Listener listener) {
        this.row = row;
        this.listener = listener;
        build();
    }

    private void build() {
        Context c = row.getContext();
        row.removeAllViews();
        TextView any = new TextView(c);
        any.setText("Any color");
        any.setTextSize(12);
        any.setGravity(Gravity.CENTER);
        any.setTag("");
        any.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0);
        any.setOnClickListener(v -> pick(""));
        LinearLayout.LayoutParams anyLp = new LinearLayout.LayoutParams(-2, Ui.dp(c, 32));
        anyLp.setMarginEnd(Ui.dp(c, 8));
        row.addView(any, anyLp);
        for (int i = 0; i < Palettes.FILTER_NAMES.length; i++) {
            String name = Palettes.FILTER_NAMES[i];
            View dot = new View(c);
            dot.setTag(name);
            dot.setContentDescription(name + " ideas");
            dot.setOnClickListener(v -> pick(selected.equals(name) ? "" : name));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 32), Ui.dp(c, 32));
            lp.setMarginEnd(Ui.dp(c, 8));
            row.addView(dot, lp);
        }
        refresh();
    }

    private void pick(String color) {
        if (color.equals(selected)) return;
        selected = color;
        refresh();
        listener.picked(color);
    }

    public void setSelected(String color) {
        selected = color == null ? "" : color;
        refresh();
    }

    private void refresh() {
        Context c = row.getContext();
        int ink = Ui.color(c, R.color.ink);
        int line = Ui.color(c, R.color.line_strong);
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            String name = (String) v.getTag();
            boolean on = name.equals(selected);
            GradientDrawable g = new GradientDrawable();
            if (name.isEmpty()) {
                g.setCornerRadius(Ui.dp(c, 16));
                g.setColor(on ? ink : Ui.color(c, R.color.surface2));
                ((TextView) v).setTextColor(on ? Ui.color(c, R.color.paper) : ink);
            } else {
                g.setShape(GradientDrawable.OVAL);
                g.setColor(Palettes.swatchFor(name));
                g.setStroke(Ui.dp(c, on ? 3 : 1), on ? ink : line);
            }
            v.setBackground(g);
            v.setScaleX(on && !name.isEmpty() ? 1.12f : 1f);
            v.setScaleY(on && !name.isEmpty() ? 1.12f : 1f);
        }
    }
}
