package com.terasumi.sellerkeyboard.seller

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SellerShortcutState(
    val sessionId: Long = 0,
    val typedShortcut: String = "",
    val templates: List<SellerReplyTemplate> = emptyList()
)

object SlashShortcutPolicy {
    private val pattern = Regex("^/[a-zA-Z0-9_-]{1,32}$")
    fun shouldQuery(token: String, isSensitive: Boolean): Boolean =
        !isSensitive && pattern.matches(token)
}

class SellerShortcutController(
    private val scope: CoroutineScope,
    private val queryTemplates: (String) -> List<SellerReplyTemplate>
) {
    private val _state = MutableStateFlow(SellerShortcutState())
    val state: StateFlow<SellerShortcutState> = _state.asStateFlow()
    private var generation = 0L
    private var job: Job? = null

    fun request(sessionId: Long, token: String, isSensitive: Boolean) {
        if (!SlashShortcutPolicy.shouldQuery(token, isSensitive)) {
            clear()
            return
        }
        generation += 1
        val currentGeneration = generation
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            val matches = queryTemplates(token).filter { template ->
                template.shortcut?.startsWith(token, ignoreCase = true) == true
            }.take(3)
            if (generation == currentGeneration) {
                _state.value = SellerShortcutState(sessionId, token, matches)
            }
        }
    }

    fun clear() {
        generation += 1
        job?.cancel()
        job = null
        _state.value = SellerShortcutState()
    }
}
