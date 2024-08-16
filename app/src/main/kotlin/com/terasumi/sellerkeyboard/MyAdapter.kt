package com.terasumi.sellerkeyboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class MyAdapter(private val viewItemsList: List<SnippetItem>) :
    RecyclerView.Adapter<SnippetViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SnippetViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_list, parent, false)
        return SnippetViewHolder(view)
    }

    override fun onBindViewHolder(holder: SnippetViewHolder, position: Int) {
        val viewItem = viewItemsList[position]
        holder.titleTextView.text = viewItem.title
        holder.contentTextView.text = viewItem.content

        // imageUrl null check
        holder.imageUrls.visibility = View.VISIBLE
        Glide.with(holder.itemView.context)
            .load(viewItem.imageUrls)
            .centerCrop()
            .into(holder.imageUrls)
    }

    override fun getItemCount(): Int {
        return viewItemsList.size
    }
}
