package com.example.pinspire.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Single source of truth for pins, boards and on-device preferences. All methods run on the main thread;
 * slow work (downloads, palette extraction) is pushed to {@link AppExecutors}.
 *
 * Storage stays in the "MainActivity" preferences file under "data" so 1.x installs keep their content.
 */
public final class PinRepository {
    public interface Callback { void done(boolean ok, String message); }

    // Activity.getPreferences() in 1.x used the activity's class name as the file name.
    private static final String PREFS = "MainActivity";
    private static final int MAX_HIDDEN = 600;
    private static final int MAX_RECENT = 8;

    private static PinRepository instance;

    public static synchronized PinRepository get(Context c) {
        if (instance == null) instance = new PinRepository(c.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final SharedPreferences prefs;
    private final ArrayList<Pin> pins = new ArrayList<>();
    private final ArrayList<Board> boards = new ArrayList<>();
    private final HashMap<String, Integer> interests = new HashMap<>();
    private final LinkedHashSet<String> hidden = new LinkedHashSet<>();
    private final ArrayList<String> recent = new ArrayList<>();
    private final HashMap<String, ArrayList<Board>> pendingSaves = new HashMap<>();
    private final MutableLiveData<Long> changes = new MutableLiveData<>(0L);
    private int nextId = 1;
    private long version;

    private PinRepository(Context app) {
        this.app = app;
        this.prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
        loadExtras();
        cleanupOrphans();
        backfillPalettes();
    }

    // ---------- Change notification ----------

    public LiveData<Long> changes() { return changes; }

    private void changed() {
        persist();
        changes.setValue(++version);
    }

    // ---------- Queries ----------

    @Nullable public Pin find(String id) {
        if (id == null) return null;
        for (Pin p : pins) if (p.id.equals(id)) return p;
        return null;
    }

    /** Stored version if saved, otherwise the in-memory discovered pin. */
    @Nullable public Pin resolve(String id) {
        Pin p = find(id);
        return p != null ? p : PinCache.get(id);
    }

    public List<Pin> createdPins() {
        ArrayList<Pin> out = new ArrayList<>();
        for (Pin p : pins) if (!p.remote) out.add(p);
        return out;
    }

    public List<Board> boards() { return new ArrayList<>(boards); }

    @Nullable public Board board(String name) {
        for (Board b : boards) if (b.name.equals(name)) return b;
        return null;
    }

    public List<Pin> pinsIn(Board b) {
        ArrayList<Pin> out = new ArrayList<>();
        if (b == null) return out;
        for (String id : b.pins) {
            Pin p = find(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    public boolean isSaved(String pinId) {
        for (Board b : boards) if (b.pins.contains(pinId)) return true;
        return pendingSaves.containsKey(pinId);
    }

    public List<Board> boardsWith(String pinId) {
        ArrayList<Board> out = new ArrayList<>();
        for (Board b : boards) if (b.pins.contains(pinId)) out.add(b);
        return out;
    }

    public int savedPinCount() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (Board b : boards) ids.addAll(b.pins);
        return ids.size();
    }

    @Nullable public Board lastBoard() {
        Board b = board(prefs.getString("lastBoard", ""));
        if (b == null && !boards.isEmpty()) b = boards.get(0);
        return b;
    }

    // ---------- Boards ----------

    /** Returns an error message, or null on success. */
    @Nullable public String createBoard(String name, String description) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) return "Give your board a name";
        if (clean.length() > 50) return "Board names can be up to 50 characters";
        if (Board.CREATED.equals(clean) || board(clean) != null) return "You already have a board called " + clean;
        Board b = new Board(clean);
        b.description = description == null ? "" : description.trim();
        b.createdAt = System.currentTimeMillis();
        boards.add(0, b);
        changed();
        return null;
    }

    @Nullable public String editBoard(Board b, String newName, String description) {
        String clean = newName == null ? "" : newName.trim();
        if (clean.isEmpty()) return "Give your board a name";
        if (clean.length() > 50) return "Board names can be up to 50 characters";
        Board other = board(clean);
        if (other != null && other != b) return "You already have a board called " + clean;
        if (b.name.equals(prefs.getString("lastBoard", ""))) prefs.edit().putString("lastBoard", clean).apply();
        b.name = clean;
        b.description = description == null ? "" : description.trim();
        changed();
        return null;
    }

    public void deleteBoard(Board b) {
        boards.remove(b);
        changed();
    }

    public void saveStudio(Board b, String layout) {
        b.studio = layout;
        persist();
    }

    // ---------- Saving pins ----------

    public void addToBoard(Pin pin, Board board, Callback cb) {
        if (board.pins.contains(pin.id)) { cb.done(true, "Already in " + board.name); return; }
        rememberBoard(board);
        Pin stored = find(pin.id);
        if (stored != null || !pin.remote || !pin.cover().startsWith("http")) {
            if (stored == null) pins.add(pin);
            board.pins.add(0, pin.id);
            onSaved(stored != null ? stored : pin);
            changed();
            cb.done(true, "Saved to " + board.name);
            return;
        }
        // Remote pin: keep a private copy so the image outlives Pixabay's temporary URLs.
        ArrayList<Board> waiting = pendingSaves.get(pin.id);
        if (waiting != null) { if (!waiting.contains(board)) waiting.add(board); cb.done(true, "Saving to " + board.name + "..."); return; }
        waiting = new ArrayList<>();
        waiting.add(board);
        pendingSaves.put(pin.id, waiting);
        changes.setValue(++version);
        final Pin snapshot = pin.copy();
        AppExecutors.io(() -> {
            File file = null;
            String error = null;
            try {
                try {
                    file = ImageStore.download(app, snapshot.fullUrl.isEmpty() ? snapshot.cover() : snapshot.fullUrl, snapshot.id);
                } catch (Exception large) {
                    file = ImageStore.download(app, snapshot.cover(), snapshot.id);
                }
                String local = Uri.fromFile(file).toString();
                if (snapshot.palette.length == 0) snapshot.palette = Palettes.extract(app, local);
                snapshot.uri = local;
                snapshot.images.clear();
                snapshot.images.add(local);
                int[] size = ImageStore.bounds(app, local);
                if (size[0] > 0) { snapshot.width = size[0]; snapshot.height = size[1]; }
            } catch (Exception e) {
                error = e.getMessage();
            }
            final String failure = error;
            AppExecutors.main(() -> {
                ArrayList<Board> targets = pendingSaves.remove(snapshot.id);
                if (failure != null) {
                    changes.setValue(++version);
                    cb.done(false, "Couldn't save the image. Check your connection and try again.");
                    return;
                }
                if (find(snapshot.id) == null) {
                    snapshot.createdAt = System.currentTimeMillis();
                    pins.add(snapshot);
                }
                if (targets != null) for (Board t : targets) {
                    if (boards.contains(t) && !t.pins.contains(snapshot.id)) t.pins.add(0, snapshot.id);
                }
                // Let the on-screen copy pick up the saved file and palette.
                pin.palette = snapshot.palette;
                onSaved(snapshot);
                changed();
                cb.done(true, "Saved to " + board.name);
            });
        });
    }

    private void onSaved(Pin p) {
        track(p, "save");
        if (p.fromSpark) markSpark();
    }

    private void rememberBoard(Board b) { prefs.edit().putString("lastBoard", b.name).apply(); }

    public void removeFromBoard(Pin pin, Board board) {
        board.pins.remove(pin.id);
        changed();
    }

    public void restoreToBoard(Pin pin, Board board, int index) {
        if (!boards.contains(board) || board.pins.contains(pin.id)) return;
        if (find(pin.id) == null) pins.add(pin);
        board.pins.add(Math.max(0, Math.min(index, board.pins.size())), pin.id);
        changed();
    }

    // ---------- Created pins ----------

    public String newId() {
        String id;
        do { id = "p" + (nextId++); } while (find(id) != null);
        return id;
    }

    public void addCreatedPin(Pin p, @Nullable Board board) {
        p.createdAt = System.currentTimeMillis();
        pins.add(0, p);
        if (board != null && boards.contains(board)) { board.pins.add(0, p.id); rememberBoard(board); }
        changed();
    }

    /** Persists edits made to a stored pin object. */
    public void updatePin(Pin p) {
        if (find(p.id) != null) changed();
    }

    public void setNote(Pin p, String note) {
        Pin stored = find(p.id);
        if (stored == null) return;
        stored.note = note == null ? "" : note.trim();
        p.note = stored.note;
        persist();
    }

    public void deletePin(Pin p) {
        Pin stored = find(p.id);
        if (stored == null) return;
        pins.remove(stored);
        for (Board b : boards) b.pins.remove(stored.id);
        for (String img : stored.images) ImageStore.deletePrivate(app, img);
        ImageStore.deletePrivate(app, stored.uri);
        changed();
    }

    // ---------- Personalization ----------

    public void track(Pin p, String event) {
        if (p == null || p.category == null || "Other".equals(p.category)) return;
        int delta = "save".equals(event) ? 5 : "negative".equals(event) ? -6 : 1;
        int next = Math.max(-20, Math.min(100, interest(p.category) + delta));
        interests.put(p.category, next);
        JSONObject j = new JSONObject();
        try { for (Map.Entry<String, Integer> e : interests.entrySet()) j.put(e.getKey(), e.getValue()); } catch (Exception ignored) {}
        prefs.edit().putString("interests", j.toString()).apply();
    }

    public int interest(String category) {
        Integer v = interests.get(category);
        return v == null ? 0 : v;
    }

    public void hide(Pin p) {
        hidden.add(p.id);
        while (hidden.size() > MAX_HIDDEN) {
            Iterator<String> it = hidden.iterator();
            it.next();
            it.remove();
        }
        prefs.edit().putString("hidden", new JSONArray(hidden).toString()).apply();
        track(p, "negative");
    }

    public boolean isHidden(String id) { return hidden.contains(id); }

    public List<String> recentSearches() { return new ArrayList<>(recent); }

    public void addRecentSearch(String q) {
        String clean = q == null ? "" : q.trim();
        if (clean.isEmpty()) return;
        for (int i = recent.size() - 1; i >= 0; i--) if (recent.get(i).equalsIgnoreCase(clean)) recent.remove(i);
        recent.add(0, clean);
        while (recent.size() > MAX_RECENT) recent.remove(recent.size() - 1);
        prefs.edit().putString("recentSearches", new JSONArray(recent).toString()).apply();
    }

    public void clearRecentSearches() {
        recent.clear();
        prefs.edit().remove("recentSearches").apply();
    }

    // ---------- Daily Spark streak ----------

    public static long today() { return TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() + java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis())); }

    private void markSpark() {
        long day = today();
        long last = prefs.getLong("sparkDay", -10);
        if (last == day) return;
        int streak = last == day - 1 ? prefs.getInt("sparkStreak", 0) + 1 : 1;
        prefs.edit().putLong("sparkDay", day).putInt("sparkStreak", streak).apply();
    }

    /** Current streak; resets to 0 if yesterday's spark was missed. */
    public int sparkStreak() {
        long last = prefs.getLong("sparkDay", -10);
        return last >= today() - 1 ? prefs.getInt("sparkStreak", 0) : 0;
    }

    public boolean sparkDoneToday() { return prefs.getLong("sparkDay", -10) == today(); }

    // ---------- Persistence ----------

    private void persist() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("version", 2);
            obj.put("nextId", nextId);
            JSONArray a = new JSONArray();
            for (Pin p : pins) a.put(p.toJson());
            obj.put("posts", a);
            JSONArray b = new JSONArray();
            for (Board board : boards) b.put(board.toJson());
            obj.put("boards", b);
            prefs.edit().putString("data", obj.toString()).apply();
        } catch (Exception e) {
            android.util.Log.e("Pinspire", "Could not persist data", e);
        }
    }

    private void load() {
        String raw = prefs.getString("data", null);
        if (raw == null) { seed(); persist(); return; }
        try {
            JSONObject obj = new JSONObject(raw);
            nextId = obj.optInt("nextId", 1);
            JSONArray a = obj.optJSONArray("posts");
            if (a != null) for (int i = 0; i < a.length(); i++) {
                JSONObject j = a.optJSONObject(i);
                Pin p = j == null ? null : Pin.fromJson(j);
                if (p != null && p.palette.length == 0 && !p.hasImage()) p.palette = new int[]{p.color};
                if (p != null && find(p.id) == null) pins.add(p);
            }
            JSONArray b = obj.optJSONArray("boards");
            if (b != null) for (int i = 0; i < b.length(); i++) {
                JSONObject j = b.optJSONObject(i);
                Board board = j == null ? null : Board.fromJson(j);
                if (board != null && board(board.name) == null) boards.add(board);
            }
        } catch (Exception e) {
            // Never silently wipe a user's data: keep the unreadable copy for recovery.
            prefs.edit().putString("data_backup_" + System.currentTimeMillis(), raw).apply();
            pins.clear();
            boards.clear();
            seed();
            persist();
        }
    }

    private void loadExtras() {
        try {
            JSONObject j = new JSONObject(prefs.getString("interests", "{}"));
            for (Iterator<String> it = j.keys(); it.hasNext(); ) {
                String k = it.next();
                interests.put(k, j.optInt(k));
            }
        } catch (Exception ignored) {}
        try {
            JSONArray h = new JSONArray(prefs.getString("hidden", "[]"));
            for (int i = 0; i < h.length(); i++) hidden.add(h.optString(i));
        } catch (Exception ignored) {}
        try {
            JSONArray r = new JSONArray(prefs.getString("recentSearches", "[]"));
            for (int i = 0; i < r.length() && i < MAX_RECENT; i++) recent.add(r.optString(i));
        } catch (Exception ignored) {}
    }

    /** Saved Pixabay pins that were removed from every board are deleted along with their image. */
    private void cleanupOrphans() {
        LinkedHashSet<String> used = new LinkedHashSet<>();
        for (Board b : boards) used.addAll(b.pins);
        boolean dirty = false;
        for (Iterator<Pin> it = pins.iterator(); it.hasNext(); ) {
            Pin p = it.next();
            if (p.remote && !used.contains(p.id)) {
                for (String img : p.images) ImageStore.deletePrivate(app, img);
                it.remove();
                dirty = true;
            }
        }
        if (dirty) persist();
    }

    /** Computes Color DNA for older saved pins in the background, a few per launch. */
    private void backfillPalettes() {
        ArrayList<Pin> todo = new ArrayList<>();
        for (Pin p : pins) if (p.palette.length == 0 && p.hasImage() && todo.size() < 24) todo.add(p);
        if (todo.isEmpty()) return;
        AppExecutors.io(() -> {
            HashMap<String, int[]> results = new HashMap<>();
            for (Pin p : todo) {
                int[] pal = Palettes.extract(app, p.cover());
                if (pal.length > 0) results.put(p.id, pal);
            }
            AppExecutors.main(() -> {
                if (results.isEmpty()) return;
                for (Map.Entry<String, int[]> e : results.entrySet()) {
                    Pin p = find(e.getKey());
                    if (p != null) p.palette = e.getValue();
                }
                changed();
            });
        });
    }

    private void seed() {
        String[] titles = {"Cozy Reading Corner", "Weekend Travel", "Minimal Outfit", "Coffee Inspiration", "Green Kitchen", "Creative Workspace"};
        String[] desc = {"A warm, comfortable corner for reading.", "Ideas for a peaceful weekend trip.", "Simple everyday fashion inspiration.", "A cozy café atmosphere.", "Plants and natural colors for a kitchen.", "A tidy desk for creative projects."};
        int[] colors = {0xffb58b73, 0xff7299b2, 0xffa88c9d, 0xffb99d79, 0xff87a28b, 0xff9693b6};
        for (int i = 0; i < titles.length; i++) {
            Pin p = new Pin("p" + (nextId++));
            p.title = titles[i];
            p.description = desc[i];
            p.color = colors[i];
            p.palette = new int[]{colors[i]};
            pins.add(p);
        }
        Board fav = new Board("Favorites");
        fav.createdAt = System.currentTimeMillis();
        boards.add(fav);
    }
}
