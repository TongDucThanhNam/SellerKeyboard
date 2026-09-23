package com.terasumi.sellerkeyboard.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardPreferencesTest {
    @Test
    fun invalidPersistedEnum_fallsBackDeterministically() {
        assertEquals(
            KeyboardThemeMode.SYSTEM,
            enumPreference("BROKEN", KeyboardThemeMode.SYSTEM)
        )
    }

    @Test
    fun defaults_coverRequiredToolbarAndAccessibilitySettings() {
        val preferences = KeyboardPreferences()

        assertTrue(preferences.popupPreview)
        assertTrue(preferences.numberRow)
        assertEquals(8, preferences.toolbarOrder.size)
        assertTrue("replies" in preferences.toolbarOrder)
        assertTrue("settings" in preferences.toolbarOrder)
    }
}
