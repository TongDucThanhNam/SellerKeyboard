package com.terasumi.sellerkeyboard.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardClassifierTest {
    private val classifier = ClipboardClassifier()
    private val contentPolicy = ClipboardContentPolicy()

    @Test
    fun classify_detectsSellerClipboardTypes() {
        assertEquals(ClipboardItemType.PhoneNumber, classifier.classify("0912345678"))
        assertEquals(ClipboardItemType.Url, classifier.classify("https://shopee.vn/product/123"))
        assertEquals(ClipboardItemType.Price, classifier.classify("250.000đ"))
        assertEquals(ClipboardItemType.TrackingCode, classifier.classify("SPXVN12345678"))
        assertEquals(ClipboardItemType.PlainText, classifier.classify("hello world"))
    }

    @Test
    fun contentPolicy_rejectsObviouslySensitiveText() {
        assertFalse(contentPolicy.canStore("Your OTP is 123456"))
        assertFalse(contentPolicy.canStore("password: seller123"))
        assertFalse(contentPolicy.canStore("4111 1111 1111 1111"))
        assertTrue(contentPolicy.canStore("0912345678"))
        assertTrue(contentPolicy.canStore("SPXVN12345678"))
    }

    @Test
    fun contentPolicy_normalizesBlankAndLongText() {
        assertEquals("hello", contentPolicy.normalizeForStorage("  hello  "))
        assertEquals(null, contentPolicy.normalizeForStorage("   "))
        assertEquals(1_000, contentPolicy.normalizeForStorage("x".repeat(1_100))?.length)
    }
}
