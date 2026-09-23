package com.terasumi.sellerkeyboard.translation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationPolicyTest {
    @Test
    fun explicitNonSensitiveText_isAllowedWithinBound() {
        assertTrue(TranslationPolicy.canTranslate("Xin chào", isSensitive = false))
        assertFalse(TranslationPolicy.canTranslate("", isSensitive = false))
        assertFalse(TranslationPolicy.canTranslate("secret", isSensitive = true))
        assertFalse(
            TranslationPolicy.canTranslate(
                "x".repeat(TranslationPolicy.MAX_TEXT_LENGTH + 1),
                isSensitive = false
            )
        )
    }

    @Test
    fun nativeProviderLoggingRequiresAndroid11() {
        assertFalse(TranslationPolicy.supportsPrivateProviderLogging(apiLevel = 29))
        assertTrue(TranslationPolicy.supportsPrivateProviderLogging(apiLevel = 30))
        assertTrue(TranslationPolicy.supportsPrivateProviderLogging(apiLevel = 36))
    }
}
