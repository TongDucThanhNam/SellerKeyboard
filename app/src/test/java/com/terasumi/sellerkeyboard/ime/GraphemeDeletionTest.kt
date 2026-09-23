package com.terasumi.sellerkeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class GraphemeDeletionTest {
    @Test
    fun previousClusterLength_handlesUnicodeCorpus() {
        val cases = mapOf(
            "a" to 1,
            "😀" to 2,
            "a\u0301" to 2,
            "🇻🇳" to 4,
            "👍🏽" to 4,
            "👩‍👩‍👧‍👦" to 11,
            "1️⃣" to 3
        )

        cases.forEach { (text, expectedLength) ->
            assertEquals(text, expectedLength, GraphemeDeletion.previousClusterUtf16Length(text))
        }
    }

    @Test
    fun scanner_deletesOnlyLastClusterFromMixedText() {
        val text = "xin chào 👩‍💻"
        assertEquals("👩‍💻".length, GraphemeDeletion.previousClusterUtf16Length(text))
    }
}
