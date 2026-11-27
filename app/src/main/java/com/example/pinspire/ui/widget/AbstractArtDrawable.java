package com.example.pinspire.ui.widget;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/** Editorial placeholder artwork for pins that have no photo (the built-in starter pins). */
public class AbstractArtDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int color;

    public AbstractArtDrawable(int color) { this.color = color; }

    @Override public void draw(@NonNull Canvas c) {
        Rect b = getBounds();
        float w = b.width(), h = b.height(), x = b.left, y = b.top;
        paint.setColor(color);
        c.drawRect(b, paint);
        paint.setColor(0x44ffffff);
        c.drawCircle(x + w * .78f, y + h * .25f, w * .38f, paint);
        paint.setColor(0x66ffffff);
        c.drawRoundRect(x + w * .10f, y + h * .48f, x + w * .78f, y + h * .86f, w * .05f, w * .05f, paint);
        paint.setColor(0x55777777);
        c.drawCircle(x + w * .34f, y + h * .40f, w * .17f, paint);
        paint.setColor(0x99ffffff);
        c.drawRect(x + w * .12f, y + h * .70f, x + w * .66f, y + h * .73f, paint);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
