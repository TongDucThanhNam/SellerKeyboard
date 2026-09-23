package com.terasumi.sellerkeyboard.seller

import androidx.annotation.Keep

@Keep
data class SellerReplyTemplate(
    val id: String,
    val title: String,
    val body: String,
    val category: String = "General",
    val tags: List<String> = emptyList(),
    val favorite: Boolean = false,
    val usageCount: Long = 0,
    val shortcut: String? = null,
    val mediaUrls: List<String> = emptyList(),
    val replyPackId: String? = null,
    val profileIds: List<String> = emptyList(),
    val archived: Boolean = false,
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0
)
