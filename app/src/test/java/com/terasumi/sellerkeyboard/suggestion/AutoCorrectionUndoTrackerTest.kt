package com.terasumi.sellerkeyboard.suggestion

import com.terasumi.sellerkeyboard.ime.ImeConnection
import com.terasumi.sellerkeyboard.ime.InputConnectionOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCorrectionUndoTrackerTest {
    @Test
    fun immediateUndo_restoresExactOriginalAtomicallyOnce() {
        val fake = FakeConnection()
        val ops = InputConnectionOps { fake }
        val tracker = AutoCorrectionUndoTracker()
        tracker.record(7, original = "shipipng", corrected = "shipping", trailingText = " ")

        assertTrue(tracker.undoIfAvailable(7, ops))
        assertFalse(tracker.undoIfAvailable(7, ops))
        assertEquals(
            listOf("begin", "delete:9:0", "commit:shipipng:1", "end"),
            fake.calls
        )
    }

    @Test
    fun differentSession_cannotConsumeUndo() {
        val tracker = AutoCorrectionUndoTracker()
        tracker.record(1, "teh", "the", " ")
        assertFalse(tracker.undoIfAvailable(2, InputConnectionOps.empty()))
    }

    @Test
    fun movedCursorOrChangedSuffix_doesNotUndoUnrelatedText() {
        val fake = FakeConnection(textBeforeCursor = "unrelated ")
        val tracker = AutoCorrectionUndoTracker()
        tracker.record(7, "teh", "the", " ")

        assertFalse(tracker.undoIfAvailable(7, InputConnectionOps { fake }))
        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun deterministicTwoHundredCaseCorpus_restoresEveryExactOriginal() {
        val baseWords = listOf("shipping", "tracking", "payment", "discount", "warranty")
        var restored = 0
        repeat(200) { index ->
            val corrected = baseWords[index % baseWords.size] + index
            val original = corrected.reversed()
            val fake = FakeConnection(textBeforeCursor = "$corrected ")
            val tracker = AutoCorrectionUndoTracker()
            tracker.record(index.toLong(), original, corrected, " ")
            if (tracker.undoIfAvailable(index.toLong(), InputConnectionOps { fake })) restored++
        }

        println("AUTOCORRECTION_UNDO cases=200 restored=$restored")
        assertEquals(200, restored)
    }

    private class FakeConnection(
        private val textBeforeCursor: String = "shipping "
    ) : ImeConnection {
        val calls = mutableListOf<String>()
        override fun commitText(text: CharSequence, newCursorPosition: Int) = true.also {
            calls += "commit:$text:$newCursorPosition"
        }
        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int) = true.also {
            calls += "delete:$beforeLength:$afterLength"
        }
        override fun getTextBeforeCursor(length: Int, flags: Int): CharSequence =
            textBeforeCursor.takeLast(length)
        override fun setComposingText(text: CharSequence, newCursorPosition: Int) = true
        override fun finishComposingText() = true
        override fun sendKeyEvent(action: Int, keyCode: Int) = true
        override fun beginBatchEdit() = true.also { calls += "begin" }
        override fun endBatchEdit() = true.also { calls += "end" }
    }
}
