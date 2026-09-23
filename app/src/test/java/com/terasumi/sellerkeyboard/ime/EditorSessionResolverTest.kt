package com.terasumi.sellerkeyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorSessionResolverTest {
    @Test
    fun decimalField_resolvesLayoutActionAndSessionMetadata() {
        val session = EditorSessionResolver.resolve(
            EditorSessionRequest(
                id = 7,
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
                imeOptions = EditorInfo.IME_ACTION_DONE,
                packageName = "example.seller",
                supportedMimeTypes = setOf("image/png")
            )
        )

        assertEquals(7, session.id)
        assertEquals(InputFieldKind.DECIMAL, session.fieldKind)
        assertEquals(EditorInfo.IME_ACTION_DONE, session.actionId)
        assertEquals("example.seller", session.packageName)
        assertEquals(setOf("image/png"), session.supportedMimeTypes)
        assertFalse(session.isMultiline)
    }

    @Test
    fun customActionId_takesPriorityOverMaskedStandardAction() {
        val session = EditorSessionResolver.resolve(
            EditorSessionRequest(
                id = 1,
                inputType = InputType.TYPE_CLASS_TEXT,
                imeOptions = EditorInfo.IME_ACTION_GO,
                actionId = 42,
                hasCustomActionLabel = true
            )
        )

        assertEquals(42, session.actionId)
    }

    @Test
    fun multilineAndNoEnterAction_arePreserved() {
        val session = EditorSessionResolver.resolve(
            EditorSessionRequest(
                id = 2,
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION
            )
        )

        assertTrue(session.isMultiline)
        assertTrue(session.noEnterAction)
    }
}
