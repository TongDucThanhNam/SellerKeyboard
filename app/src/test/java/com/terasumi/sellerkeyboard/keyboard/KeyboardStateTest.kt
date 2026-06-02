package com.terasumi.sellerkeyboard.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardStateTest {

    // Mimic Android's InputType constants
    private val TYPE_CLASS_TEXT = 1
    private val TYPE_CLASS_NUMBER = 2
    private val TYPE_CLASS_PHONE = 3
    private val TYPE_CLASS_DATETIME = 4

    private val TYPE_TEXT_VARIATION_PASSWORD = 128
    private val TYPE_TEXT_VARIATION_VISIBLE_PASSWORD = 144
    private val TYPE_TEXT_VARIATION_WEB_PASSWORD = 224
    private val TYPE_NUMBER_VARIATION_PASSWORD = 16

    @Test
    fun testIsPasswordInputType_forPasswordFields_returnsTrue() {
        // Text password
        val textPasswordInputType = TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_PASSWORD
        assertTrue(isPasswordInputType(textPasswordInputType))

        // Visible text password
        val visiblePasswordInputType = TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        assertTrue(isPasswordInputType(visiblePasswordInputType))

        // Web password
        val webPasswordInputType = TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_WEB_PASSWORD
        assertTrue(isPasswordInputType(webPasswordInputType))

        // Number password
        val numberPasswordInputType = TYPE_CLASS_NUMBER or TYPE_NUMBER_VARIATION_PASSWORD
        assertTrue(isPasswordInputType(numberPasswordInputType))
    }

    @Test
    fun testIsPasswordInputType_forNonPasswordFields_returnsFalse() {
        // Normal text
        val normalTextInputType = TYPE_CLASS_TEXT
        assertFalse(isPasswordInputType(normalTextInputType))

        // Email address
        val emailInputType = TYPE_CLASS_TEXT or 32 // TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        assertFalse(isPasswordInputType(emailInputType))

        // Normal number
        val normalNumberInputType = TYPE_CLASS_NUMBER
        assertFalse(isPasswordInputType(normalNumberInputType))
    }

    @Test
    fun testResolveKeyboardStateForInputType_forNumericFields_switchesToNumbers() {
        val numberInput = TYPE_CLASS_NUMBER
        val phoneInput = TYPE_CLASS_PHONE
        val datetimeInput = TYPE_CLASS_DATETIME

        assertEquals(KeyboardState.NUMBERS, resolveKeyboardStateForInputType(numberInput, KeyboardState.NOCAPS))
        assertEquals(KeyboardState.NUMBERS, resolveKeyboardStateForInputType(phoneInput, KeyboardState.NOCAPS))
        assertEquals(KeyboardState.NUMBERS, resolveKeyboardStateForInputType(datetimeInput, KeyboardState.NOCAPS))
    }

    @Test
    fun testResolveKeyboardStateForInputType_forTextFields_keepsOrResetsCapsState() {
        val textInput = TYPE_CLASS_TEXT

        // If current state is CAPS, keep it
        assertEquals(KeyboardState.CAPS, resolveKeyboardStateForInputType(textInput, KeyboardState.CAPS))

        // If current state is NUMBERS (focused from previous field), reset to NOCAPS
        assertEquals(KeyboardState.NOCAPS, resolveKeyboardStateForInputType(textInput, KeyboardState.NUMBERS))

        // If current state is SYMBOLS, reset to NOCAPS
        assertEquals(KeyboardState.NOCAPS, resolveKeyboardStateForInputType(textInput, KeyboardState.SYMBOLS))
    }
}
