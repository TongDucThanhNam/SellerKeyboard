package com.terasumi.sellerkeyboard.service

import android.content.Context
import android.content.ClipDescription
import android.content.ContentValues.TAG
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class SellerKeyboard : LifecycleInputMethodService(), ViewModelStoreOwner, SavedStateRegistryOwner {
    var mVoiceRecognitionTrigger: VoiceRecognitionTrigger? = null

    @Volatile
    private var hapticEnabledCache: Boolean = true

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }


    override fun onCreateInputView(): View {
        val view = KeyboardContentView(this)

        // Set tree owners on the *window's decor view* so that Compose's
        // upward ViewTree walk always finds them, even from ancestor
        // views (e.g. the parentPanel LinearLayout) that InputMethodService
        // inserts above our input view.
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }

        // Also set directly on our view as a fallback.
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
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
        hapticEnabledCache = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(PREF_HAPTIC_ENABLED, true)
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
                serviceScope.launch {
                    try {
                        val tempFile = withContext(Dispatchers.IO) {
                            File.createTempFile("temp_image", ".jpg", cacheDir).apply {
                                FileOutputStream(this).use {
                                    bitmap.compress(
                                        Bitmap.CompressFormat.JPEG,
                                        100,
                                        it
                                    )
                                }
                            }
                        }
                        val uri = FileProvider.getUriForFile(
                            this@SellerKeyboard,
                            "com.terasumi.sellerkeyboard.fileprovider",
                            tempFile
                        )
                        val inputContentInfo = InputContentInfoCompat(
                            uri,
                            ClipDescription("imageUrls", arrayOf("image/jpeg, image/jpg")),
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
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                // Clean up the resources
            }
        })
    }

    fun isKeyHapticEnabled(): Boolean {
        return hapticEnabledCache
    }

    fun setKeyHapticEnabled(enabled: Boolean) {
        hapticEnabledCache = enabled
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_HAPTIC_ENABLED, enabled)
            .apply()
    }

    fun performKeyHapticFeedback() {
        if (!isKeyHapticEnabled()) {
            return
        }
        val targetVibrator = vibrator ?: return
        if (!targetVibrator.hasVibrator()) {
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                targetVibrator.vibrate(
                    VibrationEffect.createOneShot(
                        HAPTIC_DURATION_MS,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                targetVibrator.vibrate(HAPTIC_DURATION_MS)
            }
        } catch (_: SecurityException) {
            // Ignore when device policy blocks haptics for current user/profile.
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mVoiceRecognitionTrigger?.unregister(this)
        mVoiceRecognitionTrigger = null
        super.onDestroy()
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

    companion object {
        const val PREFS_NAME = "keyboard_prefs"
        const val PREF_HAPTIC_ENABLED = "pref_haptic_enabled"
        private const val HAPTIC_DURATION_MS = 12L
    }
}
