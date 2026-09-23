package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/** Owns key-field state outside the composable so input-view recreation is lossless. */
class KeyboardRuntimeCoordinator(
    initialState: KeyboardRuntimeState = KeyboardRuntimeState()
) {
    val state: MutableState<KeyboardRuntimeState> = mutableStateOf(initialState)

    fun update(next: KeyboardRuntimeState) {
        state.value = next
    }

    fun reset() {
        state.value = KeyboardRuntimeState()
    }
}
