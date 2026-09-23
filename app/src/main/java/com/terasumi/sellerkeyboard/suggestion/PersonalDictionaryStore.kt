package com.terasumi.sellerkeyboard.suggestion

import android.content.Context
import java.util.Locale

interface PersonalDictionaryStore {
    fun words(): Set<String>
    fun add(word: String): Boolean
    fun remove(word: String): Boolean
    fun clear()
}

class SharedPreferencesPersonalDictionary(context: Context) : PersonalDictionaryStore {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun words(): Set<String> = preferences.getStringSet(KEY_WORDS, emptySet())
        ?.toSet()
        .orEmpty()

    override fun add(word: String): Boolean {
        val normalized = validatedWord(word) ?: return false
        val updated = words() + normalized
        if (updated == words()) return false
        preferences.edit().putStringSet(KEY_WORDS, updated).apply()
        return true
    }

    override fun remove(word: String): Boolean {
        val normalized = word.trim().lowercase(Locale.ROOT)
        val current = words()
        if (normalized !in current) return false
        preferences.edit().putStringSet(KEY_WORDS, current - normalized).apply()
        return true
    }

    override fun clear() {
        preferences.edit().remove(KEY_WORDS).apply()
    }

    private fun validatedWord(word: String): String? {
        val normalized = word.trim().lowercase(Locale.ROOT)
        return normalized.takeIf {
            it.length in 2..64 && it.all { character ->
                character.isLetter() || character == '-' || character == '\''
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "personal_dictionary"
        private const val KEY_WORDS = "words"
    }
}
