package com.terasumi.sellerkeyboard.settings

import androidx.annotation.Keep

enum class KeyboardThemeMode { SYSTEM, LIGHT, DARK }
enum class KeyboardHeightPreset { COMPACT, STANDARD, TALL }
enum class OneHandedSide { OFF, LEFT, RIGHT }
enum class KeyboardLanguageMode { VIETNAMESE_ENGLISH, VIETNAMESE, ENGLISH }

@Keep
data class KeyboardPreferences(
    val themeMode: KeyboardThemeMode = KeyboardThemeMode.SYSTEM,
    val keyBorders: Boolean = false,
    val highContrast: Boolean = false,
    val numberRow: Boolean = true,
    val popupPreview: Boolean = true,
    val soundEnabled: Boolean = false,
    val soundVolume: Float = 0.5f,
    val hapticEnabled: Boolean = true,
    val hapticStrength: Int = 128,
    val learningEnabled: Boolean = true,
    val heightPreset: KeyboardHeightPreset = KeyboardHeightPreset.STANDARD,
    val oneHandedSide: OneHandedSide = OneHandedSide.OFF,
    val languageMode: KeyboardLanguageMode = KeyboardLanguageMode.VIETNAMESE_ENGLISH,
    val toolbarOrder: List<String> = DEFAULT_TOOLBAR_ORDER
) {
    companion object {
        val DEFAULT_TOOLBAR_ORDER = listOf(
            "replies", "products", "clipboard", "calculator",
            "translate", "voice", "emoji", "settings"
        )
    }
}

internal inline fun <reified T : Enum<T>> enumPreference(value: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: fallback
