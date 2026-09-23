package com.terasumi.sellerkeyboard.voice

import com.terasumi.sellerkeyboard.ime.EditorInfoPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTypingControllerTest {
    private val textInputType = 1
    private val passwordInputType = 1 or 128

    @Test
    fun start_withoutPermission_entersRequestingPermissionState() {
        val fixture = controllerFixture(hasPermission = false)

        fixture.controller.start()

        assertEquals(VoiceTypingState.RequestingPermission, fixture.controller.state.value)
        assertEquals(1, fixture.permissionRequestCount())
        assertFalse(fixture.repository.started)
    }

    @Test
    fun start_withoutRecognitionService_entersSafeErrorState() {
        val fixture = controllerFixture(recognitionAvailable = false)

        fixture.controller.start()

        assertEquals(VoiceTypingState.Error(VoiceTypingError.NoRecognitionService), fixture.controller.state.value)
        assertFalse(fixture.repository.started)
    }

    @Test
    fun start_whenVoiceTypingSettingDisabled_entersDisabledErrorWithoutStartingRecognition() {
        val fixture = controllerFixture(voiceEnabled = false)

        fixture.controller.start()

        assertEquals(VoiceTypingState.Error(VoiceTypingError.Disabled), fixture.controller.state.value)
        assertFalse(fixture.repository.started)
    }

    @Test
    fun start_whenAllowed_entersListeningAndPrefersOnDeviceRecognition() {
        val fixture = controllerFixture(onDeviceAvailable = true)

        fixture.controller.start()

        assertEquals(VoiceTypingState.Listening(usingOnDevice = true), fixture.controller.state.value)
        assertTrue(fixture.repository.started)
        assertEquals("vi-VN", fixture.repository.lastLanguageTag)
        assertTrue(fixture.repository.lastPreferOnDevice)
    }

    @Test
    fun start_inSensitiveField_doesNotStartRecognition() {
        val fixture = controllerFixture(inputType = passwordInputType)

        fixture.controller.start()

        assertEquals(VoiceTypingState.Error(VoiceTypingError.DisabledForField), fixture.controller.state.value)
        assertFalse(fixture.repository.started)
    }

    @Test
    fun finalResult_waitsForExplicitReviewWithoutEditingTheTarget() {
        val fixture = controllerFixture()

        fixture.controller.start()
        fixture.repository.callback?.onFinalResult("xin chào khách hàng")

        assertEquals(
            VoiceTypingState.Result("xin chào khách hàng", usingOnDevice = false),
            fixture.controller.state.value
        )
    }

    @Test
    fun blankFinalResult_entersNoMatchAndDoesNotCommitText() {
        val fixture = controllerFixture()

        fixture.controller.start()
        fixture.repository.callback?.onFinalResult("   ")

        assertEquals(VoiceTypingState.Error(VoiceTypingError.NoMatch), fixture.controller.state.value)
    }

    @Test
    fun endOfSpeech_entersProcessingState() {
        val fixture = controllerFixture()

        fixture.controller.start()
        fixture.repository.callback?.onEndOfSpeech()

        assertEquals(VoiceTypingState.Processing(usingOnDevice = false), fixture.controller.state.value)
    }

    @Test
    fun reviewedResult_canBeDiscardedWithoutInsertion() {
        val fixture = controllerFixture(onDeviceAvailable = true)

        fixture.controller.start()
        fixture.repository.callback?.onFinalResult("review me")
        assertEquals(
            VoiceTypingState.Result("review me", usingOnDevice = true),
            fixture.controller.state.value
        )

        fixture.controller.discardResult()
        assertEquals(VoiceTypingState.Idle, fixture.controller.state.value)
    }

    @Test
    fun permissionErrorCallback_entersSafeErrorState() {
        val fixture = controllerFixture()

        fixture.controller.start()
        fixture.repository.callback?.onError(VoiceTypingError.PermissionDenied)

        assertEquals(VoiceTypingState.Error(VoiceTypingError.PermissionDenied), fixture.controller.state.value)
    }

    @Test
    fun cancelAndReset_cancelRecognizerAndReturnIdle() {
        val fixture = controllerFixture()

        fixture.controller.start()
        fixture.controller.cancel()

        assertTrue(fixture.repository.cancelled)
        assertEquals(VoiceTypingState.Idle, fixture.controller.state.value)
    }

    @Test
    fun lateCallbacks_afterCancelCannotRepopulateVoiceState() {
        val fixture = controllerFixture()

        fixture.controller.start()
        val staleCallback = fixture.repository.callback
        fixture.controller.cancel()
        staleCallback?.onPartialResult("stale partial")
        staleCallback?.onFinalResult("stale final")
        staleCallback?.onError(VoiceTypingError.Network)

        assertEquals(VoiceTypingState.Idle, fixture.controller.state.value)
    }

    @Test
    fun callbackIsRejectedImmediatelyWhenCurrentFieldBecomesSensitive() {
        val fixture = controllerFixture()

        fixture.controller.start()
        val activeCallback = fixture.repository.callback
        fixture.setInputType(passwordInputType)
        activeCallback?.onFinalResult("must not survive")

        assertEquals(
            VoiceTypingState.Listening(usingOnDevice = false),
            fixture.controller.state.value
        )
    }

    @Test
    fun destroy_releasesRecognizerAndReturnsIdle() {
        val fixture = controllerFixture()

        fixture.controller.start()
        fixture.controller.destroy()

        assertTrue(fixture.repository.destroyed)
        assertEquals(VoiceTypingState.Idle, fixture.controller.state.value)
    }

    private fun controllerFixture(
        hasPermission: Boolean = true,
        recognitionAvailable: Boolean = true,
        onDeviceAvailable: Boolean = false,
        inputType: Int = textInputType,
        voiceEnabled: Boolean = true
    ): ControllerFixture {
        val repository = FakeSpeechRecognizerRepository(
            recognitionAvailable = recognitionAvailable,
            onDeviceRecognitionAvailable = onDeviceAvailable
        )
        var permissionRequestCount = 0
        var currentInputType = inputType
        val controller = VoiceTypingController(
            speechRecognizerRepository = repository,
            hasRecordAudioPermission = { hasPermission },
            requestRecordAudioPermission = { permissionRequestCount++ },
            editorInfoPolicyProvider = { EditorInfoPolicy.fromInputType(currentInputType) },
            voiceTypingEnabled = { voiceEnabled },
            languageTagProvider = { "vi-VN" }
        )
        return ControllerFixture(
            controller = controller,
            repository = repository,
            permissionRequestCount = { permissionRequestCount },
            setInputType = { currentInputType = it }
        )
    }

    private data class ControllerFixture(
        val controller: VoiceTypingController,
        val repository: FakeSpeechRecognizerRepository,
        val permissionRequestCount: () -> Int,
        val setInputType: (Int) -> Unit
    )

    private class FakeSpeechRecognizerRepository(
        private val recognitionAvailable: Boolean,
        override val onDeviceRecognitionAvailable: Boolean
    ) : SpeechRecognizerRepository {
        var started = false
        var cancelled = false
        var destroyed = false
        var lastLanguageTag: String? = null
        var lastPreferOnDevice = false
        var callback: SpeechRecognitionCallback? = null

        override fun isRecognitionAvailable(): Boolean = recognitionAvailable

        override fun startListening(
            languageTag: String,
            preferOnDevice: Boolean,
            callback: SpeechRecognitionCallback
        ): Boolean {
            started = true
            lastLanguageTag = languageTag
            lastPreferOnDevice = preferOnDevice
            this.callback = callback
            return recognitionAvailable
        }

        override fun cancel() {
            cancelled = true
        }

        override fun destroy() {
            destroyed = true
        }
    }

}
