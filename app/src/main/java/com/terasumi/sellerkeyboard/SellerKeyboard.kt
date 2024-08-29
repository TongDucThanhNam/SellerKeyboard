package com.terasumi.sellerkeyboard

import android.content.ClipDescription
import android.content.ContentValues.TAG
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.voiceime.VoiceRecognitionTrigger
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class SellerKeyboard : LifecycleInputMethodService(), ViewModelStoreOwner, SavedStateRegistryOwner {
    var mVoiceRecognitionTrigger: VoiceRecognitionTrigger? = null


    override fun onCreateInputView(): View {
        val view = KeyboardContentView(this)
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }
        return view
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize VoiceRecognitionTrigger
        Log.i(TAG, "#onCreate")

        // Create the voice recognition trigger, and register the listener.
        // The trigger has to unregistered, when the IME is destroyed.
        mVoiceRecognitionTrigger = VoiceRecognitionTrigger(this)
        mVoiceRecognitionTrigger!!.register { // The call back is done on the main thread.
            //TODO: updateVoiceImeStatus()
        }
        savedStateRegistryController.performRestore(null)
    }

    val imeLanguage: String
        /**
         * Returns the language of the IME. The language is used in voice recognition to match the
         * current language of the IME.
         */
        get() = "vi-VN"

    private val flag: Int
        get() = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION // or 0

    fun doCommitContent(imageUrl: String) {
        Glide.with(this).load(imageUrl).into(object : CustomTarget<Drawable>() {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                val bitmap = (resource as BitmapDrawable).bitmap
                try {
                    val tempFile = File.createTempFile("temp_image", ".jpg", cacheDir)
                    FileOutputStream(tempFile).use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    }
                    val uri = FileProvider.getUriForFile(
                        this@SellerKeyboard,
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

    override fun onDestroy() {
        super.onDestroy()
        if (mVoiceRecognitionTrigger != null) {
            mVoiceRecognitionTrigger!!.unregister(this)
        }
    }

    //--------------------------//


    override val viewModelStore: ViewModelStore
        get() = store
    override val lifecycle: Lifecycle
        get() = serviceLifecycleDispatcher.lifecycle


    //ViewModelStore Methods
    private val store = ViewModelStore()

    //SaveStateRegistry Methods

    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
}