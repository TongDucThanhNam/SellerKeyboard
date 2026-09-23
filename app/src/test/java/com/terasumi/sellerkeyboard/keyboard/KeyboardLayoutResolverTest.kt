package com.terasumi.sellerkeyboard.keyboard

import com.terasumi.sellerkeyboard.ime.EditorSession
import com.terasumi.sellerkeyboard.ime.InputFieldKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutResolverTest {
    @Test
    fun emailAndUriLayouts_exposeContextKeysWithoutStringDrivenActions() {
        val email = layout(InputFieldKind.EMAIL)
        val uri = layout(InputFieldKind.URI)

        assertTrue(email.rows.flatten().any { it.label == "@" })
        assertTrue(uri.rows.flatten().any { it.label == "/" })
        assertEquals(
            KeyboardAction.Space,
            email.rows.flatten().single { it.role == KeyboardKeyRole.SPACE }.action
        )
    }

    @Test
    fun numericFieldVariants_haveDedicatedCharacterSets() {
        val integerLabels = labels(InputFieldKind.INTEGER)
        val decimalLabels = labels(InputFieldKind.DECIMAL)
        val signedLabels = labels(InputFieldKind.SIGNED_NUMBER)
        val signedDecimalLabels = labels(InputFieldKind.SIGNED_DECIMAL)
        val phoneLabels = labels(InputFieldKind.PHONE)
        val dateTimeLabels = labels(InputFieldKind.DATE_TIME)

        assertFalse("." in integerLabels)
        assertTrue("." in decimalLabels)
        assertTrue("-" in signedLabels)
        assertTrue("-" in signedDecimalLabels && "." in signedDecimalLabels)
        assertTrue("+" in phoneLabels && "#" in phoneLabels)
        assertTrue("/" in dateTimeLabels && ":" in dateTimeLabels)
    }

    @Test
    fun shiftState_changesLabelsButPreservesTypedShiftAction() {
        val lower = layout(InputFieldKind.TEXT, KeyboardState.NOCAPS)
        val oneShot = layout(InputFieldKind.TEXT, KeyboardState.CAPS)
        val locked = layout(InputFieldKind.TEXT, KeyboardState.DOUBLECAPS)

        assertTrue(lower.rows.flatten().any { it.label == "a" })
        assertTrue(oneShot.rows.flatten().any { it.label == "A" })
        val lockedShift = locked.rows.flatten().single { it.role == KeyboardKeyRole.SHIFT }
        assertEquals("SHIFT", lockedShift.label)
        assertEquals(KeyboardAction.Shift, lockedShift.action)
        assertEquals("Caps Lock on", lockedShift.contentDescription)
    }

    private fun labels(kind: InputFieldKind): Set<String> =
        layout(kind).rows.flatten().map { it.label }.toSet()

    private fun layout(
        kind: InputFieldKind,
        state: KeyboardState = KeyboardState.NOCAPS
    ): KeyboardLayoutModel = KeyboardLayoutResolver.resolve(
        EditorSession.Inactive.copy(fieldKind = kind),
        state
    )
}
