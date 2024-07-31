package com.example.sellerkeyboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sellerkeyboard.databinding.FragmentFirstBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;
    private final List<SnippetItem> snippetItemList = new ArrayList<>();
    private MyAdapter myAdapter;


    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentFirstBinding.inflate(inflater, container, false);

        RecyclerView recyclerView = binding.recyclerView;
        myAdapter = new MyAdapter(snippetItemList);
        recyclerView.setAdapter(myAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

//        fetchDataFromFirestore();
        fetchDataFromSqlite();

        return binding.getRoot();

    }

    private void fetchDataFromSqlite() {
        SnippetDbHelper dbHelper = new SnippetDbHelper(getContext());
        List<Snippet> snippets = dbHelper.getAllSnippets();

        // Clear the list and notify the adapter of item range removal
        int oldSize = snippetItemList.size();
        snippetItemList.clear();
        myAdapter.notifyItemRangeRemoved(0, oldSize);

        // Add new items and notify the adapter of item range insertion
        snippetItemList.addAll(snippets.stream()
                .map(snippet -> new SnippetItem(snippet.getTitle(), snippet.getContent(), snippet.getImageUrl()))
                .collect(Collectors.toList()));
        myAdapter.notifyItemRangeInserted(0, snippetItemList.size());
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}