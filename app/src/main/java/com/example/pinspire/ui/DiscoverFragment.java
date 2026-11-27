package com.example.pinspire.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.example.pinspire.data.Pin;
import com.example.pinspire.data.PinRepository;
import com.example.pinspire.ui.feed.FeedController;
import com.example.pinspire.ui.feed.FeedSpec;
import com.example.pinspire.ui.feed.FeedViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

/** Home feed: Daily Spark, For You / Explore / categories, color filter and the masonry grid. */
public class DiscoverFragment extends Fragment {
    public static final String VM_KEY = "discover";

    private FeedViewModel vm;
    private FeedController feed;
    private ChipGroup modes;
    private ColorRow colors;
    private TextView sparkStatus, sparkLabel, streak;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_discover, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        MainActivity main = (MainActivity) requireActivity();
        PinRepository repo = PinRepository.get(requireContext());
        vm = new ViewModelProvider(requireActivity()).get(VM_KEY, FeedViewModel.class);

        RecyclerView list = view.findViewById(R.id.list);
        SwipeRefreshLayout refresh = view.findViewById(R.id.refresh);
        View header = getLayoutInflater().inflate(R.layout.header_discover, new android.widget.FrameLayout(requireContext()), false);

        Discovery.Spark spark = Discovery.todaysSpark();
        ((TextView) header.findViewById(R.id.spark_title)).setText(spark.title);
        ((TextView) header.findViewById(R.id.spark_prompt)).setText(spark.prompt);
        sparkStatus = header.findViewById(R.id.spark_status);
        sparkLabel = header.findViewById(R.id.spark_label);
        header.findViewById(R.id.spark_card).setOnClickListener(v -> {
            if (vm.spec() != null && vm.spec().mode == FeedSpec.Mode.SPARK) start(FeedSpec.forYou());
            else start(new FeedSpec(FeedSpec.Mode.SPARK, spark.title, "", ""));
        });

        modes = header.findViewById(R.id.modes);
        addMode("For you", FeedSpec.forYou());
        addMode("Explore", new FeedSpec(FeedSpec.Mode.EXPLORE, "", "", ""));
        for (Discovery.Category c : Discovery.CATEGORIES) addMode(c.label, new FeedSpec(FeedSpec.Mode.CATEGORY, c.label, "", ""));

        colors = new ColorRow(header.findViewById(R.id.colors), color -> {
            FeedSpec current = vm.spec() == null ? FeedSpec.forYou() : vm.spec();
            start(current.withColor(color));
        });

        streak = view.findViewById(R.id.streak);
        view.findViewById(R.id.logo).setOnClickListener(v -> main.freshHome());
        view.findViewById(R.id.search_button).setOnClickListener(v -> main.selectTab(R.id.tab_search));

        // Your own creations lead the default home feed.
        feed = new FeedController(this, list, refresh, header, vm, spec -> {
            List<Pin> out = new ArrayList<>();
            if (spec.mode == FeedSpec.Mode.FOR_YOU && spec.color.isEmpty()) {
                List<Pin> mine = repo.createdPins();
                for (int i = 0; i < mine.size() && i < 6; i++) out.add(mine.get(i));
            }
            return out;
        });
        feed.footer().setEmptyText("No ideas in this color yet. Try another color or category.");

        vm.state().observe(getViewLifecycleOwner(), s -> syncHeader());
        repo.changes().observe(getViewLifecycleOwner(), v -> syncHeader());
        if (vm.spec() == null) vm.start(FeedSpec.forYou());
    }

    private void addMode(String label, FeedSpec spec) {
        Chip chip = new Chip(requireContext());
        chip.setText(label);
        chip.setCheckable(true);
        chip.setTag(spec);
        chip.setOnClickListener(v -> start(spec.withColor(vm.spec() == null ? "" : vm.spec().color)));
        modes.addView(chip);
    }

    private void start(FeedSpec spec) {
        vm.start(spec);
        feed.scrollToTop();
    }

    private void syncHeader() {
        if (getView() == null || vm.spec() == null) return;
        FeedSpec spec = vm.spec();
        for (int i = 0; i < modes.getChildCount(); i++) {
            Chip chip = (Chip) modes.getChildAt(i);
            FeedSpec chipSpec = (FeedSpec) chip.getTag();
            chip.setChecked(chipSpec.mode == spec.mode && chipSpec.value.equals(spec.value));
        }
        colors.setSelected(spec.color);
        PinRepository repo = PinRepository.get(requireContext());
        int days = repo.sparkStreak();
        boolean active = spec.mode == FeedSpec.Mode.SPARK;
        sparkLabel.setText(active ? "Showing today's spark · tap to go back" : "Today's spark");
        if (repo.sparkDoneToday()) sparkStatus.setText("✓ Sparked today · " + Ui.count(days, "day", "days") + " in a row");
        else if (days > 0) sparkStatus.setText("Save one idea to keep your " + Ui.count(days, "day", "days") + " streak →");
        else sparkStatus.setText("Save one idea from today's spark to start a streak →");
        streak.setVisibility(days > 0 ? View.VISIBLE : View.GONE);
        streak.setText("✦ " + days);
        streak.setContentDescription(Ui.count(days, "day", "days") + " spark streak");
    }

    public void scrollToTop() { if (feed != null) feed.scrollToTop(); }

    public void onReselected() {
        if (feed == null) return;
        if (!feed.isAtTop()) feed.scrollToTop();
        else ((MainActivity) requireActivity()).freshHome();
    }
}
