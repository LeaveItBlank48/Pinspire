package com.example.pinspire.ui;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.pinspire.MainActivity;
import com.example.pinspire.R;
import com.example.pinspire.data.Discovery;
import com.example.pinspire.data.Palettes;
import com.example.pinspire.data.PinRepository;
import com.example.pinspire.ui.feed.FeedController;
import com.example.pinspire.ui.feed.FeedSpec;
import com.example.pinspire.ui.feed.FeedViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;
import java.util.Locale;

/** Search with recent queries, color browsing and idea suggestions. */
public class SearchFragment extends Fragment {
    private static final String ARG_QUERY = "query", ARG_COLOR = "color", ARG_PUSHED = "pushed";

    private FeedViewModel vm;
    private EditText query;
    private View suggestions, results, clear;
    private TextView resultsTitle;
    private ColorRow colorRow;
    private FeedController feed;

    public static SearchFragment newInstance(@Nullable String query, @Nullable String color, boolean pushed) {
        SearchFragment f = new SearchFragment();
        Bundle b = new Bundle();
        b.putString(ARG_QUERY, query);
        b.putString(ARG_COLOR, color);
        b.putBoolean(ARG_PUSHED, pushed);
        f.setArguments(b);
        return f;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        Bundle args = requireArguments();
        vm = new ViewModelProvider(this).get(FeedViewModel.class);
        query = view.findViewById(R.id.query);
        clear = view.findViewById(R.id.clear);
        suggestions = view.findViewById(R.id.suggestions);
        results = view.findViewById(R.id.refresh);

        View back = view.findViewById(R.id.back);
        back.setVisibility(args.getBoolean(ARG_PUSHED) ? View.VISIBLE : View.GONE);
        back.setOnClickListener(v -> ((MainActivity) requireActivity()).goBack());

        RecyclerView list = view.findViewById(R.id.list);
        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(Ui.dp(requireContext(), 5), 0, Ui.dp(requireContext(), 5), Ui.dp(requireContext(), 8));
        resultsTitle = new TextView(requireContext());
        resultsTitle.setTextAppearance(R.style.TextAppearance_Pinspire_Section);
        header.addView(resultsTitle);
        HorizontalScrollView colorScroll = new HorizontalScrollView(requireContext());
        colorScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout colors = new LinearLayout(requireContext());
        colors.setPadding(0, Ui.dp(requireContext(), 8), 0, Ui.dp(requireContext(), 4));
        colorScroll.addView(colors);
        header.addView(colorScroll);
        colorRow = new ColorRow(colors, c -> {
            if (vm.spec() != null) run(vm.spec().value, c);
        });

        feed = new FeedController(this, list, (SwipeRefreshLayout) results, header, vm, null);

        query.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                submit(query.getText().toString());
                return true;
            }
            return false;
        });
        query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (s.length() == 0 && vm.spec() != null && !vm.spec().value.isEmpty()) showSuggestions();
            }
        });
        clear.setOnClickListener(v -> {
            query.setText("");
            query.requestFocus();
            showSuggestions();
        });
        view.findViewById(R.id.clear_recent).setOnClickListener(v -> {
            PinRepository.get(requireContext()).clearRecentSearches();
            buildSuggestions(view);
        });

        buildSuggestions(view);

        if (vm.spec() != null) {
            showResults(vm.spec());
        } else {
            String q = args.getString(ARG_QUERY);
            String color = args.getString(ARG_COLOR);
            if ((q != null && !q.isEmpty()) || (color != null && !color.isEmpty())) {
                query.setText(q);
                run(q == null ? "" : q, color == null ? "" : color);
            } else showSuggestions();
        }
    }

    private void buildSuggestions(View view) {
        PinRepository repo = PinRepository.get(requireContext());
        List<String> recent = repo.recentSearches();
        view.findViewById(R.id.recent_header).setVisibility(recent.isEmpty() ? View.GONE : View.VISIBLE);
        ChipGroup recentChips = view.findViewById(R.id.recent_chips);
        recentChips.removeAllViews();
        recentChips.setVisibility(recent.isEmpty() ? View.GONE : View.VISIBLE);
        for (String r : recent) recentChips.addView(chip(r, R.drawable.ic_history, v -> { query.setText(r); submit(r); }));

        ChipGroup colorChips = view.findViewById(R.id.color_chips);
        colorChips.removeAllViews();
        for (int i = 0; i < Palettes.FILTER_NAMES.length; i++) {
            String name = Palettes.FILTER_NAMES[i];
            Chip c = chip(name.substring(0, 1).toUpperCase(Locale.US) + name.substring(1), 0, v -> run("", name));
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(Palettes.FILTER_SWATCHES[i]);
            dot.setStroke(Ui.dp(requireContext(), 1), Ui.color(requireContext(), R.color.line_strong));
            dot.setSize(Ui.dp(requireContext(), 18), Ui.dp(requireContext(), 18));
            c.setChipIcon(dot);
            c.setChipIconVisible(true);
            colorChips.addView(c);
        }

        ChipGroup ideas = view.findViewById(R.id.idea_chips);
        ideas.removeAllViews();
        Discovery.Spark spark = Discovery.todaysSpark();
        Chip sparkChip = chip("✦ " + spark.title, 0, v -> { query.setText(spark.query); run(spark.query, spark.color); });
        sparkChip.setChipBackgroundColor(ColorStateList.valueOf(Ui.color(requireContext(), R.color.spark_soft)));
        ideas.addView(sparkChip);
        for (Discovery.Category c : Discovery.CATEGORIES) ideas.addView(chip(c.label, 0, v -> { query.setText(c.query); submit(c.query); }));
    }

    private Chip chip(String text, int icon, View.OnClickListener click) {
        Chip c = new Chip(requireContext());
        c.setText(text);
        c.setCheckable(false);
        if (icon != 0) {
            c.setChipIconResource(icon);
            c.setChipIconTint(ColorStateList.valueOf(Ui.color(requireContext(), R.color.muted)));
            c.setChipIconVisible(true);
        }
        c.setOnClickListener(click);
        return c;
    }

    private void submit(String text) {
        String q = text == null ? "" : text.trim();
        if (q.isEmpty()) return;
        PinRepository.get(requireContext()).addRecentSearch(q);
        run(q, vm.spec() == null ? "" : vm.spec().color);
    }

    private void run(String q, String color) {
        Ui.hideKeyboard(query);
        query.clearFocus();
        FeedSpec spec = new FeedSpec(FeedSpec.Mode.SEARCH, q, color, "");
        vm.start(spec);
        showResults(spec);
        feed.scrollToTop();
    }

    private void showResults(FeedSpec spec) {
        suggestions.setVisibility(View.GONE);
        results.setVisibility(View.VISIBLE);
        String colorName = spec.color.isEmpty() ? "" : spec.color.substring(0, 1).toUpperCase(Locale.US) + spec.color.substring(1);
        if (spec.value.isEmpty()) resultsTitle.setText(colorName + " ideas");
        else resultsTitle.setText(spec.color.isEmpty() ? "Ideas for “" + spec.value + "”" : colorName + " · “" + spec.value + "”");
        colorRow.setSelected(spec.color);
    }

    private void showSuggestions() {
        View v = getView();
        if (v != null) buildSuggestions(v);
        suggestions.setVisibility(View.VISIBLE);
        results.setVisibility(View.GONE);
    }
}
