package com.terasumi.sellerkeyboard;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class SnippetViewHolder extends RecyclerView.ViewHolder {
    private TextView contentTextView;
    private TextView titleTextView;
    private ImageView imageUrls;

    public SnippetViewHolder(@NonNull View itemView) {
        super(itemView);
        this.contentTextView = itemView.findViewById(R.id.contentTextView);
        this.titleTextView = itemView.findViewById(R.id.titleTextView);
        this.imageUrls = itemView.findViewById(R.id.imageView);
    }


    public TextView getContentTextView() {
        return contentTextView;
    }

    public void setContentTextView(TextView contentTextView) {
        this.contentTextView = contentTextView;
    }

    public TextView getTitleTextView() {
        return titleTextView;
    }

    public void setTitleTextView(TextView titleTextView) {
        this.titleTextView = titleTextView;
    }

    public ImageView getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(ImageView imageUrls) {
        this.imageUrls = imageUrls;
    }
}
