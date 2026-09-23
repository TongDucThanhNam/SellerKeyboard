package com.terasumi.sellerkeyboard.keyboard

import com.terasumi.sellerkeyboard.ime.EditorInfoPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class ShiftStateReconcilerTest {
    private val textPolicy = EditorInfoPolicy.fromInputType(1)

    @Test
    fun cursorCapsMode_drivesAutomaticOneShotState() {
        assertEquals(
            KeyboardState.CAPS,
            ShiftStateReconciler.reconcile(KeyboardState.NOCAPS, currentCapsMode = 1, textPolicy)
        )
        assertEquals(
            KeyboardState.NOCAPS,
            ShiftStateReconciler.reconcile(KeyboardState.CAPS, currentCapsMode = 0, textPolicy)
        )
    }

    @Test
    fun capsLockAndNonAlphabetSurfaces_areNotOverwritten() {
        assertEquals(
            KeyboardState.DOUBLECAPS,
            ShiftStateReconciler.reconcile(KeyboardState.DOUBLECAPS, 0, textPolicy)
        )
        assertEquals(
            KeyboardState.NUMBERS,
            ShiftStateReconciler.reconcile(KeyboardState.NUMBERS, 1, textPolicy)
        )
    }

    @Test
    fun identifierFields_preserveManualShiftChoice() {
        val emailPolicy = EditorInfoPolicy.fromInputType(1 or 32)
        assertEquals(
            KeyboardState.CAPS,
            ShiftStateReconciler.reconcile(KeyboardState.CAPS, 0, emailPolicy)
        )
    }

    @Test
    fun activeComposition_doesNotRearmOneShotFromStaleEditorCapsMode() {
        val textPolicy = EditorInfoPolicy.fromInputType(
            android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        )

        assertEquals(
            KeyboardState.NOCAPS,
            ShiftStateReconciler.reconcile(
                current = KeyboardState.NOCAPS,
                currentCapsMode = android.text.TextUtils.CAP_MODE_SENTENCES,
                policy = textPolicy,
                hasActiveComposition = true
            )
        )
        assertEquals(
            KeyboardState.DOUBLECAPS,
            ShiftStateReconciler.reconcile(
                current = KeyboardState.DOUBLECAPS,
                currentCapsMode = android.text.TextUtils.CAP_MODE_SENTENCES,
                policy = textPolicy,
                hasActiveComposition = true
            )
        )
    }
}
