package com.terasumi.sellerkeyboard.ime

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.terasumi.sellerkeyboard.benchmark.ImeBenchmarkActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImeLifecycleStressInstrumentedTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private var previousIme: String? = null

    @Before
    fun selectSellerKeyboard() {
        previousIme = device.executeShellCommand(
            "settings get secure default_input_method"
        ).trim()
        val enableResult = device.executeShellCommand("ime enable $IME_COMPONENT")
        val setResult = device.executeShellCommand("ime set $IME_COMPONENT")
        val selectedIme = device.executeShellCommand(
            "settings get secure default_input_method"
        ).trim()
        assertTrue(
            "IME enable failed: $enableResult",
            "now enabled" in enableResult || "already enabled" in enableResult
        )
        assertTrue("IME selection failed: $setResult", "selected" in setResult)
        assertTrue("Unexpected selected IME: $selectedIme", selectedIme == IME_COMPONENT)
    }

    @After
    fun restorePreviousKeyboard() {
        previousIme?.takeIf(String::isNotBlank)?.let { ime ->
            device.executeShellCommand("ime set $ime")
        }
    }

    @Test
    fun oneHundredShowHideAndEditorSwitchCycles_completeWithoutProcessFailure() {
        val fieldConfigurations = listOf(
            InputType.TYPE_CLASS_TEXT to EditorInfo.IME_ACTION_SEND,
            (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE) to
                EditorInfo.IME_ACTION_NONE,
            (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) to
                EditorInfo.IME_ACTION_NEXT,
            (InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED) to EditorInfo.IME_ACTION_DONE,
            (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD) to
                EditorInfo.IME_ACTION_DONE
        )
        ActivityScenario.launch(ImeBenchmarkActivity::class.java).use { scenario ->
            repeat(100) { cycle ->
                val (inputType, imeOptions) = fieldConfigurations[cycle % fieldConfigurations.size]
                scenario.onActivity { activity ->
                    activity.configureEditor(inputType, imeOptions)
                    val manager = activity.getSystemService(InputMethodManager::class.java)
                    manager?.showSoftInput(activity.editor, InputMethodManager.SHOW_IMPLICIT)
                    manager?.hideSoftInputFromWindow(activity.editor.windowToken, 0)
                }
                if (cycle % 10 == 0) device.waitForIdle()
            }

            scenario.recreate()
            device.waitForIdle()
            scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            awaitOrientation(scenario, Configuration.ORIENTATION_LANDSCAPE)
            scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            awaitOrientation(scenario, Configuration.ORIENTATION_PORTRAIT)

            scenario.onActivity { activity ->
                activity.configureEditor(
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                    EditorInfo.IME_ACTION_NONE
                )
                activity.getSystemService(InputMethodManager::class.java)?.showSoftInput(
                    activity.editor,
                    InputMethodManager.SHOW_IMPLICIT
                )
                assertTrue(activity.editor.isAttachedToWindow)
                assertTrue(activity.editor.hasFocus())
            }

        }
    }

    private fun awaitOrientation(
        scenario: ActivityScenario<ImeBenchmarkActivity>,
        expected: Int
    ) {
        val deadline = SystemClock.elapsedRealtime() + ORIENTATION_TIMEOUT_MILLIS
        var actual = Configuration.ORIENTATION_UNDEFINED
        do {
            scenario.onActivity { activity ->
                actual = activity.resources.configuration.orientation
            }
            if (actual == expected) return
            SystemClock.sleep(ORIENTATION_POLL_MILLIS)
        } while (SystemClock.elapsedRealtime() < deadline)

        assertEquals("Activity orientation", expected, actual)
    }

    private companion object {
        const val IME_COMPONENT =
            "com.terasumi.sellerkeyboard/.service.SellerKeyboard"
        const val ORIENTATION_TIMEOUT_MILLIS = 10_000L
        const val ORIENTATION_POLL_MILLIS = 50L
    }
}
