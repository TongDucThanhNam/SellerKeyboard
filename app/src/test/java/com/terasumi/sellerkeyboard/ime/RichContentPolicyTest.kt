package com.terasumi.sellerkeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RichContentPolicyTest {
    private val normal = EditorPrivacyPolicy.resolve(isPassword = false)

    @Test
    fun exactAndWildcardMimeTypes_areNegotiated() {
        assertTrue(RichContentPolicy.mimeMatches("image/jpeg", "image/jpeg"))
        assertTrue(RichContentPolicy.mimeMatches("image/*", "image/jpeg"))
        assertFalse(RichContentPolicy.mimeMatches("image/png", "image/jpeg"))
        assertFalse(RichContentPolicy.mimeMatches("image/jpeg, image/png", "image/jpeg"))
    }

    @Test
    fun unsupportedOrSensitiveTarget_usesTextFallback() {
        assertEquals(
            RichContentDecision.TEXT_FALLBACK,
            RichContentPolicy.decide(normal, setOf("text/plain"), "image/jpeg")
        )
        assertEquals(
            RichContentDecision.TEXT_FALLBACK,
            RichContentPolicy.decide(
                EditorPrivacyPolicy.resolve(isPassword = true),
                setOf("image/*"),
                "image/jpeg"
            )
        )
        assertEquals(
            RichContentDecision.COMMIT_RICH_CONTENT,
            RichContentPolicy.decide(normal, setOf("image/*"), "image/jpeg")
        )
    }
}
