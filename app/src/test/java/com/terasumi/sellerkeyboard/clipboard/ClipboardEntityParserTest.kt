package com.terasumi.sellerkeyboard.clipboard

import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardEntityParserTest {
    @Test
    fun mixedCommercialText_preservesExactSourceFragments() {
        val source = "Đơn ORD-12345, mã GHTK-ABC123456, giá 125.000₫, ship 18:30 12/07/2026 https://shop.vn/products/ao"
        val entities = ClipboardEntityParser().parse(source)

        assertTrue(entities.any { it.type == ClipboardEntityType.ORDER_ID && it.value == "ORD-12345" })
        assertTrue(entities.any { it.type == ClipboardEntityType.TRACKING_CODE && it.value == "GHTK-ABC123456" })
        assertTrue(entities.any { it.type == ClipboardEntityType.PRICE && it.value == "125.000₫" })
        assertTrue(entities.any { it.type == ClipboardEntityType.TIME && it.value == "18:30" })
        assertTrue(entities.any { it.type == ClipboardEntityType.DATE && it.value == "12/07/2026" })
        assertTrue(entities.any { it.type == ClipboardEntityType.PRODUCT_LINK })
        entities.forEach { entity ->
            assertTrue(source.substring(entity.start, entity.endExclusive) == entity.value)
        }
    }
}
