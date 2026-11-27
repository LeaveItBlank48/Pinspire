package com.example.pinspire.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.pinspire.MainActivity;
import com.example.pinspire.R;
import com.example.pinspire.data.Board;
import com.example.pinspire.data.Pin;
import com.example.pinspire.data.PinRepository;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.List;

/** Bottom sheet for choosing which board(s) a pin goes into. Tapping a checked board removes it. */
public class SaveSheet extends BottomSheetDialogFragment {
    private static final String ARG_PIN = "pin";

    public static SaveSheet newInstance(String pinId) {
        SaveSheet s = new SaveSheet();
        Bundle b = new Bundle();
        b.putString(ARG_PIN, pinId);
        s.setArguments(b);
        return s;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        Context c = requireContext();
        PinRepository repo = PinRepository.get(c);
        Pin pin = repo.resolve(requireArguments().getString(ARG_PIN));
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 24));
        if (pin == null) { dismissAllowingStateLoss(); return root; }

        TextView title = new TextView(c);
        title.setText("Save to board");
        title.setTextAppearance(R.style.TextAppearance_Pinspire_Section);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView subtitle = new TextView(c);
        subtitle.setText(pin.title);
        subtitle.setTextColor(Ui.color(c, R.color.muted));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setMaxLines(1);
        subtitle.setPadding(Ui.dp(c, 24), Ui.dp(c, 2), Ui.dp(c, 24), Ui.dp(c, 12));
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout rows = new LinearLayout(c);
        rows.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(c);
        scroll.addView(rows);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        rows.addView(row(c, null, "Create board", null, false, v -> createBoard(pin)));
        List<Board> boards = repo.boards();
        for (Board b : boards) {
            boolean has = b.pins.contains(pin.id);
            List<Pin> inBoard = repo.pinsIn(b);
            Pin cover = inBoard.isEmpty() ? null : inBoard.get(0);
            rows.addView(row(c, cover, b.name, Ui.count(b.pins.size(), "pin", "pins"), has, v -> {
                dismissAllowingStateLoss();
                if (has) {
                    int index = b.pins.indexOf(pin.id);
                    repo.removeFromBoard(pin, b);
                    MainActivity.notify("Removed from " + b.name, "Undo", () -> repo.restoreToBoard(pin, b, index));
                } else save(pin, b);
            }));
        }
        return root;
    }

    private static void save(Pin pin, Board b) {
        MainActivity a = MainActivity.current();
        if (a == null) return;
        PinRepository.get(a).addToBoard(pin, b, (ok, msg) ->
                MainActivity.notify(msg, ok ? "View" : null, ok ? () -> { MainActivity m = MainActivity.current(); if (m != null) m.openBoard(b.name); } : null));
    }

    private void createBoard(Pin pin) {
        Context c = requireContext();
        PinRepository repo = PinRepository.get(c);
        dismissAllowingStateLoss();
        Dialogs.boardEditor(c, "New board", null, "Create & save", (name, desc) -> {
            String error = repo.createBoard(name, desc);
            if (error == null) {
                Board b = repo.board(name.trim());
                if (b != null) save(pin, b);
            }
            return error;
        });
    }

    private View row(Context c, @Nullable Pin cover, String name, @Nullable String meta, boolean checked, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(c, 20), Ui.dp(c, 8), Ui.dp(c, 20), Ui.dp(c, 8));
        android.util.TypedValue tv = new android.util.TypedValue();
        c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        row.setBackgroundResource(tv.resourceId);
        row.setOnClickListener(click);

        ShapeableImageView thumb = new ShapeableImageView(c);
        thumb.setShapeAppearanceModel(ShapeAppearanceModel.builder().setAllCornerSizes(Ui.dp(c, 12)).build());
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackgroundColor(Ui.color(c, R.color.surface2));
        if (cover != null) Ui.loadPin(thumb, cover);
        else if (meta == null) {
            thumb.setImageResource(R.drawable.ic_add);
            thumb.setScaleType(ImageView.ScaleType.CENTER);
            thumb.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(c, R.color.ink)));
        }
        row.addView(thumb, new LinearLayout.LayoutParams(Ui.dp(c, 52), Ui.dp(c, 52)));

        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(Ui.dp(c, 14), 0, 0, 0);
        TextView n = new TextView(c);
        n.setText(name);
        n.setTextSize(16);
        n.setTextColor(Ui.color(c, R.color.ink));
        n.setTypeface(null, android.graphics.Typeface.BOLD);
        text.addView(n);
        if (meta != null) {
            TextView m = new TextView(c);
            m.setText(meta);
            m.setTextSize(12);
            m.setTextColor(Ui.color(c, R.color.muted));
            text.addView(m);
        }
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));

        if (checked) {
            ImageView check = new ImageView(c);
            check.setImageResource(R.drawable.ic_check);
            check.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(c, R.color.spark)));
            check.setContentDescription("Saved here. Tap to remove.");
            row.addView(check, new LinearLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24)));
        }
        return row;
    }
}
