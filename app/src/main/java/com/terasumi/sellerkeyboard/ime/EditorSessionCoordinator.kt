package com.terasumi.sellerkeyboard.ime

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

class EditorSessionCoordinator {
    val state: MutableState<EditorSession> = mutableStateOf(EditorSession.Inactive)

    fun start(session: EditorSession) {
        state.value = session
    }

    fun finish() {
        state.value = EditorSession.Inactive
    }

    fun updateSelection(start: Int, end: Int, capsMode: Int) {
        updateActive { session ->
            session.copy(
                selectionStart = start,
                selectionEnd = end,
                currentCapsMode = capsMode
            )
        }
    }

    fun updateSurface(surface: ActiveImeSurface) {
        updateActive { it.copy(activeSurface = surface) }
    }

    fun updateCandidateSource(source: ContextStripSource) {
        updateActive { it.copy(candidateSource = source) }
    }

    fun updateComposition(text: String, start: Int, end: Int) {
        updateActive {
            it.copy(
                composingText = text,
                composingStart = if (text.isEmpty()) -1 else start,
                composingEnd = if (text.isEmpty()) -1 else end
            )
        }
    }

    private inline fun updateActive(transform: (EditorSession) -> EditorSession) {
        val current = state.value
        if (current.id != 0L) state.value = transform(current)
    }
}
