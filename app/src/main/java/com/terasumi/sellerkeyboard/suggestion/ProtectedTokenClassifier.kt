package com.terasumi.sellerkeyboard.suggestion

object ProtectedTokenClassifier {
    private val email = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private val url = Regex("^(https?://|www\\.)\\S+$", RegexOption.IGNORE_CASE)
    private val phone = Regex("^\\+?[0-9][0-9 .()-]{6,}$")
    private val money = Regex(
        "^(₫|vnd|usd|\\$|€)?[0-9][0-9.,]*(₫|vnd|usd|k|m)?$",
        RegexOption.IGNORE_CASE
    )
    private val identifier = Regex(
        "^(sku|order|ord|dh|tracking|track|trk|discount|promo|code)[-_:#]?[a-z0-9-]{2,}$",
        RegexOption.IGNORE_CASE
    )
    private val mixedIdentifier = Regex("^(?=.*[a-zA-Z])(?=.*[0-9])[a-zA-Z0-9_-]{4,}$")

    fun isProtected(token: String): Boolean {
        if (token.isBlank()) return false
        return token.startsWith('#') || token.startsWith('@') ||
            email.matches(token) || url.matches(token) || phone.matches(token) ||
            money.matches(token) || identifier.matches(token) || mixedIdentifier.matches(token)
    }
}
