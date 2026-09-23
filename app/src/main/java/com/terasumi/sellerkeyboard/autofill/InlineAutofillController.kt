package com.terasumi.sellerkeyboard.autofill

import com.terasumi.sellerkeyboard.ime.EditorInfoPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class InlineAutofillSuggestion<T : Any>(
    val id: Long,
    val content: T
)

data class InlineAutofillState<T : Any>(
    val suggestions: List<InlineAutofillSuggestion<T>> = emptyList()
) {
    val hasSuggestions: Boolean
        get() = suggestions.isNotEmpty()
}

class InlineAutofillController<T : Any>(
    private val enabledProvider: () -> Boolean,
    private val editorInfoPolicyProvider: () -> EditorInfoPolicy,
    private val maxSuggestionCount: Int = InlineAutofillRequestConfig.MAX_SUGGESTION_COUNT
) {
    private val _state = MutableStateFlow(InlineAutofillState<T>())
    val state: StateFlow<InlineAutofillState<T>> = _state.asStateFlow()

    private var activeGeneration = 0L
    private var nextSuggestionId = 0L

    init {
        require(maxSuggestionCount > 0) { "maxSuggestionCount must be positive" }
    }

    fun shouldCreateRequest(): Boolean {
        return enabledProvider() && editorInfoPolicyProvider().canUseInlineAutofill
    }

    fun beginResponse(): Long {
        activeGeneration += 1
        _state.value = InlineAutofillState()
        return activeGeneration
    }

    fun appendInflatedSuggestion(generation: Long, content: T): Boolean {
        if (generation != activeGeneration) {
            return false
        }

        val currentSuggestions = _state.value.suggestions
        if (currentSuggestions.size >= maxSuggestionCount) {
            return false
        }

        _state.value = InlineAutofillState(
            suggestions = currentSuggestions + InlineAutofillSuggestion(
                id = nextSuggestionId++,
                content = content
            )
        )
        return true
    }

    fun clear() {
        activeGeneration += 1
        _state.value = InlineAutofillState()
    }
}
