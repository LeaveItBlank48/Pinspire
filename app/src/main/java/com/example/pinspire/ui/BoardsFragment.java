package com.example.pinspire.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.pinspire.MainActivity;
import com.example.pinspire.R;
import com.example.pinspire.data.Board;
import com.example.pinspire.data.Palettes;
import com.example.pinspire.data.Pin;
import com.example.pinspire.data.PinRepository;

import java.util.ArrayList;
import java.util.List;

/** The Saved tab: every board with a cover collage and its mood strip. */
public class BoardsFragment extends Fragment {
    private PinRepository repo;
    private final ArrayList<Board> items = new ArrayList<>();
    private RecyclerView.Adapter<RecyclerView.ViewHolder> adapter;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_boards, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        MainActivity main = (MainActivity) requireActivity();
        repo = PinRepository.get(requireContext());
        RecyclerView list = view.findViewById(R.id.list);
        list.setLayoutManager(new GridLayoutManager(requireContext(), Ui.spanCount(requireContext())));
        adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_board, parent, false)) {};
            }
            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) { bind(h.itemView, items.get(position), main); }
            @Override public int getItemCount() { return items.size(); }
        };
        list.setAdapter(adapter);
        View.OnClickListener create = v -> Dialogs.boardEditor(requireContext(), "New board", null, "Create",
                (name, desc) -> repo.createBoard(name, desc));
        view.findViewById(R.id.add).setOnClickListener(create);
        view.findViewById(R.id.empty_create).setOnClickListener(create);
        repo.changes().observe(getViewLifecycleOwner(), v -> refresh(view));
    }

    private void refresh(View view) {
        items.clear();
        if (!repo.createdPins().isEmpty()) items.add(new Board(Board.CREATED));
        items.addAll(repo.boards());
        adapter.notifyDataSetChanged();
        boolean empty = items.isEmpty();
        view.findViewById(R.id.list).setVisibility(empty ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.empty).setVisibility(empty ? View.VISIBLE : View.GONE);
        int boards = repo.boards().size();
        int streak = repo.sparkStreak();
        ((TextView) view.findViewById(R.id.stats)).setText(Ui.count(boards, "board", "boards") + " · "
                + Ui.count(repo.savedPinCount(), "pin", "pins") + " saved"
                + (streak > 0 ? " · ✦ " + Ui.count(streak, "day", "days") + " spark streak" : ""));
    }

    private void bind(View v, Board b, MainActivity main) {
        boolean created = Board.CREATED.equals(b.name);
        List<Pin> pins = created ? repo.createdPins() : repo.pinsIn(b);
        ((TextView) v.findViewById(R.id.name)).setText(created ? "Your creations" : b.name);
        ((TextView) v.findViewById(R.id.count)).setText(Ui.count(pins.size(), "pin", "pins"));
        ImageView[] covers = {v.findViewById(R.id.cover1), v.findViewById(R.id.cover2), v.findViewById(R.id.cover3)};
        for (int i = 0; i < covers.length; i++) {
            if (i < pins.size()) Ui.loadPin(covers[i], pins.get(i));
            else { Glide.with(covers[i]).clear(covers[i]); covers[i].setImageDrawable(null); }
        }
        int[] mood = Palettes.blend(pins, 6);
        Ui.moodStrip(v.findViewById(R.id.mood_strip), mood);
        v.findViewById(R.id.mood_card).setVisibility(mood.length == 0 ? View.INVISIBLE : View.VISIBLE);
        v.setOnClickListener(x -> main.openBoard(b.name));
    }
}
