package com.terasumi.sellerkeyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.terasumi.sellerkeyboard.settings.KeyboardLanguageMode

enum class ActiveImeSurface {
    KEYBOARD,
    REPLIES,
    CALCULATOR,
    EMOJI,
    TRANSLATE,
    PRODUCTS,
    CLIPBOARD
}

enum class InputFieldKind {
    TEXT,
    EMAIL,
    URI,
    PASSWORD,
    INTEGER,
    DECIMAL,
    SIGNED_NUMBER,
    SIGNED_DECIMAL,
    PHONE,
    DATE_TIME,
    UNKNOWN
}

data class EditorSession(
    val id: Long,
    val inputType: Int,
    val imeOptions: Int,
    val fieldKind: InputFieldKind,
    val isMultiline: Boolean,
    val actionId: Int,
    val actionLabel: String?,
    val noEnterAction: Boolean,
    val initialCapsMode: Int,
    val currentCapsMode: Int,
    val selectionStart: Int,
    val selectionEnd: Int,
    val packageName: String,
    val activeProfileId: String,
    val activeProfileName: String,
    val preferredReplyPackId: String?,
    val languageMode: KeyboardLanguageMode,
    val composingText: String,
    val composingStart: Int,
    val composingEnd: Int,
    val activeSurface: ActiveImeSurface,
    val candidateSource: ContextStripSource,
    val supportedMimeTypes: Set<String>,
    val privacy: EditorPrivacyPolicy
) {
    companion object {
        val Inactive = EditorSession(
            id = 0L,
            inputType = 0,
            imeOptions = 0,
            fieldKind = InputFieldKind.UNKNOWN,
            isMultiline = false,
            actionId = EditorInfo.IME_ACTION_UNSPECIFIED,
            actionLabel = null,
            noEnterAction = false,
            initialCapsMode = 0,
            currentCapsMode = 0,
            selectionStart = -1,
            selectionEnd = -1,
            packageName = "",
            activeProfileId = "global",
            activeProfileName = "Global",
            preferredReplyPackId = null,
            languageMode = KeyboardLanguageMode.VIETNAMESE_ENGLISH,
            composingText = "",
            composingStart = -1,
            composingEnd = -1,
            activeSurface = ActiveImeSurface.KEYBOARD,
            candidateSource = ContextStripSource.NONE,
            supportedMimeTypes = emptySet(),
            privacy = EditorPrivacyPolicy.resolve(isPassword = false, hasEditor = false)
        )
    }
}

data class EditorSessionRequest(
    val id: Long,
    val inputType: Int,
    val imeOptions: Int,
    val actionId: Int = 0,
    val actionLabel: String? = null,
    val hasCustomActionLabel: Boolean = false,
    val initialCapsMode: Int = 0,
    val selectionStart: Int = -1,
    val selectionEnd: Int = -1,
    val packageName: String = "",
    val activeProfileId: String = "global",
    val activeProfileName: String = "Global",
    val preferredReplyPackId: String? = null,
    val languageMode: KeyboardLanguageMode = KeyboardLanguageMode.VIETNAMESE_ENGLISH,
    val supportedMimeTypes: Set<String> = emptySet(),
    val isAuthenticationValue: Boolean = false,
    val writingToolsEnabled: Boolean = true,
    val isPrivateProfile: Boolean = false
)

object EditorSessionResolver {
    fun resolve(request: EditorSessionRequest): EditorSession {
        val policy = EditorInfoPolicy.fromInputType(
            inputType = request.inputType,
            isAuthenticationValue = request.isAuthenticationValue,
            writingToolsEnabled = request.writingToolsEnabled,
            isPrivateProfile = request.isPrivateProfile
        )
        val standardAction = request.imeOptions and EditorInfo.IME_MASK_ACTION
        val resolvedAction = if (request.hasCustomActionLabel && request.actionId != 0) {
            request.actionId
        } else {
            standardAction
        }

        return EditorSession(
            id = request.id,
            inputType = request.inputType,
            imeOptions = request.imeOptions,
            fieldKind = policy.fieldKind,
            isMultiline = policy.isMultiline,
            actionId = resolvedAction,
            actionLabel = request.actionLabel,
            noEnterAction = request.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0,
            initialCapsMode = request.initialCapsMode,
            currentCapsMode = request.initialCapsMode,
            selectionStart = request.selectionStart,
            selectionEnd = request.selectionEnd,
            packageName = request.packageName,
            activeProfileId = request.activeProfileId,
            activeProfileName = request.activeProfileName,
            preferredReplyPackId = request.preferredReplyPackId,
            languageMode = request.languageMode,
            composingText = "",
            composingStart = -1,
            composingEnd = -1,
            activeSurface = ActiveImeSurface.KEYBOARD,
            candidateSource = ContextStripSource.NONE,
            supportedMimeTypes = request.supportedMimeTypes,
            privacy = policy.privacy
        )
    }
}

internal fun resolveInputFieldKind(inputType: Int): InputFieldKind {
    val inputClass = inputType and InputType.TYPE_MASK_CLASS
    val variation = inputType and InputType.TYPE_MASK_VARIATION
    return when (inputClass) {
        InputType.TYPE_CLASS_TEXT -> when (variation) {
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> InputFieldKind.EMAIL
            InputType.TYPE_TEXT_VARIATION_URI -> InputFieldKind.URI
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> InputFieldKind.PASSWORD
            else -> InputFieldKind.TEXT
        }
        InputType.TYPE_CLASS_NUMBER -> when {
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD -> InputFieldKind.PASSWORD
            inputType and InputType.TYPE_NUMBER_FLAG_DECIMAL != 0 &&
                inputType and InputType.TYPE_NUMBER_FLAG_SIGNED != 0 -> InputFieldKind.SIGNED_DECIMAL
            inputType and InputType.TYPE_NUMBER_FLAG_DECIMAL != 0 -> InputFieldKind.DECIMAL
            inputType and InputType.TYPE_NUMBER_FLAG_SIGNED != 0 -> InputFieldKind.SIGNED_NUMBER
            else -> InputFieldKind.INTEGER
        }
        InputType.TYPE_CLASS_PHONE -> InputFieldKind.PHONE
        InputType.TYPE_CLASS_DATETIME -> InputFieldKind.DATE_TIME
        else -> InputFieldKind.UNKNOWN
    }
}
