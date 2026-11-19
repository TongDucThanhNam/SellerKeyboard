package com.terasumi.sellerkeyboard.emoji

import android.content.Context
import android.annotation.SuppressLint
import androidx.core.content.edit
import androidx.emoji2.emojipicker.RecentEmojiProvider

class LocalRecentEmojiProvider(context: Context) : RecentEmojiProvider {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun recordSelection(emoji: String) {
        val updated = (listOf(emoji) + current()).distinct().take(MAX_RECENTS)
        preferences.edit { putString(KEY_RECENTS, updated.joinToString(SEPARATOR)) }
    }

    override suspend fun getRecentEmojiList(): List<String> = current()

    fun clear() {
        preferences.edit { remove(KEY_RECENTS) }
    }

    internal fun current(): List<String> = preferences.getString(KEY_RECENTS, null)
        ?.split(SEPARATOR)
        ?.filter(String::isNotBlank)
        .orEmpty()

    /** Debug verification restores a user's ordered recents exactly after its fixture. */
    @SuppressLint("UseKtx") // KTX edit returns Unit, but callers need commit success/failure.
    internal fun replaceAll(recents: List<String>): Boolean {
        val normalized = recents.filter(String::isNotBlank).distinct().take(MAX_RECENTS)
        return preferences.edit()
            .putString(KEY_RECENTS, normalized.joinToString(SEPARATOR))
            .commit()
    }

    /** Debug verification needs persistence to finish before force-stopping the IME process. */
    @SuppressLint("UseKtx") // Preserve the synchronous commit result for rollback verification.
    internal fun clearSynchronously(): Boolean = preferences.edit().remove(KEY_RECENTS).commit()

    companion object {
        private const val PREFS_NAME = "emoji_recents"
        private const val KEY_RECENTS = "items"
        private const val SEPARATOR = "\u001F"
        private const val MAX_RECENTS = 40
    }
}
