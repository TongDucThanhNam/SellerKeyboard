package com.terasumi.sellerkeyboard.suggestion

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.terasumi.sellerkeyboard.performance.PerformanceTracer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateControllerTest {
    @Test
    fun requestBindsResultToSession_andClearInvalidatesIt() {
        val provider = LocalCandidateEngine(dictionary = mapOf("shipping" to 1))
        val controller = CandidateController(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            provider = provider,
            workerDispatcher = Dispatchers.Unconfined
        )

        controller.request(42, CandidateQuery("sh"))
        assertEquals(42, controller.state.value.sessionId)
        assertTrue(controller.state.value.candidates.any { it.text == "shipping" })

        controller.clear()
        assertEquals(CandidateState(), controller.state.value)
    }

    @Test
    fun candidateDisplayTrace_spansRequestUntilCommittedDisplay() {
        val tracer = RecordingPerformanceTracer()
        val controller = CandidateController(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            provider = LocalCandidateEngine(dictionary = mapOf("shipping" to 1)),
            workerDispatcher = Dispatchers.Unconfined,
            performanceTracer = tracer
        )

        controller.request(7, CandidateQuery("sh"))
        val cookie = requireNotNull(controller.state.value.displayTraceCookie)

        assertEquals(
            listOf(
                "async-begin:SellerKeyboard.candidateDisplay:$cookie",
                "begin:SellerKeyboard.candidateQuery",
                "end"
            ),
            tracer.events
        )

        controller.markCandidatesDisplayed(cookie)

        assertEquals(
            "async-end:SellerKeyboard.candidateDisplay:$cookie",
            tracer.events.last()
        )
    }

    @Test
    fun clear_endsPendingCandidateDisplayTrace() {
        val tracer = RecordingPerformanceTracer()
        val controller = CandidateController(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            provider = LocalCandidateEngine(dictionary = mapOf("shipping" to 1)),
            workerDispatcher = Dispatchers.Unconfined,
            performanceTracer = tracer
        )

        controller.request(9, CandidateQuery("sh"))
        val cookie = requireNotNull(controller.state.value.displayTraceCookie)
        controller.clear()

        assertEquals(
            "async-end:SellerKeyboard.candidateDisplay:$cookie",
            tracer.events.last()
        )
        assertEquals(CandidateState(), controller.state.value)
    }

    private class RecordingPerformanceTracer : PerformanceTracer {
        val events = mutableListOf<String>()

        override fun begin(section: String) {
            events += "begin:$section"
        }

        override fun end() {
            events += "end"
        }

        override fun beginAsync(section: String, cookie: Int) {
            events += "async-begin:$section:$cookie"
        }

        override fun endAsync(section: String, cookie: Int) {
            events += "async-end:$section:$cookie"
        }
    }
}
