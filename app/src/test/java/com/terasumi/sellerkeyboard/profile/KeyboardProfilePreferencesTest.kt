package com.terasumi.sellerkeyboard.profile

import com.terasumi.sellerkeyboard.settings.KeyboardHeightPreset
import com.terasumi.sellerkeyboard.settings.KeyboardLanguageMode
import com.terasumi.sellerkeyboard.settings.KeyboardPreferences
import com.terasumi.sellerkeyboard.settings.KeyboardThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class KeyboardProfilePreferencesTest {
    @Test
    fun applyTo_overridesOnlyProfileScopedPreferencesAndFiltersDisabledTools() {
        val base = KeyboardPreferences(
            themeMode = KeyboardThemeMode.LIGHT,
            numberRow = true,
            heightPreset = KeyboardHeightPreset.STANDARD,
            languageMode = KeyboardLanguageMode.ENGLISH,
            toolbarOrder = listOf("clipboard", "emoji")
        )
        val profile = KeyboardProfile(
            id = "seller",
            name = "Seller",
            toolbarOrder = listOf("products", "replies", "translate"),
            languageMode = KeyboardLanguageMode.VIETNAMESE,
            numberRow = false,
            themeOverride = KeyboardThemeMode.DARK,
            heightOverride = KeyboardHeightPreset.TALL,
            enabledSellerTools = setOf("products", "replies")
        )

        val result = profile.applyTo(base)

        assertEquals(KeyboardThemeMode.DARK, result.themeMode)
        assertFalse(result.numberRow)
        assertEquals(KeyboardHeightPreset.TALL, result.heightPreset)
        assertEquals(KeyboardLanguageMode.VIETNAMESE, result.languageMode)
        assertEquals(listOf("products", "replies"), result.toolbarOrder)
        assertEquals(base.hapticEnabled, result.hapticEnabled)
        assertEquals(base.popupPreview, result.popupPreview)
    }
}
