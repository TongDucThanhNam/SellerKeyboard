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

    var themeMode: KeyboardThemeMode
        get() = enumPreference(
            prefs.getString(PREF_THEME_MODE, null),
            KeyboardThemeMode.SYSTEM
        )
        set(value) = prefs.edit().putString(PREF_THEME_MODE, value.name).apply()

    var keyBorders: Boolean
        get() = prefs.getBoolean(PREF_KEY_BORDERS, false)
        set(value) = prefs.edit().putBoolean(PREF_KEY_BORDERS, value).apply()

    var highContrast: Boolean
        get() = prefs.getBoolean(PREF_HIGH_CONTRAST, false)
        set(value) = prefs.edit().putBoolean(PREF_HIGH_CONTRAST, value).apply()

    var numberRow: Boolean
        get() = prefs.getBoolean(PREF_NUMBER_ROW, true)
        set(value) = prefs.edit().putBoolean(PREF_NUMBER_ROW, value).apply()

    var popupPreview: Boolean
        get() = prefs.getBoolean(PREF_POPUP_PREVIEW, true)
        set(value) = prefs.edit().putBoolean(PREF_POPUP_PREVIEW, value).apply()

    var soundEnabled: Boolean
        get() = prefs.getBoolean(PREF_SOUND_ENABLED, false)
        set(value) = prefs.edit().putBoolean(PREF_SOUND_ENABLED, value).apply()

    var soundVolume: Float
        get() = prefs.getFloat(PREF_SOUND_VOLUME, 0.5f).coerceIn(0f, 1f)
        set(value) = prefs.edit().putFloat(PREF_SOUND_VOLUME, value.coerceIn(0f, 1f)).apply()

    var hapticStrength: Int
        get() = prefs.getInt(PREF_HAPTIC_STRENGTH, 128).coerceIn(1, 255)
        set(value) = prefs.edit().putInt(PREF_HAPTIC_STRENGTH, value.coerceIn(1, 255)).apply()

    var learningEnabled: Boolean
        get() = prefs.getBoolean(PREF_LEARNING_ENABLED, true)
        set(value) = prefs.edit().putBoolean(PREF_LEARNING_ENABLED, value).apply()

    var heightPreset: KeyboardHeightPreset
        get() = enumPreference(
            prefs.getString(PREF_HEIGHT_PRESET, null),
            KeyboardHeightPreset.STANDARD
        )
        set(value) = prefs.edit().putString(PREF_HEIGHT_PRESET, value.name).apply()

    var oneHandedSide: OneHandedSide
        get() = enumPreference(
            prefs.getString(PREF_ONE_HANDED_SIDE, null),
            OneHandedSide.OFF
        )
        set(value) = prefs.edit().putString(PREF_ONE_HANDED_SIDE, value.name).apply()

    var languageMode: KeyboardLanguageMode
        get() = enumPreference(
            prefs.getString(PREF_LANGUAGE_MODE, null),
            KeyboardLanguageMode.VIETNAMESE_ENGLISH
        )
        set(value) = prefs.edit().putString(PREF_LANGUAGE_MODE, value.name).apply()

    var toolbarOrder: List<String>
        get() = prefs.getString(PREF_TOOLBAR_ORDER, null)
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.distinct()
            ?.takeIf(List<String>::isNotEmpty)
            ?: KeyboardPreferences.DEFAULT_TOOLBAR_ORDER
        set(value) = prefs.edit().putString(
            PREF_TOOLBAR_ORDER,
            value.map(String::trim).filter(String::isNotEmpty).distinct().joinToString(",")
        ).apply()

    fun snapshot(): KeyboardPreferences = KeyboardPreferences(
        themeMode = themeMode,
        keyBorders = keyBorders,
        highContrast = highContrast,
        numberRow = numberRow,
        popupPreview = popupPreview,
        soundEnabled = soundEnabled,
        soundVolume = soundVolume,
        hapticEnabled = hapticEnabled,
        hapticStrength = hapticStrength,
        learningEnabled = learningEnabled,
        heightPreset = heightPreset,
        oneHandedSide = oneHandedSide,
        languageMode = languageMode,
        toolbarOrder = toolbarOrder
    )

    fun save(preferences: KeyboardPreferences) {
        prefs.edit()
            .putString(PREF_THEME_MODE, preferences.themeMode.name)
            .putBoolean(PREF_KEY_BORDERS, preferences.keyBorders)
            .putBoolean(PREF_HIGH_CONTRAST, preferences.highContrast)
            .putBoolean(PREF_NUMBER_ROW, preferences.numberRow)
            .putBoolean(PREF_POPUP_PREVIEW, preferences.popupPreview)
            .putBoolean(PREF_SOUND_ENABLED, preferences.soundEnabled)
            .putFloat(PREF_SOUND_VOLUME, preferences.soundVolume.coerceIn(0f, 1f))
            .putBoolean(PREF_HAPTIC_ENABLED, preferences.hapticEnabled)
            .putInt(PREF_HAPTIC_STRENGTH, preferences.hapticStrength.coerceIn(1, 255))
            .putBoolean(PREF_LEARNING_ENABLED, preferences.learningEnabled)
            .putString(PREF_HEIGHT_PRESET, preferences.heightPreset.name)
            .putString(PREF_ONE_HANDED_SIDE, preferences.oneHandedSide.name)
            .putString(PREF_LANGUAGE_MODE, preferences.languageMode.name)
            .putString(PREF_TOOLBAR_ORDER, preferences.toolbarOrder.joinToString(","))
            .apply()
    }

    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "keyboard_prefs"
        const val PREF_HAPTIC_ENABLED = "pref_haptic_enabled"
        const val PREF_VIETNAMESE_INPUT = "pref_vietnamese_input"
        const val PREF_TELEX_VARIANT = "pref_telex_variant"
        const val PREF_VOICE_TYPING = "pref_voice_typing"
        const val PREF_CLIPBOARD_HISTORY = "pref_clipboard_history"
        const val PREF_CLIPBOARD_AUTO_DELETE_DAYS = "pref_clipboard_auto_delete_days"
        const val PREF_INLINE_AUTOFILL = "pref_inline_autofill"
        const val PREF_THEME_MODE = "pref_theme_mode"
        const val PREF_KEY_BORDERS = "pref_key_borders"
        const val PREF_HIGH_CONTRAST = "pref_high_contrast"
        const val PREF_NUMBER_ROW = "pref_number_row"
        const val PREF_POPUP_PREVIEW = "pref_popup_preview"
        const val PREF_SOUND_ENABLED = "pref_sound_enabled"
        const val PREF_SOUND_VOLUME = "pref_sound_volume"
        const val PREF_HAPTIC_STRENGTH = "pref_haptic_strength"
        const val PREF_LEARNING_ENABLED = "pref_learning_enabled"
        const val PREF_HEIGHT_PRESET = "pref_height_preset"
        const val PREF_ONE_HANDED_SIDE = "pref_one_handed_side"
        const val PREF_LANGUAGE_MODE = "pref_language_mode"
        const val PREF_TOOLBAR_ORDER = "pref_toolbar_order"
    }
}
