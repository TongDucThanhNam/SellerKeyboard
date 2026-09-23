package com.terasumi.sellerkeyboard.suggestion

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.terasumi.sellerkeyboard.performance.PerformanceTracer
import com.terasumi.sellerkeyboard.performance.trace

data class CandidateState(
    val sessionId: Long = 0,
    val candidates: List<TextCandidate> = emptyList(),
    val autoCorrection: AutoCorrectionDecision? = null,
    val displayTraceCookie: Int? = null
)

class CandidateController(
    private val scope: CoroutineScope,
    private val provider: CandidateProvider,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val performanceTracer: PerformanceTracer = PerformanceTracer.None
) {
    private val _state = MutableStateFlow(CandidateState())
    val state: StateFlow<CandidateState> = _state.asStateFlow()
    private var generation = 0L
    private var queryJob: Job? = null
    @Volatile
    private var pendingDisplayTraceCookie: Int? = null

    fun request(sessionId: Long, query: CandidateQuery) {
        generation += 1
        val requestGeneration = generation
        queryJob?.cancel()
        endPendingDisplayTrace()
        val displayTraceCookie = requestGeneration.toInt()
        performanceTracer.beginAsync(CANDIDATE_DISPLAY_TRACE, displayTraceCookie)
        pendingDisplayTraceCookie = displayTraceCookie
        queryJob = scope.launch(workerDispatcher) {
            val (candidates, correction) = performanceTracer.trace("SellerKeyboard.candidateQuery") {
                provider.suggest(query) to provider.autoCorrection(query)
            }
            if (requestGeneration == generation) {
                if (candidates.isEmpty()) {
                    endPendingDisplayTrace(displayTraceCookie)
                }
                _state.value = CandidateState(
                    sessionId = sessionId,
                    candidates = candidates,
                    autoCorrection = correction,
                    displayTraceCookie = displayTraceCookie.takeIf { candidates.isNotEmpty() }
                )
            }
        }
    }

    fun markCandidatesDisplayed(cookie: Int) {
        endPendingDisplayTrace(cookie)
    }

    fun clear() {
        generation += 1
        queryJob?.cancel()
        queryJob = null
        endPendingDisplayTrace()
        _state.value = CandidateState()
    }

    private fun endPendingDisplayTrace(expectedCookie: Int? = null) {
        val cookie = pendingDisplayTraceCookie ?: return
        if (expectedCookie != null && cookie != expectedCookie) return
        pendingDisplayTraceCookie = null
        performanceTracer.endAsync(CANDIDATE_DISPLAY_TRACE, cookie)
    }

    private companion object {
        const val CANDIDATE_DISPLAY_TRACE = "SellerKeyboard.candidateDisplay"
    }
}
