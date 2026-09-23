package com.terasumi.sellerkeyboard.seller

import java.math.BigDecimal
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductReplyComposerTest {
    @Test
    fun textFallback_preservesEveryAvailableCommercialField() {
        val now = 100L * 24 * 60 * 60 * 1_000
        val draft = ProductReplyComposer.compose(
            ProductReply(
                id = "p1",
                name = "Áo linen",
                price = BigDecimal("249000"),
                productUrl = "https://shop.vn/products/ao-linen",
                imageUrl = "https://cdn.shop.vn/ao.jpg",
                imageSourceAuthorized = true,
                promotion = PromotionBlock("SALE10", "Valid through Sunday", "Orders above 500.000₫"),
                updatedAtMillis = now
            ),
            now
        )!!

        assertTrue("Áo linen" in draft.textFallback)
        assertTrue("249.000₫" in draft.textFallback)
        assertTrue("https://shop.vn/products/ao-linen" in draft.textFallback)
        assertTrue("SALE10" in draft.textFallback)
        assertEquals("https://cdn.shop.vn/ao.jpg", draft.imageUrl)
        assertNull(draft.imageSourceIssue)
        assertFalse(draft.isStale)
    }

    @Test
    fun missingIdentityIsRejected_andOldDataIsMarkedStale() {
        assertNull(
            ProductReplyComposer.compose(
                ProductReply("", "", updatedAtMillis = 0),
                nowMillis = 1
            )
        )
        val stale = ProductReplyComposer.compose(
            ProductReply("p", "Product", updatedAtMillis = 0),
            nowMillis = 31L * 24 * 60 * 60 * 1_000
        )!!
        assertTrue(stale.isStale)
    }

    @Test
    fun imageWithoutAuthorizationOrHttps_isOmittedButTextFallbackSurvives() {
        val unauthorized = ProductReplyComposer.compose(
            ProductReply(
                id = "p1",
                name = "Product",
                productUrl = "https://shop.example/product",
                imageUrl = "https://cdn.example/product.jpg",
                updatedAtMillis = 1
            ),
            nowMillis = 1
        )!!
        assertNull(unauthorized.imageUrl)
        assertEquals(ProductImageSourceIssue.AUTHORIZATION_REQUIRED, unauthorized.imageSourceIssue)
        assertTrue("https://shop.example/product" in unauthorized.textFallback)

        val cleartext = ProductReplyComposer.compose(
            ProductReply(
                id = "p2",
                name = "Product",
                imageUrl = "http://cdn.example/product.jpg",
                imageSourceAuthorized = true,
                updatedAtMillis = 1
            ),
            nowMillis = 1
        )!!
        assertNull(cleartext.imageUrl)
        assertEquals(ProductImageSourceIssue.HTTPS_REQUIRED, cleartext.imageSourceIssue)
    }

    @Test
    fun legacyStoredProduct_withoutAuthorizationField_defaultsToUnauthorized() {
        val legacy = ProductReplyPersistenceCodec.decode(
            """[{"id":"legacy","name":"Legacy","imageUrl":"https://cdn.example/image.jpg"}]"""
        ).single()

        assertFalse(legacy.imageSourceAuthorized)
        assertEquals("", legacy.description)
        assertEquals(0L, legacy.updatedAtMillis)
        val draft = ProductReplyComposer.compose(legacy, nowMillis = 1)!!
        assertNull(draft.imageUrl)
        assertEquals(ProductImageSourceIssue.AUTHORIZATION_REQUIRED, draft.imageSourceIssue)
    }
}
