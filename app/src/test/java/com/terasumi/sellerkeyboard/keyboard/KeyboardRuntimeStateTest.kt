package com.terasumi.sellerkeyboard.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardRuntimeStateTest {
    @Test
    fun shiftAndSurface_areIndependent() {
        val state = KeyboardRuntimeState(
            shiftMode = ShiftMode.CAPS_LOCK,
            surface = KeyboardSurface.NUMBERS
        )

        assertEquals(KeyboardState.NUMBERS, state.toKeyboardState())
        assertEquals(ShiftMode.CAPS_LOCK, state.copy(surface = KeyboardSurface.ALPHABET).shiftMode)
        assertEquals(KeyboardState.DOUBLECAPS, state.copy(surface = KeyboardSurface.ALPHABET).toKeyboardState())
    }
}
