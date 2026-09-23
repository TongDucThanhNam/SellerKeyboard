package com.terasumi.sellerkeyboard.profile

import com.terasumi.sellerkeyboard.settings.KeyboardHeightPreset
import com.terasumi.sellerkeyboard.settings.KeyboardLanguageMode
import com.terasumi.sellerkeyboard.settings.KeyboardPreferences
import com.terasumi.sellerkeyboard.settings.KeyboardThemeMode

data class KeyboardProfile(
    val id: String,
    val name: String,
    val toolbarOrder: List<String>,
    val preferredReplyPackId: String? = null,
    val languageMode: KeyboardLanguageMode = KeyboardLanguageMode.VIETNAMESE_ENGLISH,
    val numberRow: Boolean? = null,
    val themeOverride: KeyboardThemeMode? = null,
    val heightOverride: KeyboardHeightPreset? = null,
    val enabledSellerTools: Set<String> = toolbarOrder.toSet(),
    val isPrivate: Boolean = false
) {
    companion object {
        val Global = KeyboardProfile(
            id = "global",
            name = "Global",
            toolbarOrder = com.terasumi.sellerkeyboard.settings.KeyboardPreferences.DEFAULT_TOOLBAR_ORDER
        )
    }
}

data class AppProfileBinding(
    val packageName: String,
    val profileId: String
)

fun KeyboardProfile.applyTo(base: KeyboardPreferences): KeyboardPreferences = base.copy(
    themeMode = themeOverride ?: base.themeMode,
    numberRow = numberRow ?: base.numberRow,
    heightPreset = heightOverride ?: base.heightPreset,
    languageMode = languageMode,
    toolbarOrder = toolbarOrder.filter { it in enabledSellerTools }
)
