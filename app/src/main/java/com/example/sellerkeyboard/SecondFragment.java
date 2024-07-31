package com.example.sellerkeyboard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.sellerkeyboard.databinding.FragmentSecondBinding;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

public class SecondFragment extends Fragment {

    private FragmentSecondBinding binding;
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentSecondBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null && data.getData() != null) {
                            Uri imageUri = data.getData();
                            ImageView imageView = binding.getRoot().findViewById(R.id.imagePickerView);
                            imageView.setImageURI(imageUri);
                            imageView.setTag(imageUri);
                        }
                    }
                });

        binding.imagePickerView.setOnClickListener(v -> openImagePicker());

        binding.submitButton.setOnClickListener(v -> {
            if (validateFormData()) {
                uploadImageAndSaveData();
            }
        });
    }

    private boolean validateFormData() {
        // Add validation checks for title and description if needed
        return true;
    }

    private void uploadImageAndSaveData() {
        String title = Objects.requireNonNull(binding.titleEditText.getText()).toString();
        String description = Objects.requireNonNull(binding.contentEditText.getText()).toString();
        Uri imageUri = (Uri) binding.imagePickerView.getTag();

        if (imageUri != null) {
            saveImageToInternalStorage(binding.getRoot());
            //save to SQLite
        } else {
            // Handle case where no image is selected
            //save to SQLite
            saveDataToSQLite(title, description, null);
            Log.d("SQLite", "Snippet added to SQLite database, no image");
        }
    }

    private void saveImageToInternalStorage(View view) {
        Uri imageUri = (Uri) binding.imagePickerView.getTag();
        if (imageUri != null) {
            Glide.with(this)
                    .asBitmap()
                    .load(imageUri)
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            String imageUrl = saveImageWithGlide(resource);
                            saveDataToSQLite(Objects.requireNonNull(binding.titleEditText.getText()).toString(), Objects.requireNonNull(binding.contentEditText.getText()).toString(), imageUrl);
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                            // Handle placeholder if needed
                        }
                    });
            //
            Snackbar.make(binding.getRoot(), "Lưu ảnh thành công!", Snackbar.LENGTH_SHORT).show();


        } else {
            Snackbar.make(binding.getRoot(), "Chưa chọn ảnh!", Snackbar.LENGTH_SHORT).show();
        }
    }

    //return file.getAbsolutePath()
    private String saveImageWithGlide(Bitmap bitmap) {
        String imageName = "img_" + UUID.randomUUID().toString() + ".jpg";
        File file = new File(requireContext().getCacheDir(), imageName); // Or another suitable directory
        try (FileOutputStream fos = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos); // You can adjust the quality (0-100)
        } catch (IOException e) {
            e.printStackTrace(); // Handle the exception appropriately
        }
        // Now you have the file path: file.getAbsolutePath()
        Log.d("Image", "Image saved to internal storage: " + file.getAbsolutePath());
        return file.getAbsolutePath();
    }

    private void saveDataToSQLite(String title, String content, String imageUrls) {
        SnippetDbHelper dbHelper = new SnippetDbHelper(getContext());

        // Insert a new snippet
        Snippet snippet = new Snippet(title, content, imageUrls);
        dbHelper.addSnippet(snippet);
        Log.d("SQLite", "Snippet added to SQLite database");

        // Snackbar notification success for user
        Snackbar.make(binding.getRoot(), "Data saved successfully", Snackbar.LENGTH_SHORT).show();
        // Return to FirstFragment
        getParentFragmentManager().popBackStack();
    }

    public void openImagePicker() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        imagePickerLauncher.launch(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}