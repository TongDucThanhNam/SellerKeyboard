package com.terasumi.sellerkeyboard.ime

import android.text.Editable
import android.text.Selection
import android.text.SpannableStringBuilder
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImePlatformContractInstrumentedTest {
    @Test
    fun androidInputConnection_deletesCompleteEmojiGraphemeAndSelection() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var editable: SpannableStringBuilder
        lateinit var operations: InputConnectionOps

        instrumentation.runOnMainSync {
            editable = SpannableStringBuilder("order 👩🏽‍💻")
            Selection.setSelection(editable, editable.length)
            val target = View(ApplicationProvider.getApplicationContext())
            val connection = object : BaseInputConnection(target, true) {
                override fun getEditable(): Editable = editable
            }
            operations = InputConnectionOps.fromInputConnection { connection }
            operations.deleteBackward()
        }
        assertEquals("order ", editable.toString())

        instrumentation.runOnMainSync {
            editable.replace(0, editable.length, "invoice 12345")
            Selection.setSelection(editable, 8, 13)
            operations.deleteBackward()
        }
        assertEquals("invoice ", editable.toString())

        val fallbackEvents = mutableListOf<Pair<Int, Int>>()
        instrumentation.runOnMainSync {
            val target = View(ApplicationProvider.getApplicationContext())
            val incompleteConnection = object : BaseInputConnection(target, false) {
                override fun getSelectedText(flags: Int): CharSequence? = null

                override fun getTextBeforeCursor(length: Int, flags: Int): CharSequence? = null

                override fun deleteSurroundingTextInCodePoints(
                    beforeLength: Int,
                    afterLength: Int
                ): Boolean = false

                override fun sendKeyEvent(event: KeyEvent): Boolean {
                    fallbackEvents += event.action to event.keyCode
                    return true
                }
            }
            InputConnectionOps.fromInputConnection { incompleteConnection }.deleteBackward()
        }
        assertEquals(
            listOf(
                KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_DEL,
                KeyEvent.ACTION_UP to KeyEvent.KEYCODE_DEL
            ),
            fallbackEvents
        )
    }

    @Test
    fun editorSessionGenerationStress_keepsLatestIdentityAndSensitivePolicy() {
        var latest = EditorSession.Inactive
        repeat(100) { index ->
            latest = EditorSessionResolver.resolve(
                EditorSessionRequest(
                    id = index + 1L,
                    inputType = if (index == 99) {
                        android.text.InputType.TYPE_CLASS_TEXT or
                            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                    } else {
                        android.text.InputType.TYPE_CLASS_TEXT
                    },
                    imeOptions = 0,
                    packageName = "test.editor.$index"
                )
            )
        }

        assertEquals(100L, latest.id)
        assertEquals("test.editor.99", latest.packageName)
        assertEquals(true, latest.privacy.isSensitive)
    }
}
