package com.terasumi.sellerkeyboard.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardContentViewTest {
    @Test
    fun keyboardFontScale_preservesNormalAndCapsLargeAccessibilityScales() {
        assertEquals(1.0f, cappedKeyboardFontScale(1.0f))
        assertEquals(MAX_KEYBOARD_FONT_SCALE, cappedKeyboardFontScale(1.3f))
        assertEquals(MAX_KEYBOARD_FONT_SCALE, cappedKeyboardFontScale(2.0f))
    }
}
