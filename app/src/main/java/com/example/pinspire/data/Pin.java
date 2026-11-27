package com.example.pinspire.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * A single idea. Remote pins come from Pixabay; created pins come from the user's gallery.
 * JSON keys match the 1.x storage format so earlier saved data keeps loading.
 */
public class Pin {
    public String id;
    public String title = "";
    public String description = "";
    public String uri = "";
    /** Higher-resolution image used on the detail page and when saving (remote pins only). */
    public String fullUrl = "";
    public String category = "Other";
    public String source = "";
    public String creator = "";
    public String license = "";
    /** Comma-separated tags. */
    public String tags = "";
    /** Private note the user attaches to a saved pin. */
    public String note = "";
    public boolean remote;
    public int color = 0xffe7e7e5;
    public int width, height;
    public long createdAt;
    public final ArrayList<String> images = new ArrayList<>();
    /** Dominant colors (the "Color DNA"), most prominent first. */
    public int[] palette = new int[0];

    /** Set when the pin was discovered through today's Daily Spark. Never persisted. */
    public transient boolean fromSpark;

    public Pin(String id) { this.id = id; }

    public String cover() {
        if (!images.isEmpty()) return images.get(0);
        return uri == null ? "" : uri;
    }

    public boolean hasImage() { return !cover().isEmpty(); }

    /** Image to show at full size: the large remote rendition when available. */
    public String displayImage(int index) {
        if (index == 0 && remote && !fullUrl.isEmpty() && cover().startsWith("http")) return fullUrl;
        if (index < 0 || index >= images.size()) return cover();
        return images.get(index);
    }

    public int imageCount() { return Math.max(1, images.size()); }

    /** Height divided by width; falls back to a stable varied value so the grid stays staggered. */
    public float ratio() {
        if (width > 0 && height > 0) return Math.max(0.55f, Math.min(1.9f, (float) height / width));
        float[] r = {1.3f, 1.5f, 1.0f, 1.4f, 0.8f, 1.2f};
        return r[Math.abs(id.hashCode()) % r.length];
    }

    public List<String> tagList() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (tags != null) for (String t : tags.split(",")) {
            String clean = t.trim().toLowerCase(Locale.US);
            if (!clean.isEmpty()) out.add(clean);
        }
        return new ArrayList<>(out);
    }

    public Pin copy() {
        Pin p = new Pin(id);
        p.title = title; p.description = description; p.uri = uri; p.fullUrl = fullUrl;
        p.category = category; p.source = source; p.creator = creator; p.license = license;
        p.tags = tags; p.note = note; p.remote = remote; p.color = color;
        p.width = width; p.height = height; p.createdAt = createdAt;
        p.images.addAll(images); p.palette = palette.clone(); p.fromSpark = fromSpark;
        return p;
    }

    JSONObject toJson() throws Exception {
        JSONObject j = new JSONObject();
        j.put("id", id);
        j.put("title", title);
        j.put("description", description);
        j.put("uri", uri);
        j.put("images", new JSONArray(images));
        j.put("color", color);
        j.put("remote", remote);
        j.put("source", source);
        j.put("category", category);
        j.put("creator", creator);
        j.put("license", license);
        j.put("tags", tags);
        j.put("note", note);
        j.put("fullUrl", fullUrl);
        j.put("width", width);
        j.put("height", height);
        j.put("createdAt", createdAt);
        JSONArray pal = new JSONArray();
        for (int c : palette) pal.put(c);
        j.put("palette", pal);
        return j;
    }

    static Pin fromJson(JSONObject j) {
        String id = j.optString("id", "");
        if (id.isEmpty()) return null;
        Pin p = new Pin(id);
        p.title = j.optString("title", "Untitled");
        p.description = j.optString("description", "");
        p.uri = j.optString("uri", "");
        JSONArray imgs = j.optJSONArray("images");
        if (imgs != null) {
            for (int i = 0; i < imgs.length(); i++) {
                String s = imgs.optString(i, "");
                if (!s.isEmpty()) p.images.add(s);
            }
        } else if (!p.uri.isEmpty()) p.images.add(p.uri);
        p.color = j.optInt("color", p.color);
        p.remote = j.optBoolean("remote", false);
        p.source = j.optString("source", "");
        p.category = j.optString("category", "Other");
        p.creator = j.optString("creator", "");
        p.license = j.optString("license", "");
        p.tags = j.optString("tags", "");
        // 1.x stored Pixabay tags inside the description.
        if (p.tags.isEmpty() && p.description.startsWith("Tags: ")) {
            p.tags = p.description.substring(6);
            p.description = "";
        }
        p.note = j.optString("note", "");
        p.fullUrl = j.optString("fullUrl", "");
        p.width = j.optInt("width", 0);
        p.height = j.optInt("height", 0);
        p.createdAt = j.optLong("createdAt", 0);
        JSONArray pal = j.optJSONArray("palette");
        if (pal != null) {
            p.palette = new int[pal.length()];
            for (int i = 0; i < pal.length(); i++) p.palette[i] = pal.optInt(i);
        }
        return p;
    }
}
