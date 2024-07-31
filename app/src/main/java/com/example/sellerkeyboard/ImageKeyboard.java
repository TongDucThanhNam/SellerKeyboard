package com.example.sellerkeyboard;

import android.content.ClipDescription;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.GridLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.filament.BuildConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;


public class ImageKeyboard extends InputMethodService {

    private static final String TAG = "ImageKeyboard";
    private static final String MIME_TYPE_PNG = "image/png";
    File tempFile = null;


    ArrayList<Button> buttons = new ArrayList<>();
    private List<Snippet> snippetItemList = new ArrayList<>();


    private static int getFlag() {
        return InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION; //
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
            imageButton.setOnClickListener(view -> new Thread(() -> {
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
                            snippetItem.getImageUrl()
                    );

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start());
            buttons.add(imageButton);
        }

        final GridLayout layout = new GridLayout(this);

        // 1. Full chiều rộng
        layout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // 2. Thiết lập số cột
        layout.setColumnCount(3);

        // 3. Căn giữa text (cho tất cả button trong GridLayout)
        layout.setAlignmentMode(GridLayout.ALIGN_MARGINS);

        // 4. Thêm button vào GridLayout
        for (Button button : buttons) {
            // 4.1. Căn giữa text trong button
            button.setGravity(Gravity.CENTER);

            // 4.2. Thêm button vào layout
            layout.addView(button);
        }

        return layout;
    }

    private void fetchDataFromSQLite() {
        SnippetDbHelper dbHelper = new SnippetDbHelper(this);
        snippetItemList = dbHelper.getAllSnippets();
    }


    private void doCommitContent(String imageUrls) {
        Glide.with(this)
                .load(imageUrls)
                .into(new CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                        Bitmap bitmap = ((BitmapDrawable) resource).getBitmap();
                        try {
                            tempFile = File.createTempFile("temp_image", ".jpg", ImageKeyboard.this.getCacheDir());
                            String tempFilePath = tempFile.getAbsolutePath();
                            Log.d(TAG, "onResourceReady: " + tempFilePath);

                            FileOutputStream outputStream = new FileOutputStream(tempFile);
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                            outputStream.close();

                            //check if tempFile[0] is null
                            if (tempFile == null) {
                                Log.d(TAG, "doCommitContent: tempFile[0] is null");
                            }

                            Uri uri = FileProvider.getUriForFile(ImageKeyboard.this, "com.example.sellerkeyboard.fileprovider", tempFile);
                            final int flag = getFlag();
                            final InputContentInfoCompat inputContentInfoCompat = new InputContentInfoCompat(
                                    uri,
                                    new ClipDescription("imageUrls", new String[]{MIME_TYPE_PNG}),
                                    null
                            );
                            InputConnectionCompat.commitContent(
                                    getCurrentInputConnection(),
                                    getCurrentInputEditorInfo(),
                                    inputContentInfoCompat,
                                    flag,
                                    null
                            );
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                    }
                });


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