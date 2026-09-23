package com.terasumi.sellerkeyboard.suggestion

import java.text.Normalizer
import java.util.Locale

class LocalCandidateEngine(
    private val dictionary: Map<String, Int> = SellerLexicon.words,
    private val nextWords: Map<String, List<String>> = SellerLexicon.nextWords,
    private val personalWords: () -> Set<String> = { emptySet() },
    private val correctionThreshold: Double = 0.72
) : CandidateProvider {
    override fun suggest(query: CandidateQuery): List<TextCandidate> {
        if (query.isSensitive || query.maxResults <= 0) return emptyList()
        val token = query.currentToken.trim()
        if (token.isEmpty()) {
            return nextWordCandidates(query.previousToken, query.maxResults)
        }
        if (ProtectedTokenClassifier.isProtected(token)) {
            return listOf(candidate(token, CandidateKind.PROTECTED_TOKEN, 1.0, token))
        }

        val normalizedToken = normalize(token)
        val results = linkedMapOf<String, TextCandidate>()
        results[token] = candidate(token, CandidateKind.TYPED, 0.70, token)

        query.rawTelexToken
            ?.takeIf { it.isNotBlank() && !it.equals(token, ignoreCase = true) }
            ?.let { results[token] = candidate(token, CandidateKind.TELEX_NORMALIZED, 0.98, token) }

        personalWords()
            .asSequence()
            .filter { normalize(it).startsWith(normalizedToken) }
            .sortedBy { normalize(it).length }
            .forEach { word ->
                results.putIfAbsent(word, candidate(word, CandidateKind.USER_WORD, 0.96, token))
            }

        dictionary.entries
            .asSequence()
            .filter { normalize(it.key).startsWith(normalizedToken) }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.length })
            .forEach { (word, frequency) ->
                val score = 0.80 + (frequency.coerceAtMost(10_000) / 100_000.0)
                results.putIfAbsent(word, candidate(word, CandidateKind.DICTIONARY, score, token))
            }

        correctionCandidates(token).forEach { correction ->
            results.putIfAbsent(correction.text, correction)
        }

        return results.values
            .sortedWith(compareByDescending<TextCandidate> { it.score }.thenBy { it.text })
            .take(query.maxResults)
    }

    override fun autoCorrection(query: CandidateQuery): AutoCorrectionDecision? {
        if (query.isSensitive || ProtectedTokenClassifier.isProtected(query.currentToken)) return null
        val original = query.currentToken
        val normalizedOriginal = normalize(original)
        val knownWords = sequence {
            yieldAll(dictionary.keys)
            yieldAll(nextWords.keys)
            yieldAll(nextWords.values.flatten())
            yieldAll(personalWords())
        }
        if (original.length < 3 || knownWords.any { normalize(it) == normalizedOriginal }) {
            return null
        }
        // The bundled seller lexicon is deliberately compact, so an arbitrary
        // one-edit substitution can easily be another valid English word
        // (for example, shopping -> shipping). Only apply the low-ambiguity
        // adjacent-transposition correction automatically; all other nearby
        // terms remain non-destructive suggestions.
        val correction = correctionCandidates(original).firstOrNull {
            isSingleAdjacentTransposition(normalizedOriginal, normalize(it.text))
        } ?: return null
        if (correction.score < correctionThreshold) return null
        return AutoCorrectionDecision(
            original = original,
            replacement = matchCase(original, correction.text),
            confidence = correction.score
        )
    }

    private fun correctionCandidates(token: String): List<TextCandidate> {
        val normalizedToken = normalize(token)
        return dictionary.entries.mapNotNull { (word, frequency) ->
            val normalizedWord = normalize(word)
            if (kotlin.math.abs(normalizedWord.length - normalizedToken.length) > 2) return@mapNotNull null
            val distance = damerauLevenshtein(normalizedToken, normalizedWord)
            val confidence = 1.0 - distance.toDouble() /
                maxOf(normalizedToken.length, normalizedWord.length).coerceAtLeast(1)
            if (distance == 0 || confidence < correctionThreshold) return@mapNotNull null
            candidate(
                text = matchCase(token, word),
                kind = CandidateKind.CORRECTION,
                score = confidence + frequency.coerceAtMost(10_000) / 1_000_000.0,
                original = token
            )
        }.sortedByDescending(TextCandidate::score)
    }

    private fun nextWordCandidates(previousToken: String?, maxResults: Int): List<TextCandidate> {
        val previous = previousToken?.let(::normalize) ?: return emptyList()
        val words = nextWords.entries.firstOrNull { normalize(it.key) == previous }?.value.orEmpty()
        return words.take(maxResults).mapIndexed { index, word ->
            TextCandidate(word, CandidateKind.NEXT_WORD, 0.9 - index * 0.05, 0)
        }
    }

    private fun candidate(
        text: String,
        kind: CandidateKind,
        score: Double,
        original: String
    ) = TextCandidate(text, kind, score, original.length)

    private fun normalize(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")

    private fun matchCase(original: String, replacement: String): String = when {
        original.all(Char::isUpperCase) -> replacement.uppercase(Locale.ROOT)
        original.firstOrNull()?.isUpperCase() == true -> replacement.replaceFirstChar(Char::uppercase)
        else -> replacement
    }

    private fun damerauLevenshtein(left: String, right: String): Int {
        val matrix = Array(left.length + 1) { IntArray(right.length + 1) }
        for (i in 0..left.length) matrix[i][0] = i
        for (j in 0..right.length) matrix[0][j] = j
        for (i in 1..left.length) {
            for (j in 1..right.length) {
                val cost = if (left[i - 1] == right[j - 1]) 0 else 1
                matrix[i][j] = minOf(
                    matrix[i - 1][j] + 1,
                    matrix[i][j - 1] + 1,
                    matrix[i - 1][j - 1] + cost
                )
                if (i > 1 && j > 1 && left[i - 1] == right[j - 2] && left[i - 2] == right[j - 1]) {
                    matrix[i][j] = minOf(matrix[i][j], matrix[i - 2][j - 2] + cost)
                }
            }
        }
        return matrix[left.length][right.length]
    }

    private fun isSingleAdjacentTransposition(left: String, right: String): Boolean {
        if (left.length != right.length) return false
        val differences = left.indices.filter { left[it] != right[it] }
        if (differences.size != 2 || differences[1] != differences[0] + 1) return false
        val first = differences[0]
        val second = differences[1]
        return left[first] == right[second] && left[second] == right[first]
    }
}
