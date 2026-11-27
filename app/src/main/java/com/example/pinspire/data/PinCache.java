package com.example.pinspire.data;

import java.util.LinkedHashMap;
import java.util.Map;

/** In-memory lookup for discovered (unsaved) pins so screens can be opened by id. */
public final class PinCache {
    private static final int MAX = 500;
    private static final LinkedHashMap<String, Pin> CACHE = new LinkedHashMap<String, Pin>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Pin> e) { return size() > MAX; }
    };

    private PinCache() {}

    public static synchronized void put(Pin p) { if (p != null) CACHE.put(p.id, p); }

    public static synchronized Pin get(String id) { return CACHE.get(id); }
}
