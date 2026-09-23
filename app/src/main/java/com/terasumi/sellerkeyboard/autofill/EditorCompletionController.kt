package com.terasumi.sellerkeyboard.autofill

import android.view.inputmethod.CompletionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EditorCompletionItem(
    val stableId: Long,
    val text: String,
    val label: String?,
    val completionInfo: CompletionInfo
)

data class EditorCompletionState(
    val sessionId: Long = 0,
    val items: List<EditorCompletionItem> = emptyList()
)

class EditorCompletionController(private val maxItems: Int = 5) {
    private val _state = MutableStateFlow(EditorCompletionState())
    val state: StateFlow<EditorCompletionState> = _state.asStateFlow()

    fun update(sessionId: Long, completions: Array<out CompletionInfo>?) {
        if (sessionId == 0L || completions.isNullOrEmpty()) {
            clear()
            return
        }
        _state.value = EditorCompletionState(
            sessionId = sessionId,
            items = completions
                .asSequence()
                .filter { !it.text.isNullOrEmpty() }
                .take(maxItems)
                .mapIndexed { index, completion ->
                    EditorCompletionItem(
                        stableId = completion.id.takeIf { it != 0L } ?: index.toLong(),
                        text = completion.text.toString(),
                        label = completion.label?.toString(),
                        completionInfo = completion
                    )
                }
                .toList()
        )
    }

    fun clear() {
        _state.value = EditorCompletionState()
    }
}
