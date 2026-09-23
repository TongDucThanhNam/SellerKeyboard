package com.terasumi.sellerkeyboard.ime

enum class RichContentDecision { COMMIT_RICH_CONTENT, TEXT_FALLBACK }

object RichContentPolicy {
    fun decide(
        privacy: EditorPrivacyPolicy,
        supportedMimeTypes: Set<String>,
        requestedMimeType: String
    ): RichContentDecision {
        if (!privacy.canCommitRichContent) return RichContentDecision.TEXT_FALLBACK
        val supported = supportedMimeTypes.any { declared -> mimeMatches(declared, requestedMimeType) }
        return if (supported) RichContentDecision.COMMIT_RICH_CONTENT
        else RichContentDecision.TEXT_FALLBACK
    }

    internal fun mimeMatches(declared: String, requested: String): Boolean {
        val declaredParts = declared.lowercase().split('/')
        val requestedParts = requested.lowercase().split('/')
        if (declaredParts.size != 2 || requestedParts.size != 2) return false
        return (declaredParts[0] == "*" || declaredParts[0] == requestedParts[0]) &&
            (declaredParts[1] == "*" || declaredParts[1] == requestedParts[1])
    }
}
