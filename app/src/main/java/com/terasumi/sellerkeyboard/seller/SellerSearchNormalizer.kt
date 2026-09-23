package com.terasumi.sellerkeyboard.seller

import java.text.Normalizer
import java.util.Locale

object SellerSearchNormalizer {
    fun normalize(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun matches(template: SellerReplyTemplate, query: String): Boolean {
        val needle = normalize(query)
        if (needle.isEmpty()) return true
        return listOfNotNull(
            template.title,
            template.body,
            template.category,
            template.shortcut,
            template.tags.joinToString(" ")
        ).any { needle in normalize(it) }
    }
}
