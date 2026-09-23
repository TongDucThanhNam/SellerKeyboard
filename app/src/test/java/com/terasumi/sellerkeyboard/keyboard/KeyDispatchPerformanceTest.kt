package com.terasumi.sellerkeyboard.keyboard

import com.terasumi.sellerkeyboard.ime.ImeConnection
import com.terasumi.sellerkeyboard.ime.InputConnectionOps
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyDispatchPerformanceTest {
    @Test
    fun semanticDispatch_p95StaysWithinOne60HzFrame() {
        val dispatcher = KeyboardActionDispatcher(
            inputConnectionOps = InputConnectionOps { FastConnection },
            performHaptic = {},
            nowMillis = { 1L }
        )
        var state = KeyboardRuntimeState()
        repeat(1_000) {
            state = dispatcher.dispatchRuntime(
                KeyboardAction.KeyPress("a"), state, 0L
            ).state
        }

        val durations = LongArray(10_000)
        repeat(durations.size) { index ->
            val started = System.nanoTime()
            state = dispatcher.dispatchRuntime(
                KeyboardAction.KeyPress("a"), state, 0L
            ).state
            durations[index] = System.nanoTime() - started
        }
        durations.sort()
        val p95Nanos = durations[(durations.size * 0.95).toInt()]
        println("semantic_key_dispatch_p95_ns=$p95Nanos samples=${durations.size}")
        assertTrue("p95=$p95Nanos ns exceeded 16 ms", p95Nanos < 16_000_000L)
    }

    private object FastConnection : ImeConnection {
        override fun commitText(text: CharSequence, newCursorPosition: Int) = true
        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int) = true
        override fun setComposingText(text: CharSequence, newCursorPosition: Int) = true
        override fun finishComposingText() = true
        override fun sendKeyEvent(action: Int, keyCode: Int) = true
        override fun beginBatchEdit() = true
        override fun endBatchEdit() = true
    }
}
