package com.terasumi.sellerkeyboard.clipboard

class ClipboardContentPolicy {
    fun normalizeForStorage(text: CharSequence?): String? {
        val normalized = text?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return normalized.take(MAX_STORED_TEXT_LENGTH)
    }

    fun canStore(text: String): Boolean {
        val lower = text.lowercase()
        if (sensitiveKeywords.any { keyword -> lower.contains(keyword) }) {
            return false
        }

        val digitsOnly = text.filter(Char::isDigit)
        return digitsOnly.length !in CREDIT_CARD_LENGTH_RANGE
    }

    private companion object {
        private const val MAX_STORED_TEXT_LENGTH = 1_000
        private val CREDIT_CARD_LENGTH_RANGE = 13..19
        private val sensitiveKeywords = listOf(
            "password",
            "passcode",
            "otp",
            "one-time password",
            "verification code",
            "mã otp",
            "mat khau",
            "mật khẩu"
        )
    }
}
