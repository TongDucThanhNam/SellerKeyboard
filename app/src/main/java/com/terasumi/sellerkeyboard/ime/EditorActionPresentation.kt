package com.terasumi.sellerkeyboard.ime

import android.view.inputmethod.EditorInfo

object EditorActionPresentation {
    fun contentDescription(session: EditorSession): String {
        session.actionLabel?.takeIf { it.isNotBlank() }?.let { return it }
        return when (session.actionId) {
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_PREVIOUS -> "Previous"
            EditorInfo.IME_ACTION_DONE -> "Done"
            else -> if (session.isMultiline) "Enter" else "Return"
        }
    }
}
