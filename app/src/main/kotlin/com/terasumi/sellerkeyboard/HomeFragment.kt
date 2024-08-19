package com.terasumi.sellerkeyboard

import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.terasumi.sellerkeyboard.databinding.HomeFragmentBinding
import java.util.stream.Collectors

class HomeFragment : Fragment() {
    private var binding: HomeFragmentBinding? = null
    private val snippetItemList: MutableList<SnippetItem> = ArrayList()
    private var myAdapter: MyAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return try {
            Log.d(TAG, "onCreateView")

            binding = HomeFragmentBinding.inflate(inflater, container, false)

            val recyclerView = binding!!.recyclerView
            myAdapter = MyAdapter(snippetItemList)
            recyclerView.adapter = myAdapter
            recyclerView.layoutManager = LinearLayoutManager(context)

            // fetchDataFromFirestore()
            fetchDataFromSqlite()

            binding!!.root
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreateView", e)
            View(context)
        }
    }

    private fun fetchDataFromSqlite() {
        try {
            val dbHelper = context?.let { SnippetDbHelper(it) }
            Log.d(TAG, "Fetching data from SQLite" + dbHelper.toString())


            val snippets = dbHelper?.allSnippets

            // Clear the list and notify the adapter of item range removal
            val oldSize = snippetItemList.size
            snippetItemList.clear()
            myAdapter!!.notifyItemRangeRemoved(0, oldSize)

            Log.d(TAG, "Fetched ${snippets?.size} snippets from SQLite")

            // Add new items and notify the adapter of item range insertion
            if (snippets != null) {
                snippetItemList.addAll(snippets.stream()
                    .map { snippet: Snippet ->
                        snippet.imageUrl.let {
                            SnippetItem(
                                snippet.title,
                                snippet.content,
                                it
                            )
                        }
                    }
                    .collect(Collectors.toList()))
            }
            myAdapter!!.notifyItemRangeInserted(0, snippetItemList.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching data from SQLite", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            binding = null
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroyView", e)
        }
    }
}