package com.terasumi.sellerkeyboard.clipboard

class ClipboardClassifier {
    fun classify(text: String): ClipboardItemType {
        val value = text.trim()
        return when {
            phoneRegex.matches(value) -> ClipboardItemType.PhoneNumber
            urlRegex.matches(value) -> ClipboardItemType.Url
            priceRegex.matches(value) -> ClipboardItemType.Price
            trackingCodeRegex.matches(value) -> ClipboardItemType.TrackingCode
            else -> ClipboardItemType.PlainText
        }
    }

    private companion object {
        private val phoneRegex = Regex("""^(?:\+?84|0)(?:\d[\s.-]?){8,10}\d$""")
        private val urlRegex = Regex("""^(?:https?://)?(?:[\w-]+\.)+[a-z]{2,}(?:/.*)?$""", RegexOption.IGNORE_CASE)
        private val priceRegex = Regex("""^\d{1,3}(?:[.,]\d{3})*(?:\s?)(?:đ|₫|vnd|VND)$|^\d+(?:\s?)(?:đ|₫|vnd|VND)$""")
        private val trackingCodeRegex = Regex("""^[A-Z]{2,6}[A-Z0-9]{8,24}$""")
    }
}
