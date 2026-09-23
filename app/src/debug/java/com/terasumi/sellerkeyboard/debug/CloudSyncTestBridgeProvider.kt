package com.terasumi.sellerkeyboard.debug

import SnippetsDao
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import com.terasumi.sellerkeyboard.MyApplication
import com.terasumi.sellerkeyboard.database.DatabaseHelper
import com.terasumi.sellerkeyboard.database.Snippets

class CloudSyncTestBridgeProvider : ContentProvider() {
    private var seededSnippetId: Int? = null

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        check(Binder.getCallingUid() == Process.SHELL_UID) {
            "The cloud release-boundary bridge accepts adb shell only"
        }
        return when (method) {
            METHOD_SNAPSHOT -> snapshot()
            METHOD_SEED -> seed()
            METHOD_RESTORE -> restore()
            else -> throw IllegalArgumentException("Unknown cloud bridge method: $method")
        }
    }

    private fun seed(): Bundle {
        if (seededSnippetId == null) {
            val appContext = requireNotNull(context).applicationContext
            DatabaseHelper(appContext).use { helper ->
                val id = SnippetsDao(helper).insertSnippet(
                    Snippets(
                        id = 0,
                        title = SYNC_CANARY_TITLE,
                        content = SYNC_CANARY_CONTENT,
                        imageUrls = emptyList()
                    )
                )
                check(id >= 0) { "Could not seed the local sync-boundary snippet" }
                seededSnippetId = id.toInt()
            }
        }
        return snapshot().apply { putBoolean(KEY_SEEDED, true) }
    }

    private fun restore(): Bundle {
        val appContext = requireNotNull(context).applicationContext
        seededSnippetId?.let { id ->
            DatabaseHelper(appContext).use { helper -> SnippetsDao(helper).deleteSnippetById(id) }
        }
        seededSnippetId = null
        return snapshot().apply { putBoolean(KEY_RESTORED, true) }
    }

    private fun snapshot(): Bundle {
        val application = requireNotNull(context).applicationContext as MyApplication
        val state = application.cloudDependencyInitializationState()
        val localSnippetPresent = seededSnippetId?.let { id ->
            DatabaseHelper(application).use { helper ->
                SnippetsDao(helper).fetchSnippetById(id)?.let { snippet ->
                    snippet.title == SYNC_CANARY_TITLE && snippet.content == SYNC_CANARY_CONTENT
                }
            }
        } ?: false
        return Bundle().apply {
            putBoolean(KEY_CLIENT_INITIALIZED, state.client)
            putBoolean(KEY_AUTH_INITIALIZED, state.auth)
            putBoolean(KEY_BACKUP_INITIALIZED, state.backup)
            putBoolean(KEY_LOCAL_SNIPPET_PRESENT, localSnippetPresent)
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
        const val SYNC_CANARY_TITLE = "Connected local-only sync boundary"
        const val SYNC_CANARY_CONTENT = "Connected template must remain local"
        private const val METHOD_SNAPSHOT = "snapshot"
        private const val METHOD_SEED = "seed"
        private const val METHOD_RESTORE = "restore"
        private const val KEY_CLIENT_INITIALIZED = "client_initialized"
        private const val KEY_AUTH_INITIALIZED = "auth_initialized"
        private const val KEY_BACKUP_INITIALIZED = "backup_initialized"
        private const val KEY_LOCAL_SNIPPET_PRESENT = "local_snippet_present"
        private const val KEY_SEEDED = "seeded"
        private const val KEY_RESTORED = "restored"
    }
}
