package com.example.pinspire.ui.feed;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pinspire.R;
import com.example.pinspire.data.Pin;
import com.example.pinspire.data.PinRepository;
import com.example.pinspire.ui.widget.RatioImageView;

import java.util.ArrayList;
import java.util.List;

/** Masonry pin cards. Views are recycled, so long feeds stay smooth and memory-bounded. */
public class PinAdapter extends RecyclerView.Adapter<PinAdapter.VH> {
    public interface Listener {
        void open(Pin p);
        void longPress(Pin p, View anchor);
        void more(Pin p, View anchor);
    }

    private static final Object PAYLOAD_SAVED = new Object();
    private final ArrayList<Pin> items = new ArrayList<>();
    private final PinRepository repo;
    private final Listener listener;
    private boolean showSavedBadge = true;

    public PinAdapter(PinRepository repo, Listener listener) {
        this.repo = repo;
        this.listener = listener;
    }

    public void setShowSavedBadge(boolean show) { showSavedBadge = show; }

    public List<Pin> items() { return items; }

    public void submit(List<Pin> next) {
        final List<Pin> old = new ArrayList<>(items);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return old.size(); }
            @Override public int getNewListSize() { return next.size(); }
            @Override public boolean areItemsTheSame(int o, int n) { return old.get(o).id.equals(next.get(n).id); }
            @Override public boolean areContentsTheSame(int o, int n) {
                Pin a = old.get(o), b = next.get(n);
                return a == b && a.title.equals(b.title) && a.cover().equals(b.cover());
            }
        }, false);
        items.clear();
        items.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    /** Updates saved badges without reloading images. */
    public void refreshSaved() { notifyItemRangeChanged(0, items.size(), PAYLOAD_SAVED); }

    @Override public int getItemViewType(int position) { return R.layout.item_pin; }

    @Override public int getItemCount() { return items.size(); }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pin, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull VH h, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_SAVED)) bindSaved(h, items.get(position));
        else onBindViewHolder(h, position);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int position) {
        Pin p = items.get(position);
        h.image.setRatio(p.ratio());
        com.example.pinspire.ui.Ui.loadPin(h.image, p);
        h.image.setContentDescription(p.title);
        h.title.setText(p.title);
        h.title.setVisibility(p.title.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        h.multi.setVisibility(p.images.size() > 1 ? View.VISIBLE : View.GONE);
        if (p.images.size() > 1) h.multi.setText("1/" + p.images.size());
        bindSaved(h, p);
        h.image.setOnClickListener(v -> listener.open(p));
        h.image.setOnLongClickListener(v -> { listener.longPress(p, v); return true; });
        h.more.setOnClickListener(v -> listener.more(p, v));
    }

    private void bindSaved(VH h, Pin p) {
        h.saved.setVisibility(showSavedBadge && repo.isSaved(p.id) ? View.VISIBLE : View.GONE);
    }

    static final class VH extends RecyclerView.ViewHolder {
        final RatioImageView image;
        final ImageView saved;
        final TextView title, multi;
        final ImageButton more;
        VH(View v) {
            super(v);
            image = v.findViewById(R.id.image);
            saved = v.findViewById(R.id.saved_badge);
            title = v.findViewById(R.id.title);
            multi = v.findViewById(R.id.multi_badge);
            more = v.findViewById(R.id.more);
        }
    }
}
