package com.terasumi.sellerkeyboard.ime

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputConnectionOpsTest {
    @Test
    fun nullConnectionProvider_guardsEveryOperation() {
        val ops = InputConnectionOps.empty()

        assertFalse(ops.commitText("a"))
        assertFalse(ops.deleteBeforeCursor())
        assertFalse(ops.setComposingText("a"))
        assertFalse(ops.finishComposingText())
        assertFalse(ops.clearComposingText())
        assertFalse(ops.sendEnterKey())
    }

    @Test
    fun invalidInputs_doNotCallConnection() {
        val fake = FakeImeConnection()
        val ops = InputConnectionOps { fake }

        assertFalse(ops.commitText(""))
        assertFalse(ops.deleteBeforeCursor(0))
        assertFalse(ops.setComposingText(""))

        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun commitDeleteAndComposingOperations_delegateToConnection() {
        val fake = FakeImeConnection()
        val ops = InputConnectionOps { fake }

        assertTrue(ops.commitText("hello"))
        assertTrue(ops.deleteBeforeCursor(2))
        assertTrue(ops.setComposingText("xin"))
        assertTrue(ops.clearComposingText())
        assertTrue(ops.finishComposingText())

        assertEquals(
            listOf(
                "commit:hello:1",
                "deleteCodePoints:2:0",
                "compose:xin:1",
                "compose::1",
                "finishComposing"
            ),
            fake.calls
        )
    }

    @Test
    fun sendEnterKey_usesBatchEditForDownAndUpEvents() {
        val fake = FakeImeConnection()
        val ops = InputConnectionOps { fake }

        assertTrue(ops.sendEnterKey())

        assertEquals(
            listOf(
                "beginBatch",
                "key:${KeyEvent.ACTION_DOWN}:${KeyEvent.KEYCODE_ENTER}",
                "key:${KeyEvent.ACTION_UP}:${KeyEvent.KEYCODE_ENTER}",
                "endBatch"
            ),
            fake.calls
        )
    }

    @Test
    fun deleteBackward_deletesSelectionBeforeSurroundingText() {
        val fake = FakeImeConnection(selectedText = "selected")
        val ops = InputConnectionOps { fake }

        assertTrue(ops.deleteBackward())

        assertEquals(listOf("selected:0", "commit::1"), fake.calls)
    }

    @Test
    fun deleteBackward_usesCodePointApiAndNeverRequestsOneUtf16Unit() {
        val fake = FakeImeConnection()
        val ops = InputConnectionOps { fake }

        assertTrue(ops.deleteBackward())

        assertEquals(listOf("selected:0", "deleteCodePoints:1:0"), fake.calls)
    }

    @Test
    fun rejectedCodePointDelete_fallsBackToEditorDeleteKey() {
        val fake = FakeImeConnection(codePointDeleteSucceeds = false)
        val ops = InputConnectionOps { fake }

        assertTrue(ops.deleteBackward())

        assertEquals(
            listOf(
                "selected:0",
                "deleteCodePoints:1:0",
                "key:${KeyEvent.ACTION_DOWN}:${KeyEvent.KEYCODE_DEL}",
                "key:${KeyEvent.ACTION_UP}:${KeyEvent.KEYCODE_DEL}"
            ),
            fake.calls
        )
        assertFalse(fake.calls.any { it.startsWith("delete:") })
    }

    @Test
    fun deleteBackward_deletesWholeGraphemeWhenBoundedContextIsAvailable() {
        val fake = FakeImeConnection(textBeforeCursor = "hello 👩‍👩‍👧‍👦")
        val ops = InputConnectionOps { fake }

        assertTrue(ops.deleteBackward())

        assertEquals(
            listOf("selected:0", "delete:${"👩‍👩‍👧‍👦".length}:0"),
            fake.calls
        )
    }

    @Test
    fun declaredEditorAction_isPreferred_andMultilineFallsBackToNewline() {
        val actionFake = FakeImeConnection()
        val actionOps = InputConnectionOps { actionFake }
        assertTrue(actionOps.performEditorActionOrEnter(4, isMultiline = false, noEnterAction = false))
        assertEquals(listOf("editorAction:4"), actionFake.calls)

        val multilineFake = FakeImeConnection(editorActionSucceeds = false)
        val multilineOps = InputConnectionOps { multilineFake }
        assertTrue(multilineOps.performEditorActionOrEnter(0, isMultiline = true, noEnterAction = false))
        assertEquals(listOf("commit:\n:1"), multilineFake.calls)
    }

    @Test
    fun previewDelete_selectsWholeGraphemeClusters_andRestoreCollapsesSelection() {
        val text = "ab👩‍💻"
        val fake = FakeImeConnection(textBeforeCursor = text)
        val ops = InputConnectionOps { fake }

        assertTrue(ops.previewDeleteBackward(graphemeCount = 2, cursorPosition = text.length))
        assertTrue(ops.restoreCursor(text.length))

        val expectedStart = text.length - "b👩‍💻".length
        assertEquals(
            listOf("selection:$expectedStart:${text.length}", "selection:${text.length}:${text.length}"),
            fake.calls
        )
    }

    @Test
    fun moveCursor_rejectsNegativePositions() {
        val fake = FakeImeConnection()
        val ops = InputConnectionOps { fake }

        assertFalse(ops.moveCursorTo(-1))
        assertTrue(ops.moveCursorTo(3))
        assertEquals(listOf("selection:3:3"), fake.calls)
    }

    @Test
    fun replaceBeforeCursor_isAtomic() {
        val fake = FakeImeConnection()
        val ops = InputConnectionOps { fake }

        assertTrue(ops.replaceBeforeCursor(4, "shipping"))

        assertEquals(
            listOf("beginBatch", "delete:4:0", "commit:shipping:1", "endBatch"),
            fake.calls
        )
    }

    private class FakeImeConnection(
        private val selectedText: CharSequence? = null,
        private val textBeforeCursor: CharSequence? = null,
        private val codePointDeleteSucceeds: Boolean = true,
        private val editorActionSucceeds: Boolean = true
    ) : ImeConnection {
        val calls = mutableListOf<String>()

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            calls += "commit:$text:$newCursorPosition"
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            calls += "delete:$beforeLength:$afterLength"
            return true
        }

        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
            calls += "deleteCodePoints:$beforeLength:$afterLength"
            return codePointDeleteSucceeds
        }

        override fun getSelectedText(flags: Int): CharSequence? {
            calls += "selected:$flags"
            return selectedText
        }

        override fun getTextBeforeCursor(length: Int, flags: Int): CharSequence? {
            return textBeforeCursor
        }

        override fun setSelection(start: Int, end: Int): Boolean {
            calls += "selection:$start:$end"
            return true
        }

        override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
            calls += "compose:$text:$newCursorPosition"
            return true
        }

        override fun finishComposingText(): Boolean {
            calls += "finishComposing"
            return true
        }

        override fun performEditorAction(actionCode: Int): Boolean {
            calls += "editorAction:$actionCode"
            return editorActionSucceeds
        }

        override fun sendKeyEvent(action: Int, keyCode: Int): Boolean {
            calls += "key:$action:$keyCode"
            return true
        }

        override fun beginBatchEdit(): Boolean {
            calls += "beginBatch"
            return true
        }

        override fun endBatchEdit(): Boolean {
            calls += "endBatch"
            return true
        }
    }
}
