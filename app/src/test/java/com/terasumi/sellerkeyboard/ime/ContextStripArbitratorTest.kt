package com.terasumi.sellerkeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class ContextStripArbitratorTest {
    private val normal = EditorPrivacyPolicy.resolve(isPassword = false)
    private val sensitive = EditorPrivacyPolicy.resolve(isPassword = true)

    @Test
    fun priorityOrder_isDeterministic() {
        val all = ContextStripAvailability(
            hasInlineAutofill = true,
            hasEditorCompletions = true,
            hasExplicitVoice = true,
            hasExplicitSellerWorkflow = true,
            hasTextCandidates = true,
            hasClipboardSuggestion = true,
            hasExplicitSourceSellerTopic = true
        )

        assertEquals(ContextStripSource.INLINE_AUTOFILL, ContextStripArbitrator.select(normal, all))
        assertEquals(
            ContextStripSource.EDITOR_COMPLETION,
            ContextStripArbitrator.select(normal, all.copy(hasInlineAutofill = false))
        )
        assertEquals(
            ContextStripSource.EXPLICIT_VOICE,
            ContextStripArbitrator.select(
                normal,
                all.copy(hasInlineAutofill = false, hasEditorCompletions = false)
            )
        )
        assertEquals(
            ContextStripSource.EXPLICIT_SELLER_WORKFLOW,
            ContextStripArbitrator.select(
                normal,
                all.copy(
                    hasInlineAutofill = false,
                    hasEditorCompletions = false,
                    hasExplicitVoice = false
                )
            )
        )
    }

    @Test
    fun sensitiveMode_suppressesEverythingExceptSystemOwnedInlineAutofill() {
        val contextFeatures = ContextStripAvailability(
            hasEditorCompletions = true,
            hasExplicitVoice = true,
            hasTextCandidates = true,
            hasClipboardSuggestion = true
        )
        assertEquals(ContextStripSource.NONE, ContextStripArbitrator.select(sensitive, contextFeatures))
        assertEquals(
            ContextStripSource.INLINE_AUTOFILL,
            ContextStripArbitrator.select(sensitive, contextFeatures.copy(hasInlineAutofill = true))
        )
    }
}
