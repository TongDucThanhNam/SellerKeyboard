package com.terasumi.sellerkeyboard.seller

import com.terasumi.sellerkeyboard.ime.InputConnectionOps

class TemplateInsertionUndoTracker {
    private var pending: PendingInsertion? = null

    fun record(sessionId: Long, insertedText: String) {
        pending = insertedText.takeIf(String::isNotEmpty)?.let {
            PendingInsertion(sessionId, it)
        }
    }

    fun undoIfAvailable(sessionId: Long, inputConnectionOps: InputConnectionOps): Boolean {
        val insertion = pending?.takeIf { it.sessionId == sessionId } ?: return false
        pending = null
        val currentSuffix = inputConnectionOps.getTextBeforeCursor(insertion.text.length)
            ?.toString()
            ?: return false
        if (currentSuffix != insertion.text) return false
        return inputConnectionOps.replaceBeforeCursor(
            beforeUtf16Length = insertion.text.length,
            replacement = ""
        )
    }

    fun clear() {
        pending = null
    }

    private data class PendingInsertion(val sessionId: Long, val text: String)
}
