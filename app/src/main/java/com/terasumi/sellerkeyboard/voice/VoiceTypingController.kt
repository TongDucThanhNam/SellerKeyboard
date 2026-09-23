package com.terasumi.sellerkeyboard.voice

import com.terasumi.sellerkeyboard.ime.EditorInfoPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoiceTypingController(
    private val speechRecognizerRepository: SpeechRecognizerRepository,
    private val hasRecordAudioPermission: () -> Boolean,
    private val requestRecordAudioPermission: () -> Unit = {},
    private val editorInfoPolicyProvider: () -> EditorInfoPolicy,
    private val voiceTypingEnabled: () -> Boolean,
    private val languageTagProvider: () -> String
) {
    private val mutableState = MutableStateFlow<VoiceTypingState>(VoiceTypingState.Idle)
    val state: StateFlow<VoiceTypingState> = mutableState.asStateFlow()
    private var usingOnDevice = false
    private var generation = 0L

    fun start() {
        generation += 1
        val requestGeneration = generation
        if (!voiceTypingEnabled()) {
            mutableState.value = VoiceTypingState.Error(VoiceTypingError.Disabled)
            return
        }

        if (!editorInfoPolicyProvider().canUseVoiceTyping) {
            mutableState.value = VoiceTypingState.Error(VoiceTypingError.DisabledForField)
            return
        }

        if (!hasRecordAudioPermission()) {
            mutableState.value = VoiceTypingState.RequestingPermission
            requestRecordAudioPermission()
            return
        }

        if (!speechRecognizerRepository.isRecognitionAvailable()) {
            mutableState.value = VoiceTypingState.Error(VoiceTypingError.NoRecognitionService)
            return
        }

        usingOnDevice = speechRecognizerRepository.onDeviceRecognitionAvailable
        mutableState.value = VoiceTypingState.Listening(usingOnDevice = usingOnDevice)
        val started = speechRecognizerRepository.startListening(
            languageTag = languageTagProvider(),
            preferOnDevice = true,
            callback = recognitionCallback(requestGeneration)
        )
        if (!started) {
            generation += 1
            mutableState.value = VoiceTypingState.Error(VoiceTypingError.NoRecognitionService)
        }
    }

    fun cancel() {
        generation += 1
        speechRecognizerRepository.cancel()
        mutableState.value = VoiceTypingState.Idle
    }

    fun discardResult() {
        generation += 1
        mutableState.value = VoiceTypingState.Idle
    }

    fun resetForFieldChange() {
        cancel()
    }

    fun destroy() {
        generation += 1
        speechRecognizerRepository.destroy()
        mutableState.value = VoiceTypingState.Idle
    }

    private fun recognitionCallback(requestGeneration: Long) = object : SpeechRecognitionCallback {
        private fun isCurrentAndAllowed(): Boolean =
            requestGeneration == generation &&
                voiceTypingEnabled() &&
                editorInfoPolicyProvider().canUseVoiceTyping

        override fun onReady(usingOnDevice: Boolean) {
            if (!isCurrentAndAllowed()) return
            this@VoiceTypingController.usingOnDevice = usingOnDevice
            mutableState.value = VoiceTypingState.Listening(usingOnDevice = usingOnDevice)
        }

        override fun onPartialResult(transcript: String) {
            if (!isCurrentAndAllowed()) return
            mutableState.value = VoiceTypingState.Listening(
                partialTranscript = transcript.takeIf { it.isNotBlank() },
                usingOnDevice = speechRecognizerRepository.onDeviceRecognitionAvailable
            )
        }

        override fun onEndOfSpeech() {
            if (!isCurrentAndAllowed()) return
            mutableState.value = VoiceTypingState.Processing(usingOnDevice)
        }

        override fun onFinalResult(transcript: String) {
            if (!isCurrentAndAllowed()) return
            val normalized = transcript.trim()
            if (normalized.isNotEmpty()) {
                mutableState.value = VoiceTypingState.Result(normalized, usingOnDevice)
            } else {
                mutableState.value = VoiceTypingState.Error(VoiceTypingError.NoMatch)
            }
        }

        override fun onError(error: VoiceTypingError) {
            if (!isCurrentAndAllowed()) return
            mutableState.value = VoiceTypingState.Error(error)
        }
    }
}
