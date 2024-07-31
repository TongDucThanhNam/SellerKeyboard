package com.terasumi.sellerkeyboard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class MyAdapter extends RecyclerView.Adapter<SnippetViewHolder> {
    private final List<SnippetItem> viewItemsList;

    public MyAdapter(List<SnippetItem> viewItemsList) {
        this.viewItemsList = viewItemsList;
    }

    @NonNull
    @Override
    public SnippetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_list, parent, false);
        return new SnippetViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SnippetViewHolder holder, int position) {
        SnippetItem viewItem = viewItemsList.get(position);
        holder.getTitleTextView().setText(viewItem.getTitle());
        holder.getContentTextView().setText(viewItem.getContent());
        //Log image URL
//        Log.d("MyAdapter", "onBindViewHolder: imageUrl: " + viewItem.getImageUrls());

        // imageUrl null check
        if (viewItem.getImageUrls() == null) {
            holder.getImageUrls().setVisibility(View.GONE);
//            Log.d("MyAdapter", "onBindViewHolder: imageUrl is null");
        } else {
            holder.getImageUrls().setVisibility(View.VISIBLE);
            Glide.with(holder.itemView.getContext())
                    .load(viewItem.getImageUrls())
                    .centerCrop()
                    .into(holder.getImageUrls());
        }
    }

    @Override
    public int getItemCount() {
        return viewItemsList.size();
    }
}