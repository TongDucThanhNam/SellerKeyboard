package com.terasumi.sellerkeyboard.seller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateRendererTest {
    @Test
    fun resolvedTemplate_preservesUnicodePunctuationAndLines() {
        val template = "Chào {{customer_name}},\nĐơn {{order_id}}: {{price}}."
        val result = TemplateRenderer.render(
            template,
            mapOf("customer_name" to "Hà", "order_id" to "DH-01", "price" to "125.000₫")
        )
        assertEquals(
            TemplateRenderResult.Ready("Chào Hà,\nĐơn DH-01: 125.000₫."),
            result
        )
    }

    @Test
    fun unresolvedOrUnknownVariables_canNeverBeCommittedAsReady() {
        val unresolved = TemplateRenderer.render("Mã {{tracking_code}}", emptyMap())
        val invalid = TemplateRenderer.render("{{customer_secret}}", mapOf("customer_secret" to "x"))

        assertEquals(
            TemplateRenderResult.Unresolved(setOf("tracking_code")),
            unresolved
        )
        assertTrue(invalid is TemplateRenderResult.Invalid)
    }

    @Test
    fun adjacentPlaceholdersAndWhitespace_useEscapedBraceBoundaries() {
        val result = TemplateRenderer.render(
            "{{ customer_name }}{{order_id}}",
            mapOf("customer_name" to "Lan", "order_id" to "DH-02")
        )

        assertEquals(TemplateRenderResult.Ready("LanDH-02"), result)
    }
}
