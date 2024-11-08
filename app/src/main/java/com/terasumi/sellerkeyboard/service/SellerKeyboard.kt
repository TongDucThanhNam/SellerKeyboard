package com.terasumi.sellerkeyboard.service

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
import com.terasumi.sellerkeyboard.keyboard.KeyboardContentView
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

    fun getCurrentView(): View {
        return KeyboardContentView(this)
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize VoiceRecognitionTrigger
//        Log.i(TAG, "#onCreate")

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
//        //Check send image available
//        val mimeTypes = EditorInfoCompat.getContentMimeTypes(currentInputEditorInfo)
//        val isMimeSupported: Boolean = mimeTypes.any {
//            Log.i(TAG, "MIME type: $it")
//            ClipDescription.compareMimeTypes(
//                it,
//                "image/jpg"
//            )
//                    || ClipDescription.compareMimeTypes(it, "image/jpeg")
//                    || ClipDescription.compareMimeTypes(it, "image/*")
//        }
//
//        if (!isMimeSupported) {
//            Log.i(TAG, "MIME type is not supported")
//            return
//        }

        Glide.with(this).load(imageUrl).into(object : CustomTarget<Drawable>() {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                val bitmap = (resource as BitmapDrawable).bitmap
                try {
                    val tempFile = File.createTempFile("temp_image", ".jpg", cacheDir).apply {
                        FileOutputStream(this).use {
                            bitmap.compress(
                                Bitmap.CompressFormat.JPEG,
                                100,
                                it
                            )
                        }
                    }
                    val uri = FileProvider.getUriForFile(
                        this@SellerKeyboard,
                        "com.terasumi.sellerkeyboard.fileprovider",
                        tempFile
                    )
                    val inputContentInfo = InputContentInfoCompat(
                        uri,
                        ClipDescription("imageUrls", arrayOf("image/jpeg, image/jpg")), // or null
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
                    Log.e(TAG, "Error committing content", e)
                }
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                // Clean up the resources
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