package com.example.pinspire.ui.widget;

import android.content.Context;
import android.util.AttributeSet;

import com.google.android.material.imageview.ShapeableImageView;

/** Rounded image whose height follows a ratio of its width, for the masonry grid. */
public class RatioImageView extends ShapeableImageView {
    private float ratio = 1f;

    public RatioImageView(Context c) { super(c); }
    public RatioImageView(Context c, AttributeSet a) { super(c, a); }
    public RatioImageView(Context c, AttributeSet a, int s) { super(c, a, s); }

    public void setRatio(float r) {
        if (Math.abs(r - ratio) > 0.001f) { ratio = r; requestLayout(); }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.round(w * ratio), MeasureSpec.EXACTLY));
    }
}
