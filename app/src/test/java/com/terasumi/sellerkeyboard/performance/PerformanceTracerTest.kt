package com.terasumi.sellerkeyboard.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PerformanceTracerTest {
    @Test
    fun trace_closesAfterSuccessfulDispatch() {
        val tracer = RecordingTracer()

        val result = tracer.trace("keyDispatch") { 42 }

        assertEquals(42, result)
        assertEquals(listOf("begin:keyDispatch", "end"), tracer.events)
    }

    @Test
    fun trace_closesAndPropagatesFailureIncludingCompositionCleanup() {
        val tracer = RecordingTracer()
        val failure = IllegalStateException("composition cleanup failed")

        val thrown = runCatching {
            tracer.trace("keyDispatch") {
                try {
                    Unit
                } finally {
                    throw failure
                }
            }
        }.exceptionOrNull()

        assertSame(failure, thrown)
        assertEquals(listOf("begin:keyDispatch", "end"), tracer.events)
    }

    @Test
    fun trace_closesOnNonLocalReturn() {
        val tracer = RecordingTracer()

        fun dispatch(): Int = tracer.trace("keyDispatch") { return 42 }

        assertEquals(42, dispatch())
        assertEquals(listOf("begin:keyDispatch", "end"), tracer.events)
    }

    private class RecordingTracer : PerformanceTracer {
        val events = mutableListOf<String>()

        override fun begin(section: String) { events += "begin:$section" }
        override fun end() { events += "end" }
        override fun beginAsync(section: String, cookie: Int) = Unit
        override fun endAsync(section: String, cookie: Int) = Unit
    }
}
