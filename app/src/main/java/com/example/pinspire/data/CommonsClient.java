package com.example.pinspire.data;

import android.content.Context;
import android.text.Html;

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
import java.util.ArrayList;
import java.util.Locale;

/**
 * Keyless image source (Wikimedia Commons), used automatically when no Pixabay API key is configured,
 * so the app works out of the box. Every result carries its creator and license.
 */
public final class CommonsClient {
    private static final String API = "https://commons.wikimedia.org/w/api.php";
    private static final long CACHE_MS = 24L * 60 * 60 * 1000;
    /** Wikimedia asks API clients to send an identifying User-Agent. */
    public static final String USER_AGENT = "Pinspire/2.0 (Android image inspiration app)";

    private CommonsClient() {}

    public static PixabayClient.Page search(Context c, String query, String color, int page, String label) throws IOException {
        String terms = (query + " " + (color == null ? "" : color)).trim();
        if (terms.isEmpty()) terms = "photography";
        int perPage = PixabayClient.PER_PAGE;
        int offset = (page - 1) * perPage;
        String url = API + "?action=query&format=json&formatversion=2&generator=search"
                + "&gsrsearch=" + enc(terms + " filetype:bitmap")
                + "&gsrnamespace=6&gsrlimit=" + perPage + "&gsroffset=" + offset
                + "&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata%7Cuser&iiurlwidth=640"
                + "&iiextmetadatafilter=Artist%7CLicenseShortName%7CImageDescription";
        JSONObject data = fetch(c, url);
        ArrayList<Pin> out = new ArrayList<>();
        JSONObject q = data.optJSONObject("query");
        JSONArray pages = q == null ? null : q.optJSONArray("pages");
        if (pages != null) {
            // Results arrive unordered; "index" holds the search rank.
            ArrayList<JSONObject> sorted = new ArrayList<>();
            for (int i = 0; i < pages.length(); i++) if (pages.optJSONObject(i) != null) sorted.add(pages.optJSONObject(i));
            sorted.sort((a, b) -> a.optInt("index") - b.optInt("index"));
            for (JSONObject p : sorted) {
                JSONArray infos = p.optJSONArray("imageinfo");
                JSONObject info = infos == null ? null : infos.optJSONObject(0);
                if (info == null) continue;
                // Commons appends tracking query strings; they aren't needed to fetch the image.
                String thumb = stripQuery(info.optString("thumburl", ""));
                String full = stripQuery(info.optString("url", ""));
                int w = info.optInt("thumbwidth", 0), h = info.optInt("thumbheight", 0);
                if (!thumb.startsWith("https://") || w < 200 || h < 150) continue;
                String lower = full.toLowerCase(Locale.US);
                if (!(lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp"))) continue;
                Pin pin = new Pin("commons-" + p.optLong("pageid"));
                pin.remote = true;
                pin.images.add(thumb);
                pin.uri = thumb;
                // Originals can be huge; ask for a 1280px rendition for the detail page.
                pin.fullUrl = thumb.replaceFirst("/(\\d+)px-", "/1280px-");
                pin.width = w;
                pin.height = h;
                pin.title = titleFrom(p.optString("title", ""));
                pin.tags = tagsFrom(query, pin.title);
                pin.category = label;
                pin.source = info.optString("descriptionurl", "");
                JSONObject meta = info.optJSONObject("extmetadata");
                String artist = plain(meta, "Artist");
                pin.creator = artist.isEmpty() ? info.optString("user", "Wikimedia contributor") : artist;
                String license = plain(meta, "LicenseShortName");
                pin.license = license.isEmpty() ? "See source for license" : license;
                pin.description = plain(meta, "ImageDescription");
                out.add(pin);
            }
        }
        boolean more = data.has("continue");
        return new PixabayClient.Page(out, more ? 100_000 : page * perPage);
    }

    private static String stripQuery(String url) {
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
    }

    private static String titleFrom(String fileTitle) {
        String t = fileTitle.replaceFirst("^File:", "").replaceFirst("\\.[A-Za-z0-9]+$", "").replace('_', ' ').trim();
        t = t.replaceAll("\\s+", " ");
        if (t.length() > 70) t = t.substring(0, 70).trim() + "…";
        return t.isEmpty() ? "Photo inspiration" : t;
    }

    private static String tagsFrom(String query, String title) {
        StringBuilder sb = new StringBuilder();
        for (String w : query.toLowerCase(Locale.US).split("\\s+")) {
            if (w.length() < 3) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(w);
        }
        return sb.toString();
    }

    private static String plain(JSONObject meta, String key) {
        if (meta == null) return "";
        JSONObject j = meta.optJSONObject(key);
        if (j == null) return "";
        String html = j.optString("value", "");
        String text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
                .replace("￼", "") // inline <img> placeholders
                .replaceAll("\\s+", " ").trim();
        return text.length() > 160 ? text.substring(0, 160).trim() + "…" : text;
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return ""; }
    }

    private static JSONObject fetch(Context c, String url) throws IOException {
        File folder = new File(c.getCacheDir(), "commons_api");
        if (!folder.exists()) //noinspection ResultOfMethodCallIgnored
            folder.mkdirs();
        File cached = new File(folder, Integer.toHexString(url.hashCode()) + "_" + url.length() + ".json");
        long age = System.currentTimeMillis() - cached.lastModified();
        if (cached.exists() && age >= 0 && age < CACHE_MS) {
            JSONObject j = read(cached);
            if (j != null) return j;
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            if (code == 429) throw new PixabayClient.FeedException("Too many requests. Wait a minute, then try again.", false);
            if (code != 200) throw new PixabayClient.FeedException("The image service is unavailable right now (HTTP " + code + ").", false);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try (InputStream in = conn.getInputStream()) {
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) != -1) {
                    if (buf.size() + n > 4_000_000) throw new IOException("Response too large");
                    buf.write(b, 0, n);
                }
            }
            JSONObject data = new JSONObject(buf.toString("UTF-8"));
            try (FileOutputStream out = new FileOutputStream(cached)) { out.write(buf.toByteArray()); } catch (IOException ignored) {}
            return data;
        } catch (PixabayClient.FeedException e) {
            throw e;
        } catch (Exception e) {
            JSONObject stale = cached.exists() ? read(cached) : null;
            if (stale != null) return stale;
            throw new PixabayClient.FeedException("Can't reach the image service. Check your internet connection.", false);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static JSONObject read(File f) {
        try (InputStream in = new FileInputStream(f); ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) != -1) buf.write(b, 0, n);
            return new JSONObject(buf.toString("UTF-8"));
        } catch (Exception e) { return null; }
    }
}
