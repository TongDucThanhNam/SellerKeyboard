package com.terasumi.sellerkeyboard.voice

sealed class VoiceTypingState {
    data object Idle : VoiceTypingState()
    data object RequestingPermission : VoiceTypingState()
    data class Listening(
        val partialTranscript: String? = null,
        val usingOnDevice: Boolean = false
    ) : VoiceTypingState()
    data class Processing(val usingOnDevice: Boolean) : VoiceTypingState()
    data class Result(
        val transcript: String,
        val usingOnDevice: Boolean
    ) : VoiceTypingState()
    data class Error(val error: VoiceTypingError) : VoiceTypingState()
}

enum class VoiceTypingError {
    Disabled,
    DisabledForField,
    PermissionDenied,
    NoRecognitionService,
    Busy,
    NoMatch,
    Network,
    Client,
    Unknown
}
