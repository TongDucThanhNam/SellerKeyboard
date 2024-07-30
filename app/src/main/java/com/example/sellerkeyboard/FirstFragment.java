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
//        List<SnippetItem> snippetItemList = new ArrayList<>();
//        for (int i = 0; i < 15; i++) {
//            String text = "Shop Nhận Làm đúng theo yêu cầu, cắt sản phẩm theo số đo thực tế của Bạn\n\n" +
//                    "[ Bạn phải đo chính xác Centimet nhé]\n\n" +
//                    "Chỉ cần bạn đặt một size tương đương, gần giống với số đo thực tế của bạn, rồi đặt đơn hàng.\n\n" +
//                    "Lưu ý: gửi số Đo phủ nhé! Phủ bì là đã tính cả viền (đo tới đâu là dán tới đó)\n\n" +
//                    "💥Chiều rộng đo từ tráiqua phải là : ?\n" +
//                    "💥Chiều cao từ trên xuống dưới là : ?\n" +
//                    "💥Số lượng : ?\n" +
//                    "💥Màu Sắc : ?\n\n" +
//                    "Cần tư vấn thêm \n" +
//                    "Bạn nhắn cho shop nhé!\n\n" +
//                    "Shop có thể mặc định giử thêm thông tin để bạn hiểu thêm ạ\n\n" +
//                    "Ước mong nhận được đơn hàng của Bạn!\n";
//            snippetItemList.add(new SnippetItem("Snippet " + i, text, List.of("Tag " + i, "Tag " + i + 1)));
//        }


        myAdapter = new MyAdapter(snippetItemList);
        recyclerView.setAdapter(myAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        fetchDataFromFirestore();

        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void fetchDataFromFirestore() {
        snippetItemList.clear();

        db.collection("snippets")
                .get()
                .addOnCompleteListener(task -> {
                    //print data

                    if (task.isSuccessful()) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            //print data
                            Log.d("TAG", document.getId() + " => " + document.getData());

                            String title = document.getString("title");
                            String content = document.getString("content");
                            String imageUrls = document.getString("imageUrls");

                            snippetItemList.add(new SnippetItem(title, content, imageUrls));
                        }
                        myAdapter.notifyDataSetChanged();
                    } else {
                        // Handle errors
                    }
                });
    }
}