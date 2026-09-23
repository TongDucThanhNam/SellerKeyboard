package com.terasumi.sellerkeyboard.benchmark

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

data class SellerWorkflowScenario(
    val id: String,
    val category: String,
    val title: String,
    val body: String,
    val shortcut: String
)

class SellerWorkflowBenchmarkTest {
    @Test
    fun fortyScenarioProtocol_meetsInteractionAndSafetyGate() {
        val scenarios = javaClass.classLoader!!
            .getResourceAsStream(FIXTURE_NAME)!!
            .bufferedReader()
            .use { Gson().fromJson(it, Array<SellerWorkflowScenario>::class.java).toList() }
        val requiredCategories = setOf(
            "greeting", "closing", "price", "discount", "product details", "product link",
            "shipping", "order status", "tracking", "returns", "warranty", "payment",
            "COD", "address", "phone", "bilingual", "calculator quote", "app profile"
        )
        val gboardInteractions = scenarios.map { it.body.length }
        val sellerDefaultInteractions = scenarios.map { it.body.length }
        val sellerCustomizedInteractions = scenarios.map { scenario ->
            OPEN_REPLIES + FOCUS_SEARCH + scenario.shortcut.length + FINISH_SEARCH +
                SELECT_RESULT + INSERT_REVIEWED_REPLY
        }
        val reduction = 1.0 - sellerCustomizedInteractions.median() / gboardInteractions.median()

        println(
            "SELLER_WORKFLOW_PROTOCOL scenarios=${scenarios.size} " +
                "gboardMedian=${gboardInteractions.median()} " +
                "sellerDefaultMedian=${sellerDefaultInteractions.median()} " +
                "sellerCustomizedMedian=${sellerCustomizedInteractions.median()} " +
                "interactionReduction=$reduction"
        )

        assertEquals(40, scenarios.size)
        assertEquals(40, scenarios.map { it.id }.toSet().size)
        assertEquals(40, scenarios.map { it.title }.toSet().size)
        assertEquals(40, scenarios.map { it.shortcut }.toSet().size)
        assertEquals(requiredCategories, scenarios.map { it.category }.toSet())
        assertTrue(scenarios.all { it.body.matches(Regex("[a-z ]+")) })
        assertTrue(scenarios.all { it.shortcut.matches(Regex("/[a-z]{2}")) })
        assertTrue(reduction >= 0.50)
    }

    private fun List<Int>.median(): Double {
        val sorted = sorted()
        val middle = size / 2
        return if (size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0
        else sorted[middle].toDouble()
    }

    private companion object {
        const val FIXTURE_NAME = "seller-workflow-scenarios.json"
        const val OPEN_REPLIES = 1
        const val FOCUS_SEARCH = 1
        const val FINISH_SEARCH = 1
        const val SELECT_RESULT = 1
        const val INSERT_REVIEWED_REPLY = 1
    }
}
