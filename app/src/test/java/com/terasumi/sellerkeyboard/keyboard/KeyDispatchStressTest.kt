package com.terasumi.sellerkeyboard.keyboard

import com.terasumi.sellerkeyboard.ime.ImeConnection
import com.terasumi.sellerkeyboard.ime.InputConnectionOps
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyDispatchStressTest {
    @Test
    fun tenThousandSemanticActions_preserveOrderWithoutLossOrDuplication() {
        val fake = RecordingConnection()
        val dispatcher = KeyboardActionDispatcher(
            inputConnectionOps = InputConnectionOps { fake },
            performHaptic = {}
        )
        var runtime = KeyboardRuntimeState()
        val tokens = listOf("a", "đ", "😀", "1", " ")
        val expected = StringBuilder()

        repeat(10_000) { index ->
            val token = tokens[index % tokens.size]
            val action = if (token == " ") KeyboardAction.Space else KeyboardAction.KeyPress(token)
            runtime = dispatcher.dispatchRuntime(action, runtime, 0L).state
            expected.append(token)
        }

        assertEquals(expected.toString(), fake.committed.toString())
        assertEquals(10_000, fake.commitCount)
    }

    private class RecordingConnection : ImeConnection {
        val committed = StringBuilder()
        var commitCount = 0
        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            committed.append(text)
            commitCount += 1
            return true
        }
        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int) = true
        override fun setComposingText(text: CharSequence, newCursorPosition: Int) = true
        override fun finishComposingText() = true
        override fun sendKeyEvent(action: Int, keyCode: Int) = true
        override fun beginBatchEdit() = true
        override fun endBatchEdit() = true
    }
}
