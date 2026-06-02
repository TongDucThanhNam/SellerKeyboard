package com.terasumi.sellerkeyboard.settings

import android.content.Context
import android.content.SharedPreferences

class KeyboardSettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var hapticEnabled: Boolean
        get() = prefs.getBoolean(PREF_HAPTIC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(PREF_HAPTIC_ENABLED, value).apply()

    // Feature Flags & Defaults for future goals
    var vietnameseInputEnabled: Boolean
        get() = prefs.getBoolean(PREF_VIETNAMESE_INPUT, true)
        set(value) = prefs.edit().putBoolean(PREF_VIETNAMESE_INPUT, value).apply()

    var telexVariant: String
        get() = prefs.getString(PREF_TELEX_VARIANT, "default") ?: "default"
        set(value) = prefs.edit().putString(PREF_TELEX_VARIANT, value).apply()

    var voiceTypingEnabled: Boolean
        get() = prefs.getBoolean(PREF_VOICE_TYPING, true)
        set(value) = prefs.edit().putBoolean(PREF_VOICE_TYPING, value).apply()

    var clipboardHistoryEnabled: Boolean
        get() = prefs.getBoolean(PREF_CLIPBOARD_HISTORY, true)
        set(value) = prefs.edit().putBoolean(PREF_CLIPBOARD_HISTORY, value).apply()

    var clipboardAutoDeleteDays: Int
        get() = prefs.getInt(PREF_CLIPBOARD_AUTO_DELETE_DAYS, 30)
        set(value) = prefs.edit().putInt(PREF_CLIPBOARD_AUTO_DELETE_DAYS, value).apply()

    var inlineAutofillEnabled: Boolean
        get() = prefs.getBoolean(PREF_INLINE_AUTOFILL, true)
        set(value) = prefs.edit().putBoolean(PREF_INLINE_AUTOFILL, value).apply()

    companion object {
        const val PREFS_NAME = "keyboard_prefs"
        const val PREF_HAPTIC_ENABLED = "pref_haptic_enabled"
        const val PREF_VIETNAMESE_INPUT = "pref_vietnamese_input"
        const val PREF_TELEX_VARIANT = "pref_telex_variant"
        const val PREF_VOICE_TYPING = "pref_voice_typing"
        const val PREF_CLIPBOARD_HISTORY = "pref_clipboard_history"
        const val PREF_CLIPBOARD_AUTO_DELETE_DAYS = "pref_clipboard_auto_delete_days"
        const val PREF_INLINE_AUTOFILL = "pref_inline_autofill"
    }
}
