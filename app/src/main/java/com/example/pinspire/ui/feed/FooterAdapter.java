package com.example.pinspire.ui.feed;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.example.pinspire.R;

/** Loading spinner, error with retry, or end-of-feed message below the grid. */
public class FooterAdapter extends RecyclerView.Adapter<FooterAdapter.VH> {
    private boolean loading, end, empty;
    private String error;
    private String emptyText = "No ideas found. Try different words or another color.";
    private final Runnable retry;

    public FooterAdapter(Runnable retry) { this.retry = retry; }

    public void setEmptyText(String text) { emptyText = text; }

    public void setState(boolean loading, String error, boolean end, boolean empty) {
        this.loading = loading;
        this.error = error;
        this.end = end;
        this.empty = empty;
        notifyItemChanged(0);
    }

    @Override public int getItemViewType(int position) { return R.layout.item_footer; }

    @Override public int getItemCount() { return 1; }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_footer, parent, false);
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof StaggeredGridLayoutManager.LayoutParams) ((StaggeredGridLayoutManager.LayoutParams) lp).setFullSpan(true);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int position) {
        h.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        h.retry.setVisibility(!loading && error != null ? View.VISIBLE : View.GONE);
        h.retry.setOnClickListener(v -> retry.run());
        String msg = null;
        if (!loading && error != null) msg = error;
        else if (!loading && end && empty) msg = emptyText;
        else if (!loading && end) msg = "You've seen it all. Pull down for a fresh mix.";
        h.message.setText(msg);
        h.message.setVisibility(msg == null ? View.GONE : View.VISIBLE);
    }

    static final class VH extends RecyclerView.ViewHolder {
        final View progress, retry;
        final TextView message;
        VH(View v) {
            super(v);
            progress = v.findViewById(R.id.progress);
            retry = v.findViewById(R.id.retry);
            message = v.findViewById(R.id.message);
        }
    }
}
