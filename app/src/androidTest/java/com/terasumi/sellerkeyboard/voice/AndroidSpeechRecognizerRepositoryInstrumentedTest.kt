package com.terasumi.sellerkeyboard.voice

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.text.InputType
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.terasumi.sellerkeyboard.ime.EditorInfoPolicy
import com.terasumi.sellerkeyboard.voice.fixture.DeterministicCancelRecognitionService
import com.terasumi.sellerkeyboard.voice.fixture.DeterministicNetworkRecognitionService
import com.terasumi.sellerkeyboard.voice.fixture.DeterministicNoMatchRecognitionService
import com.terasumi.sellerkeyboard.voice.fixture.DeterministicRecognitionService
import com.terasumi.sellerkeyboard.voice.fixture.LegacyDeterministicRecognitionService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AndroidSpeechRecognizerRepositoryInstrumentedTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun a_explicitService_deliversPartialProcessingAndReviewableFinalResult() {
        val fixture = fixture(DeterministicRecognitionService.LANGUAGE_RESULT)
        try {
            assertFalse(fixture.repository.onDeviceRecognitionAvailable)
            fixture.controller.start()

            awaitState(fixture.controller) {
                it is VoiceTypingState.Listening &&
                    it.partialTranscript == DeterministicRecognitionService.PARTIAL_TRANSCRIPT
            }
            awaitState(fixture.controller) { it is VoiceTypingState.Processing }
            assertEquals(
                VoiceTypingState.Result(
                    transcript = DeterministicRecognitionService.FINAL_TRANSCRIPT,
                    usingOnDevice = false
                ),
                awaitState(fixture.controller) { it is VoiceTypingState.Result }
            )
        } finally {
            destroyFixture(fixture)
        }
    }

    @Test
    fun b_explicitService_mapsNetworkAndNoMatchErrors() {
        assertEquals(
            VoiceTypingState.Error(VoiceTypingError.Network),
            runErrorCase(
                DeterministicRecognitionService.LANGUAGE_NETWORK_ERROR,
                DeterministicNetworkRecognitionService::class.java
            )
        )
        assertEquals(
            VoiceTypingState.Error(VoiceTypingError.NoMatch),
            runErrorCase(
                DeterministicRecognitionService.LANGUAGE_NO_MATCH,
                DeterministicNoMatchRecognitionService::class.java
            )
        )
    }

    @Test
    fun z_cancel_preventsDelayedProviderResultFromRepopulatingState() {
        val fixture = fixture(
            DeterministicRecognitionService.LANGUAGE_RESULT,
            DeterministicCancelRecognitionService::class.java
        )
        try {
            fixture.controller.start()
            awaitState(fixture.controller) { it is VoiceTypingState.Listening }
            fixture.controller.cancel()
            SystemClock.sleep(1_100L)

            assertEquals(VoiceTypingState.Idle, fixture.controller.state.value)
        } finally {
            destroyFixture(fixture)
        }
    }

    private fun runErrorCase(
        languageTag: String,
        serviceClass: Class<out DeterministicRecognitionService>
    ): VoiceTypingState {
        val fixture = fixture(languageTag, serviceClass)
        return try {
            fixture.controller.start()
            awaitState(fixture.controller) { it is VoiceTypingState.Error }
        } finally {
            destroyFixture(fixture)
        }
    }

    private fun destroyFixture(fixture: Fixture) {
        fixture.controller.destroy()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(200L)
        fixture.audioPipe.forEach { runCatching { it.close() } }
    }

    private fun fixture(
        languageTag: String,
        serviceClass: Class<out DeterministicRecognitionService> =
            DeterministicRecognitionService::class.java
    ): Fixture {
        val audioPipe = ParcelFileDescriptor.createPipe()
        val resolvedServiceClass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            serviceClass
        } else {
            LegacyDeterministicRecognitionService::class.java
        }
        val repository = AndroidSpeechRecognizerRepository(
            context = context,
            recognitionServiceComponent = ComponentName(
                testContext.packageName,
                resolvedServiceClass.name
            ),
            injectedAudioSource = audioPipe[0]
        )
        val controller = VoiceTypingController(
            speechRecognizerRepository = repository,
            hasRecordAudioPermission = { true },
            editorInfoPolicyProvider = {
                EditorInfoPolicy.fromInputType(InputType.TYPE_CLASS_TEXT)
            },
            voiceTypingEnabled = { true },
            languageTagProvider = { languageTag }
        )
        return Fixture(controller, repository, audioPipe)
    }

    private fun awaitState(
        controller: VoiceTypingController,
        timeoutMillis: Long = 4_000L,
        predicate: (VoiceTypingState) -> Boolean
    ): VoiceTypingState {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        do {
            val state = controller.state.value
            if (predicate(state)) return state
            SystemClock.sleep(10L)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("Timed out waiting for voice state; last=${controller.state.value}")
    }

    private data class Fixture(
        val controller: VoiceTypingController,
        val repository: AndroidSpeechRecognizerRepository,
        val audioPipe: Array<ParcelFileDescriptor>
    )
}
