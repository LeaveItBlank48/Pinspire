package com.example.pinspire.ui.feed;

import android.app.Application;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.pinspire.data.AppExecutors;
import com.example.pinspire.data.CommonsClient;
import com.example.pinspire.data.Discovery;
import com.example.pinspire.data.Pin;
import com.example.pinspire.data.PinCache;
import com.example.pinspire.data.PinRepository;
import com.example.pinspire.data.PixabayClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

/**
 * Paged image feed. Survives rotation and back navigation, so the list and scroll position are kept.
 */
public class FeedViewModel extends AndroidViewModel {
    private static final int MAX_ITEMS = 900;

    public static final class State {
        public final FeedSpec spec;
        public final List<Pin> items;
        public final boolean loading, end;
        public final String error;
        State(FeedSpec spec, List<Pin> items, boolean loading, boolean end, String error) {
            this.spec = spec; this.items = items; this.loading = loading; this.end = end; this.error = error;
        }
    }

    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final PinRepository repo;
    private final Random random = new Random();
    private FeedSpec spec;
    private final ArrayList<Pin> items = new ArrayList<>();
    private final HashSet<String> seen = new HashSet<>();
    private final HashMap<String, Integer> nextPage = new HashMap<>();
    private final HashSet<String> exhausted = new HashSet<>();
    private boolean loading, end;
    private String error;
    private int generation;
    /** RecyclerView scroll state kept across view recreation. */
    public Parcelable listState;

    public FeedViewModel(@NonNull Application app) {
        super(app);
        repo = PinRepository.get(app);
    }

    public LiveData<State> state() { return state; }

    public FeedSpec spec() { return spec; }

    /** Shows the given feed, keeping current results if it is already showing. */
    public void start(FeedSpec next) {
        if (next.equals(spec) && (!items.isEmpty() || loading)) return;
        reset(next);
        loadMore();
    }

    /** Always starts over, even if this feed is already showing. */
    public void restart(FeedSpec next) {
        reset(next);
        loadMore();
    }

    public void refresh() {
        if (spec == null) return;
        reset(spec);
        loadMore();
    }

    /** Clears the error and tries again. */
    public void retry() {
        error = null;
        end = false;
        loadMore();
    }

    public void remove(String pinId) {
        for (int i = items.size() - 1; i >= 0; i--) if (items.get(i).id.equals(pinId)) items.remove(i);
        publish();
    }

    private void reset(FeedSpec next) {
        spec = next;
        generation++;
        items.clear();
        seen.clear();
        nextPage.clear();
        exhausted.clear();
        loading = false;
        end = false;
        error = null;
        listState = null;
        publish();
    }

    public boolean canLoadMore() { return spec != null && !loading && !end && error == null; }

    public void loadMore() {
        if (spec == null || loading || end) return;
        final Request req = nextRequest();
        if (req == null) { end = true; publish(); return; }
        loading = true;
        error = null;
        publish();
        final int gen = generation;
        final FeedSpec forSpec = spec;
        AppExecutors.io(() -> {
            PixabayClient.Page page = null;
            String failure = null;
            boolean permanent = false;
            try {
                page = PixabayClient.hasKey()
                        ? PixabayClient.search(getApplication(), req.query, req.category, req.color, req.page, req.label)
                        : CommonsClient.search(getApplication(), req.query, req.color, req.page, req.label);
            } catch (PixabayClient.FeedException e) {
                failure = e.getMessage();
                permanent = e.permanent;
            } catch (Exception e) {
                failure = "Something went wrong loading images.";
            }
            final PixabayClient.Page result = page;
            final String err = failure;
            final boolean fatal = permanent;
            AppExecutors.main(() -> {
                if (gen != generation) return; // Feed changed while loading.
                loading = false;
                if (err != null) {
                    error = err;
                    if (fatal) end = true;
                    publish();
                    return;
                }
                nextPage.put(req.key, req.page + 1);
                if (result.pins.isEmpty() || req.page * PixabayClient.PER_PAGE >= Math.min(result.totalHits, 500)) exhausted.add(req.key);
                int added = 0;
                for (Pin p : result.pins) {
                    if (p.id.equals(forSpec.exclude) || repo.isHidden(p.id) || !seen.add(p.id)) continue;
                    p.fromSpark = forSpec.mode == FeedSpec.Mode.SPARK;
                    PinCache.put(p);
                    items.add(p);
                    added++;
                }
                if (items.size() >= MAX_ITEMS || (isSingleQuery() && exhausted.contains(req.key))) end = true;
                publish();
                // A page made entirely of duplicates would stall infinite scroll; fetch the next one.
                if (added == 0 && !end) loadMore();
            });
        });
    }

    private boolean isSingleQuery() {
        return spec.mode != FeedSpec.Mode.FOR_YOU && spec.mode != FeedSpec.Mode.EXPLORE;
    }

    private static final class Request {
        String key, query, category, color, label;
        int page;
    }

    private Request nextRequest() {
        Request r = new Request();
        r.color = spec.color;
        switch (spec.mode) {
            case SEARCH:
            case RELATED:
                r.query = spec.value;
                r.category = "";
                r.label = spec.mode == FeedSpec.Mode.SEARCH ? spec.value : "Other";
                break;
            case SPARK: {
                Discovery.Spark s = Discovery.todaysSpark();
                r.query = s.query;
                r.category = "";
                if (r.color.isEmpty()) r.color = s.color;
                r.label = "Other";
                break;
            }
            case CATEGORY: {
                Discovery.Category c = Discovery.category(spec.value);
                if (c == null) return null;
                r.query = c.query;
                r.category = c.pixabayCategory;
                r.label = c.label;
                break;
            }
            default: {
                Discovery.Category c = pickCategory(spec.mode == FeedSpec.Mode.EXPLORE);
                if (c == null) return null;
                r.query = c.query;
                r.category = c.pixabayCategory;
                r.label = c.label;
            }
        }
        if (r.query == null) r.query = "";
        // An empty query is fine when browsing purely by color.
        if (r.query.trim().isEmpty() && r.color.isEmpty()) return null;
        r.key = r.query + "|" + r.category + "|" + r.color;
        if (exhausted.contains(r.key)) return null;
        Integer page = nextPage.get(r.key);
        if (page == null) {
            // Start mixed feeds at a random page so "refresh" brings genuinely new images.
            page = isSingleQuery() ? 1 : 1 + random.nextInt(spec.mode == FeedSpec.Mode.EXPLORE ? 5 : 3);
        }
        r.page = page;
        return r;
    }

    /** For You: weighted by local interest with 25% exploration. Explore: uniform random. */
    private Discovery.Category pickCategory(boolean randomOnly) {
        ArrayList<Discovery.Category> open = new ArrayList<>();
        for (Discovery.Category c : Discovery.CATEGORIES) {
            if (!exhausted.contains(c.query + "|" + c.pixabayCategory + "|" + spec.color)) open.add(c);
        }
        if (open.isEmpty()) return null;
        if (randomOnly || random.nextFloat() < .25f) {
            Collections.shuffle(open, random);
            return open.get(0);
        }
        int total = 0;
        for (Discovery.Category c : open) total += Math.max(1, 3 + repo.interest(c.label));
        int pick = random.nextInt(Math.max(1, total));
        for (Discovery.Category c : open) {
            pick -= Math.max(1, 3 + repo.interest(c.label));
            if (pick < 0) return c;
        }
        return open.get(0);
    }

    private void publish() {
        state.setValue(new State(spec, new ArrayList<>(items), loading, end, error));
    }
}
