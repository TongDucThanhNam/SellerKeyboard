package com.example.sellerkeyboard;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sellerkeyboard.databinding.FragmentFirstBinding;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private List<SnippetItem> snippetItemList = new ArrayList<>();
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
        snippetItemList.clear();
        SnippetDbHelper dbHelper = new SnippetDbHelper(getContext());
        List<Snippet> snippets = dbHelper.getAllSnippets();
        for (Snippet snippet : snippets) {
            snippetItemList.add(new SnippetItem(snippet.getTitle(), snippet.getContent(), snippet.getImageUrl()));
        }


        myAdapter.notifyDataSetChanged();
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