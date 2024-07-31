package com.example.sellerkeyboard;

import android.content.ClipDescription;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;


public class ImageKeyboard extends InputMethodService {

    private static final String TAG = "ImageKeyboard";
    private static final String AUTHORITY = "com.example.sellerkeyboard.fileprovider";
    private static final String MIME_TYPE_GIF = "image/gif";
    private static final String MIME_TYPE_PNG = "image/png";

    ArrayList<Button> buttons = new ArrayList<>();
    private List<Snippet> snippetItemList = new ArrayList<>();


    private boolean isCommitContentSupported(
            @Nullable EditorInfo editorInfo, @NonNull String mimeType) {
        if (editorInfo == null) {
            return false;
        }

        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return false;
        }

        if (validatePackageName(editorInfo)) {
            return false;
        }

        final String[] supportedMimeTypes = EditorInfoCompat.getContentMimeTypes(editorInfo);
        for (String supportedMimeType : supportedMimeTypes) {
            if (ClipDescription.compareMimeTypes(mimeType, supportedMimeType)) {
                return true;
            }
        }
        return false;
    }

    private static int getFlag() {
//        return InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION; //
        return 1;
    }

    private boolean validatePackageName(@Nullable EditorInfo editorInfo) {
        if (editorInfo == null) {
            return true;
        }
        final String packageName = editorInfo.packageName;
        return packageName == null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    //Create a button to insert GIF, PNG, WebP
    @Override
    public View onCreateInputView() {
        snippetItemList = new ArrayList<>(); // Initialize snippetItemList
        fetchDataFromSQLite();
        buttons.clear(); // Clear existing buttons
        for (Snippet snippetItem : snippetItemList) {
            Button imageButton = new Button(this);
            imageButton.setText(snippetItem.getTitle());
            imageButton.setOnClickListener(view -> {
                new Thread(() -> {
                    try {
                        Random random = new Random();
                        InputConnection inputConnection = getCurrentInputConnection();

                        Thread.sleep(random.nextInt(100) + 400);
                        if (inputConnection != null) {
                            inputConnection.commitText(snippetItem.getContent(), 1);
                        } else {
                            Log.d("Onclick", "inputConnection is null");
                        }

                        Thread.sleep(500);
                        assert inputConnection != null;
                        inputConnection.performEditorAction(EditorInfo.IME_ACTION_SEND);
                        Thread.sleep(500);

                        // Send imageUrls
                        ImageKeyboard.this.doCommitContent(
                                "imageUrls",
                                snippetItem.getImageUrl()
                        );

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }).start();
            });
            buttons.add(imageButton);
        }

        //Grid Layout
        final GridLayout layout = new GridLayout(this);
        layout.setColumnCount(3); // Set the desired number of columns
        for (Button button : buttons) {
            layout.addView(button);
        }
        return layout;
    }

    private void fetchDataFromSQLite() {
        SnippetDbHelper dbHelper = new SnippetDbHelper(this);
        snippetItemList = dbHelper.getAllSnippets();
    }



    private void doCommitContent(String description, String imageUrls) {
        //get Image from local storage
        Glide.with(this)
                .load(imageUrls)
                .into(
                        new CustomTarget<Drawable>() {
                            @Override
                            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                                Bitmap bitmap = ((BitmapDrawable) resource).getBitmap();
                                String path = MediaStore.Images.Media.insertImage(getContentResolver(), bitmap, "Image Description", null);
                                Uri uri = Uri.parse(path);
                                final int flag = getFlag();
                                final InputContentInfoCompat inputContentInfoCompat = new InputContentInfoCompat(
                                        uri,
                                        new ClipDescription(description, new String[]{MIME_TYPE_PNG}),
                                        null
                                );
                                InputConnectionCompat.commitContent(
                                        getCurrentInputConnection(), getCurrentInputEditorInfo(), inputContentInfoCompat,
                                        flag, null);
                            }

                            @Override
                            public void onLoadCleared(@Nullable Drawable placeholder) {
                            }
                        }
                );

        final EditorInfo editorInfo = getCurrentInputEditorInfo();

        // Validate packageName again just in case.
        if (validatePackageName(editorInfo)) {
            return;
        }
    }


    @Override
    public boolean onEvaluateFullscreenMode() {
        // In full-screen mode the inserted content is likely to be hidden by the IME. Hence in this
        // sample we simply disable full-screen mode.
        return false;
    }

    //
    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        //send EditorInfo.contentMimeTypes
        Log.d(TAG, "onStartInputView: " + Arrays.toString(info.contentMimeTypes));


        for (Button button : buttons) {
            button.setEnabled(true);
        }
    }
}