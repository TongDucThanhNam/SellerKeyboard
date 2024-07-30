package com.example.sellerkeyboard;

import android.content.ClipDescription;
import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
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
import androidx.core.content.FileProvider;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;

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
import java.util.List;
import java.util.Random;


public class ImageKeyboard extends InputMethodService {

    private static final String TAG = "ImageKeyboard";
    private static final String AUTHORITY = "com.example.sellerkeyboard.fileprovider";
    private static final String MIME_TYPE_GIF = "image/gif";
    private static final String MIME_TYPE_PNG = "image/png";
    private static final String MIME_TYPE_WEBP = "image/webp";

    private File mPngFile;
    private File mGifFile;
    private File mWebpFile;
    ArrayList<Button> buttons = new ArrayList<>();
    ArrayList<ButtonData> buttonDatas = new ArrayList<>();
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private List<SnippetItem> snippetItemList = new ArrayList<>();


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

//    private void doCommitContent(@NonNull String description, @NonNull String mimeType,
//                                 @NonNull File file) {
//        final EditorInfo editorInfo = getCurrentInputEditorInfo();
//
//        // Validate packageName again just in case.
//        if (validatePackageName(editorInfo)) {
//            return;
//        }
//
//        final Uri contentUri = FileProvider.getUriForFile(this, AUTHORITY, file);
//
//        // As you as an IME author are most likely to have to implement your own content provider
//        // to support CommitContent API, it is important to have a clear spec about what
//        // applications are going to be allowed to access the content that your are going to share.
//        final int flag = getFlag();
//
//        final InputContentInfoCompat inputContentInfoCompat = new InputContentInfoCompat(
//                contentUri,
//                new ClipDescription(description, new String[]{mimeType}),
//                null /* linkUrl */);
//        InputConnectionCompat.commitContent(
//                getCurrentInputConnection(), getCurrentInputEditorInfo(), inputContentInfoCompat,
//                flag, null);
//    }

    private static int getFlag() {
        return InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION;
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

        // TODO: Avoid file I/O in the main thread.
//        final File imagesDir = new File(getFilesDir(), "images");
//        imagesDir.mkdirs();
//        mGifFile = getFileForResource(this, R.raw.animated_gif, imagesDir, "image.gif");
//        mPngFile = getFileForResource(this, R.raw.dessert_android, imagesDir, "image.png");
//        mWebpFile = getFileForResource(this, R.raw.animated_webp, imagesDir, "image.webp");
        

    }

    //Create a button to insert GIF, PNG, WebP
    @Override
    public View onCreateInputView() {
        //Using ArrayList buttons
//        buttonDatas.add(new ButtonData("Insert GIF", MIME_TYPE_GIF, mGifFile));

        //get Data from firebase
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
                        //
                    } else {
                        // Handle errors
                    }
                });


        Log.d(TAG, "onCreateInputView: " + snippetItemList.size());
        for (SnippetItem snippetItem : snippetItemList) {
            Button imageButton = new Button(this);
            imageButton.setText(snippetItem.getTitle());
            imageButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
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

                            //send imageUrls
                            ImageKeyboard.this.doCommitContent(
                                    "imageUrls",
                                    snippetItem.getImageUrls()
                            );

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            });
            buttons.add(imageButton);
        }

        final GridLayout layout = new GridLayout(this);
        layout.setColumnCount(3); // Set the desired number of columns

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        );
        layout.setLayoutParams(layoutParams);

        for (Button button : buttons) {
            layout.addView(button);
        }
        return layout;
    }

    private void doCommitContent(String description, String imageUrls) {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference imageRef = storage.getReferenceFromUrl(imageUrls);

        try {
            final File localFile = File.createTempFile("image", "jpg"); // Create a temporary file
            imageRef.getFile(localFile).addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                    // Get the local Uri for the downloaded file
                    Uri contentUri = Uri.fromFile(localFile);
                    // Use the contentUri
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception exception) {
                    // Handle any errors
                }
            });

            // get image from firebase storage
            final Uri contentUri = Uri.fromFile(localFile);

            // As you as an IME author are most likely to have to implement your own content provider
            // to support CommitContent API, it is important to have a clear spec about what
            // applications are going to be allowed to access the content that your are going to share.
            final int flag = getFlag();

            final InputContentInfoCompat inputContentInfoCompat = new InputContentInfoCompat(
                    contentUri,
                    new ClipDescription(description, new String[]{MIME_TYPE_PNG}),
                    null /* linkUrl */);
            InputConnectionCompat.commitContent(
                    getCurrentInputConnection(), getCurrentInputEditorInfo(), inputContentInfoCompat,
                    flag, null);
        } catch (IOException e) {
            e.printStackTrace();
        }

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
        for (Button button : buttons) {
            button.setEnabled(isCommitContentSupported(info, MIME_TYPE_GIF));
        }
    }

    private static File getFileForResource(
            @NonNull Context context, @RawRes int res, @NonNull File outputDir,
            @NonNull String filename) {
        final File outputFile = new File(outputDir, filename);
        final byte[] buffer = new byte[4096];
        InputStream resourceReader = null;
        try {
            try {
                resourceReader = context.getResources().openRawResource(res);
                OutputStream dataWriter = null;
                try {
                    dataWriter = new FileOutputStream(outputFile);
                    while (true) {
                        final int numRead = resourceReader.read(buffer);
                        if (numRead <= 0) {
                            break;
                        }
                        dataWriter.write(buffer, 0, numRead);
                    }
                    return outputFile;
                } finally {
                    if (dataWriter != null) {
                        dataWriter.flush();
                        dataWriter.close();
                    }
                }
            } finally {
                if (resourceReader != null) {
                    resourceReader.close();
                }
            }
        } catch (IOException e) {
            return null;
        }
    }
}