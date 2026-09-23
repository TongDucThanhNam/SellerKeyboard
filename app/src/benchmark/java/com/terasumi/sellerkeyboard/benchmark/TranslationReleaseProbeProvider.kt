package com.terasumi.sellerkeyboard.benchmark

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import com.terasumi.sellerkeyboard.translation.MlKitTranslationController
import com.terasumi.sellerkeyboard.translation.TranslationDirection
import com.terasumi.sellerkeyboard.translation.TranslationState

/** Shell-only probe compiled into the non-debuggable benchmark variant, never release. */
class TranslationReleaseProbeProvider : ContentProvider() {
    private val controller by lazy { MlKitTranslationController() }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        check(Binder.getCallingUid() == Process.SHELL_UID) {
            "The benchmark translation probe accepts adb shell only"
        }
        when (method) {
            METHOD_DELETE -> controller.deleteDownloadedModel(TranslationDirection.VI_TO_EN)
            METHOD_TRANSLATE -> controller.translate(
                TRANSLATION_CANARY,
                TranslationDirection.VI_TO_EN,
                isSensitive = false
            )
            METHOD_CANCEL -> controller.cancel()
            METHOD_SNAPSHOT -> Unit
            else -> throw IllegalArgumentException("Unknown translation probe method: $method")
        }
        return snapshot()
    }

    private fun snapshot(): Bundle {
        val state = controller.state.value
        return Bundle().apply {
            putString(KEY_STATE, state.javaClass.simpleName)
            putString(KEY_ERROR, (state as? TranslationState.Error)?.message.orEmpty())
            putBoolean(KEY_DELETING, state is TranslationState.DeletingModel)
            putBoolean(KEY_MODEL_DELETED, state is TranslationState.ModelDeleted)
            putBoolean(KEY_PREVIEW, state is TranslationState.Preview)
            putBoolean(KEY_ERROR_PRESENT, state is TranslationState.Error)
            putBoolean(
                KEY_ORIGINAL_MATCHES,
                (state as? TranslationState.Preview)?.original == TRANSLATION_CANARY
            )
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        const val TRANSLATION_CANARY = "Don hang attribution rieng tu"
        private const val METHOD_DELETE = "delete"
        private const val METHOD_TRANSLATE = "translate"
        private const val METHOD_CANCEL = "cancel"
        private const val METHOD_SNAPSHOT = "snapshot"
        private const val KEY_STATE = "state"
        private const val KEY_ERROR = "error"
        private const val KEY_ORIGINAL_MATCHES = "original_matches"
        private const val KEY_DELETING = "deleting"
        private const val KEY_MODEL_DELETED = "model_deleted"
        private const val KEY_PREVIEW = "preview"
        private const val KEY_ERROR_PRESENT = "error_present"
    }
}
