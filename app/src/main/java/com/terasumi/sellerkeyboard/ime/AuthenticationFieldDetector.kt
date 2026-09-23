package com.terasumi.sellerkeyboard.ime

import java.util.Locale

object AuthenticationFieldDetector {
    private val markers = listOf(
        "otp",
        "one-time",
        "one time",
        "verification code",
        "authentication code",
        "auth code",
        "2fa",
        "two-factor"
    )

    fun isAuthenticationValue(vararg editorHints: CharSequence?): Boolean {
        val normalized = editorHints
            .filterNotNull()
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        return markers.any(normalized::contains)
    }
}
