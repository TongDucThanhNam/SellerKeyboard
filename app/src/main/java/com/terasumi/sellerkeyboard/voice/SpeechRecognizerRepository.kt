package com.terasumi.sellerkeyboard.voice

interface SpeechRecognizerRepository {
    val onDeviceRecognitionAvailable: Boolean
    fun isRecognitionAvailable(): Boolean
    fun startListening(
        languageTag: String,
        preferOnDevice: Boolean,
        callback: SpeechRecognitionCallback
    ): Boolean
    fun cancel()
    fun destroy()
}

interface SpeechRecognitionCallback {
    fun onReady(usingOnDevice: Boolean)
    fun onPartialResult(transcript: String)
    fun onEndOfSpeech()
    fun onFinalResult(transcript: String)
    fun onError(error: VoiceTypingError)
}
