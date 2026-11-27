package com.example.pinspire.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.example.pinspire.MainActivity;
import com.example.pinspire.R;
import com.example.pinspire.data.Board;
import com.example.pinspire.data.Palettes;
import com.example.pinspire.data.Pin;
import com.example.pinspire.data.PinRepository;
import com.example.pinspire.ui.feed.PinAdapter;
import com.example.pinspire.ui.feed.SingleViewAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One board: mood strip, studio entry point, and its pins. Also shows "Your creations". */
public class BoardDetailFragment extends Fragment {
    private static final String ARG_BOARD = "board";

    private PinRepository repo;
    private String name;
    private View header;
    private PinAdapter adapter;

    public static BoardDetailFragment newInstance(String board) {
        BoardDetailFragment f = new BoardDetailFragment();
        Bundle b = new Bundle();
        b.putString(ARG_BOARD, board);
        f.setArguments(b);
        return f;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_board, container, false);
    }

    private boolean isCreated() { return Board.CREATED.equals(name); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        MainActivity main = (MainActivity) requireActivity();
        repo = PinRepository.get(requireContext());
        name = requireArguments().getString(ARG_BOARD);
        view.findViewById(R.id.back).setOnClickListener(v -> main.goBack());

        RecyclerView list = view.findViewById(R.id.list);
        StaggeredGridLayoutManager layout = new StaggeredGridLayoutManager(Ui.spanCount(requireContext()), StaggeredGridLayoutManager.VERTICAL);
        layout.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
        list.setLayoutManager(layout);
        header = getLayoutInflater().inflate(R.layout.header_board, new android.widget.FrameLayout(requireContext()), false);
        adapter = new PinAdapter(repo, new PinAdapter.Listener() {
            @Override public void open(Pin p) { main.openPin(p); }
            @Override public void longPress(Pin p, View anchor) { pinMenu(main, p, anchor); }
            @Override public void more(Pin p, View anchor) { pinMenu(main, p, anchor); }
        });
        adapter.setShowSavedBadge(isCreated());
        list.setAdapter(new ConcatAdapter(new ConcatAdapter.Config.Builder().setIsolateViewTypes(false).build(),
                new SingleViewAdapter(header), adapter));

        header.findViewById(R.id.studio).setOnClickListener(v -> main.openStudio(name));
        header.findViewById(R.id.more_ideas).setOnClickListener(v -> main.openSearch(ideaQuery(), null));
        header.findViewById(R.id.edit).setOnClickListener(v -> editBoard());
        header.findViewById(R.id.delete).setOnClickListener(v -> deleteBoard(main));
        header.findViewById(R.id.mood_section).setOnClickListener(v -> moodColors(main));

        repo.changes().observe(getViewLifecycleOwner(), v -> refresh(main));
    }

    private List<Pin> pins() {
        if (isCreated()) return repo.createdPins();
        return repo.pinsIn(repo.board(name));
    }

    private void refresh(MainActivity main) {
        Board board = repo.board(name);
        if (!isCreated() && board == null) { main.goBack(); return; }
        List<Pin> pins = pins();
        adapter.submit(pins);
        ((TextView) header.findViewById(R.id.name)).setText(isCreated() ? "Your creations" : name);
        TextView desc = header.findViewById(R.id.description);
        String d = isCreated() ? "Pins you've made from your own photos." : board.description;
        desc.setText(d);
        desc.setVisibility(d.isEmpty() ? View.GONE : View.VISIBLE);
        ((TextView) header.findViewById(R.id.count)).setText(Ui.count(pins.size(), "pin", "pins"));
        int[] mood = Palettes.blend(pins, 8);
        Ui.moodStrip(header.findViewById(R.id.mood_strip), mood);
        header.findViewById(R.id.mood_section).setVisibility(mood.length == 0 ? View.GONE : View.VISIBLE);
        header.findViewById(R.id.studio).setVisibility(pins.isEmpty() ? View.GONE : View.VISIBLE);
        header.findViewById(R.id.edit).setVisibility(isCreated() ? View.GONE : View.VISIBLE);
        header.findViewById(R.id.delete).setVisibility(isCreated() ? View.GONE : View.VISIBLE);
        header.findViewById(R.id.empty).setVisibility(pins.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Most common tags across the board drive "More ideas". */
    private String ideaQuery() {
        HashMap<String, Integer> counts = new HashMap<>();
        for (Pin p : pins()) for (String t : p.tagList()) counts.put(t, counts.getOrDefault(t, 0) + 1);
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder q = new StringBuilder();
        for (int i = 0; i < sorted.size() && i < 2; i++) q.append(i == 0 ? "" : " ").append(sorted.get(i).getKey());
        if (q.length() == 0) q.append(isCreated() ? "inspiration" : name);
        return q.toString();
    }

    private void moodColors(MainActivity main) {
        int[] mood = Palettes.blend(pins(), 8);
        String[] labels = new String[mood.length];
        for (int i = 0; i < mood.length; i++) labels[i] = Palettes.hex(mood[i]) + "  ·  " + Palettes.nearestFilter(mood[i]);
        new MaterialAlertDialogBuilder(main)
                .setTitle("Board mood")
                .setItems(labels, (d, which) -> PinActions.colorSheet(main, mood[which], isCreated() ? "" : name))
                .setPositiveButton("Close", null)
                .show();
    }

    private void editBoard() {
        Board board = repo.board(name);
        if (board == null) return;
        Dialogs.boardEditor(requireContext(), "Edit board", board, "Save", (n, d) -> {
            String error = repo.editBoard(board, n, d);
            if (error == null) {
                name = board.name;
                requireArguments().putString(ARG_BOARD, name);
                refresh((MainActivity) requireActivity());
            }
            return error;
        });
    }

    private void deleteBoard(MainActivity main) {
        Board board = repo.board(name);
        if (board == null) return;
        Dialogs.confirm(main, "Delete \"" + board.name + "\"?",
                "The board will be removed. Your own pins stay in Your creations.", "Delete", () -> {
                    repo.deleteBoard(board);
                    MainActivity.notify("Board deleted", null, null);
                });
    }

    private void pinMenu(MainActivity main, Pin p, View anchor) {
        PopupMenu menu = new PopupMenu(main, anchor);
        Menu m = menu.getMenu();
        Board board = repo.board(name);
        if (board != null) {
            m.add(0, 1, 0, "Remove from board");
            if (repo.boards().size() > 1) m.add(0, 2, 1, "Move to…");
        } else m.add(0, 6, 0, "Save to board…");
        m.add(0, 3, 2, "Share");
        boolean mine = !p.remote && repo.find(p.id) != null;
        if (mine) m.add(0, 4, 3, "Edit pin");
        if (mine && isCreated()) m.add(0, 5, 4, "Delete pin");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: {
                    int index = board.pins.indexOf(p.id);
                    repo.removeFromBoard(p, board);
                    MainActivity.notify("Removed from " + board.name, "Undo", () -> repo.restoreToBoard(p, board, index));
                    return true;
                }
                case 2: moveTo(main, p, board); return true;
                case 3: PinActions.share(main, p); return true;
                case 4: main.editPin(p.id); return true;
                case 5:
                    Dialogs.confirm(main, "Delete this pin?", "It will be removed from every board.", "Delete", () -> repo.deletePin(p));
                    return true;
                case 6: main.showSaveSheet(p); return true;
            }
            return false;
        });
        menu.show();
    }

    private void moveTo(MainActivity main, Pin p, Board from) {
        List<Board> targets = new ArrayList<>();
        for (Board b : repo.boards()) if (b != from) targets.add(b);
        String[] names = new String[targets.size()];
        for (int i = 0; i < names.length; i++) names[i] = targets.get(i).name;
        new MaterialAlertDialogBuilder(main)
                .setTitle("Move to")
                .setItems(names, (d, which) -> {
                    Board to = targets.get(which);
                    repo.addToBoard(p, to, (ok, msg) -> {
                        if (!ok) { MainActivity.notify(msg, null, null); return; }
                        repo.removeFromBoard(p, from);
                        MainActivity.notify("Moved to " + to.name, null, null);
                    });
                })
                .show();
    }
}
