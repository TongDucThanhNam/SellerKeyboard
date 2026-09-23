package com.terasumi.sellerkeyboard.suggestion

import com.terasumi.sellerkeyboard.ime.InputConnectionOps

class AutoCorrectionUndoTracker {
    private var state: UndoState? = null

    fun record(sessionId: Long, original: String, corrected: String, trailingText: String) {
        state = UndoState(sessionId, original, corrected, trailingText)
    }

    fun undoIfAvailable(sessionId: Long, inputConnectionOps: InputConnectionOps): Boolean {
        val undo = state?.takeIf { it.sessionId == sessionId } ?: return false
        state = null
        val correctedSuffix = undo.corrected + undo.trailingText
        val currentSuffix = inputConnectionOps.getTextBeforeCursor(correctedSuffix.length)
            ?.toString()
            ?: return false
        if (currentSuffix != correctedSuffix) return false
        return inputConnectionOps.replaceBeforeCursor(
            beforeUtf16Length = correctedSuffix.length,
            replacement = undo.original
        )
    }

    fun clear() {
        state = null
    }

    private data class UndoState(
        val sessionId: Long,
        val original: String,
        val corrected: String,
        val trailingText: String
    )
}
