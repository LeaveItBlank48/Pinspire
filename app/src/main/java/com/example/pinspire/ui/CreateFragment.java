package com.example.pinspire.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.pinspire.MainActivity;
import com.example.pinspire.R;
import com.example.pinspire.data.AppExecutors;
import com.example.pinspire.data.Board;
import com.example.pinspire.data.Discovery;
import com.example.pinspire.data.ImageStore;
import com.example.pinspire.data.Palettes;
import com.example.pinspire.data.Pin;
import com.example.pinspire.data.PinRepository;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/** Create a pin from your photos, or edit one you made. Photos are copied into app storage. */
public class CreateFragment extends Fragment {
    private static final String ARG_EDIT = "edit";
    private static final int MAX_IMAGES = 10;
    private static final String NO_TOPIC = "No topic", NO_BOARD = "Don't add to a board";

    private final ArrayList<String> images = new ArrayList<>();
    private Pin editing;
    private PinRepository repo;
    private LinearLayout imageRow;
    private TextInputEditText title, description, tags;
    private MaterialAutoCompleteTextView category, board;
    private View publish, progress;
    private boolean busy;

    private final ActivityResultLauncher<PickVisualMediaRequest> picker =
            registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES), uris -> {
                if (uris == null) return;
                for (Uri u : uris) {
                    if (images.size() >= MAX_IMAGES) break;
                    try { requireContext().getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
                    catch (Exception ignored) {} // Photo-picker grants last for this session; we copy on publish.
                    String s = u.toString();
                    if (!images.contains(s)) images.add(s);
                }
                renderImages();
            });

    public static CreateFragment newInstance(@Nullable String editId) {
        CreateFragment f = new CreateFragment();
        Bundle b = new Bundle();
        b.putString(ARG_EDIT, editId);
        f.setArguments(b);
        return f;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_create, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        MainActivity main = (MainActivity) requireActivity();
        repo = PinRepository.get(requireContext());
        String editId = requireArguments().getString(ARG_EDIT);
        if (editId != null) {
            editing = repo.find(editId);
            if (editing == null) { view.post(main::goBack); return; }
        }
        imageRow = view.findViewById(R.id.images);
        title = view.findViewById(R.id.title);
        description = view.findViewById(R.id.description);
        tags = view.findViewById(R.id.tags);
        category = view.findViewById(R.id.category);
        board = view.findViewById(R.id.board);
        publish = view.findViewById(R.id.publish);
        progress = view.findViewById(R.id.progress);

        View back = view.findViewById(R.id.back);
        back.setVisibility(editing != null ? View.VISIBLE : View.GONE);
        back.setOnClickListener(v -> main.goBack());
        ((TextView) view.findViewById(R.id.heading)).setText(editing != null ? "Edit pin" : "Create a pin");
        ((TextView) publish).setText(editing != null ? "Save changes" : "Publish");

        String[] topics = new String[Discovery.CATEGORIES.length + 1];
        topics[0] = NO_TOPIC;
        for (int i = 0; i < Discovery.CATEGORIES.length; i++) topics[i + 1] = Discovery.CATEGORIES[i].label;
        category.setSimpleItems(topics);

        List<Board> boards = repo.boards();
        String[] boardNames = new String[boards.size() + 1];
        boardNames[0] = NO_BOARD;
        for (int i = 0; i < boards.size(); i++) boardNames[i + 1] = boards.get(i).name;
        board.setSimpleItems(boardNames);
        view.findViewById(R.id.board_layout).setVisibility(editing != null ? View.GONE : View.VISIBLE);

        if (state != null) {
            ArrayList<String> savedImages = state.getStringArrayList("images");
            if (savedImages != null) images.addAll(savedImages);
        } else if (editing != null) {
            images.addAll(editing.images);
            title.setText(editing.title);
            description.setText(editing.description);
            tags.setText(editing.tags);
            category.setText("Other".equals(editing.category) ? NO_TOPIC : editing.category, false);
        } else {
            category.setText(NO_TOPIC, false);
            Board last = repo.lastBoard();
            board.setText(last != null ? last.name : NO_BOARD, false);
        }
        renderImages();
        publish.setOnClickListener(v -> publish(main));
    }

    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putStringArrayList("images", images);
    }

    private void renderImages() {
        Context c = requireContext();
        imageRow.removeAllViews();
        int w = Ui.dp(c, 96), h = Ui.dp(c, 128), gap = Ui.dp(c, 10);
        for (int i = 0; i < images.size(); i++) {
            final String uri = images.get(i);
            FrameLayout tile = new FrameLayout(c);
            ShapeableImageView iv = new ShapeableImageView(c);
            iv.setShapeAppearanceModel(ShapeAppearanceModel.builder().setAllCornerSizes(Ui.dp(c, 14)).build());
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundColor(Ui.color(c, R.color.surface2));
            iv.setContentDescription("Photo " + (i + 1));
            Glide.with(iv).load(uri).into(iv);
            tile.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            ImageButton remove = new ImageButton(c);
            remove.setImageResource(R.drawable.ic_close);
            remove.setBackgroundResource(R.drawable.circle_scrim);
            remove.setImageTintList(android.content.res.ColorStateList.valueOf(0xff1b1b1b));
            remove.setContentDescription("Remove photo " + (i + 1));
            remove.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
            remove.setScaleType(ImageView.ScaleType.FIT_CENTER);
            remove.setOnClickListener(v -> { images.remove(uri); renderImages(); });
            FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28), Gravity.TOP | Gravity.END);
            rp.setMargins(0, Ui.dp(c, 6), Ui.dp(c, 6), 0);
            tile.addView(remove, rp);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
            lp.setMarginEnd(gap);
            imageRow.addView(tile, lp);
        }
        if (images.size() < MAX_IMAGES) {
            MaterialCardView add = new MaterialCardView(c);
            add.setRadius(Ui.dp(c, 14));
            add.setCardElevation(0);
            add.setStrokeWidth(Ui.dp(c, 1));
            add.setStrokeColor(Ui.color(c, R.color.line_strong));
            add.setCardBackgroundColor(Ui.color(c, R.color.paper));
            LinearLayout inner = new LinearLayout(c);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setGravity(Gravity.CENTER);
            ImageView plus = new ImageView(c);
            plus.setImageResource(R.drawable.ic_add);
            plus.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(c, R.color.ink)));
            inner.addView(plus, new LinearLayout.LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28)));
            TextView label = new TextView(c);
            label.setText(images.isEmpty() ? "Add photos" : "Add more");
            label.setTextSize(12);
            label.setGravity(Gravity.CENTER);
            label.setTextColor(Ui.color(c, R.color.ink));
            inner.addView(label);
            add.addView(inner, new FrameLayout.LayoutParams(-1, -1));
            add.setContentDescription("Add photos");
            add.setOnClickListener(v -> picker.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()));
            imageRow.addView(add, new LinearLayout.LayoutParams(w, h));
        }
    }

    private void publish(MainActivity main) {
        if (busy) return;
        String t = String.valueOf(title.getText()).trim();
        TextInputLayout titleLayout = requireView().findViewById(R.id.title_layout);
        if (t.isEmpty()) { titleLayout.setError("Give your pin a title"); title.requestFocus(); return; }
        titleLayout.setError(null);
        Ui.hideKeyboard(title);
        setBusy(true);

        final String desc = String.valueOf(description.getText()).trim();
        final String tagText = cleanTags(String.valueOf(tags.getText()));
        final String topic = NO_TOPIC.equals(category.getText().toString()) ? "Other" : category.getText().toString();
        final Board target = repo.board(board.getText().toString());
        final ArrayList<String> sources = new ArrayList<>(images);
        final Context app = requireContext().getApplicationContext();

        AppExecutors.io(() -> {
            ArrayList<String> stored = new ArrayList<>();
            int failed = 0;
            for (String s : sources) {
                if (s.startsWith("content://")) {
                    try { stored.add(Uri.fromFile(ImageStore.importLocal(app, Uri.parse(s))).toString()); }
                    catch (Exception e) { failed++; }
                } else stored.add(s);
            }
            int[] size = stored.isEmpty() ? new int[]{0, 0} : ImageStore.bounds(app, stored.get(0));
            int[] palette = stored.isEmpty() ? new int[0] : Palettes.extract(app, stored.get(0));
            final int failures = failed;
            AppExecutors.main(() -> {
                Pin result = commit(t, desc, tagText, topic, target, stored, size, palette, app);
                if (failures > 0) MainActivity.notify(failures + " photo(s) couldn't be added", null, null);
                if (getView() == null) return;
                setBusy(false);
                MainActivity live = MainActivity.current();
                if (live == null) return;
                if (editing != null) {
                    MainActivity.notify("Changes saved", null, null);
                    live.goBack();
                } else {
                    resetForm();
                    MainActivity.notify("Pin published", null, null);
                    live.openPin(result);
                }
            });
        });
    }

    private Pin commit(String t, String desc, String tagText, String topic, @Nullable Board target,
                       List<String> stored, int[] size, int[] palette, Context app) {
        if (editing != null) {
            ArrayList<String> before = new ArrayList<>(editing.images);
            editing.title = t;
            editing.description = desc;
            editing.tags = tagText;
            editing.category = topic;
            editing.images.clear();
            editing.images.addAll(stored);
            editing.uri = stored.isEmpty() ? "" : stored.get(0);
            editing.width = size[0];
            editing.height = size[1];
            if (palette.length > 0 || stored.isEmpty()) editing.palette = palette;
            for (String old : before) if (!stored.contains(old)) ImageStore.deletePrivate(app, old);
            repo.updatePin(editing);
            return editing;
        }
        int[] colors = {0xff8a9bbd, 0xffcf9f8a, 0xff8da89a, 0xffbba0bd, 0xffb99d79};
        Pin p = new Pin(repo.newId());
        p.title = t;
        p.description = desc;
        p.tags = tagText;
        p.category = topic;
        p.images.addAll(stored);
        p.uri = stored.isEmpty() ? "" : stored.get(0);
        p.width = size[0];
        p.height = size[1];
        p.palette = palette;
        p.color = colors[(int) (System.currentTimeMillis() % colors.length)];
        repo.addCreatedPin(p, target);
        return p;
    }

    private static String cleanTags(String raw) {
        StringBuilder out = new StringBuilder();
        for (String t : raw.split(",")) {
            String s = t.trim().replaceAll("^#", "");
            if (s.isEmpty()) continue;
            if (out.length() > 0) out.append(", ");
            out.append(s);
        }
        return out.toString();
    }

    private void resetForm() {
        images.clear();
        title.setText("");
        description.setText("");
        tags.setText("");
        category.setText(NO_TOPIC, false);
        renderImages();
    }

    private void setBusy(boolean b) {
        busy = b;
        publish.setEnabled(!b);
        progress.setVisibility(b ? View.VISIBLE : View.GONE);
    }
}
