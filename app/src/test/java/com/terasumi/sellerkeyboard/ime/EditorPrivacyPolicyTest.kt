package com.terasumi.sellerkeyboard.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPrivacyPolicyTest {
    @Test
    fun normalEditor_allowsLocalContextFeatures() {
        val policy = EditorPrivacyPolicy.resolve(isPassword = false)

        assertFalse(policy.isSensitive)
        assertTrue(policy.canReadSurroundingText)
        assertTrue(policy.canOfferCandidates)
        assertTrue(policy.canLearn)
        assertTrue(policy.canCaptureClipboard)
        assertTrue(policy.canUseSellerTools)
        assertTrue(policy.canUseVoice)
        assertTrue(policy.canUseTranslation)
        assertTrue(policy.canCommitRichContent)
        assertTrue(policy.canUseInlineAutofill)
    }

    @Test
    fun everySensitiveReason_disablesContextBearingFeatures() {
        val policies = listOf(
            EditorPrivacyPolicy.resolve(isPassword = true),
            EditorPrivacyPolicy.resolve(isPassword = false, isAuthenticationValue = true),
            EditorPrivacyPolicy.resolve(isPassword = false, writingToolsEnabled = false),
            EditorPrivacyPolicy.resolve(isPassword = false, isPrivateProfile = true)
        )

        policies.forEach { policy ->
            assertTrue(policy.isSensitive)
            assertFalse(policy.canReadSurroundingText)
            assertFalse(policy.canOfferCandidates)
            assertFalse(policy.canLearn)
            assertFalse(policy.canCaptureClipboard)
            assertFalse(policy.canOfferClipboard)
            assertFalse(policy.canUseSellerTools)
            assertFalse(policy.canUseVoice)
            assertFalse(policy.canUseTranslation)
            assertFalse(policy.canCommitRichContent)
            assertTrue(policy.canUseInlineAutofill)
        }
    }

    @Test
    fun inactiveEditor_disablesAllCapabilities() {
        val policy = EditorPrivacyPolicy.resolve(isPassword = false, hasEditor = false)

        assertFalse(policy.canReadSurroundingText)
        assertFalse(policy.canUseInlineAutofill)
    }
}
