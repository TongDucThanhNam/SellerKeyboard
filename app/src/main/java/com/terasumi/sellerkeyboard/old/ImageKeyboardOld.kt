package com.terasumi.sellerkeyboard.old

import android.content.ClipDescription
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.GridLayout
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.voiceime.VoiceRecognitionTrigger
import com.terasumi.sellerkeyboard.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Random

class ImageKeyboardOld : InputMethodService() {
    //    File tempFile = null;
    private var buttons: ArrayList<Button>? = ArrayList()
    private var snippetItemList: List<Snippet> = ArrayList()
    private var mVoiceRecognitionTrigger: VoiceRecognitionTrigger? = null

    //    Button mButton;
    private var openGoogleVoiceButton: Button? = null
    private var mLayout: GridLayout? = null


    override fun onCreate() {
        super.onCreate()
        // Initialize VoiceRecognitionTrigger
        Log.i(TAG, "#onCreate")

        // Create the voice recognition trigger, and register the listener.
        // The trigger has to unregistered, when the IME is destroyed.
        mVoiceRecognitionTrigger = VoiceRecognitionTrigger(this)
        mVoiceRecognitionTrigger!!.register { // The call back is done on the main thread.
            updateVoiceImeStatus()
        }
    }

    /**
     * Update the microphone icon to reflect the status of the voice recognition.
     */
    private fun updateVoiceImeStatus() {
        Log.i(TAG, "#updateVoiceImeStatus")

        if (openGoogleVoiceButton == null) {
            Log.i(TAG, "openGoogleVoiceButton is null")
            return
        }

        if (mVoiceRecognitionTrigger!!.isInstalled) {
            openGoogleVoiceButton!!.visibility = View.VISIBLE
            if (mVoiceRecognitionTrigger!!.isEnabled) {
                // Voice recognition is installed and enabled.
                Log.i(TAG, "Voice recognition is enabled.")
                openGoogleVoiceButton!!.isEnabled = true
            } else {
                // Voice recognition is installed, but it is not enabled (no network).
                // The microphone icon is displayed greyed-out.
                Log.i(TAG, "Voice recognition is disabled.")
                openGoogleVoiceButton!!.isEnabled = false
            }
        } else {
            // Voice recognition is not installed, and the microphone icon is not displayed.
            Log.i(TAG, "Voice recognition is not installed.")
            openGoogleVoiceButton!!.visibility = View.GONE
        }
        mLayout?.invalidate()
    }

    private val imeLanguage: String
        /**
         * Returns the language of the IME. The langauge is used in voice recognition to match the
         * current language of the IME.
         */
        get() = "vi-VN"

    //Create a button to insert GIF, PNG, WebP
    override fun onCreateInputView(): GridLayout? {
        snippetItemList = ArrayList() // Initialize snippetItemList
        fetchDataFromSQLite()
        buttons!!.clear() // Clear existing buttons
        for (snippetItem in snippetItemList) {
            val imageButton = getButton(snippetItem)
            buttons!!.add(imageButton)
        }

        //contraint layout
        val rootView = layoutInflater.inflate(R.layout.keyboard, null)
        mLayout = rootView.findViewById(R.id.buttonGrid)
        //config layout
//        openGoogleVoiceButton = new ImageButton(this);
        openGoogleVoiceButton = Button(this)
        openGoogleVoiceButton!!.text = "Voice"
        val voiceDrawable = ResourcesCompat.getDrawable(
            resources, R.drawable.voice, null
        )
        voiceDrawable!!.setBounds(0, 0, 50, 50) // Set the desired width and height
        openGoogleVoiceButton!!.setCompoundDrawables(voiceDrawable, null, null, null)
        openGoogleVoiceButton!!.height = 3

        openGoogleVoiceButton!!.setOnClickListener(object : View.OnClickListener {
            override fun onClick(view: View) {
                mVoiceRecognitionTrigger!!.startVoiceRecognition(this@ImageKeyboardOld.imeLanguage)
            }
        })

        buttons!!.add(0, openGoogleVoiceButton!!)

        // Get the screen width
        val screenWidth = resources.displayMetrics.widthPixels - 6 // 6 is the padding of the layout

        // Set the number of columns
        for (button in buttons!!) {
            button.gravity = Gravity.CENTER
            //buton = screen width / 4
            button.width = screenWidth / 4
            mLayout?.addView(button)
        }
        return mLayout as GridLayout?
    }

    private fun getButton(snippetItem: Snippet): Button {
        val imageButton = Button(this)
        imageButton.text = snippetItem.title
        imageButton.setOnClickListener { view: View? ->
            Thread {
                try {
                    val random = Random()
                    val inputConnection = currentInputConnection

                    Thread.sleep((random.nextInt(100) + 400).toLong())
                    inputConnection?.commitText(snippetItem.content, 1)
                        ?: Log.d("Onclick", "inputConnection is null")

                    Thread.sleep(500)
                    checkNotNull(inputConnection)
                    inputConnection.performEditorAction(EditorInfo.IME_ACTION_SEND)
                    //                    inputConnection.performEditorAction(EditorInfo.IME_ACTION_DONE);
                    Thread.sleep(500)

                    // Send imageUrls
                    this@ImageKeyboardOld.doCommitContent(snippetItem.imageUrl)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }.start()
        }
        return imageButton
    }

    private fun fetchDataFromSQLite() {
        val dbHelper = SnippetDbHelper(this)
        snippetItemList = dbHelper.allSnippets
    }


    private fun doCommitContent(imageUrl: String) {
        Glide.with(this).load(imageUrl).into(object : CustomTarget<Drawable>() {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                val bitmap = (resource as BitmapDrawable).bitmap
                try {
                    val tempFile = File.createTempFile("temp_image", ".jpg", cacheDir)
                    FileOutputStream(tempFile).use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    }
                    val uri = FileProvider.getUriForFile(
                        this@ImageKeyboardOld,
                        "com.terasumi.sellerkeyboard.fileprovider",
                        tempFile
                    )
                    val inputContentInfo = InputContentInfoCompat(
                        uri,
                        ClipDescription("imageUrls", arrayOf("image/jpeg")),
                        null
                    )
                    InputConnectionCompat.commitContent(
                        currentInputConnection,
                        currentInputEditorInfo,
                        inputContentInfo,
                        flag,
                        null
                    )
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
            }

            override fun onLoadCleared(placeholder: Drawable?) {
            }
        })
    }


    override fun onEvaluateFullscreenMode(): Boolean {
        // In full-screen mode the inserted content is likely to be hidden by the IME. Hence in this
        // sample we simply disable full-screen mode.
        return false
    }

    //
    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        if (buttons != null) {
            for (button in buttons!!) {
                button.isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "#onDestroy")
        super.onDestroy()

        if (mVoiceRecognitionTrigger != null) {
            mVoiceRecognitionTrigger!!.unregister(this)
        }
    }

    companion object {
        private const val TAG = "ImageKeyboard"
        private const val MIME_TYPE_PNG = "image/png"


        private val flag: Int
            get() = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION //
    }
}