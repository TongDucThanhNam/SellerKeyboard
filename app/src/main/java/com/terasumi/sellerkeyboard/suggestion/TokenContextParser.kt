package com.terasumi.sellerkeyboard.suggestion

data class TokenContext(
    val currentToken: String,
    val previousToken: String?
)

object TokenContextParser {
    fun parse(textBeforeCursor: CharSequence?): TokenContext {
        if (textBeforeCursor.isNullOrEmpty()) return TokenContext("", null)
        val text = textBeforeCursor.toString()
        val current = text.takeLastWhile { !it.isWhitespace() }
        val previous = text
            .dropLast(current.length)
            .trimEnd()
            .takeLastWhile { !it.isWhitespace() }
            .trimTokenBoundary()
            .ifEmpty { null }
        return TokenContext(
            currentToken = current.trimTokenBoundary(keepProtectedPunctuation = true),
            previousToken = previous
        )
    }

    private fun String.trimTokenBoundary(keepProtectedPunctuation: Boolean = false): String {
        val allowedBoundary = if (keepProtectedPunctuation) "@#/+₫$€" else ""
        return trim { character ->
            !character.isLetterOrDigit() && character !in allowedBoundary
        }
    }
}
