package com.terasumi.sellerkeyboard.keyboard

object KeyAlternativeMap {
    private val alternatives = mapOf(
        "a" to listOf("á", "à", "ả", "ã", "ạ", "ă", "â"),
        "d" to listOf("đ"),
        "e" to listOf("é", "è", "ẻ", "ẽ", "ẹ", "ê"),
        "i" to listOf("í", "ì", "ỉ", "ĩ", "ị"),
        "o" to listOf("ó", "ò", "ỏ", "õ", "ọ", "ô", "ơ"),
        "u" to listOf("ú", "ù", "ủ", "ũ", "ụ", "ư"),
        "y" to listOf("ý", "ỳ", "ỷ", "ỹ", "ỵ"),
        "." to listOf(",", "?", "!", ":", ";", "…"),
        "-" to listOf("–", "—", "_"),
        "'" to listOf("‘", "’", "\""),
        "/" to listOf("\\", "|"),
        "$" to listOf("₫", "€", "£", "¥")
    )

    fun forKey(key: String): List<String> {
        val lower = key.lowercase()
        val values = alternatives[lower].orEmpty()
        return if (key.any(Char::isUpperCase)) {
            values.map(String::uppercase)
        } else {
            values
        }
    }
}
