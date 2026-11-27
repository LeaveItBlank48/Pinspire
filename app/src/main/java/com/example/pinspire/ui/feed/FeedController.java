package com.example.pinspire.ui.feed;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.pinspire.MainActivity;
import com.example.pinspire.R;
import com.example.pinspire.data.Pin;
import com.example.pinspire.data.PinRepository;
import com.example.pinspire.ui.PinActions;
import com.example.pinspire.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Wires a RecyclerView to a {@link FeedViewModel}: masonry layout, infinite scroll, pull to refresh,
 * footer states and scroll-position restore.
 */
public final class FeedController {
    /** Pins shown before the feed results (e.g. your own creations on the home feed). */
    public interface Prefix { List<Pin> get(FeedSpec spec); }

    private final Fragment fragment;
    private final RecyclerView list;
    private final StaggeredGridLayoutManager layout;
    private final PinAdapter pins;
    private final FooterAdapter footer;
    private final FeedViewModel vm;
    private final Prefix prefix;
    @Nullable private final SwipeRefreshLayout refresh;

    public FeedController(Fragment fragment, RecyclerView list, @Nullable SwipeRefreshLayout refresh,
                          @Nullable View header, FeedViewModel vm, @Nullable Prefix prefix) {
        this.fragment = fragment;
        this.list = list;
        this.refresh = refresh;
        this.vm = vm;
        this.prefix = prefix;
        PinRepository repo = PinRepository.get(fragment.requireContext());

        layout = new StaggeredGridLayoutManager(Ui.spanCount(fragment.requireContext()), StaggeredGridLayoutManager.VERTICAL);
        layout.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
        list.setLayoutManager(layout);
        list.setItemAnimator(null);

        pins = new PinAdapter(repo, new PinAdapter.Listener() {
            @Override public void open(Pin p) { main().openPin(p); }
            @Override public void longPress(Pin p, View anchor) { main().showSaveSheet(p); }
            @Override public void more(Pin p, View anchor) { PinActions.showMenu(main(), anchor, p, vm); }
        });
        footer = new FooterAdapter(vm::retry);
        ConcatAdapter.Config config = new ConcatAdapter.Config.Builder().setIsolateViewTypes(false).build();
        ConcatAdapter concat = new ConcatAdapter(config);
        if (header != null) concat.addAdapter(new SingleViewAdapter(header));
        concat.addAdapter(pins);
        concat.addAdapter(footer);
        list.setAdapter(concat);

        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || !vm.canLoadMore()) return;
                int[] last = layout.findLastVisibleItemPositions(null);
                int max = 0;
                for (int i : last) max = Math.max(max, i);
                if (max >= layout.getItemCount() - 12) vm.loadMore();
            }
        });

        if (refresh != null) {
            refresh.setColorSchemeResources(R.color.spark);
            refresh.setOnRefreshListener(vm::refresh);
        }

        LifecycleOwner owner = fragment.getViewLifecycleOwner();
        vm.state().observe(owner, this::render);
        repo.changes().observe(owner, v -> {
            pins.refreshSaved();
            FeedViewModel.State s = vm.state().getValue();
            if (s != null && prefix != null) render(s);
        });
        owner.getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override public void onDestroy(@NonNull LifecycleOwner o) {
                vm.listState = layout.onSaveInstanceState();
                list.setAdapter(null);
            }
        });
    }

    private MainActivity main() { return (MainActivity) fragment.requireActivity(); }

    public FooterAdapter footer() { return footer; }

    private void render(FeedViewModel.State s) {
        if (s == null) return;
        List<Pin> all = new ArrayList<>();
        if (prefix != null && s.spec != null) all.addAll(prefix.get(s.spec));
        all.addAll(s.items);
        pins.submit(all);
        footer.setState(s.loading, s.error, s.end, all.isEmpty());
        if (refresh != null && !s.loading) refresh.setRefreshing(false);
        if (vm.listState != null && !all.isEmpty()) {
            layout.onRestoreInstanceState(vm.listState);
            vm.listState = null;
        }
    }

    public boolean isAtTop() { return !list.canScrollVertically(-1); }

    public void scrollToTop() {
        if (layout.getItemCount() == 0) return;
        int[] first = layout.findFirstVisibleItemPositions(null);
        int min = Integer.MAX_VALUE;
        for (int i : first) min = Math.min(min, i);
        if (min > 30) list.scrollToPosition(12);
        list.smoothScrollToPosition(0);
    }
}
