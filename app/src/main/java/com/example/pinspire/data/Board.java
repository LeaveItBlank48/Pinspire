package com.example.pinspire.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

/** A named collection of pin ids. Stored with the same "name"/"saved" keys as 1.x. */
public class Board {
    /** Pseudo-board that lists the user's own created pins. */
    public static final String CREATED = "__created__";

    public String name;
    public String description = "";
    public long createdAt;
    public final ArrayList<String> pins = new ArrayList<>();
    /** Saved Moodboard Studio layout (JSON), or empty. */
    public String studio = "";

    public Board(String name) { this.name = name; }

    JSONObject toJson() throws Exception {
        JSONObject j = new JSONObject();
        j.put("name", name);
        j.put("saved", new JSONArray(pins));
        j.put("description", description);
        j.put("createdAt", createdAt);
        j.put("studio", studio);
        return j;
    }

    static Board fromJson(JSONObject j) {
        String name = j.optString("name", "").trim();
        if (name.isEmpty()) return null;
        Board b = new Board(name);
        JSONArray arr = j.optJSONArray("saved");
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            String id = arr.optString(i, "");
            if (!id.isEmpty() && !b.pins.contains(id)) b.pins.add(id);
        }
        b.description = j.optString("description", "");
        b.createdAt = j.optLong("createdAt", 0);
        b.studio = j.optString("studio", "");
        return b;
    }
}
