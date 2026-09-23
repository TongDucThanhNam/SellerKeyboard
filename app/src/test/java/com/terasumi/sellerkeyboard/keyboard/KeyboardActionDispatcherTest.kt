package com.terasumi.sellerkeyboard.keyboard

import com.terasumi.sellerkeyboard.ime.ImeConnection
import com.terasumi.sellerkeyboard.ime.InputConnectionOps
import com.terasumi.sellerkeyboard.ime.EditorSession
import com.terasumi.sellerkeyboard.vietnamese.TelexProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardActionDispatcherTest {
    @Test
    fun keyPress_commitsTextTriggersHapticAndRevertsSingleShift() {
        val fake = FakeImeConnection()
        var hapticCount = 0
        val dispatcher = KeyboardActionDispatcher(
            inputConnectionOps = InputConnectionOps { fake },
            performHaptic = { hapticCount++ }
        )

        val result = dispatcher.dispatch(
            action = KeyboardAction.KeyPress("A"),
            keyboardState = KeyboardState.CAPS,
            lastShiftTapTime = 0L
        )

        assertEquals(listOf("commit:A:1"), fake.calls)
        assertEquals(1, hapticCount)
        assertEquals(KeyboardState.NOCAPS, result.keyboardState)
    }

    @Test
    fun deleteAndSpace_useInputConnectionOps() {
        val fake = FakeImeConnection()
        val dispatcher = KeyboardActionDispatcher(
            inputConnectionOps = InputConnectionOps { fake },
            performHaptic = {}
        )

        dispatcher.dispatch(KeyboardAction.Delete, KeyboardState.NOCAPS, 0L)
        dispatcher.dispatch(KeyboardAction.Space, KeyboardState.NOCAPS, 0L)

        assertEquals(
            listOf("selected:0", "deleteCodePoints:1:0", "commit: :1"),
            fake.calls
        )
    }

    @Test
    fun enter_usesBatchedEnterOperationAndDoesNotConsumeSingleShift() {
        val fake = FakeImeConnection()
        val dispatcher = KeyboardActionDispatcher(
            inputConnectionOps = InputConnectionOps { fake },
            performHaptic = {}
        )

        val result = dispatcher.dispatch(KeyboardAction.Enter, KeyboardState.CAPS, 0L)

        assertEquals(
            listOf(
                "beginBatch",
                "key:0:66",
                "key:1:66",
                "endBatch"
            ),
            fake.calls
        )
        assertEquals(KeyboardState.CAPS, result.keyboardState)
    }

    @Test
    fun shiftAndSwitchState_updateKeyboardStateWithoutEditorMutation() {
        val fake = FakeImeConnection()
        val dispatcher = KeyboardActionDispatcher(
            inputConnectionOps = InputConnectionOps { fake },
            performHaptic = {},
            nowMillis = { 1_000L }
        )

        val shifted = dispatcher.dispatch(KeyboardAction.Shift, KeyboardState.NOCAPS, 0L)
        val switched = dispatcher.dispatch(
            KeyboardAction.SwitchState(KeyboardState.NUMBERS),
            shifted.keyboardState,
            shifted.lastShiftTapTime
        )

        assertEquals(KeyboardState.CAPS, shifted.keyboardState)
        assertEquals(1_000L, shifted.lastShiftTapTime)
        assertEquals(KeyboardState.NUMBERS, switched.keyboardState)
        assertEquals(emptyList<String>(), fake.calls)
    }

    @Test
    fun shiftCycle_matchesStandardKeyboardBehavior() {
        val fake = FakeImeConnection()
        var now = 1_000L
        val dispatcher = KeyboardActionDispatcher(
            inputConnectionOps = InputConnectionOps { fake },
            performHaptic = {},
            nowMillis = { now }
        )

        val singleShift = dispatcher.dispatch(KeyboardAction.Shift, KeyboardState.NOCAPS, 0L)
        now = 1_200L
        val capsLock = dispatcher.dispatch(
            KeyboardAction.Shift,
            singleShift.keyboardState,
            singleShift.lastShiftTapTime
        )
        val lower = dispatcher.dispatch(
            KeyboardAction.Shift,
            capsLock.keyboardState,
            capsLock.lastShiftTapTime
        )

        assertEquals(KeyboardState.CAPS, singleShift.keyboardState)
        assertEquals(KeyboardState.DOUBLECAPS, capsLock.keyboardState)
        assertEquals(KeyboardState.NOCAPS, lower.keyboardState)
        assertEquals(emptyList<String>(), fake.calls)
    }

    @Test
    fun temporaryLayoutSwitch_preservesOneShotShift() {
        val dispatcher = KeyboardActionDispatcher(
            inputConnectionOps = InputConnectionOps { FakeImeConnection() },
            performHaptic = {}
        )
        val oneShot = KeyboardRuntimeState(shiftMode = ShiftMode.ONE_SHOT)

        val numbers = dispatcher.dispatchRuntime(
            KeyboardAction.SwitchState(KeyboardState.NUMBERS),
            oneShot,
            0L
        )
        val alphabet = dispatcher.dispatchRuntime(
            KeyboardAction.SwitchState(KeyboardState.NOCAPS),
            numbers.state,
            numbers.lastShiftTapTime
        )

        assertEquals(KeyboardSurface.NUMBERS, numbers.state.surface)
        assertEquals(ShiftMode.ONE_SHOT, numbers.state.shiftMode)
        assertEquals(KeyboardSurface.ALPHABET, alphabet.state.surface)
        assertEquals(ShiftMode.ONE_SHOT, alphabet.state.shiftMode)
    }

    @Test
    fun nonLetterActions_doNotConsumeOneShotShift() {
        val fake = FakeImeConnection()
        val dispatcher = KeyboardActionDispatcher(
            inputConnectionOps = InputConnectionOps { fake },
            performHaptic = {}
        )
        val oneShot = KeyboardRuntimeState(shiftMode = ShiftMode.ONE_SHOT)

        val afterSpace = dispatcher.dispatchRuntime(KeyboardAction.Space, oneShot, 0L)
        val afterDelete = dispatcher.dispatchRuntime(KeyboardAction.Delete, afterSpace.state, 0L)
        val afterPunctuation = dispatcher.dispatchRuntime(
            KeyboardAction.KeyPress("."),
            afterDelete.state,
            0L
        )

        assertEquals(ShiftMode.ONE_SHOT, afterSpace.state.shiftMode)
        assertEquals(ShiftMode.ONE_SHOT, afterDelete.state.shiftMode)
        assertEquals(ShiftMode.ONE_SHOT, afterPunctuation.state.shiftMode)
    }

    @Test
    fun injectedDoubleTapTimeout_controlsCapsLockTransition() {
        var now = 1_000L
        val dispatcher = KeyboardActionDispatcher(
            inputConnectionOps = InputConnectionOps.empty(),
            performHaptic = {},
            nowMillis = { now },
            doubleTapTimeoutMillis = 600L
        )
        val first = dispatcher.dispatchRuntime(
            KeyboardAction.Shift,
            KeyboardRuntimeState(),
            0L
        )
        now = 1_500L
        val second = dispatcher.dispatchRuntime(
            KeyboardAction.Shift,
            first.state,
            first.lastShiftTapTime
        )

        assertEquals(ShiftMode.CAPS_LOCK, second.state.shiftMode)
    }

    @Test
    fun autocorrectionAndImmediateBackspace_restoreExactOriginal() {
        val fake = FakeImeConnection(textBeforeCursor = "shipping ")
        val dispatcher = KeyboardActionDispatcher(
            inputConnectionOps = InputConnectionOps { fake },
            performHaptic = {},
            editorSessionProvider = { EditorSession.Inactive.copy(id = 7) }
        )
        val state = KeyboardRuntimeState()

        dispatcher.dispatchRuntime(
            KeyboardAction.ApplyAutoCorrection("shipipng", "shipping"),
            state,
            0L
        )
        dispatcher.dispatchRuntime(KeyboardAction.Delete, state, 0L)

        assertEquals(
            listOf(
                "beginBatch", "delete:8:0", "commit:shipping :1", "endBatch",
                "beginBatch", "delete:9:0", "commit:shipipng:1", "endBatch"
            ),
            fake.calls
        )
    }

    @Test
    fun translationRestore_onlyReplacesTheExactImmediateTranslation() {
        val matching = FakeImeConnection(textBeforeCursor = "Hello")
        val dispatcher = KeyboardActionDispatcher(
            inputConnectionOps = InputConnectionOps { matching },
            performHaptic = {}
        )

        dispatcher.dispatchRuntime(
            KeyboardAction.RestoreTranslation(original = "Xin chào", translated = "Hello"),
            KeyboardRuntimeState(),
            0L
        )

        assertEquals(
            listOf("beginBatch", "delete:5:0", "commit:Xin chào:1", "endBatch"),
            matching.calls
        )

        val movedCursor = FakeImeConnection(textBeforeCursor = "Other")
        KeyboardActionDispatcher(InputConnectionOps { movedCursor }, performHaptic = {})
            .dispatchRuntime(
                KeyboardAction.RestoreTranslation("Xin chào", "Hello"),
                KeyboardRuntimeState(),
                0L
            )
        assertTrue(movedCursor.calls.isEmpty())
    }

    @Test
    fun translationInsert_collapsesSelectionAtItsEndAndCommitsAtomically() {
        val fake = FakeImeConnection()
        val dispatcher = KeyboardActionDispatcher(
            inputConnectionOps = InputConnectionOps { fake },
            performHaptic = {}
        )

        dispatcher.dispatchRuntime(
            KeyboardAction.InsertTranslation("\nCảm ơn", selectionEnd = 24),
            KeyboardRuntimeState(),
            0L
        )

        assertEquals(
            listOf(
                "beginBatch",
                "selection:24:24",
                "commit:\nCảm ơn:1",
                "endBatch"
            ),
            fake.calls
        )
    }

    @Test
    fun telexEnabled_keyPressUpdatesComposingInsteadOfCommittingRawText() {
        val fake = FakeImeConnection()
        val ops = InputConnectionOps { fake }
        val processor = TelexProcessor(ops)
        val dispatcher = KeyboardActionDispatcher(
            inputConnectionOps = ops,
            performHaptic = {},
            telexProcessor = processor,
            isTelexEnabled = { true }
        )

        dispatcher.dispatch(KeyboardAction.KeyPress("a"), KeyboardState.NOCAPS, 0L)
        dispatcher.dispatch(KeyboardAction.KeyPress("w"), KeyboardState.NOCAPS, 0L)
        dispatcher.dispatch(KeyboardAction.KeyPress("s"), KeyboardState.NOCAPS, 0L)

        assertEquals("aws", processor.rawSyllable)
        assertEquals("ắ", processor.composingText)
        assertEquals(
            listOf("compose:a:1", "compose:ă:1", "compose:ắ:1"),
            fake.calls
        )
    }

    @Test
    fun telexEnabled_punctuationCommitsComposingBeforePunctuation() {
        val fake = FakeImeConnection()
        val ops = InputConnectionOps { fake }
        val processor = TelexProcessor(ops)
        val dispatcher = KeyboardActionDispatcher(
            inputConnectionOps = ops,
            performHaptic = {},
            telexProcessor = processor,
            isTelexEnabled = { true }
        )
        "tieesng".forEach { char ->
            dispatcher.dispatch(KeyboardAction.KeyPress(char.toString()), KeyboardState.NOCAPS, 0L)
        }

        dispatcher.dispatch(KeyboardAction.KeyPress("."), KeyboardState.NOCAPS, 0L)

        assertEquals("", processor.rawSyllable)
        assertEquals("finishComposing", fake.calls[fake.calls.lastIndex - 1])
        assertEquals("commit:.:1", fake.calls.last())
    }

    @Test
    fun telexEnabled_spaceCommitsComposingBeforeSpaceAndThenDeleteRemovesCommittedText() {
        val fake = FakeImeConnection()
        val ops = InputConnectionOps { fake }
        val processor = TelexProcessor(ops)
        val dispatcher = KeyboardActionDispatcher(
            inputConnectionOps = ops,
            performHaptic = {},
            telexProcessor = processor,
            isTelexEnabled = { true }
        )
        "ddawng".forEach { char ->
            dispatcher.dispatch(KeyboardAction.KeyPress(char.toString()), KeyboardState.NOCAPS, 0L)
        }

        dispatcher.dispatch(KeyboardAction.Space, KeyboardState.NOCAPS, 0L)
        dispatcher.dispatch(KeyboardAction.Delete, KeyboardState.NOCAPS, 0L)

        assertEquals("", processor.rawSyllable)
        assertEquals(
            listOf("finishComposing", "commit: :1", "selected:0", "deleteCodePoints:1:0"),
            fake.calls.takeLast(4)
        )
    }

    @Test
    fun telexEnabled_enterCommitsComposingBeforeBatchedEnterKey() {
        val fake = FakeImeConnection()
        val ops = InputConnectionOps { fake }
        val processor = TelexProcessor(ops)
        val dispatcher = KeyboardActionDispatcher(
            inputConnectionOps = ops,
            performHaptic = {},
            telexProcessor = processor,
            isTelexEnabled = { true }
        )
        "aws".forEach { char ->
            dispatcher.dispatch(KeyboardAction.KeyPress(char.toString()), KeyboardState.NOCAPS, 0L)
        }

        dispatcher.dispatch(KeyboardAction.Enter, KeyboardState.NOCAPS, 0L)

        assertEquals("", processor.rawSyllable)
        assertEquals(
            listOf(
                "finishComposing",
                "beginBatch",
                "key:0:66",
                "key:1:66",
                "endBatch"
            ),
            fake.calls.takeLast(5)
        )
    }

    @Test
    fun telexEnabled_backspaceUpdatesComposingAndDoesNotDeleteCommittedText() {
        val fake = FakeImeConnection()
        val ops = InputConnectionOps { fake }
        val processor = TelexProcessor(ops)
        val dispatcher = KeyboardActionDispatcher(
            inputConnectionOps = ops,
            performHaptic = {},
            telexProcessor = processor,
            isTelexEnabled = { true }
        )
        "aws".forEach { char ->
            dispatcher.dispatch(KeyboardAction.KeyPress(char.toString()), KeyboardState.NOCAPS, 0L)
        }

        dispatcher.dispatch(KeyboardAction.Delete, KeyboardState.NOCAPS, 0L)

        assertEquals("aw", processor.rawSyllable)
        assertEquals("ă", processor.composingText)
        assertEquals("compose:ă:1", fake.calls.last())
    }

    @Test
    fun telexDisabled_commitsRawTextWithoutComposing() {
        val fake = FakeImeConnection()
        val ops = InputConnectionOps { fake }
        val processor = TelexProcessor(ops)
        val dispatcher = KeyboardActionDispatcher(
            inputConnectionOps = ops,
            performHaptic = {},
            telexProcessor = processor,
            isTelexEnabled = { false }
        )

        dispatcher.dispatch(KeyboardAction.KeyPress("a"), KeyboardState.NOCAPS, 0L)
        dispatcher.dispatch(KeyboardAction.KeyPress("w"), KeyboardState.NOCAPS, 0L)

        assertEquals("", processor.rawSyllable)
        assertEquals(listOf("commit:a:1", "commit:w:1"), fake.calls)
    }

    private class FakeImeConnection(
        private val textBeforeCursor: String? = null
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
            return true
        }

        override fun getSelectedText(flags: Int): CharSequence? {
            calls += "selected:$flags"
            return null
        }

        override fun getTextBeforeCursor(length: Int, flags: Int): CharSequence? =
            textBeforeCursor?.takeLast(length)

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
