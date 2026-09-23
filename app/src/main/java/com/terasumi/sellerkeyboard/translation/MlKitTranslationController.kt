package com.terasumi.sellerkeyboard.translation

import android.os.Build
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.TranslateRemoteModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MlKitTranslationController {
    private val _state = MutableStateFlow<TranslationState>(TranslationState.Idle)
    val state: StateFlow<TranslationState> = _state.asStateFlow()
    private var translator: Translator? = null
    private var activeLogGuard: NativeTranslationLogGuard.Scope? = null
    private var generation = 0L

    fun isAvailableOnThisDevice(): Boolean =
        TranslationPolicy.supportsPrivateProviderLogging(Build.VERSION.SDK_INT) &&
            NativeTranslationLogGuard.isSupported()

    fun translate(text: String, direction: TranslationDirection, isSensitive: Boolean) {
        if (!TranslationPolicy.canTranslate(text, isSensitive)) {
            _state.value = TranslationState.Error("Translation is unavailable for this text or field")
            return
        }
        if (!isAvailableOnThisDevice()) {
            _state.value = TranslationState.Error(
                "Translation is unavailable because private provider logging cannot be guaranteed"
            )
            return
        }
        cancelTranslator(resetState = false)
        generation += 1
        val requestGeneration = generation
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(direction.sourceLanguage())
            .setTargetLanguage(direction.targetLanguage())
            .build()
        val activeTranslator = Translation.getClient(options)
        translator = activeTranslator
        _state.value = TranslationState.DownloadingModel(direction)
        val conditions = DownloadConditions.Builder().requireWifi().build()
        activeTranslator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                if (requestGeneration != generation) return@addOnSuccessListener
                _state.value = TranslationState.Translating(direction)
                val logGuard = NativeTranslationLogGuard.begin()
                if (logGuard == null) {
                    _state.value = TranslationState.Error(
                        "Translation privacy guard is unavailable"
                    )
                    return@addOnSuccessListener
                }
                activeLogGuard = logGuard
                activeTranslator.translate(text)
                    .addOnSuccessListener { translated ->
                        releaseLogGuard(logGuard)
                        if (requestGeneration == generation) {
                            _state.value = TranslationState.Preview(text, translated, direction)
                        }
                    }
                    .addOnFailureListener {
                        releaseLogGuard(logGuard)
                        if (requestGeneration == generation) {
                            _state.value = TranslationState.Error("Translation failed")
                        }
                    }
            }
            .addOnFailureListener {
                if (requestGeneration == generation) {
                    _state.value = TranslationState.Error("Translation model download failed")
                }
            }
    }

    fun cancel() {
        generation += 1
        cancelTranslator(resetState = true)
    }

    fun clearPreview() {
        _state.value = TranslationState.Idle
    }

    fun deleteDownloadedModel(direction: TranslationDirection) {
        cancelTranslator(resetState = false)
        generation += 1
        val requestGeneration = generation
        _state.value = TranslationState.DeletingModel(direction)
        val model = TranslateRemoteModel.Builder(direction.targetLanguage()).build()
        RemoteModelManager.getInstance().deleteDownloadedModel(model)
            .addOnSuccessListener {
                if (requestGeneration == generation) {
                    _state.value = TranslationState.ModelDeleted(direction)
                }
            }
            .addOnFailureListener {
                if (requestGeneration == generation) {
                    _state.value = TranslationState.Error("Translation model deletion failed")
                }
            }
    }

    fun close() {
        cancel()
    }

    private fun cancelTranslator(resetState: Boolean) {
        activeLogGuard?.close()
        activeLogGuard = null
        translator?.close()
        translator = null
        if (resetState) _state.value = TranslationState.Idle
    }

    private fun releaseLogGuard(guard: NativeTranslationLogGuard.Scope) {
        guard.close()
        if (activeLogGuard === guard) activeLogGuard = null
    }

    private fun TranslationDirection.sourceLanguage(): String = when (this) {
        TranslationDirection.VI_TO_EN -> TranslateLanguage.VIETNAMESE
        TranslationDirection.EN_TO_VI -> TranslateLanguage.ENGLISH
    }

    private fun TranslationDirection.targetLanguage(): String = when (this) {
        TranslationDirection.VI_TO_EN -> TranslateLanguage.ENGLISH
        TranslationDirection.EN_TO_VI -> TranslateLanguage.VIETNAMESE
    }
}
