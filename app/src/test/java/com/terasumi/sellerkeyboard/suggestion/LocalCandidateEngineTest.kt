package com.terasumi.sellerkeyboard.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCandidateEngineTest {
    private val dictionary = mapOf(
        "shipping" to 100,
        "shop" to 90,
        "cảm" to 100,
        "ơn" to 90,
        "đơn" to 80,
        "hàng" to 70,
        "price" to 60
    )
    private val engine = LocalCandidateEngine(
        dictionary = dictionary,
        nextWords = mapOf("cảm" to listOf("ơn"), "đơn" to listOf("hàng")),
        correctionThreshold = 0.70
    )

    @Test
    fun prefixAndMixedLanguageCandidates_areRankedLocally() {
        val suggestions = engine.suggest(CandidateQuery(currentToken = "sh", maxResults = 3))
        assertTrue(suggestions.any { it.text == "shipping" })
        assertTrue(suggestions.any { it.text == "shop" })
    }

    @Test
    fun protectedSellerTokens_areNeverCorrected() {
        val tokens = listOf(
            "ORD-12345", "SKU_99", "https://shop.example/p/ao", "seller@example.com",
            "+84901234567", "125.000₫", "#khuyenmai", "@customer"
        )
        tokens.forEach { token ->
            val suggestions = engine.suggest(CandidateQuery(currentToken = token))
            assertEquals(CandidateKind.PROTECTED_TOKEN, suggestions.single().kind)
            assertEquals(token, suggestions.single().text)
            assertNull(engine.autoCorrection(CandidateQuery(currentToken = token)))
        }
    }

    @Test
    fun transpositionCorrection_meetsConfidenceThreshold() {
        val decision = engine.autoCorrection(CandidateQuery(currentToken = "shipipng"))
        assertEquals("shipping", decision?.replacement)
        assertTrue((decision?.confidence ?: 0.0) >= 0.70)
    }

    @Test
    fun validWordsOutsideSmallSellerLexicon_areNotAggressivelyCorrected() {
        val engineWithNextWords = LocalCandidateEngine(
            dictionary = dictionary,
            nextWords = mapOf("thank" to listOf("you")),
            correctionThreshold = 0.70
        )

        assertNull(engineWithNextWords.autoCorrection(CandidateQuery(currentToken = "thank")))
        assertNull(engineWithNextWords.autoCorrection(CandidateQuery(currentToken = "shopping")))
    }

    @Test
    fun sensitiveQuery_returnsNoCandidatesOrCorrection() {
        val query = CandidateQuery(currentToken = "shipipng", isSensitive = true)
        assertTrue(engine.suggest(query).isEmpty())
        assertNull(engine.autoCorrection(query))
    }

    @Test
    fun previousToken_producesDeterministicNextWord() {
        val suggestions = engine.suggest(CandidateQuery(currentToken = "", previousToken = "cảm"))
        assertEquals("ơn", suggestions.single().text)
        assertEquals(CandidateKind.NEXT_WORD, suggestions.single().kind)
    }
}
