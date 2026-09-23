package com.terasumi.sellerkeyboard.seller

import com.terasumi.sellerkeyboard.ime.ImeConnection
import com.terasumi.sellerkeyboard.ime.InputConnectionOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateInsertionUndoTrackerTest {
    @Test
    fun exactImmediateSuffix_isRemovedAtomicallyOnce() {
        val connection = RecordingConnection("Hello Minh\nOrder ORD-42")
        val tracker = TemplateInsertionUndoTracker()
        tracker.record(9L, connection.text)

        assertTrue(tracker.undoIfAvailable(9L, InputConnectionOps { connection }))
        assertFalse(tracker.undoIfAvailable(9L, InputConnectionOps { connection }))
        assertEquals(
            listOf(
                "begin",
                "delete:${connection.text.length}:0",
                "commit::1",
                "end"
            ),
            connection.calls
        )
    }

    @Test
    fun cursorMoveOrSessionChange_cannotDeleteUnrelatedText() {
        val tracker = TemplateInsertionUndoTracker()
        tracker.record(1L, "saved reply")

        assertFalse(
            tracker.undoIfAvailable(
                2L,
                InputConnectionOps { RecordingConnection("saved reply") }
            )
        )
        assertFalse(
            tracker.undoIfAvailable(
                1L,
                InputConnectionOps { RecordingConnection("different text") }
            )
        )
    }

    private class RecordingConnection(val text: String) : ImeConnection {
        val calls = mutableListOf<String>()
        override fun getTextBeforeCursor(length: Int, flags: Int): CharSequence =
            text.takeLast(length)
        override fun commitText(text: CharSequence, newCursorPosition: Int) = true.also {
            calls += "commit:$text:$newCursorPosition"
        }
        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int) = true.also {
            calls += "delete:$beforeLength:$afterLength"
        }
        override fun setComposingText(text: CharSequence, newCursorPosition: Int) = true
        override fun finishComposingText() = true
        override fun sendKeyEvent(action: Int, keyCode: Int) = true
        override fun beginBatchEdit() = true.also { calls += "begin" }
        override fun endBatchEdit() = true.also { calls += "end" }
    }
}
