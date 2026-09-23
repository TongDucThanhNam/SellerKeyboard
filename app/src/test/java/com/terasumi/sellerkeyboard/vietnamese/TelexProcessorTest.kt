package com.terasumi.sellerkeyboard.vietnamese

import android.view.KeyEvent
import com.terasumi.sellerkeyboard.ime.ImeConnection
import com.terasumi.sellerkeyboard.ime.InputConnectionOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelexProcessorTest {
    @Test
    fun inputLetter_updatesComposingTextForRawSyllable() {
        val fake = FakeImeConnection()
        val processor = TelexProcessor(InputConnectionOps { fake })

        "ddawng".forEach { char ->
            assertTrue(processor.inputLetter(char.toString()))
        }

        assertEquals("ddawng", processor.rawSyllable)
        assertEquals("đăng", processor.composingText)
        assertEquals(
            listOf("compose:d", "compose:đ", "compose:đa", "compose:đă", "compose:đăn", "compose:đăng"),
            fake.calls
        )
    }

    @Test
    fun backspace_updatesComposingTextUntilRawSyllableIsEmpty() {
        val fake = FakeImeConnection()
        val processor = TelexProcessor(InputConnectionOps { fake })
        "aws".forEach { char -> processor.inputLetter(char.toString()) }

        assertTrue(processor.backspace())
        assertEquals("aw", processor.rawSyllable)
        assertEquals("ă", processor.composingText)

        assertTrue(processor.backspace())
        assertEquals("a", processor.rawSyllable)
        assertEquals("a", processor.composingText)

        assertTrue(processor.backspace())
        assertEquals("", processor.rawSyllable)
        assertEquals("", processor.composingText)
        assertEquals("clearComposing", fake.calls.last())
    }

    @Test
    fun commitComposing_finishesAndClearsProcessorState() {
        val fake = FakeImeConnection()
        val processor = TelexProcessor(InputConnectionOps { fake })
        "tieesng".forEach { char -> processor.inputLetter(char.toString()) }

        assertTrue(processor.commitComposing())

        assertEquals("", processor.rawSyllable)
        assertEquals("", processor.composingText)
        assertEquals("finishComposing", fake.calls.last())
    }

    @Test
    fun invalidLetterInput_isNotHandledByProcessor() {
        val fake = FakeImeConnection()
        val processor = TelexProcessor(InputConnectionOps { fake })

        assertFalse(processor.inputLetter("."))
        assertFalse(processor.inputLetter("ab"))
        assertFalse(processor.backspace())
        assertFalse(processor.commitComposing())
        assertTrue(fake.calls.isEmpty())
    }

    private class FakeImeConnection : ImeConnection {
        val calls = mutableListOf<String>()

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            calls += "commit:$text"
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            calls += "delete:$beforeLength:$afterLength"
            return true
        }

        override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
            calls += if (text.isEmpty()) "clearComposing" else "compose:$text"
            return true
        }

        override fun finishComposingText(): Boolean {
            calls += "finishComposing"
            return true
        }

        override fun sendKeyEvent(action: Int, keyCode: Int): Boolean {
            calls += "key:$action:$keyCode"
            return action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_UP
        }

        override fun beginBatchEdit(): Boolean {
            calls += "beginBatch"
            return true
        }

        override fun endBatchEdit(): Boolean {
            calls += "endBatch"
            return true
        }
    }
}
