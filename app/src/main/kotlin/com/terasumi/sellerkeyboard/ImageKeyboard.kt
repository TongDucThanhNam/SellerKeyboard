package com.terasumi.sellerkeyboard;

import android.content.ClipDescription;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.voiceime.VoiceRecognitionTrigger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class ImageKeyboard extends InputMethodService {

    private static final String TAG = "ImageKeyboard";
    private static final String MIME_TYPE_PNG = "image/png";
//    File tempFile = null;


    ArrayList<Button> buttons = new ArrayList<>();
    private List<Snippet> snippetItemList = new ArrayList<>();
    private VoiceRecognitionTrigger mVoiceRecognitionTrigger;
    //    Button mButton;
    Button openGoogleVoiceButton;
    GridLayout mLayout;


    private static int getFlag() {
        return InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION; //
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize VoiceRecognitionTrigger
        Log.i(TAG, "#onCreate");

        // Create the voice recognition trigger, and register the listener.
        // The trigger has to unregistered, when the IME is destroyed.
        mVoiceRecognitionTrigger = new VoiceRecognitionTrigger(this);
        mVoiceRecognitionTrigger.register(new VoiceRecognitionTrigger.Listener() {

            @Override
            public void onVoiceImeEnabledStatusChange() {
                // The call back is done on the main thread.
                updateVoiceImeStatus();
            }
        });
    }

    /**
     * Update the microphone icon to reflect the status of the voice recognition.
     */
    private void updateVoiceImeStatus() {
        Log.i(TAG, "#updateVoiceImeStatus");

        if (openGoogleVoiceButton == null) {
            Log.i(TAG, "openGoogleVoiceButton is null");
            return;
        }

        if (mVoiceRecognitionTrigger.isInstalled()) {
            openGoogleVoiceButton.setVisibility(View.VISIBLE);
            if (mVoiceRecognitionTrigger.isEnabled()) {
                // Voice recognition is installed and enabled.
                Log.i(TAG, "Voice recognition is enabled.");
                openGoogleVoiceButton.setEnabled(true);
            } else {
                // Voice recognition is installed, but it is not enabled (no network).
                // The microphone icon is displayed greyed-out.
                Log.i(TAG, "Voice recognition is disabled.");
                openGoogleVoiceButton.setEnabled(false);
            }
        } else {
            // Voice recognition is not installed, and the microphone icon is not displayed.
            Log.i(TAG, "Voice recognition is not installed.");
            openGoogleVoiceButton.setVisibility(View.GONE);
        }
        mLayout.invalidate();
    }

    /**
     * Returns the language of the IME. The langauge is used in voice recognition to match the
     * current language of the IME.
     */
    private String getImeLanguage() {
        return "vi-VN";
    }

    //Create a button to insert GIF, PNG, WebP
    @Override
    public View onCreateInputView() {
        snippetItemList = new ArrayList<>(); // Initialize snippetItemList
        fetchDataFromSQLite();
        buttons.clear(); // Clear existing buttons
        for (Snippet snippetItem : snippetItemList) {
            Button imageButton = getButton(snippetItem);
            buttons.add(imageButton);
        }

        //contraint layout
        View rootView = getLayoutInflater().inflate(R.layout.keyboard, null);
        mLayout = rootView.findViewById(R.id.buttonGrid);
        //config layout
//        openGoogleVoiceButton = new ImageButton(this);
        openGoogleVoiceButton = new Button(this);
        openGoogleVoiceButton.setText("Voice");
        Drawable voiceDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.voice, null);
        voiceDrawable.setBounds(0, 0, 50, 50); // Set the desired width and height
        openGoogleVoiceButton.setCompoundDrawables(voiceDrawable, null, null, null);        openGoogleVoiceButton.setHeight(3);

        openGoogleVoiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mVoiceRecognitionTrigger.startVoiceRecognition(getImeLanguage());
            }
        });

        buttons.add(0, openGoogleVoiceButton);

        // Get the screen width
        int screenWidth = getResources().getDisplayMetrics().widthPixels - 6; // 6 is the padding of the layout
        // Set the number of columns

        for (Button button : buttons) {
            button.setGravity(Gravity.CENTER);
            //buton = screen width / 4
            button.setWidth(screenWidth / 4);
            mLayout.addView(button);
        }
        return mLayout;
    }

    private @NonNull Button getButton(Snippet snippetItem) {
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
//                    inputConnection.performEditorAction(EditorInfo.IME_ACTION_DONE);
                Thread.sleep(500);

                // Send imageUrls
                ImageKeyboard.this.doCommitContent(snippetItem.getImageUrl());

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start());
        return imageButton;
    }

    private void fetchDataFromSQLite() {
        SnippetDbHelper dbHelper = new SnippetDbHelper(this);
        snippetItemList = dbHelper.getAllSnippets();
    }


    private void doCommitContent(String imageUrl) {
        Glide.with(this).load(imageUrl).into(new CustomTarget<Drawable>() {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                Bitmap bitmap = ((BitmapDrawable) resource).getBitmap();
                try {
                    File tempFile = File.createTempFile("temp_image", ".jpg", getCacheDir());
                    try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                    }
                    Uri uri = FileProvider.getUriForFile(ImageKeyboard.this, "com.terasumi.sellerkeyboard.fileprovider", tempFile);
                    InputContentInfoCompat inputContentInfo = new InputContentInfoCompat(uri, new ClipDescription("imageUrls", new String[]{"image/jpeg"}), null);
                    InputConnectionCompat.commitContent(getCurrentInputConnection(), getCurrentInputEditorInfo(), inputContentInfo, getFlag(), null);
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
        if (buttons != null) {
            for (Button button : buttons) {
                button.setEnabled(true);
            }
        }
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "#onDestroy");
        super.onDestroy();

        if (mVoiceRecognitionTrigger != null) {
            mVoiceRecognitionTrigger.unregister(this);
        }
    }
}