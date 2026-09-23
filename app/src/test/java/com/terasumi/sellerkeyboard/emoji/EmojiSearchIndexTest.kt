package com.terasumi.sellerkeyboard.emoji

import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiSearchIndexTest {
    @Test
    fun bilingualSellerTerms_findRelevantEmoji() {
        assertTrue("📦" in EmojiSearchIndex.search("đơn hàng"))
        assertTrue("🚚" in EmojiSearchIndex.search("shipping"))
        assertTrue("🙏" in EmojiSearchIndex.search("cảm ơn"))
        assertTrue(EmojiSearchIndex.search("").isEmpty())
    }
}
