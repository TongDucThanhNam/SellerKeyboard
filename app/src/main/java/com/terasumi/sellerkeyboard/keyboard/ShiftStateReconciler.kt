package com.terasumi.sellerkeyboard.keyboard

import com.terasumi.sellerkeyboard.ime.EditorInfoPolicy

object ShiftStateReconciler {
    fun reconcile(
        current: KeyboardState,
        currentCapsMode: Int,
        policy: EditorInfoPolicy,
        hasActiveComposition: Boolean = false
    ): KeyboardState {
        if (current == KeyboardState.DOUBLECAPS ||
            current == KeyboardState.NUMBERS ||
            current == KeyboardState.SYMBOLS ||
            !policy.canAutoCapitalize) {
            return current
        }
        // Some editors continue reporting sentence caps while the first word is
        // still a composing span. Re-arming one-shot Shift in that interval
        // makes every Telex letter uppercase. Composition is already inside a
        // word, so only explicit Caps Lock may remain active here.
        if (hasActiveComposition) return KeyboardState.NOCAPS
        return if (currentCapsMode != 0) KeyboardState.CAPS else KeyboardState.NOCAPS
    }
}
