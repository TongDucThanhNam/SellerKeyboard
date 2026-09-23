package com.terasumi.sellerkeyboard.translation

enum class TranslationDirection { VI_TO_EN, EN_TO_VI }

sealed interface TranslationState {
    data object Idle : TranslationState
    data class DownloadingModel(val direction: TranslationDirection) : TranslationState
    data class Translating(val direction: TranslationDirection) : TranslationState
    data class DeletingModel(val direction: TranslationDirection) : TranslationState
    data class ModelDeleted(val direction: TranslationDirection) : TranslationState
    data class Preview(
        val original: String,
        val translated: String,
        val direction: TranslationDirection
    ) : TranslationState
    data class Error(val message: String) : TranslationState
}

object TranslationPolicy {
    fun canTranslate(text: String, isSensitive: Boolean): Boolean =
        text.isNotBlank() && text.length <= MAX_TEXT_LENGTH && !isSensitive

    const val MAX_TEXT_LENGTH = 2_000

    fun supportsPrivateProviderLogging(apiLevel: Int): Boolean = apiLevel >= 30
}
