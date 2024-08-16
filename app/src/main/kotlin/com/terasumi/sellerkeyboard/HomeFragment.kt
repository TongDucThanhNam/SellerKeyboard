package com.terasumi.sellerkeyboard

import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.terasumi.sellerkeyboard.databinding.FragmentFirstBinding
import java.util.stream.Collectors

class HomeFragment : Fragment() {
    private var binding: FragmentFirstBinding? = null
    private val snippetItemList: MutableList<SnippetItem> = ArrayList()
    private var myAdapter: MyAdapter? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentFirstBinding.inflate(inflater, container, false)

        val recyclerView = binding!!.recyclerView
        myAdapter = MyAdapter(snippetItemList)
        recyclerView.adapter = myAdapter
        recyclerView.layoutManager = LinearLayoutManager(context)

        //        fetchDataFromFirestore();
        fetchDataFromSqlite()

        return binding!!.root
    }

    private fun fetchDataFromSqlite() {
        try {
            val dbHelper = context?.let { SnippetDbHelper(it) }
            val snippets = dbHelper?.allSnippets

            // Clear the list and notify the adapter of item range removal
            val oldSize = snippetItemList.size
            snippetItemList.clear()
            myAdapter!!.notifyItemRangeRemoved(0, oldSize)

            // Add new items and notify the adapter of item range insertion
            if (snippets != null) {
                snippetItemList.addAll(snippets.stream()
                    .map { snippet: Snippet ->
                        SnippetItem(
                            snippet.title,
                            snippet.content,
                            snippet.imageUrl
                        )
                    }
                    .collect(Collectors.toList()))
            }
            myAdapter!!.notifyItemRangeInserted(0, snippetItemList.size)
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Error fetching data from SQLite")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}