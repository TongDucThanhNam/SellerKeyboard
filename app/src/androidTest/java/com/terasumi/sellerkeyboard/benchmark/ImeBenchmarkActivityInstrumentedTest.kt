package com.terasumi.sellerkeyboard.benchmark

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ImeBenchmarkActivityInstrumentedTest {
    @Test
    fun intentExtras_configureFieldContractBeforeImeStarts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val intent = Intent(context, ImeBenchmarkActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(ImeBenchmarkActivity.EXTRA_INPUT_TYPE, inputType)
            putExtra(ImeBenchmarkActivity.EXTRA_IME_OPTIONS, EditorInfo.IME_ACTION_NEXT)
        }

        ActivityScenario.launch<ImeBenchmarkActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(inputType, activity.editor.inputType)
                assertEquals(EditorInfo.IME_ACTION_NEXT, activity.editor.imeOptions)
            }

            val replacementType = InputType.TYPE_CLASS_PHONE
            context.startActivity(
                Intent(context, ImeBenchmarkActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(ImeBenchmarkActivity.EXTRA_INPUT_TYPE, replacementType)
                    putExtra(
                        ImeBenchmarkActivity.EXTRA_IME_OPTIONS,
                        EditorInfo.IME_ACTION_DONE
                    )
                }
            )
            awaitEditorContract(
                scenario,
                EditorContract(
                    name = "phone replacement",
                    inputType = replacementType,
                    imeOptions = EditorInfo.IME_ACTION_DONE
                )
            )

            requiredEditorContracts().forEach { contract ->
                context.startActivity(
                    Intent(context, ImeBenchmarkActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra(ImeBenchmarkActivity.EXTRA_INPUT_TYPE, contract.inputType)
                        putExtra(ImeBenchmarkActivity.EXTRA_IME_OPTIONS, contract.imeOptions)
                        putExtra(ImeBenchmarkActivity.EXTRA_HINT_TEXT, contract.hint)
                        putExtra(
                            ImeBenchmarkActivity.EXTRA_PRIVATE_IME_OPTIONS,
                            contract.privateImeOptions
                        )
                    }
                )
                awaitEditorContract(scenario, contract)
            }
        }

        verifyContentEditableWebView()
    }

    private fun verifyContentEditableWebView() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, WebViewBenchmarkActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ActivityScenario.launch<WebViewBenchmarkActivity>(intent).use { scenario ->
            awaitWebViewValue(scenario, "\"editor\"") { activity, callback ->
                activity.focusEditor(callback)
            }

            var editorInfo: EditorInfo? = null
            var inputConnection: InputConnection? = null
            val connectionDeadline = SystemClock.elapsedRealtime() + WEB_VIEW_TIMEOUT_MILLIS
            do {
                scenario.onActivity { activity ->
                    val candidateInfo = EditorInfo()
                    activity.editor.onCreateInputConnection(candidateInfo)?.let { connection ->
                        editorInfo = candidateInfo
                        inputConnection = connection
                    }
                }
                if (inputConnection == null) SystemClock.sleep(EDITOR_UPDATE_POLL_MILLIS)
            } while (
                inputConnection == null &&
                SystemClock.elapsedRealtime() < connectionDeadline
            )
            assertNotNull("contenteditable WebView did not create an InputConnection", inputConnection)
            assertEquals(
                InputType.TYPE_CLASS_TEXT,
                editorInfo!!.inputType and InputType.TYPE_MASK_CLASS
            )

            var committed = false
            scenario.onActivity {
                committed = inputConnection?.commitText(WEB_VIEW_TEST_TEXT, 1) == true
            }
            assertTrue("contenteditable WebView rejected committed text", committed)
            awaitWebViewValue(scenario, "\"$WEB_VIEW_TEST_TEXT\"") { activity, callback ->
                activity.readEditorText(callback)
            }
        }
    }

    private fun awaitWebViewValue(
        scenario: ActivityScenario<WebViewBenchmarkActivity>,
        expected: String,
        request: (WebViewBenchmarkActivity, (String) -> Unit) -> Unit
    ) {
        val deadline = SystemClock.elapsedRealtime() + WEB_VIEW_TIMEOUT_MILLIS
        var actual: String? = null
        do {
            val callbackReceived = CountDownLatch(1)
            scenario.onActivity { activity ->
                request(activity) { value ->
                    actual = value
                    callbackReceived.countDown()
                }
            }
            callbackReceived.await(WEB_VIEW_CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (actual == expected) return
            SystemClock.sleep(EDITOR_UPDATE_POLL_MILLIS)
        } while (SystemClock.elapsedRealtime() < deadline)

        assertEquals("WebView JavaScript value", expected, actual)
    }

    private fun awaitEditorContract(
        scenario: ActivityScenario<ImeBenchmarkActivity>,
        expected: EditorContract
    ) {
        val deadline = SystemClock.elapsedRealtime() + EDITOR_UPDATE_TIMEOUT_MILLIS
        var actual: EditorContract
        do {
            lateinit var snapshot: EditorContract
            scenario.onActivity { activity ->
                snapshot = EditorContract(
                    name = expected.name,
                    inputType = activity.editor.inputType,
                    imeOptions = activity.editor.imeOptions,
                    hint = activity.editor.hint?.toString(),
                    privateImeOptions = activity.editor.privateImeOptions
                )
            }
            actual = snapshot
            if (actual == expected) return
            SystemClock.sleep(EDITOR_UPDATE_POLL_MILLIS)
        } while (SystemClock.elapsedRealtime() < deadline)

        assertEquals(expected.name, expected.inputType, actual.inputType)
        assertEquals(expected.name, expected.imeOptions, actual.imeOptions)
        assertEquals(expected.name, expected.hint, actual.hint)
        assertEquals(expected.name, expected.privateImeOptions, actual.privateImeOptions)
    }

    private fun requiredEditorContracts() = listOf(
        EditorContract("plain", InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEND),
        EditorContract(
            "multiline",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            EditorInfo.IME_ACTION_NONE
        ),
        EditorContract("search", InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEARCH),
        EditorContract(
            "email",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            EditorInfo.IME_ACTION_NEXT
        ),
        EditorContract(
            "uri",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            EditorInfo.IME_ACTION_GO
        ),
        EditorContract(
            "password",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            EditorInfo.IME_ACTION_DONE
        ),
        EditorContract(
            name = "otp-authentication",
            inputType = InputType.TYPE_CLASS_NUMBER,
            imeOptions = EditorInfo.IME_ACTION_DONE,
            hint = "One-time OTP code",
            privateImeOptions = "sellerkeyboard.authentication"
        ),
        EditorContract("phone", InputType.TYPE_CLASS_PHONE, EditorInfo.IME_ACTION_DONE),
        EditorContract("integer", InputType.TYPE_CLASS_NUMBER, EditorInfo.IME_ACTION_DONE),
        EditorContract(
            "signed-decimal",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or
                InputType.TYPE_NUMBER_FLAG_DECIMAL,
            EditorInfo.IME_ACTION_DONE
        ),
        EditorContract(
            "date-time",
            InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_DATE,
            EditorInfo.IME_ACTION_NEXT
        ),
        EditorContract("type-null", InputType.TYPE_NULL, EditorInfo.IME_ACTION_NONE)
    )

    private data class EditorContract(
        val name: String,
        val inputType: Int,
        val imeOptions: Int,
        val hint: String? = "IME benchmark editor",
        val privateImeOptions: String? = null
    )

    private companion object {
        const val EDITOR_UPDATE_TIMEOUT_MILLIS = 2_000L
        const val EDITOR_UPDATE_POLL_MILLIS = 20L
        const val WEB_VIEW_TIMEOUT_MILLIS = 10_000L
        const val WEB_VIEW_CALLBACK_TIMEOUT_SECONDS = 1L
        const val WEB_VIEW_TEST_TEXT = "seller web"
    }
}
