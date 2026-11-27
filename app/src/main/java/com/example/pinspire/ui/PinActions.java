package com.example.pinspire.ui;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.view.Menu;
import android.view.View;
import android.widget.PopupMenu;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.pinspire.MainActivity;
import com.example.pinspire.data.AppExecutors;
import com.example.pinspire.data.Board;
import com.example.pinspire.data.ImageStore;
import com.example.pinspire.data.Palettes;
import com.example.pinspire.data.Pin;
import com.example.pinspire.data.PinRepository;
import com.example.pinspire.ui.feed.FeedSpec;
import com.example.pinspire.ui.feed.FeedViewModel;

/** Actions available on a pin from any screen. */
public final class PinActions {
    private PinActions() {}

    public static void showMenu(MainActivity a, View anchor, Pin p, @Nullable FeedViewModel feed) {
        PinRepository repo = PinRepository.get(a);
        Board last = repo.lastBoard();
        PopupMenu menu = new PopupMenu(a, anchor);
        Menu m = menu.getMenu();
        if (last != null && !last.pins.contains(p.id)) m.add(0, 1, 0, "Save to " + last.name);
        m.add(0, 2, 1, "Save to board…");
        m.add(0, 3, 2, "Share");
        if (p.hasImage()) m.add(0, 4, 3, "Download image");
        if (p.remote && feed != null && !repo.isSaved(p.id)) m.add(0, 5, 4, "Not interested");
        if (!p.remote && repo.find(p.id) != null) m.add(0, 6, 5, "Edit pin");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: repo.addToBoard(p, last, (ok, msg) -> MainActivity.notify(msg, ok ? "View" : null, ok ? () -> a.openBoard(last.name) : null)); return true;
                case 2: a.showSaveSheet(p); return true;
                case 3: share(a, p); return true;
                case 4: download(a, p); return true;
                case 5: notInterested(a, p, feed); return true;
                case 6: a.editPin(p.id); return true;
            }
            return false;
        });
        menu.show();
    }

    public static void notInterested(MainActivity a, Pin p, @Nullable FeedViewModel feed) {
        PinRepository.get(a).hide(p);
        if (feed != null) feed.remove(p.id);
        MainActivity.notify("Got it. You'll see fewer ideas like this.", null, null);
    }

    public static void share(MainActivity a, Pin p) {
        String text = p.title + (p.source.isEmpty() ? "" : "\n" + p.source) + "\n\nFound on Pinspire";
        if (!p.hasImage()) {
            startShare(a, text, null);
            return;
        }
        Context app = a.getApplicationContext();
        String image = p.cover();
        AppExecutors.io(() -> {
            Uri uri = null;
            try { uri = ImageStore.shareableUri(app, image, p.id); } catch (Exception ignored) {}
            final Uri shared = uri;
            AppExecutors.main(() -> {
                MainActivity live = MainActivity.current();
                if (live != null) startShare(live, text, shared);
            });
        });
    }

    private static void startShare(MainActivity a, String text, @Nullable Uri image) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.putExtra(Intent.EXTRA_TEXT, text);
        if (image != null) {
            send.setType("image/*");
            send.putExtra(Intent.EXTRA_STREAM, image);
            send.setClipData(ClipData.newRawUri("", image));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else send.setType("text/plain");
        try { a.startActivity(Intent.createChooser(send, "Share pin")); }
        catch (Exception e) { MainActivity.notify("No app available to share with", null, null); }
    }

    /** Returns false (and asks) when legacy storage permission is still needed. */
    public static boolean ensureStoragePermission(MainActivity a) {
        if (Build.VERSION.SDK_INT >= 29) return true;
        if (ContextCompat.checkSelfPermission(a, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) return true;
        ActivityCompat.requestPermissions(a, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 7);
        MainActivity.notify("Allow storage access, then try again", null, null);
        return false;
    }

    public static void download(MainActivity a, Pin p) {
        if (!p.hasImage() || !ensureStoragePermission(a)) return;
        Context app = a.getApplicationContext();
        String image = p.displayImage(0);
        MainActivity.notify("Downloading…", null, null);
        AppExecutors.io(() -> {
            String error = null;
            try { ImageStore.exportToGallery(app, image, "Pinspire-" + p.id); }
            catch (Exception e) { error = e.getMessage(); }
            final String err = error;
            AppExecutors.main(() -> MainActivity.notify(err == null ? "Saved to Pictures/Pinspire" : "Download failed: " + err, null, null));
        });
    }

    public static void copyHex(Context c, int color) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("color", Palettes.hex(color)));
        if (Build.VERSION.SDK_INT < 33) MainActivity.notify("Copied " + Palettes.hex(color), null, null);
    }

    /** Swatch tap: copy the hex code or search ideas in that shade. */
    public static void colorSheet(MainActivity a, int color, @Nullable String context) {
        String filter = Palettes.nearestFilter(color);
        String[] options = {"Copy " + Palettes.hex(color), "Find " + filter + " ideas" + (context == null || context.isEmpty() ? "" : " like this")};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(a)
                .setTitle(Palettes.hex(color))
                .setIcon(new android.graphics.drawable.ColorDrawable(color))
                .setItems(options, (d, which) -> {
                    if (which == 0) copyHex(a, color);
                    else a.openSearch(context == null ? "" : context, filter);
                })
                .show();
    }

    public static FeedSpec relatedSpec(Pin p) {
        StringBuilder q = new StringBuilder();
        int n = 0;
        for (String t : p.tagList()) { if (n++ == 2) break; q.append(q.length() == 0 ? "" : " ").append(t); }
        if (q.length() == 0) q.append(p.title);
        return new FeedSpec(FeedSpec.Mode.RELATED, q.toString(), "", p.id);
    }
}
