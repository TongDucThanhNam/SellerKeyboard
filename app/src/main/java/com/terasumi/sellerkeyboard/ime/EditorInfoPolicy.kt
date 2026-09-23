package com.terasumi.sellerkeyboard.ime

import android.text.InputType
import com.terasumi.sellerkeyboard.keyboard.KeyboardState

data class EditorInfoPolicy(
    val inputType: Int,
    val privacy: EditorPrivacyPolicy
) {
    val inputClass: Int = inputType and InputType.TYPE_MASK_CLASS
    val variation: Int = inputType and InputType.TYPE_MASK_VARIATION

    val isTextField: Boolean = inputClass == InputType.TYPE_CLASS_TEXT
    val isNumericField: Boolean = inputClass == InputType.TYPE_CLASS_NUMBER ||
        inputClass == InputType.TYPE_CLASS_PHONE ||
        inputClass == InputType.TYPE_CLASS_DATETIME

    val isEmailField: Boolean = isTextField && (
        variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        )

    val isUrlField: Boolean = isTextField && variation == InputType.TYPE_TEXT_VARIATION_URI

    private val isTextPasswordField: Boolean = isTextField && (
        variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )

    private val isNumberPasswordField: Boolean =
        inputClass == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD

    val isPasswordField: Boolean = isTextPasswordField || isNumberPasswordField

    val isSensitiveField: Boolean = privacy.isSensitive

    val fieldKind: InputFieldKind = resolveInputFieldKind(inputType)

    val isMultiline: Boolean = isTextField &&
        inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0

    val canAutoCapitalize: Boolean = isTextField && !isSensitiveField &&
        !isEmailField && !isUrlField

    val canUseTelex: Boolean = isTextField && !isSensitiveField && !isEmailField && !isUrlField
    val canShowClipboard: Boolean = privacy.canOfferClipboard
    val canUseVoiceTyping: Boolean = isTextField && privacy.canUseVoice
    val canUseInlineAutofill: Boolean = privacy.canUseInlineAutofill

    fun resolveKeyboardState(currentState: KeyboardState): KeyboardState {
        return if (isNumericField) {
            KeyboardState.NUMBERS
        } else if (currentState == KeyboardState.NUMBERS || currentState == KeyboardState.SYMBOLS) {
            KeyboardState.NOCAPS
        } else {
            currentState
        }
    }

    companion object {
        fun fromInputType(
            inputType: Int,
            isAuthenticationValue: Boolean = false,
            writingToolsEnabled: Boolean = true,
            isPrivateProfile: Boolean = false
        ): EditorInfoPolicy {
            val fieldKind = resolveInputFieldKind(inputType)
            val privacy = EditorPrivacyPolicy.resolve(
                isPassword = fieldKind == InputFieldKind.PASSWORD,
                isAuthenticationValue = isAuthenticationValue,
                writingToolsEnabled = writingToolsEnabled,
                isPrivateProfile = isPrivateProfile,
                hasEditor = inputType != 0
            )
            return EditorInfoPolicy(inputType = inputType, privacy = privacy)
        }
    }
}
