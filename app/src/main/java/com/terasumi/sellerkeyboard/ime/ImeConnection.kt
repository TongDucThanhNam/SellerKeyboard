package com.terasumi.sellerkeyboard.ime

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.view.inputmethod.CorrectionInfo

/**
 * Narrow adapter around InputConnection so keyboard action dispatch can be tested
 * without depending on Android framework implementations.
 */
interface ImeConnection {
    fun commitText(text: CharSequence, newCursorPosition: Int): Boolean
    fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean
    fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean = false
    fun getSelectedText(flags: Int): CharSequence? = null
    fun getTextBeforeCursor(length: Int, flags: Int): CharSequence? = null
    fun setSelection(start: Int, end: Int): Boolean = false
    fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean
    fun finishComposingText(): Boolean
    fun performEditorAction(actionCode: Int): Boolean = false
    fun commitCorrection(offset: Int, oldText: CharSequence, newText: CharSequence): Boolean = false
    fun getCursorCapsMode(reqModes: Int): Int = 0
    fun sendKeyEvent(action: Int, keyCode: Int): Boolean
    fun beginBatchEdit(): Boolean
    fun endBatchEdit(): Boolean
}

internal class AndroidImeConnection(
    private val inputConnection: InputConnection
) : ImeConnection {
    override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
        return inputConnection.commitText(text, newCursorPosition)
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        return inputConnection.deleteSurroundingText(beforeLength, afterLength)
    }

    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
        return inputConnection.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
    }

    override fun getSelectedText(flags: Int): CharSequence? {
        return inputConnection.getSelectedText(flags)
    }

    override fun getTextBeforeCursor(length: Int, flags: Int): CharSequence? {
        return inputConnection.getTextBeforeCursor(length, flags)
    }

    override fun setSelection(start: Int, end: Int): Boolean {
        return inputConnection.setSelection(start, end)
    }

    override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
        return inputConnection.setComposingText(text, newCursorPosition)
    }

    override fun finishComposingText(): Boolean {
        return inputConnection.finishComposingText()
    }

    override fun performEditorAction(actionCode: Int): Boolean {
        return inputConnection.performEditorAction(actionCode)
    }

    override fun commitCorrection(
        offset: Int,
        oldText: CharSequence,
        newText: CharSequence
    ): Boolean = inputConnection.commitCorrection(CorrectionInfo(offset, oldText, newText))

    override fun getCursorCapsMode(reqModes: Int): Int {
        return inputConnection.getCursorCapsMode(reqModes)
    }

    override fun sendKeyEvent(action: Int, keyCode: Int): Boolean {
        return inputConnection.sendKeyEvent(KeyEvent(action, keyCode))
    }

    override fun beginBatchEdit(): Boolean {
        return inputConnection.beginBatchEdit()
    }

    override fun endBatchEdit(): Boolean {
        return inputConnection.endBatchEdit()
    }
}
