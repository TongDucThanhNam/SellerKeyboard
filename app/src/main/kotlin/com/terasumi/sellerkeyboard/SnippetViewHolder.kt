package com.terasumi.sellerkeyboard

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SnippetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val contentTextView: TextView by lazy { itemView.findViewById<TextView>(R.id.contentTextView) }
    val titleTextView: TextView by lazy { itemView.findViewById<TextView>(R.id.titleTextView) }
    val imageUrls: ImageView by lazy { itemView.findViewById<ImageView>(R.id.imageView) }
}