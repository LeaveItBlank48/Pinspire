package com.example.pinspire.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.pinspire.MainActivity;
import com.example.pinspire.R;
import com.example.pinspire.data.AppExecutors;
import com.example.pinspire.data.Board;
import com.example.pinspire.data.Palettes;
import com.example.pinspire.data.Pin;
import com.example.pinspire.data.PinRepository;
import com.example.pinspire.ui.feed.FeedController;
import com.example.pinspire.ui.feed.FeedViewModel;
import com.example.pinspire.ui.widget.AbstractArtDrawable;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

/** Pin close-up: swipeable images, actions, Color DNA, private note and "More like this". */
public class PinDetailFragment extends Fragment {
    private static final String ARG_PIN = "pin";

    private Pin pin;
    private PinRepository repo;
    private View header;
    private MaterialButton save;
    private com.google.android.material.textfield.TextInputEditText note;

    public static PinDetailFragment newInstance(String pinId) {
        PinDetailFragment f = new PinDetailFragment();
        Bundle b = new Bundle();
        b.putString(ARG_PIN, pinId);
        f.setArguments(b);
        return f;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_detail, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        MainActivity main = (MainActivity) requireActivity();
        repo = PinRepository.get(requireContext());
        pin = repo.resolve(requireArguments().getString(ARG_PIN));
        view.findViewById(R.id.back).setOnClickListener(v -> main.goBack());
        if (pin == null) {
            // Discovered pins live in memory only; after the app is restarted they are gone.
            MainActivity.notify("That pin is no longer available", null, null);
            view.post(main::goBack);
            return;
        }

        RecyclerView list = view.findViewById(R.id.list);
        header = getLayoutInflater().inflate(R.layout.header_pin, new android.widget.FrameLayout(requireContext()), false);
        bindMedia();
        bindText(main);
        bindActions(main);
        bindPalette(main);

        FeedViewModel related = new ViewModelProvider(this).get(FeedViewModel.class);
        FeedController feed = new FeedController(this, list, null, header, related, null);
        feed.footer().setEmptyText("No similar ideas found.");
        related.start(PinActions.relatedSpec(pin));

        final boolean ownPin = !pin.remote && repo.find(pin.id) != null;
        repo.changes().observe(getViewLifecycleOwner(), v -> {
            if (ownPin && repo.find(pin.id) == null) { main.goBack(); return; }
            Pin stored = repo.find(pin.id);
            if (stored != null) pin = stored;
            bindSaveState(main);
        });
    }

    private void bindMedia() {
        View media = header.findViewById(R.id.media);
        ViewPager2 pager = header.findViewById(R.id.pager);
        TextView count = header.findViewById(R.id.page_count);
        float ratio = Math.max(0.75f, Math.min(1.45f, pin.ratio()));
        int width = getResources().getDisplayMetrics().widthPixels - Ui.dp(requireContext(), 24);
        ViewGroup.LayoutParams lp = media.getLayoutParams();
        lp.height = Math.round(width * ratio);
        media.setLayoutParams(lp);

        int n = pin.imageCount();
        pager.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ImageView iv = new ImageView(parent.getContext());
                iv.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
                iv.setScaleType(pin.width > 0 ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
                return new RecyclerView.ViewHolder(iv) {};
            }
            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
                ImageView iv = (ImageView) h.itemView;
                iv.setContentDescription(pin.title + (n > 1 ? ", image " + (position + 1) + " of " + n : ""));
                if (!pin.hasImage()) { iv.setImageDrawable(new AbstractArtDrawable(pin.color)); return; }
                Glide.with(iv)
                        .load(com.example.pinspire.data.ImageModels.of(pin.displayImage(position)))
                        .thumbnail(Glide.with(iv).load(com.example.pinspire.data.ImageModels.of(position == 0 ? pin.cover() : pin.displayImage(position))).sizeMultiplier(0.4f))
                        .error(new AbstractArtDrawable(pin.color))
                        .into(iv);
            }
            @Override public int getItemCount() { return n; }
        });
        count.setVisibility(n > 1 ? View.VISIBLE : View.GONE);
        count.setText("1 / " + n);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) { count.setText((position + 1) + " / " + n); }
        });
    }

    private void bindText(MainActivity main) {
        ((TextView) header.findViewById(R.id.title)).setText(pin.title);
        TextView desc = header.findViewById(R.id.description);
        desc.setText(pin.description);
        desc.setVisibility(pin.description.isEmpty() ? View.GONE : View.VISIBLE);

        TextView attribution = header.findViewById(R.id.attribution);
        if (pin.remote) {
            String by = pin.creator.isEmpty() ? "a contributor" : pin.creator;
            attribution.setText("Photo by " + by + " on " + sourceName() + (pin.license.isEmpty() ? "" : " · " + pin.license));
        } else if (!pin.category.isEmpty() && !"Other".equals(pin.category)) {
            attribution.setText("Your pin · " + pin.category);
        } else attribution.setText("Your pin");

        ChipGroup tags = header.findViewById(R.id.tags);
        List<String> list = pin.tagList();
        header.findViewById(R.id.tags_scroll).setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        for (String t : list) {
            Chip c = new Chip(requireContext());
            c.setText("#" + t.replace(' ', '_'));
            c.setCheckable(false);
            c.setOnClickListener(v -> main.openSearch(t, null));
            tags.addView(c);
        }

        note = header.findViewById(R.id.note);
        note.setText(pin.note);
        note.setOnFocusChangeListener((v, focused) -> { if (!focused) saveNote(); });
    }

    private String sourceName() { return pin.id.startsWith("commons-") ? "Wikimedia Commons" : "Pixabay"; }

    private void bindActions(MainActivity main) {
        save = header.findViewById(R.id.save);
        save.setOnClickListener(v -> main.showSaveSheet(pin));
        save.setOnLongClickListener(v -> {
            Board last = repo.lastBoard();
            if (last == null || last.pins.contains(pin.id)) return false;
            repo.addToBoard(pin, last, (ok, msg) -> MainActivity.notify(msg, null, null));
            return true;
        });
        header.findViewById(R.id.share).setOnClickListener(v -> PinActions.share(main, pin));
        View download = header.findViewById(R.id.download);
        download.setVisibility(pin.hasImage() ? View.VISIBLE : View.GONE);
        download.setOnClickListener(v -> PinActions.download(main, pin));
        header.findViewById(R.id.more).setOnClickListener(v -> showMore(main, v));
        header.findViewById(R.id.saved_in).setOnClickListener(v -> {
            List<Board> in = repo.boardsWith(pin.id);
            if (!in.isEmpty()) main.openBoard(in.get(0).name);
        });
        bindSaveState(main);
    }

    private void bindSaveState(MainActivity main) {
        if (header == null) return;
        boolean saved = repo.isSaved(pin.id);
        List<Board> in = repo.boardsWith(pin.id);
        boolean pending = saved && in.isEmpty();
        save.setText(pending ? "Saving…" : saved ? "Saved" : "Save");
        save.setBackgroundTintList(ColorStateList.valueOf(Ui.color(main, saved ? R.color.surface3 : R.color.ink)));
        save.setTextColor(Ui.color(main, saved ? R.color.ink : R.color.paper));
        View section = header.findViewById(R.id.saved_section);
        section.setVisibility(in.isEmpty() ? View.GONE : View.VISIBLE);
        if (!in.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (Board b : in) names.append(names.length() == 0 ? "" : ", ").append(b.name);
            ((TextView) header.findViewById(R.id.saved_in)).setText("Saved in " + names + "  →");
        }
    }

    private void showMore(MainActivity main, View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        Menu m = menu.getMenu();
        if (!pin.source.isEmpty()) m.add(0, 1, 0, "View original on " + sourceName());
        if (pin.remote && !repo.isSaved(pin.id)) m.add(0, 2, 1, "Not interested");
        boolean mine = !pin.remote && repo.find(pin.id) != null;
        if (mine) { m.add(0, 3, 2, "Edit pin"); m.add(0, 4, 3, "Delete pin"); }
        if (m.size() == 0) m.add(0, 5, 0, "Share").setOnMenuItemClickListener(i -> { PinActions.share(main, pin); return true; });
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(pin.source))); }
                    catch (Exception e) { MainActivity.notify("No browser available", null, null); }
                    return true;
                case 2:
                    PinActions.notInterested(main, pin, null);
                    main.goBack();
                    return true;
                case 3: main.editPin(pin.id); return true;
                case 4:
                    Dialogs.confirm(main, "Delete this pin?", "It will be removed from every board. This can't be undone.", "Delete", () -> {
                        repo.deletePin(pin);
                        MainActivity.notify("Pin deleted", null, null);
                    });
                    return true;
            }
            return false;
        });
        menu.show();
    }

    private void bindPalette(MainActivity main) {
        if (pin.palette.length > 0) { renderPalette(main); return; }
        if (!pin.hasImage()) { header.findViewById(R.id.dna_section).setVisibility(View.GONE); return; }
        final Pin target = pin;
        final android.content.Context app = requireContext().getApplicationContext();
        AppExecutors.io(() -> {
            int[] colors = Palettes.extract(app, target.cover());
            AppExecutors.main(() -> {
                target.palette = colors;
                if (repo.find(target.id) == target) repo.updatePin(target);
                if (getView() != null && header != null) renderPalette(main);
            });
        });
    }

    private void renderPalette(MainActivity main) {
        LinearLayout row = header.findViewById(R.id.dna_row);
        TextView hint = header.findViewById(R.id.dna_hint);
        row.removeAllViews();
        if (pin.palette.length == 0) { header.findViewById(R.id.dna_section).setVisibility(View.GONE); return; }
        String context = pin.tagList().isEmpty() ? "" : pin.tagList().get(0);
        for (int color : pin.palette) {
            LinearLayout cell = new LinearLayout(requireContext());
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            View dot = Ui.swatch(requireContext(), color, 42);
            ((LinearLayout.LayoutParams) dot.getLayoutParams()).setMarginEnd(0);
            cell.addView(dot);
            TextView hex = new TextView(requireContext());
            hex.setText(Palettes.hex(color));
            hex.setTextSize(10);
            hex.setTextColor(Ui.color(requireContext(), R.color.muted));
            hex.setPadding(0, Ui.dp(requireContext(), 4), 0, 0);
            cell.addView(hex);
            cell.setContentDescription("Color " + Palettes.hex(color));
            cell.setOnClickListener(v -> PinActions.colorSheet(main, color, context));
            row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
        }
        hint.setText("Tap a color to copy its code or find ideas in that shade");
    }

    private void saveNote() {
        if (note == null || pin == null || repo.find(pin.id) == null) return;
        String text = String.valueOf(note.getText());
        if (!text.trim().equals(pin.note)) repo.setNote(pin, text);
    }

    @Override public void onPause() {
        saveNote();
        super.onPause();
    }
}
