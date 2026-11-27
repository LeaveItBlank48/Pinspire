package com.example.pinspire;

import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.example.pinspire.data.Pin;
import com.example.pinspire.data.PinCache;
import com.example.pinspire.data.PinRepository;
import com.example.pinspire.ui.BoardDetailFragment;
import com.example.pinspire.ui.BoardsFragment;
import com.example.pinspire.ui.CreateFragment;
import com.example.pinspire.ui.DiscoverFragment;
import com.example.pinspire.ui.PinDetailFragment;
import com.example.pinspire.ui.SaveSheet;
import com.example.pinspire.ui.SearchFragment;
import com.example.pinspire.ui.StudioFragment;
import com.example.pinspire.ui.feed.FeedSpec;
import com.example.pinspire.ui.feed.FeedViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.lang.ref.WeakReference;

/** Single activity: bottom tabs plus a back stack of detail screens. */
public class MainActivity extends AppCompatActivity {
    private static final String TAB_TAG = "tab";
    private static WeakReference<MainActivity> live = new WeakReference<>(null);

    private BottomNavigationView nav;
    private View navDivider;
    private int currentTab = R.id.tab_discover;

    /** The visible activity, or null (used by callbacks that finish after a rotation). */
    @Nullable public static MainActivity current() {
        MainActivity a = live.get();
        return a == null || a.isFinishing() || a.isDestroyed() ? null : a;
    }

    /** Shows a message on whichever activity is currently alive. */
    public static void notify(String message, @Nullable String action, @Nullable Runnable onAction) {
        MainActivity a = current();
        if (a == null) return;
        Snackbar s = Snackbar.make(a.findViewById(R.id.container), message, Snackbar.LENGTH_LONG);
        if (a.nav.getVisibility() == View.VISIBLE) s.setAnchorView(a.nav);
        if (action != null && onAction != null) s.setAction(action, v -> onAction.run());
        s.show();
    }

    @Override protected void onCreate(@Nullable Bundle saved) {
        super.onCreate(saved);
        live = new WeakReference<>(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        nav = findViewById(R.id.nav);
        navDivider = findViewById(R.id.nav_divider);

        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            int ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            v.setPadding(bars.left, bars.top, bars.right, imeVisible ? Math.max(ime, bars.bottom) : 0);
            nav.setVisibility(imeVisible ? View.GONE : View.VISIBLE);
            navDivider.setVisibility(imeVisible ? View.GONE : View.VISIBLE);
            nav.setPadding(0, 0, 0, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        nav.setOnItemSelectedListener(item -> { showTab(item.getItemId()); return true; });
        nav.setOnItemReselectedListener(item -> onTabReselected(item.getItemId()));

        if (saved == null) showTab(R.id.tab_discover);
        else currentTab = saved.getInt("tab", R.id.tab_discover);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                FragmentManager fm = getSupportFragmentManager();
                if (fm.getBackStackEntryCount() > 0) {
                    fm.popBackStack();
                } else if (currentTab != R.id.tab_discover) {
                    nav.setSelectedItemId(R.id.tab_discover);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        live = new WeakReference<>(this);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", currentTab);
    }

    // ---------- Navigation ----------

    private void showTab(int id) {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.isStateSaved()) return;
        if (fm.getBackStackEntryCount() > 0) fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        Fragment f;
        if (id == R.id.tab_search) f = SearchFragment.newInstance(null, null, false);
        else if (id == R.id.tab_create) f = CreateFragment.newInstance(null);
        else if (id == R.id.tab_saved) f = new BoardsFragment();
        else f = new DiscoverFragment();
        currentTab = id;
        fm.beginTransaction().setReorderingAllowed(true)
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.container, f, TAB_TAG).commit();
    }

    private void onTabReselected(int id) {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.isStateSaved()) return;
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            return;
        }
        Fragment f = fm.findFragmentByTag(TAB_TAG);
        if (f instanceof DiscoverFragment) ((DiscoverFragment) f).onReselected();
    }

    public void selectTab(int id) { nav.setSelectedItemId(id); }

    private void push(Fragment f) {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.isStateSaved()) return;
        fm.beginTransaction().setReorderingAllowed(true)
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.container, f)
                .addToBackStack(null)
                .commit();
    }

    public void goBack() { getOnBackPressedDispatcher().onBackPressed(); }

    /** Logo / Discover tap: a fresh For You feed with filters cleared. Saved data is untouched. */
    public void freshHome() {
        new ViewModelProvider(this).get(DiscoverFragment.VM_KEY, FeedViewModel.class).restart(FeedSpec.forYou());
        if (currentTab != R.id.tab_discover || getSupportFragmentManager().getBackStackEntryCount() > 0) {
            if (currentTab == R.id.tab_discover) onTabReselected(R.id.tab_discover);
            else nav.setSelectedItemId(R.id.tab_discover);
        } else {
            Fragment f = getSupportFragmentManager().findFragmentByTag(TAB_TAG);
            if (f instanceof DiscoverFragment) ((DiscoverFragment) f).scrollToTop();
        }
    }

    public void openPin(Pin p) {
        PinCache.put(p);
        PinRepository.get(this).track(p, "view");
        push(PinDetailFragment.newInstance(p.id));
    }

    public void openBoard(String name) { push(BoardDetailFragment.newInstance(name)); }

    public void openStudio(String board) { push(StudioFragment.newInstance(board)); }

    public void openSearch(@Nullable String query, @Nullable String color) {
        push(SearchFragment.newInstance(query, color, true));
    }

    public void editPin(String id) { push(CreateFragment.newInstance(id)); }

    public void showSaveSheet(Pin p) {
        if (getSupportFragmentManager().isStateSaved()) return;
        PinCache.put(p);
        SaveSheet.newInstance(p.id).show(getSupportFragmentManager(), "save");
    }
}
