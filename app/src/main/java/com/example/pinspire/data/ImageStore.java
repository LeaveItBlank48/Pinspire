package com.example.pinspire.data;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

/** File handling: private copies of saved/created images, gallery export and share URIs. */
public final class ImageStore {
    private static final long MAX_IMAGE_BYTES = 25L * 1024 * 1024;
    public static final String SAVED_DIR = "saved_pixabay";
    public static final String LOCAL_DIR = "local_images";

    private ImageStore() {}

    private static File dir(Context c, String name) throws IOException {
        File folder = new File(c.getFilesDir(), name);
        if (!folder.exists() && !folder.mkdirs()) throw new IOException("Could not create " + name);
        return folder;
    }

    /** Downloads a remote image into private storage (Pixabay URLs expire after 24h). */
    public static File download(Context c, String remoteUrl, String id) throws IOException {
        File target = new File(dir(c, SAVED_DIR), id.replaceAll("[^a-zA-Z0-9_-]", "_") + ".jpg");
        if (target.exists() && target.length() > 0) return target;
        File temp = new File(target.getPath() + ".tmp");
        HttpURLConnection conn = (HttpURLConnection) new URL(remoteUrl).openConnection();
        conn.setConnectTimeout(15000);
        conn.setRequestProperty("User-Agent", CommonsClient.USER_AGENT);
        conn.setReadTimeout(20000);
        try {
            int code = conn.getResponseCode();
            if (code != 200) throw new IOException("Image download failed (HTTP " + code + ")");
            try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(temp)) {
                copy(in, out);
            }
        } finally {
            conn.disconnect();
        }
        if (!isDecodable(temp)) { temp.delete(); throw new IOException("Downloaded file is not an image"); }
        if (!temp.renameTo(target)) { temp.delete(); throw new IOException("Could not store image"); }
        return target;
    }

    /** Copies a picked gallery image into private storage so it survives the original being moved. */
    public static File importLocal(Context c, Uri source) throws IOException {
        File target = new File(dir(c, LOCAL_DIR), UUID.randomUUID().toString() + ".jpg");
        try (InputStream in = c.getContentResolver().openInputStream(source);
             OutputStream out = new FileOutputStream(target)) {
            if (in == null) throw new IOException("Image could not be opened");
            copy(in, out);
        } catch (IOException e) {
            target.delete();
            throw e;
        }
        if (!isDecodable(target)) { target.delete(); throw new IOException("That file is not a supported image"); }
        return target;
    }

    /** True if the uri points to a file we own (and may delete). */
    public static boolean isPrivate(Context c, String uri) {
        if (uri == null || !uri.startsWith("file://")) return false;
        String path = Uri.parse(uri).getPath();
        return path != null && path.startsWith(c.getFilesDir().getAbsolutePath());
    }

    public static void deletePrivate(Context c, String uri) {
        if (!isPrivate(c, uri)) return;
        String path = Uri.parse(uri).getPath();
        if (path != null) //noinspection ResultOfMethodCallIgnored
            new File(path).delete();
    }

    /** Returns {width, height} of an image without decoding it fully, or {0,0}. */
    public static int[] bounds(Context c, String uri) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = open(c, uri)) {
            BitmapFactory.decodeStream(in, null, o);
        } catch (Exception ignored) {}
        return new int[]{Math.max(0, o.outWidth), Math.max(0, o.outHeight)};
    }

    private static boolean isDecodable(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        return o.outWidth > 0 && o.outHeight > 0;
    }

    private static InputStream open(Context c, String uri) throws IOException {
        if (uri.startsWith("http")) {
            HttpURLConnection conn = (HttpURLConnection) new URL(uri).openConnection();
            conn.setConnectTimeout(15000);
        conn.setRequestProperty("User-Agent", CommonsClient.USER_AGENT);
            conn.setReadTimeout(20000);
            if (conn.getResponseCode() != 200) throw new IOException("HTTP " + conn.getResponseCode());
            return conn.getInputStream();
        }
        Uri u = Uri.parse(uri);
        if ("file".equals(u.getScheme()) && u.getPath() != null) return new FileInputStream(u.getPath());
        InputStream in = c.getContentResolver().openInputStream(u);
        if (in == null) throw new IOException("Cannot open image");
        return in;
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[16 * 1024];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > MAX_IMAGE_BYTES) throw new IOException("Image is too large");
            out.write(buf, 0, n);
        }
    }

    /** Copies any image (remote, private or content uri) into the public Pictures/Pinspire folder. */
    public static void exportToGallery(Context c, String uri, String name) throws IOException {
        try (InputStream in = open(c, uri)) {
            writeToGallery(c, name, out -> copy(in, out));
        }
    }

    public static void exportBitmapToGallery(Context c, Bitmap bmp, String name) throws IOException {
        writeToGallery(c, name, out -> {
            if (!bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)) throw new IOException("Could not encode image");
        });
    }

    interface Writer { void write(OutputStream out) throws IOException; }

    private static void writeToGallery(Context c, String name, Writer writer) throws IOException {
        String file = name.replaceAll("[^a-zA-Z0-9_-]", "_") + ".jpg";
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver r = c.getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, file);
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Pinspire");
            v.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri item = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (item == null) throw new IOException("Gallery is unavailable");
            try (OutputStream out = r.openOutputStream(item)) {
                if (out == null) throw new IOException("Gallery is unavailable");
                writer.write(out);
            } catch (IOException e) {
                r.delete(item, null, null);
                throw e;
            }
            v.clear();
            v.put(MediaStore.Images.Media.IS_PENDING, 0);
            r.update(item, v, null, null);
        } else {
            File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Pinspire");
            if (!folder.exists() && !folder.mkdirs()) throw new IOException("Could not create Pictures/Pinspire");
            File target = new File(folder, file);
            try (OutputStream out = new FileOutputStream(target)) { writer.write(out); }
            android.media.MediaScannerConnection.scanFile(c, new String[]{target.getPath()}, new String[]{"image/jpeg"}, null);
        }
    }

    /** A uri other apps can read, for the share sheet. Remote images are downloaded to cache first. */
    public static Uri shareableUri(Context c, String uri, String id) throws IOException {
        File file;
        if (uri.startsWith("http")) {
            File folder = new File(c.getCacheDir(), "shared");
            if (!folder.exists() && !folder.mkdirs()) throw new IOException("Cache unavailable");
            file = new File(folder, id.replaceAll("[^a-zA-Z0-9_-]", "_") + ".jpg");
            if (!file.exists() || file.length() == 0) {
                try (InputStream in = open(c, uri); OutputStream out = new FileOutputStream(file)) { copy(in, out); }
            }
        } else if (uri.startsWith("file://")) {
            file = new File(Uri.parse(uri).getPath());
        } else {
            return Uri.parse(uri);
        }
        return FileProvider.getUriForFile(c, c.getPackageName() + ".files", file);
    }

    public static Uri shareBitmap(Context c, Bitmap bmp, String name) throws IOException {
        File folder = new File(c.getCacheDir(), "shared");
        if (!folder.exists() && !folder.mkdirs()) throw new IOException("Cache unavailable");
        File file = new File(folder, name + ".jpg");
        try (OutputStream out = new FileOutputStream(file)) {
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out);
        }
        return FileProvider.getUriForFile(c, c.getPackageName() + ".files", file);
    }
}
