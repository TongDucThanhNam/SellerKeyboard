package com.terasumi.sellerkeyboard.keyboard

sealed class KeyboardAction {
    data class KeyPress(val key: String) : KeyboardAction()
    object Shift : KeyboardAction()
    object Delete : KeyboardAction()
    object Space : KeyboardAction()
    object Enter : KeyboardAction()
    data class SwitchState(val newState: KeyboardState) : KeyboardAction()
}
