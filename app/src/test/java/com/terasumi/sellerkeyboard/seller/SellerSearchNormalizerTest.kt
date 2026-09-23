package com.terasumi.sellerkeyboard.seller

import org.junit.Assert.assertTrue
import org.junit.Test

class SellerSearchNormalizerTest {
    @Test
    fun vietnameseSearch_isAccentInsensitiveAcrossFields() {
        val template = SellerReplyTemplate(
            id = "1",
            title = "Phí vận chuyển",
            body = "Miễn phí giao hàng",
            tags = listOf("đơn hàng"),
            shortcut = "/ship"
        )

        assertTrue(SellerSearchNormalizer.matches(template, "van chuyen"))
        assertTrue(SellerSearchNormalizer.matches(template, "don hang"))
        assertTrue(SellerSearchNormalizer.matches(template, "/ship"))
    }
}
