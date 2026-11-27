package com.example.pinspire.ui.feed;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

/** Shows one fixed view spanning all grid columns (used for screen headers). */
public class SingleViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final View view;
    private final int type = View.generateViewId();

    public SingleViewAdapter(View view) { this.view = view; }

    @Override public int getItemViewType(int position) { return type; }

    @Override public int getItemCount() { return 1; }

    @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        FrameLayout holder = new FrameLayout(parent.getContext());
        StaggeredGridLayoutManager.LayoutParams lp = new StaggeredGridLayoutManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setFullSpan(true);
        holder.setLayoutParams(lp);
        holder.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new RecyclerView.ViewHolder(holder) {};
    }

    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {}
}
