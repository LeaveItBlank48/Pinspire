package com.example.pinspire.data;

import android.content.Context;

import com.example.pinspire.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Minimal Pixabay image search client with the 24-hour response cache Pixabay's API terms require. */
public final class PixabayClient {
    public static final int PER_PAGE = 30;
    private static final String API = "https://pixabay.com/api/";
    private static final long CACHE_MS = 24L * 60 * 60 * 1000;

    /** A failure the user can act on. {@code permanent} errors should not be retried automatically. */
    public static class FeedException extends IOException {
        public final boolean permanent;
        FeedException(String message, boolean permanent) { super(message); this.permanent = permanent; }
    }

    public static class Page {
        public final List<Pin> pins;
        public final int totalHits;
        public Page(List<Pin> pins, int totalHits) { this.pins = pins; this.totalHits = totalHits; }
    }

    private PixabayClient() {}

    public static boolean hasKey() {
        String k = BuildConfig.PIXABAY_API_KEY.trim();
        // The setup guide's placeholder counts as "no key".
        return !k.isEmpty() && !k.toUpperCase(java.util.Locale.US).contains("YOUR");
    }

    /**
     * @param query       search words (required)
     * @param category    Pixabay category filter or empty
     * @param color       Pixabay color filter or empty
     * @param label       category label stored on resulting pins (drives personalization)
     */
    public static Page search(Context c, String query, String category, String color, int page, String label) throws IOException {
        if (!hasKey()) throw new FeedException("Add your Pixabay API key to local.properties (pixabayApiKey=...) and rebuild to load images.", true);
        String q = query.trim();
        if (q.length() > 100) q = q.substring(0, 100);
        String params = "q=" + enc(q) + "&image_type=photo&safesearch=true&per_page=" + PER_PAGE + "&page=" + page + "&order=popular"
                + (category == null || category.isEmpty() ? "" : "&category=" + enc(category))
                + (color == null || color.isEmpty() ? "" : "&colors=" + enc(color));
        JSONObject data = fetch(c, params);
        ArrayList<Pin> out = new ArrayList<>();
        JSONArray hits = data.optJSONArray("hits");
        if (hits != null) for (int i = 0; i < hits.length(); i++) {
            JSONObject hit = hits.optJSONObject(i);
            if (hit == null) continue;
            long id = hit.optLong("id", 0);
            String url = hit.optString("webformatURL", "");
            if (id == 0 || !url.startsWith("https://")) continue;
            String tags = hit.optString("tags", "");
            Pin p = new Pin("pixabay-" + id);
            p.remote = true;
            p.images.add(url);
            p.uri = url;
            p.fullUrl = hit.optString("largeImageURL", "");
            if (!p.fullUrl.startsWith("https://")) p.fullUrl = "";
            p.width = hit.optInt("webformatWidth", 0);
            p.height = hit.optInt("webformatHeight", 0);
            p.tags = tags;
            p.title = titleFrom(tags);
            p.category = label;
            p.source = hit.optString("pageURL", "");
            p.creator = hit.optString("user", "Pixabay contributor");
            p.license = "Pixabay Content License";
            out.add(p);
        }
        return new Page(out, data.optInt("totalHits", 0));
    }

    private static String titleFrom(String tags) {
        String first = tags.isEmpty() ? "" : tags.split(",")[0].trim();
        if (first.isEmpty()) return "Photo inspiration";
        return first.substring(0, 1).toUpperCase(Locale.US) + first.substring(1);
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return ""; }
    }

    private static JSONObject fetch(Context c, String params) throws IOException {
        File folder = new File(c.getCacheDir(), "pixabay_api");
        if (!folder.exists()) //noinspection ResultOfMethodCallIgnored
            folder.mkdirs();
        // Cache key excludes the API key.
        File cached = new File(folder, sha1(params) + ".json");
        long age = System.currentTimeMillis() - cached.lastModified();
        if (cached.exists() && age >= 0 && age < CACHE_MS) {
            JSONObject j = readJson(cached);
            if (j != null) return j;
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(API + "?key=" + enc(BuildConfig.PIXABAY_API_KEY.trim()) + "&" + params).openConnection();
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            if (code == 400) {
                String body = readError(conn);
                // Pixabay only serves the first 500 results of a query.
                if (body.contains("page")) return new JSONObject().put("hits", new JSONArray()).put("totalHits", 0);
                throw new FeedException("Pixabay rejected the request. Check that your API key is correct.", true);
            }
            if (code == 429) throw new FeedException("Too many requests to Pixabay. Wait a minute, then try again.", false);
            if (code != 200) throw new FeedException("Pixabay is unavailable right now (HTTP " + code + ").", false);
            byte[] body = readAll(conn.getInputStream(), 3_000_000);
            JSONObject data = new JSONObject(new String(body, "UTF-8"));
            try (FileOutputStream out = new FileOutputStream(cached)) { out.write(body); } catch (IOException ignored) {}
            return data;
        } catch (FeedException e) {
            throw e;
        } catch (Exception e) {
            // Offline: fall back to an older cached page rather than showing nothing.
            JSONObject stale = cached.exists() ? readJson(cached) : null;
            if (stale != null) return stale;
            throw new FeedException("Can't reach Pixabay. Check your internet connection.", false);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Deletes cached API responses older than a day. */
    public static void pruneCache(Context c) {
        File[] files = new File(c.getCacheDir(), "pixabay_api").listFiles();
        if (files == null) return;
        long now = System.currentTimeMillis();
        for (File f : files) if (now - f.lastModified() > 2 * CACHE_MS) //noinspection ResultOfMethodCallIgnored
            f.delete();
    }

    private static String readError(HttpURLConnection conn) {
        try (InputStream in = conn.getErrorStream()) {
            return in == null ? "" : new String(readAll(in, 10_000), "UTF-8");
        } catch (Exception e) { return ""; }
    }

    private static JSONObject readJson(File f) {
        try (InputStream in = new FileInputStream(f)) {
            return new JSONObject(new String(readAll(in, 3_000_000), "UTF-8"));
        } catch (Exception e) { return null; }
    }

    private static byte[] readAll(InputStream in, int limit) throws IOException {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = input.read(buf)) != -1) {
                if (out.size() + n > limit) throw new IOException("Response too large");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static String sha1(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format(Locale.US, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
