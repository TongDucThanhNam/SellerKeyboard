package com.terasumi.sellerkeyboard.emoji

import java.util.Locale

object EmojiSearchIndex {
    private val entries = listOf(
        Entry("😀", "smile happy vui cười"), Entry("😂", "laugh tears cười"),
        Entry("😊", "smile blush vui"), Entry("😍", "love heart yêu"),
        Entry("🥰", "love hearts yêu"), Entry("😢", "sad cry buồn khóc"),
        Entry("😭", "cry sob khóc"), Entry("😡", "angry mad giận"),
        Entry("👍", "thumb up like đồng ý tốt"), Entry("👎", "thumb down dislike không"),
        Entry("🙏", "thanks please cảm ơn vui lòng"), Entry("👏", "clap applause"),
        Entry("👋", "wave hello chào"), Entry("🤝", "handshake deal hợp tác"),
        Entry("❤️", "heart love yêu"), Entry("💛", "yellow heart"),
        Entry("🔥", "fire hot sale"), Entry("✨", "sparkles new mới"),
        Entry("🎉", "party celebration chúc mừng"), Entry("🎁", "gift quà"),
        Entry("🛍️", "shopping bags mua sắm"), Entry("🛒", "cart checkout giỏ hàng"),
        Entry("📦", "package order đơn hàng"), Entry("🚚", "truck shipping giao hàng"),
        Entry("💳", "card payment thanh toán"), Entry("💵", "cash money tiền"),
        Entry("🏷️", "tag price discount giảm giá"), Entry("✅", "check done xác nhận"),
        Entry("❌", "cross cancel hủy"), Entry("⚠️", "warning cảnh báo"),
        Entry("📍", "location address địa chỉ"), Entry("📞", "phone call điện thoại")
    )

    fun search(query: String, maxResults: Int = 24): List<String> {
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty() || maxResults <= 0) return emptyList()
        return entries.asSequence()
            .filter { normalized in it.keywords || it.emoji == normalized }
            .map(Entry::emoji)
            .distinct()
            .take(maxResults)
            .toList()
    }

    private data class Entry(val emoji: String, val keywords: String)
}
