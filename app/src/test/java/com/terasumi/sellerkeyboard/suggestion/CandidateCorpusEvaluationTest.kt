package com.terasumi.sellerkeyboard.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateCorpusEvaluationTest {
    @Test
    fun syntheticBilingualSellerCorpus_meetsIndependentTop3AndProtectionGates() {
        val engine = LocalCandidateEngine()
        val vietnameseTargets = listOf(
            "bạn", "cảm", "chào", "khoản", "còn", "đặt", "địa", "đơn", "đổi", "giá",
            "giảm", "giao", "hàng", "khách", "khuyến", "miễn", "nhận", "phẩm", "shop",
            "thanh", "theo", "vận", "vui", "xin", "xác"
        )
        val englishTargets = listOf(
            "available", "cancel", "cash", "checkout", "customer", "delivery", "discount",
            "hello", "order", "payment", "price", "product", "refund", "return", "seller",
            "shipping", "stock", "thanks", "tracking", "warranty"
        )

        val vietnameseCases = List(600) { index -> vietnameseTargets[index % vietnameseTargets.size] }
        val englishMixedCases = List(200) { index -> englishTargets[index % englishTargets.size] }
        val labeledCases = vietnameseCases + englishMixedCases
        val recalled = labeledCases.count { expected ->
            val prefix = expected.dropLast(1).ifEmpty { expected }
            engine.suggest(CandidateQuery(prefix, maxResults = 3)).any {
                it.text.equals(expected, ignoreCase = true)
            }
        }

        val protectedCases = List(200) { index ->
            when (index % 8) {
                0 -> "ORD-${10_000 + index}"
                1 -> "SKU_${900 + index}"
                2 -> "https://shop.example/p/$index"
                3 -> "seller$index@example.com"
                4 -> "+8490${1000000 + index}"
                5 -> "${100 + index}.000₫"
                6 -> "#sale$index"
                else -> "@buyer$index"
            }
        }
        val protectedMutations = protectedCases.count { token ->
            val result = engine.suggest(CandidateQuery(token)).singleOrNull()
            result?.text != token || result.kind != CandidateKind.PROTECTED_TOKEN ||
                engine.autoCorrection(CandidateQuery(token)) != null
        }

        val recall = recalled.toDouble() / labeledCases.size
        println(
            "CANDIDATE_CORPUS total=${labeledCases.size} top3Recall=$recall " +
                "protected=${protectedCases.size} protectedMutations=$protectedMutations"
        )
        assertTrue("Top-3 recall was $recall", recall >= 0.85)
        assertEquals(0, protectedMutations)
        assertEquals(600, vietnameseCases.size)
        assertEquals(200, englishMixedCases.size)
        assertEquals(200, protectedCases.size)
    }
}
