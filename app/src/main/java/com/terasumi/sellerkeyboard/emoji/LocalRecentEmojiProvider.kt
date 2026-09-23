package com.terasumi.sellerkeyboard.emoji

import android.content.Context
import androidx.emoji2.emojipicker.RecentEmojiProvider

class LocalRecentEmojiProvider(context: Context) : RecentEmojiProvider {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun recordSelection(emoji: String) {
        val updated = (listOf(emoji) + current()).distinct().take(MAX_RECENTS)
        preferences.edit().putString(KEY_RECENTS, updated.joinToString(SEPARATOR)).apply()
    }

    override suspend fun getRecentEmojiList(): List<String> = current()

    fun clear() {
        preferences.edit().remove(KEY_RECENTS).apply()
    }

    internal fun current(): List<String> = preferences.getString(KEY_RECENTS, null)
        ?.split(SEPARATOR)
        ?.filter(String::isNotBlank)
        .orEmpty()

    /** Debug verification restores a user's ordered recents exactly after its fixture. */
    internal fun replaceAll(recents: List<String>): Boolean {
        val normalized = recents.filter(String::isNotBlank).distinct().take(MAX_RECENTS)
        return preferences.edit()
            .putString(KEY_RECENTS, normalized.joinToString(SEPARATOR))
            .commit()
    }

    /** Debug verification needs persistence to finish before force-stopping the IME process. */
    internal fun clearSynchronously(): Boolean = preferences.edit().remove(KEY_RECENTS).commit()

    companion object {
        private const val PREFS_NAME = "emoji_recents"
        private const val KEY_RECENTS = "items"
        private const val SEPARATOR = "\u001F"
        private const val MAX_RECENTS = 40
    }
}
