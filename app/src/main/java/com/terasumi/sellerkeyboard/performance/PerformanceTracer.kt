package com.terasumi.sellerkeyboard.performance

import androidx.tracing.Trace

interface PerformanceTracer {
    fun begin(section: String)
    fun end()
    fun beginAsync(section: String, cookie: Int)
    fun endAsync(section: String, cookie: Int)

    object None : PerformanceTracer {
        override fun begin(section: String) = Unit
        override fun end() = Unit
        override fun beginAsync(section: String, cookie: Int) = Unit
        override fun endAsync(section: String, cookie: Int) = Unit
    }
}

object AndroidPerformanceTracer : PerformanceTracer {
    override fun begin(section: String) = Trace.beginSection(section)
    override fun end() = Trace.endSection()
    override fun beginAsync(section: String, cookie: Int) =
        Trace.beginAsyncSection(section, cookie)
    override fun endAsync(section: String, cookie: Int) =
        Trace.endAsyncSection(section, cookie)
}

inline fun <T> PerformanceTracer.trace(section: String, block: () -> T): T {
    begin(section)
    return try {
        block()
    } finally {
        end()
    }
}
