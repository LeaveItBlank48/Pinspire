package com.example.pinspire.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.pinspire.data.Palettes;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Free-form moodboard canvas (4:5). Drag to move, pinch to resize, twist to rotate, tap to bring forward.
 * Positions are stored relative to the canvas size so layouts survive rotation and export at any size.
 */
public class MoodCanvasView extends View {
    public static final class Item {
        public final String pinId;
        final Bitmap bitmap;
        float x = .5f, y = .5f, scale = .4f, rotation;
        public Item(String pinId, Bitmap bitmap) { this.pinId = pinId; this.bitmap = bitmap; }
    }

    public interface Listener { void changed(); }

    private static final float RATIO = 1.25f;
    private final ArrayList<Item> items = new ArrayList<>();
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockPaint = new Paint();
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int background = Color.WHITE;
    private int[] strip = new int[0];
    private boolean showStrip = true, showCaption = true, exporting;
    private String caption = "";
    @Nullable private Item selected;
    @Nullable private Listener listener;

    // Gesture state
    private float lastX, lastY, startDistance, startAngle, startScale, startRotation;
    private boolean multiTouch, moved;

    public MoodCanvasView(Context c) { super(c); init(); }
    public MoodCanvasView(Context c, @Nullable AttributeSet a) { super(c, a); init(); }

    private void init() {
        framePaint.setColor(Color.WHITE);
        shadowPaint.setColor(0x26000000);
        selectPaint.setStyle(Paint.Style.STROKE);
        selectPaint.setColor(0xff5b4bff);
        selectPaint.setStrokeWidth(getResources().getDisplayMetrics().density * 2);
        selectPaint.setPathEffect(new DashPathEffect(new float[]{18, 12}, 0));
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setLetterSpacing(-0.03f);
        setContentDescription("Moodboard canvas");
    }

    public void setListener(@Nullable Listener l) { listener = l; }

    public void setItems(List<Item> list) {
        items.clear();
        items.addAll(list);
        selected = null;
        invalidate();
    }

    public int itemCount() { return items.size(); }

    public void setBoardBackground(int color) { background = color; invalidate(); }

    public int boardBackground() { return background; }

    public void setStrip(int[] colors) { strip = colors; invalidate(); }

    public void setShowStrip(boolean show) { showStrip = show; invalidate(); }

    public boolean showStrip() { return showStrip; }

    public void setCaption(String text) { caption = text == null ? "" : text; invalidate(); }

    public void setShowCaption(boolean show) { showCaption = show; invalidate(); }

    public boolean showCaption() { return showCaption; }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = Math.round(w * RATIO);
        int maxH = MeasureSpec.getSize(heightSpec);
        if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED && maxH > 0 && h > maxH) {
            h = maxH;
            w = Math.round(h / RATIO);
        }
        setMeasuredDimension(w, h);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        c.drawColor(background);
        float pad = w * 0.012f;
        float lift = w * 0.008f;
        for (Item it : items) {
            float iw = it.scale * w;
            float ih = iw * it.bitmap.getHeight() / Math.max(1f, it.bitmap.getWidth());
            c.save();
            c.translate(it.x * w, it.y * h);
            c.rotate(it.rotation);
            rect.set(-iw / 2 - pad + lift, -ih / 2 - pad + lift, iw / 2 + pad + lift, ih / 2 + pad + lift);
            c.drawRect(rect, shadowPaint);
            rect.set(-iw / 2 - pad, -ih / 2 - pad, iw / 2 + pad, ih / 2 + pad);
            c.drawRect(rect, framePaint);
            rect.set(-iw / 2, -ih / 2, iw / 2, ih / 2);
            c.drawBitmap(it.bitmap, null, rect, bitmapPaint);
            if (it == selected && !exporting) {
                rect.inset(-pad * 2.5f, -pad * 2.5f);
                c.drawRect(rect, selectPaint);
            }
            c.restore();
        }
        if (showStrip && strip.length > 0) {
            float margin = w * 0.05f, bandH = h * 0.06f;
            float top = h - margin - bandH;
            float bw = (w - margin * 2) / strip.length;
            for (int i = 0; i < strip.length; i++) {
                blockPaint.setColor(strip[i]);
                c.drawRect(margin + i * bw, top, margin + (i + 1) * bw, top + bandH, blockPaint);
            }
        }
        if (showCaption && !caption.isEmpty()) {
            textPaint.setTextSize(w * 0.065f);
            textPaint.setColor(Palettes.isLight(background) ? 0xff1b1b1b : Color.WHITE);
            c.drawText(caption + ".", w * 0.05f, w * 0.05f + textPaint.getTextSize(), textPaint);
        }
    }

    // ---------- Gestures ----------

    @Override public boolean onTouchEvent(MotionEvent e) {
        float w = getWidth(), h = getHeight();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                selected = hit(e.getX(), e.getY());
                if (selected != null) { items.remove(selected); items.add(selected); }
                lastX = e.getX();
                lastY = e.getY();
                multiTouch = false;
                moved = false;
                invalidate();
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (selected != null && e.getPointerCount() >= 2) {
                    multiTouch = true;
                    startDistance = distance(e);
                    startAngle = angle(e);
                    startScale = selected.scale;
                    startRotation = selected.rotation;
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (selected == null) return true;
                if (multiTouch && e.getPointerCount() >= 2 && startDistance > 0) {
                    selected.scale = clamp(startScale * distance(e) / startDistance, 0.12f, 1.1f);
                    selected.rotation = startRotation + angle(e) - startAngle;
                    moved = true;
                } else if (!multiTouch) {
                    selected.x = clamp(selected.x + (e.getX() - lastX) / w, 0f, 1f);
                    selected.y = clamp(selected.y + (e.getY() - lastY) / h, 0f, 1f);
                    lastX = e.getX();
                    lastY = e.getY();
                    moved = true;
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                if (listener != null) listener.changed();
                if (!moved) performClick();
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override public boolean performClick() { return super.performClick(); }

    @Nullable private Item hit(float px, float py) {
        float w = getWidth(), h = getHeight();
        for (int i = items.size() - 1; i >= 0; i--) {
            Item it = items.get(i);
            float iw = it.scale * w, ih = iw * it.bitmap.getHeight() / Math.max(1f, it.bitmap.getWidth());
            double rad = Math.toRadians(-it.rotation);
            float dx = px - it.x * w, dy = py - it.y * h;
            float lx = (float) (dx * Math.cos(rad) - dy * Math.sin(rad));
            float ly = (float) (dx * Math.sin(rad) + dy * Math.cos(rad));
            if (Math.abs(lx) <= iw / 2 + 12 && Math.abs(ly) <= ih / 2 + 12) return it;
        }
        return null;
    }

    private static float distance(MotionEvent e) {
        float dx = e.getX(0) - e.getX(1), dy = e.getY(0) - e.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float angle(MotionEvent e) {
        return (float) Math.toDegrees(Math.atan2(e.getY(1) - e.getY(0), e.getX(1) - e.getX(0)));
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    // ---------- Layout ----------

    /** Scatters items over a loose grid with a little tilt, like prints on a table. */
    public void shuffle() {
        Random r = new Random();
        Collections.shuffle(items, r);
        int n = items.size();
        if (n == 0) return;
        int cols = (int) Math.ceil(Math.sqrt(n));
        int rows = (int) Math.ceil(n / (float) cols);
        float top = 0.12f, bottom = showStrip && strip.length > 0 ? 0.86f : 0.95f;
        float cw = 1f / cols, ch = (bottom - top) / rows;
        for (int i = 0; i < n; i++) {
            Item it = items.get(i);
            int col = i % cols, row = i / cols;
            it.x = clamp((col + .5f) * cw + (r.nextFloat() - .5f) * cw * .25f, .08f, .92f);
            it.y = clamp(top + (row + .5f) * ch + (r.nextFloat() - .5f) * ch * .2f, .1f, .9f);
            it.scale = clamp(cw * (0.95f + r.nextFloat() * 0.3f), 0.18f, 0.75f);
            it.rotation = (r.nextFloat() - .5f) * 12f;
        }
        selected = null;
        invalidate();
        if (listener != null) listener.changed();
    }

    public JSONObject toJson() {
        JSONObject j = new JSONObject();
        try {
            j.put("bg", background);
            j.put("strip", showStrip);
            j.put("caption", showCaption);
            JSONArray arr = new JSONArray();
            for (Item it : items) {
                JSONObject o = new JSONObject();
                o.put("id", it.pinId);
                o.put("x", it.x);
                o.put("y", it.y);
                o.put("s", it.scale);
                o.put("r", it.rotation);
                arr.put(o);
            }
            j.put("items", arr);
        } catch (Exception ignored) {}
        return j;
    }

    /** Applies a saved layout; returns false if it doesn't cover the current items. */
    public boolean applyJson(String raw) {
        if (raw == null || raw.isEmpty()) return false;
        try {
            JSONObject j = new JSONObject(raw);
            background = j.optInt("bg", background);
            showStrip = j.optBoolean("strip", true);
            showCaption = j.optBoolean("caption", true);
            JSONArray arr = j.optJSONArray("items");
            if (arr == null) return false;
            ArrayList<Item> ordered = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                for (Item it : items) if (it.pinId.equals(o.optString("id")) && !ordered.contains(it)) {
                    it.x = (float) o.optDouble("x", .5);
                    it.y = (float) o.optDouble("y", .5);
                    it.scale = (float) o.optDouble("s", .4);
                    it.rotation = (float) o.optDouble("r", 0);
                    ordered.add(it);
                }
            }
            boolean complete = ordered.size() == items.size();
            for (Item it : items) if (!ordered.contains(it)) ordered.add(it);
            items.clear();
            items.addAll(ordered);
            invalidate();
            return complete;
        } catch (Exception e) {
            return false;
        }
    }

    /** Renders the board at the given width for export (selection outline hidden). */
    public Bitmap render(int outWidth) {
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        int outHeight = Math.round(outWidth * (h / (float) w));
        Bitmap out = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.scale(outWidth / (float) w, outHeight / (float) h);
        exporting = true;
        onDraw(c);
        exporting = false;
        return out;
    }
}
