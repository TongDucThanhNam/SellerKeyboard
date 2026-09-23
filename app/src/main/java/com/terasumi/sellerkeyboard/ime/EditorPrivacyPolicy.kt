package com.terasumi.sellerkeyboard.ime

/**
 * A fail-closed capability decision shared by every context-bearing IME feature.
 * Feature code consumes these capabilities instead of independently interpreting
 * EditorInfo flags.
 */
data class EditorPrivacyPolicy(
    val isSensitive: Boolean,
    val canReadSurroundingText: Boolean,
    val canOfferCandidates: Boolean,
    val canLearn: Boolean,
    val canCaptureClipboard: Boolean,
    val canOfferClipboard: Boolean,
    val canUseSellerTools: Boolean,
    val canUseVoice: Boolean,
    val canUseTranslation: Boolean,
    val canCommitRichContent: Boolean,
    val canUseInlineAutofill: Boolean
) {
    companion object {
        fun resolve(
            isPassword: Boolean,
            isAuthenticationValue: Boolean = false,
            writingToolsEnabled: Boolean = true,
            isPrivateProfile: Boolean = false,
            hasEditor: Boolean = true
        ): EditorPrivacyPolicy {
            val sensitive = isPassword || isAuthenticationValue ||
                !writingToolsEnabled || isPrivateProfile

            return EditorPrivacyPolicy(
                isSensitive = sensitive,
                canReadSurroundingText = hasEditor && !sensitive,
                canOfferCandidates = hasEditor && !sensitive,
                canLearn = hasEditor && !sensitive,
                canCaptureClipboard = hasEditor && !sensitive,
                canOfferClipboard = hasEditor && !sensitive,
                canUseSellerTools = hasEditor && !sensitive,
                canUseVoice = hasEditor && !sensitive,
                canUseTranslation = hasEditor && !sensitive,
                canCommitRichContent = hasEditor && !sensitive,
                // Android inline autofill does not reveal hidden values until
                // the user explicitly selects a system-owned suggestion.
                canUseInlineAutofill = hasEditor
            )
        }
    }
}
