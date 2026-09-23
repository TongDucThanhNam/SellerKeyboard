package com.terasumi.sellerkeyboard.ime

enum class ContextStripSource {
    NONE,
    INLINE_AUTOFILL,
    EDITOR_COMPLETION,
    EXPLICIT_VOICE,
    EXPLICIT_SELLER_WORKFLOW,
    TEXT_CANDIDATE,
    CLIPBOARD,
    EXPLICIT_SOURCE_SELLER_TOPIC
}

data class ContextStripAvailability(
    val hasInlineAutofill: Boolean = false,
    val hasEditorCompletions: Boolean = false,
    val hasExplicitVoice: Boolean = false,
    val hasExplicitSellerWorkflow: Boolean = false,
    val hasTextCandidates: Boolean = false,
    val hasClipboardSuggestion: Boolean = false,
    val hasExplicitSourceSellerTopic: Boolean = false
)

object ContextStripArbitrator {
    const val STABLE_HEIGHT_DP = 52

    fun select(
        privacy: EditorPrivacyPolicy,
        availability: ContextStripAvailability
    ): ContextStripSource {
        if (availability.hasInlineAutofill && privacy.canUseInlineAutofill) {
            return ContextStripSource.INLINE_AUTOFILL
        }
        if (privacy.isSensitive) {
            return ContextStripSource.NONE
        }
        return when {
            availability.hasEditorCompletions -> ContextStripSource.EDITOR_COMPLETION
            availability.hasExplicitVoice && privacy.canUseVoice ->
                ContextStripSource.EXPLICIT_VOICE
            availability.hasExplicitSellerWorkflow && privacy.canUseSellerTools ->
                ContextStripSource.EXPLICIT_SELLER_WORKFLOW
            availability.hasTextCandidates && privacy.canOfferCandidates ->
                ContextStripSource.TEXT_CANDIDATE
            availability.hasClipboardSuggestion && privacy.canOfferClipboard ->
                ContextStripSource.CLIPBOARD
            availability.hasExplicitSourceSellerTopic && privacy.canUseSellerTools ->
                ContextStripSource.EXPLICIT_SOURCE_SELLER_TOPIC
            else -> ContextStripSource.NONE
        }
    }
}
