package com.terasumi.sellerkeyboard.clipboard

data class ClipboardItem(
    val id: Long = 0L,
    val text: String,
    val type: ClipboardItemType,
    val source: ClipboardSource,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val pinned: Boolean = false,
    val sensitive: Boolean = false
)

enum class ClipboardItemType {
    PhoneNumber,
    Url,
    Price,
    TrackingCode,
    PlainText
}

enum class ClipboardSource {
    SystemClipboard,
    Manual
}
