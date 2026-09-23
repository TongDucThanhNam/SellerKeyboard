package com.terasumi.sellerkeyboard.ime

import android.view.KeyEvent
import android.view.inputmethod.InputConnection

class InputConnectionOps(
    private val connectionProvider: () -> ImeConnection?
) {
    fun commitText(text: CharSequence, newCursorPosition: Int = 1): Boolean {
        if (text.isEmpty()) {
            return false
        }
        return connectionProvider()?.commitText(text, newCursorPosition) ?: false
    }

    fun deleteBeforeCursor(count: Int = 1): Boolean {
        if (count <= 0) {
            return false
        }
        val connection = connectionProvider() ?: return false
        if (connection.deleteSurroundingTextInCodePoints(count, 0)) {
            return true
        }
        return if (count == 1) sendDeleteKey(connection) else false
    }

    /**
     * Deletes a selection first, otherwise one complete Unicode code point.
     * Editors that reject the code-point API receive a DEL key event instead of
     * an unsafe UTF-16-unit delete that could split a surrogate pair.
     */
    fun deleteBackward(): Boolean {
        val connection = connectionProvider() ?: return false
        val selectedText = connection.getSelectedText(0)
        if (!selectedText.isNullOrEmpty()) {
            return connection.commitText("", 1)
        }
        val textBeforeCursor = connection.getTextBeforeCursor(GRAPHEME_LOOKBEHIND_UTF16, 0)
        if (!textBeforeCursor.isNullOrEmpty()) {
            val utf16Length = GraphemeDeletion.previousClusterUtf16Length(textBeforeCursor)
            if (utf16Length > 0 && connection.deleteSurroundingText(utf16Length, 0)) {
                return true
            }
        }
        if (connection.deleteSurroundingTextInCodePoints(1, 0)) {
            return true
        }
        return sendDeleteKey(connection)
    }

    fun setComposingText(text: CharSequence, newCursorPosition: Int = 1): Boolean {
        if (text.isEmpty()) {
            return false
        }
        return connectionProvider()?.setComposingText(text, newCursorPosition) ?: false
    }

    fun finishComposingText(): Boolean {
        return connectionProvider()?.finishComposingText() ?: false
    }

    fun clearComposingText(): Boolean {
        return connectionProvider()?.setComposingText("", 1) ?: false
    }

    fun sendEnterKey(): Boolean {
        return batchEdit { connection ->
            val down = connection.sendKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
            val up = connection.sendKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)
            down && up
        }
    }

    fun performEditorActionOrEnter(
        actionId: Int,
        isMultiline: Boolean,
        noEnterAction: Boolean
    ): Boolean {
        val connection = connectionProvider() ?: return false
        val hasDeclaredAction = actionId != IME_ACTION_UNSPECIFIED && actionId != IME_ACTION_NONE
        if (hasDeclaredAction && !noEnterAction && connection.performEditorAction(actionId)) {
            return true
        }
        if (isMultiline && !noEnterAction) {
            return connection.commitText("\n", 1)
        }
        return sendEnterKey()
    }

    fun getCursorCapsMode(requestedModes: Int): Int {
        return connectionProvider()?.getCursorCapsMode(requestedModes) ?: 0
    }

    fun getTextBeforeCursor(maxUtf16Length: Int = 128): CharSequence? {
        if (maxUtf16Length <= 0) return null
        return connectionProvider()?.getTextBeforeCursor(maxUtf16Length, 0)
    }

    fun getSelectedText(): CharSequence? = connectionProvider()?.getSelectedText(0)

    fun notifyCorrection(offset: Int, oldText: CharSequence, newText: CharSequence): Boolean {
        if (offset < 0 || oldText.isEmpty() || newText.isEmpty()) return false
        return connectionProvider()?.commitCorrection(offset, oldText, newText) ?: false
    }

    fun replaceBeforeCursor(beforeUtf16Length: Int, replacement: CharSequence): Boolean {
        if (beforeUtf16Length < 0) return false
        return batchEdit { connection ->
            val deleted = beforeUtf16Length == 0 ||
                connection.deleteSurroundingText(beforeUtf16Length, 0)
            deleted && connection.commitText(replacement, 1)
        }
    }

    fun moveCursorTo(position: Int): Boolean {
        if (position < 0) return false
        return connectionProvider()?.setSelection(position, position) ?: false
    }

    fun insertAt(position: Int, text: CharSequence): Boolean {
        if (position < 0 || text.isEmpty()) return false
        return batchEdit { connection ->
            connection.setSelection(position, position) && connection.commitText(text, 1)
        }
    }

    fun previewDeleteBackward(graphemeCount: Int, cursorPosition: Int): Boolean {
        if (graphemeCount <= 0 || cursorPosition < 0) return false
        val connection = connectionProvider() ?: return false
        val text = connection.getTextBeforeCursor(DELETE_PREVIEW_LOOKBEHIND_UTF16, 0)
            ?: return false
        var remaining = text
        var utf16Length = 0
        repeat(graphemeCount) {
            if (remaining.isEmpty()) return@repeat
            val clusterLength = GraphemeDeletion.previousClusterUtf16Length(remaining)
            if (clusterLength <= 0) return@repeat
            utf16Length += clusterLength
            remaining = remaining.subSequence(0, remaining.length - clusterLength)
        }
        if (utf16Length <= 0) return false
        return connection.setSelection(
            (cursorPosition - utf16Length).coerceAtLeast(0),
            cursorPosition
        )
    }

    fun restoreCursor(position: Int): Boolean = moveCursorTo(position)

    fun batchEdit(block: (ImeConnection) -> Boolean): Boolean {
        val connection = connectionProvider() ?: return false
        val began = connection.beginBatchEdit()
        return try {
            block(connection)
        } finally {
            if (began) {
                connection.endBatchEdit()
            }
        }
    }

    private fun sendDeleteKey(connection: ImeConnection): Boolean {
        val down = connection.sendKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)
        val up = connection.sendKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL)
        return down && up
    }

    companion object {
        private const val IME_ACTION_UNSPECIFIED = 0
        private const val IME_ACTION_NONE = 1
        private const val GRAPHEME_LOOKBEHIND_UTF16 = 64
        private const val DELETE_PREVIEW_LOOKBEHIND_UTF16 = 256

        fun fromInputConnection(provider: () -> InputConnection?): InputConnectionOps {
            return InputConnectionOps {
                provider()?.let(::AndroidImeConnection)
            }
        }

        fun empty(): InputConnectionOps {
            return InputConnectionOps { null }
        }
    }
}
