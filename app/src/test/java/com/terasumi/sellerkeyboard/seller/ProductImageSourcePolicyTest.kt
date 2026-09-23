package com.terasumi.sellerkeyboard.seller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductImageSourcePolicyTest {
    @Test
    fun blankSource_isAnAllowedNoImageChoice() {
        val decision = ProductImageSourcePolicy.evaluate("  ", sellerAuthorized = false)

        assertNull(decision.normalizedUrl)
        assertNull(decision.issue)
        assertFalse(decision.canShare)
    }

    @Test
    fun authorizedHttpsSource_isNormalizedAndShareable() {
        val decision = ProductImageSourcePolicy.evaluate(
            "  HTTPS://cdn.example.com/product%20image.jpg  ",
            sellerAuthorized = true
        )

        assertEquals("https://cdn.example.com/product%20image.jpg", decision.normalizedUrl)
        assertNull(decision.issue)
        assertTrue(decision.canShare)
    }

    @Test
    fun cleartextMissingHostAndCredentials_areRejected() {
        assertEquals(
            ProductImageSourceIssue.HTTPS_REQUIRED,
            ProductImageSourcePolicy.evaluate(
                "http://cdn.example.com/product.jpg",
                sellerAuthorized = true
            ).issue
        )
        assertEquals(
            ProductImageSourceIssue.HOST_REQUIRED,
            ProductImageSourcePolicy.evaluate(
                "https:///product.jpg",
                sellerAuthorized = true
            ).issue
        )
        assertEquals(
            ProductImageSourceIssue.EMBEDDED_CREDENTIALS_NOT_ALLOWED,
            ProductImageSourcePolicy.evaluate(
                "https://seller:secret@cdn.example.com/product.jpg",
                sellerAuthorized = true
            ).issue
        )
    }

    @Test
    fun validHttpsSourceStillRequiresSellerAuthorization() {
        val decision = ProductImageSourcePolicy.evaluate(
            "https://cdn.example.com/product.jpg",
            sellerAuthorized = false
        )

        assertNull(decision.normalizedUrl)
        assertEquals(ProductImageSourceIssue.AUTHORIZATION_REQUIRED, decision.issue)
        assertFalse(decision.canShare)
    }
}
