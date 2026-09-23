package com.terasumi.sellerkeyboard.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class AndroidSpeechRecognizerRepository(
    context: Context,
    private val recognitionServiceComponent: ComponentName? = null,
    private val injectedAudioSource: ParcelFileDescriptor? = null
) : SpeechRecognizerRepository {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    override val onDeviceRecognitionAvailable: Boolean
        get() = recognitionServiceComponent == null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)

    override fun isRecognitionAvailable(): Boolean {
        recognitionServiceComponent?.let { component ->
            return runCatching {
                @Suppress("DEPRECATION")
                appContext.packageManager.getServiceInfo(component, 0)
                true
            }.getOrDefault(false)
        }
        return SpeechRecognizer.isRecognitionAvailable(appContext)
    }

    override fun startListening(
        languageTag: String,
        preferOnDevice: Boolean,
        callback: SpeechRecognitionCallback
    ): Boolean {
        if (!isRecognitionAvailable()) {
            return false
        }

        runOnMain {
            runCatching {
                destroyRecognizer()
                val useOnDevice = preferOnDevice && onDeviceRecognitionAvailable
                recognizer = when {
                    recognitionServiceComponent != null -> SpeechRecognizer.createSpeechRecognizer(
                        appContext,
                        recognitionServiceComponent
                    )
                    useOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                        SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                    else -> SpeechRecognizer.createSpeechRecognizer(appContext)
                }
                recognizer?.setRecognitionListener(AndroidRecognitionListener(callback, useOnDevice))
                recognizer?.startListening(createRecognizerIntent(languageTag, useOnDevice))
                scheduleInjectedAudioClose()
            }.onFailure {
                callback.onError(VoiceTypingError.Client)
            }
        }
        return true
    }

    override fun cancel() {
        runOnMain {
            runCatching {
                recognizer?.cancel()
            }
        }
    }

    override fun destroy() {
        runOnMain {
            destroyRecognizer()
        }
    }

    private fun destroyRecognizer() {
        recognizer?.setRecognitionListener(null)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun createRecognizerIntent(languageTag: String, useOnDevice: Boolean): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, useOnDevice)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                injectedAudioSource?.let {
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, it)
                    putExtra(
                        RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                        RecognizerIntent.EXTRA_AUDIO_SOURCE
                    )
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                    putExtra(
                        RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16_000)
                }
            }
        }
    }

    private fun scheduleInjectedAudioClose() {
        val source = injectedAudioSource ?: return
        val audioDurationMillis = source.statSize
            .takeIf { it > 0L }
            ?.let { bytes -> (bytes * 1_000L) / PCM_16_KHZ_MONO_BYTES_PER_SECOND }
            ?: DEFAULT_INJECTED_AUDIO_CLOSE_DELAY_MILLIS
        mainHandler.postDelayed(
            { runCatching { source.close() } },
            audioDurationMillis + INJECTED_AUDIO_CLOSE_GRACE_MILLIS
        )
    }

    private class AndroidRecognitionListener(
        private val callback: SpeechRecognitionCallback,
        private val usingOnDevice: Boolean
    ) : RecognitionListener {
        private val segmentedTranscripts = mutableListOf<String>()

        override fun onReadyForSpeech(params: Bundle?) {
            callback.onReady(usingOnDevice)
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            callback.onEndOfSpeech()
        }

        override fun onError(error: Int) {
            callback.onError(error.toVoiceTypingError())
        }

        override fun onResults(results: Bundle?) {
            val transcript = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull { it.isNotBlank() }
                .orEmpty()
            callback.onFinalResult(transcript)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val transcript = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull { it.isNotBlank() }
                .orEmpty()
            callback.onPartialResult(transcript)
        }

        override fun onSegmentResults(segmentResults: Bundle) {
            segmentResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull { it.isNotBlank() }
                ?.let { transcript ->
                    segmentedTranscripts += transcript
                    callback.onPartialResult(segmentedTranscripts.joinToString(" "))
                }
        }

        override fun onEndOfSegmentedSession() {
            callback.onFinalResult(segmentedTranscripts.joinToString(" "))
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private companion object {
        const val PCM_16_KHZ_MONO_BYTES_PER_SECOND = 16_000L * 2L
        const val DEFAULT_INJECTED_AUDIO_CLOSE_DELAY_MILLIS = 10_000L
        const val INJECTED_AUDIO_CLOSE_GRACE_MILLIS = 1_000L
    }
}

private fun Int.toVoiceTypingError(): VoiceTypingError {
    return when (this) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceTypingError.PermissionDenied
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceTypingError.Busy
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceTypingError.NoMatch
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER -> VoiceTypingError.Network
        SpeechRecognizer.ERROR_CLIENT -> VoiceTypingError.Client
        else -> VoiceTypingError.Unknown
    }
}
