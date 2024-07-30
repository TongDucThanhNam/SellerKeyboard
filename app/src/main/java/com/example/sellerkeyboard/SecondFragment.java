package com.example.sellerkeyboard;

import android.app.Activity;
import android.content.Intent;
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
import androidx.fragment.app.Fragment;

import com.example.sellerkeyboard.databinding.FragmentSecondBinding;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SecondFragment extends Fragment {

    private FragmentSecondBinding binding;
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    //Prepare Firebase
    FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference myRef = database.getReference("snippets");
    FirebaseFirestore db = FirebaseFirestore.getInstance();

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
        String title = binding.titleEditText.getText().toString();
        String description = binding.contentEditText.getText().toString();
        Uri imageUri = (Uri) binding.imagePickerView.getTag();

        if (imageUri != null) {
            // 1. Generate a unique filename for the image
            String imageName = UUID.randomUUID().toString() + ".jpg"; // Or get file extension from URI

            // 2. Create a Storage reference
            StorageReference imageRef = FirebaseStorage.getInstance().getReference().child("images/" + imageName);

            // 3. Upload the image
            UploadTask uploadTask = imageRef.putFile(imageUri);

            // 4. Get the download URL after upload
            uploadTask.continueWithTask(task -> {
                if (!task.isSuccessful()) {
                    throw task.getException(); // Handle potential errors
                }
                return imageRef.getDownloadUrl();
            }).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    String downloadUrl = task.getResult().toString();
                    saveDataToFirebase(title, description, downloadUrl);
                } else {
                    // Handle failures
                }
            });
        } else {
            // Handle case where no image is selected
            saveDataToFirebase(title, description, null);
        }
    }

    private void saveDataToFirebase(String title, String content, String imageUrls) {
        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("content", content);
        if (imageUrls != null) {
            data.put("imageUrls", imageUrls);
        }

        myRef.setValue(data)
                .addOnSuccessListener(aVoid -> {
                    // Data saved successfully
                })
                .addOnFailureListener(e -> {
                    // Handle errors
                });

        db.collection("snippets")
                .add(data)
                .addOnSuccessListener(documentReference -> {
                    // Data saved successfully
                })
                .addOnFailureListener(e -> {
                    // Handle errors
                });

        //Check if data is saved in Firebase
        Log.d("TAG", "Data saved successfully");

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