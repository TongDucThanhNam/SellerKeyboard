package com.terasumi.sellerkeyboard.keyboard

sealed class KeyboardAction {
    data class KeyPress(val key: String) : KeyboardAction()
    object Shift : KeyboardAction()
    object Delete : KeyboardAction()
    object Space : KeyboardAction()
    object Enter : KeyboardAction()
    data class MoveCursorTo(val position: Int) : KeyboardAction()
    data class PreviewDeleteBackward(
        val graphemeCount: Int,
        val cursorPosition: Int
    ) : KeyboardAction()
    data class RestoreCursor(val position: Int) : KeyboardAction()
    data class AcceptCandidate(
        val text: String,
        val replaceBeforeUtf16: Int
    ) : KeyboardAction()
    data class ApplyAutoCorrection(
        val original: String,
        val corrected: String,
        val trailingText: String = " "
    ) : KeyboardAction()
    data class InsertTemplate(
        val text: String,
        val replaceBeforeUtf16: Int = 0
    ) : KeyboardAction()
    data class ReplaceSelection(val text: String) : KeyboardAction()
    data class InsertTranslation(
        val text: String,
        val selectionEnd: Int
    ) : KeyboardAction()
    data class RestoreTranslation(
        val original: String,
        val translated: String
    ) : KeyboardAction()
    data class SwitchState(val newState: KeyboardState) : KeyboardAction()
}
