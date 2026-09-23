package com.terasumi.sellerkeyboard.ime

import com.terasumi.sellerkeyboard.keyboard.KeyboardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorInfoPolicyTest {
    private val typeClassText = 1
    private val typeClassNumber = 2
    private val typeClassPhone = 3
    private val typeClassDatetime = 4

    private val textVariationUri = 16
    private val textVariationEmailAddress = 32
    private val textVariationPassword = 128
    private val textVariationVisiblePassword = 144
    private val textVariationWebEmailAddress = 208
    private val textVariationWebPassword = 224
    private val numberVariationPassword = 16

    @Test
    fun passwordFields_areSensitiveAndDisableImeFeatures() {
        val inputTypes = listOf(
            typeClassText or textVariationPassword,
            typeClassText or textVariationVisiblePassword,
            typeClassText or textVariationWebPassword,
            typeClassNumber or numberVariationPassword
        )

        inputTypes.forEach { inputType ->
            val policy = EditorInfoPolicy.fromInputType(inputType)

            assertTrue(policy.isPasswordField)
            assertTrue(policy.isSensitiveField)
            assertFalse(policy.canUseTelex)
            assertFalse(policy.canShowClipboard)
            assertFalse(policy.canUseVoiceTyping)
            assertTrue(policy.canUseInlineAutofill)
        }
    }

    @Test
    fun uriField_isNotMisclassifiedAsNumberPassword() {
        val policy = EditorInfoPolicy.fromInputType(typeClassText or textVariationUri)

        assertTrue(policy.isUrlField)
        assertFalse(policy.isPasswordField)
        assertFalse(policy.isSensitiveField)
        assertFalse(policy.canUseTelex)
    }

    @Test
    fun emailFields_areClassifiedAndDoNotUseTelex() {
        val emailPolicy = EditorInfoPolicy.fromInputType(typeClassText or textVariationEmailAddress)
        val webEmailPolicy = EditorInfoPolicy.fromInputType(typeClassText or textVariationWebEmailAddress)

        assertTrue(emailPolicy.isEmailField)
        assertTrue(webEmailPolicy.isEmailField)
        assertFalse(emailPolicy.canUseTelex)
        assertFalse(webEmailPolicy.canUseTelex)
        assertTrue(emailPolicy.canShowClipboard)
    }

    @Test
    fun normalTextField_allowsTextFeatures() {
        val policy = EditorInfoPolicy.fromInputType(typeClassText)

        assertTrue(policy.canUseTelex)
        assertTrue(policy.canShowClipboard)
        assertTrue(policy.canUseVoiceTyping)
        assertTrue(policy.canUseInlineAutofill)
    }

    @Test
    fun numericPhoneAndDateFields_switchKeyboardToNumbers() {
        listOf(typeClassNumber, typeClassPhone, typeClassDatetime).forEach { inputType ->
            val policy = EditorInfoPolicy.fromInputType(inputType)

            assertTrue(policy.isNumericField)
            assertFalse(policy.canUseTelex)
            assertEquals(KeyboardState.NUMBERS, policy.resolveKeyboardState(KeyboardState.NOCAPS))
        }
    }

    @Test
    fun textFieldLeavingNumericOrSymbolState_resetsToNoCaps() {
        val policy = EditorInfoPolicy.fromInputType(typeClassText)

        assertEquals(KeyboardState.NOCAPS, policy.resolveKeyboardState(KeyboardState.NUMBERS))
        assertEquals(KeyboardState.NOCAPS, policy.resolveKeyboardState(KeyboardState.SYMBOLS))
        assertEquals(KeyboardState.CAPS, policy.resolveKeyboardState(KeyboardState.CAPS))
    }

    @Test
    fun writingToolsOptOut_andPrivateProfile_failClosed() {
        val writingToolsDisabled = EditorInfoPolicy.fromInputType(
            inputType = typeClassText,
            writingToolsEnabled = false
        )
        val privateProfile = EditorInfoPolicy.fromInputType(
            inputType = typeClassText,
            isPrivateProfile = true
        )

        listOf(writingToolsDisabled, privateProfile).forEach { policy ->
            assertTrue(policy.isSensitiveField)
            assertFalse(policy.canUseTelex)
            assertFalse(policy.canShowClipboard)
            assertFalse(policy.canUseVoiceTyping)
            assertTrue(policy.canUseInlineAutofill)
        }
    }
}
