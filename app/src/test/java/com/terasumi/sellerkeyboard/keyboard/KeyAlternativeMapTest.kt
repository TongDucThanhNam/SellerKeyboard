package com.terasumi.sellerkeyboard.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyAlternativeMapTest {
    @Test
    fun vietnameseLetters_andPeriodExposeAlternatives() {
        assertTrue("ă" in KeyAlternativeMap.forKey("a"))
        assertTrue("đ" in KeyAlternativeMap.forKey("d"))
        assertTrue("ơ" in KeyAlternativeMap.forKey("o"))
        assertTrue("…" in KeyAlternativeMap.forKey("."))
    }

    @Test
    fun uppercaseKey_returnsUppercaseAlternatives() {
        assertEquals(listOf("Đ"), KeyAlternativeMap.forKey("D"))
    }

    @Test
    fun unsupportedKey_hasNoMisleadingAlternatives() {
        assertTrue(KeyAlternativeMap.forKey("q").isEmpty())
    }
}
