package com.terasumi.sellerkeyboard.keyboard

enum class ShiftMode {
    LOWERCASE,
    ONE_SHOT,
    CAPS_LOCK
}

enum class KeyboardSurface {
    ALPHABET,
    NUMBERS,
    SYMBOLS
}

data class KeyboardRuntimeState(
    val shiftMode: ShiftMode = ShiftMode.LOWERCASE,
    val surface: KeyboardSurface = KeyboardSurface.ALPHABET
) {
    fun toKeyboardState(): KeyboardState = when (surface) {
        KeyboardSurface.NUMBERS -> KeyboardState.NUMBERS
        KeyboardSurface.SYMBOLS -> KeyboardState.SYMBOLS
        KeyboardSurface.ALPHABET -> when (shiftMode) {
            ShiftMode.LOWERCASE -> KeyboardState.NOCAPS
            ShiftMode.ONE_SHOT -> KeyboardState.CAPS
            ShiftMode.CAPS_LOCK -> KeyboardState.DOUBLECAPS
        }
    }

    companion object {
        fun fromKeyboardState(state: KeyboardState): KeyboardRuntimeState = when (state) {
            KeyboardState.NOCAPS -> KeyboardRuntimeState(ShiftMode.LOWERCASE)
            KeyboardState.CAPS -> KeyboardRuntimeState(ShiftMode.ONE_SHOT)
            KeyboardState.DOUBLECAPS -> KeyboardRuntimeState(ShiftMode.CAPS_LOCK)
            KeyboardState.NUMBERS -> KeyboardRuntimeState(surface = KeyboardSurface.NUMBERS)
            KeyboardState.SYMBOLS -> KeyboardRuntimeState(surface = KeyboardSurface.SYMBOLS)
        }
    }
}

internal fun resolveShiftMode(
    current: ShiftMode,
    timeSinceLastShift: Long,
    doubleTapTimeoutMillis: Long
): ShiftMode = when (current) {
    ShiftMode.LOWERCASE -> ShiftMode.ONE_SHOT
    ShiftMode.ONE_SHOT -> if (timeSinceLastShift in 0 until doubleTapTimeoutMillis) {
        ShiftMode.CAPS_LOCK
    } else {
        ShiftMode.LOWERCASE
    }
    ShiftMode.CAPS_LOCK -> ShiftMode.LOWERCASE
}
