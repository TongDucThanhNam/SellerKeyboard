package com.terasumi.sellerkeyboard.clipboard

enum class ClipboardEntityType {
    EMAIL, URL, PHONE, ADDRESS, DATE, TIME, PRICE, ORDER_ID, TRACKING_CODE,
    DISCOUNT_CODE, PRODUCT_LINK
}

data class ParsedClipboardEntity(
    val type: ClipboardEntityType,
    val value: String,
    val start: Int,
    val endExclusive: Int
)

class ClipboardEntityParser {
    fun parse(source: String): List<ParsedClipboardEntity> {
        val results = mutableListOf<ParsedClipboardEntity>()
        patterns.forEach { (type, regex) ->
            regex.findAll(source).forEach { match ->
                results += ParsedClipboardEntity(type, match.value, match.range.first, match.range.last + 1)
            }
        }
        return results
            .distinctBy { Triple(it.type, it.start, it.endExclusive) }
            .sortedWith(compareBy(ParsedClipboardEntity::start, ParsedClipboardEntity::endExclusive))
    }

    private val patterns = listOf(
        ClipboardEntityType.EMAIL to Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE),
        ClipboardEntityType.PRODUCT_LINK to Regex("https?://\\S+/(?:products?|p|item)/\\S+", RegexOption.IGNORE_CASE),
        ClipboardEntityType.URL to Regex("https?://\\S+", RegexOption.IGNORE_CASE),
        ClipboardEntityType.PHONE to Regex("(?:\\+?84|0)(?:[0-9 .()-]){8,12}[0-9]"),
        ClipboardEntityType.PRICE to Regex("(?:₫|VND\\s*)?[0-9]{1,3}(?:[.,][0-9]{3})+(?:\\s*(?:₫|đ|VND))?", RegexOption.IGNORE_CASE),
        ClipboardEntityType.DATE to Regex("\\b(?:[0-3]?\\d)[/-](?:[01]?\\d)[/-](?:\\d{2}|\\d{4})\\b"),
        ClipboardEntityType.TIME to Regex("\\b(?:[01]?\\d|2[0-3]):[0-5]\\d\\b"),
        ClipboardEntityType.ORDER_ID to Regex("\\b(?:DH|ORD(?:ER)?)[-_:]?[A-Z0-9-]{3,}\\b", RegexOption.IGNORE_CASE),
        ClipboardEntityType.TRACKING_CODE to Regex("\\b(?:VNPOST|GHN|GHTK|JNT|SPX|TRACK|TRK)[-_:]?[A-Z0-9-]{6,}\\b", RegexOption.IGNORE_CASE),
        ClipboardEntityType.DISCOUNT_CODE to Regex("\\b(?:SALE|GIAM|DISCOUNT|PROMO)[-_]?[A-Z0-9]{2,}\\b", RegexOption.IGNORE_CASE),
        ClipboardEntityType.ADDRESS to Regex("\\b\\d{1,5}\\s+[\\p{L}0-9 .'-]+(?:street|st|road|rd|đường|phường|quận)\\b", RegexOption.IGNORE_CASE)
    )
}
