package com.terasumi.sellerkeyboard.autofilltest

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process

class ProviderTestBridge : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val caller = Binder.getCallingUid()
        check(caller == Process.SHELL_UID || caller == context?.applicationInfo?.uid) {
            "The autofill test bridge only accepts adb shell or same-app calls"
        }
        val providerContext = requireNotNull(context)
        return when (method) {
            METHOD_RESET -> ProviderStatus.reset(providerContext)
            METHOD_SNAPSHOT -> ProviderStatus.snapshot(providerContext)
            else -> throw IllegalArgumentException("Unknown autofill bridge method: $method")
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

    private companion object {
        const val METHOD_RESET = "reset"
        const val METHOD_SNAPSHOT = "snapshot"
    }
}

internal object ProviderStatus {
    private const val PREFS = "autofill_test_status"
    private const val KEY_REQUEST_COUNT = "request_count"
    private const val KEY_TARGET_PACKAGE = "target_package"
    private const val KEY_INLINE_REQUEST_PRESENT = "inline_request_present"
    private const val KEY_MAX_SUGGESTION_COUNT = "max_suggestion_count"
    private const val KEY_DATASET_RETURNED = "dataset_returned"

    fun recordRequest(
        context: Context,
        targetPackage: String,
        inlineRequestPresent: Boolean,
        maxSuggestionCount: Int,
        datasetReturned: Boolean
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_REQUEST_COUNT, prefs.getInt(KEY_REQUEST_COUNT, 0) + 1)
            .putString(KEY_TARGET_PACKAGE, targetPackage)
            .putBoolean(KEY_INLINE_REQUEST_PRESENT, inlineRequestPresent)
            .putInt(KEY_MAX_SUGGESTION_COUNT, maxSuggestionCount)
            .putBoolean(KEY_DATASET_RETURNED, datasetReturned)
            .commit()
    }

    fun reset(context: Context): Bundle {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        return snapshot(context)
    }

    fun snapshot(context: Context): Bundle {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Bundle().apply {
            putInt(KEY_REQUEST_COUNT, prefs.getInt(KEY_REQUEST_COUNT, 0))
            putString(KEY_TARGET_PACKAGE, prefs.getString(KEY_TARGET_PACKAGE, ""))
            putBoolean(
                KEY_INLINE_REQUEST_PRESENT,
                prefs.getBoolean(KEY_INLINE_REQUEST_PRESENT, false)
            )
            putInt(KEY_MAX_SUGGESTION_COUNT, prefs.getInt(KEY_MAX_SUGGESTION_COUNT, 0))
            putBoolean(KEY_DATASET_RETURNED, prefs.getBoolean(KEY_DATASET_RETURNED, false))
        }
    }
}
