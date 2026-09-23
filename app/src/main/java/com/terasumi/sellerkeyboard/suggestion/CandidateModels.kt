package com.terasumi.sellerkeyboard.suggestion

enum class CandidateKind {
    TYPED,
    TELEX_NORMALIZED,
    DICTIONARY,
    CORRECTION,
    NEXT_WORD,
    USER_WORD,
    PROTECTED_TOKEN
}

data class CandidateQuery(
    val currentToken: String,
    val previousToken: String? = null,
    val rawTelexToken: String? = null,
    val isSensitive: Boolean = false,
    val maxResults: Int = 3
)

data class TextCandidate(
    val text: String,
    val kind: CandidateKind,
    val score: Double,
    val replaceBeforeUtf16: Int
)

data class AutoCorrectionDecision(
    val original: String,
    val replacement: String,
    val confidence: Double
)

interface CandidateProvider {
    fun suggest(query: CandidateQuery): List<TextCandidate>
    fun autoCorrection(query: CandidateQuery): AutoCorrectionDecision?
}
