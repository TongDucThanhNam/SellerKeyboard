package com.terasumi.sellerkeyboard.keyboard

import com.terasumi.sellerkeyboard.ime.InputConnectionOps
import com.terasumi.sellerkeyboard.ime.EditorSession
import com.terasumi.sellerkeyboard.suggestion.AutoCorrectionUndoTracker
import com.terasumi.sellerkeyboard.seller.TemplateInsertionUndoTracker
import com.terasumi.sellerkeyboard.vietnamese.TelexProcessor

internal data class KeyboardDispatchResult(
    val keyboardState: KeyboardState,
    val lastShiftTapTime: Long
)

internal data class KeyboardRuntimeDispatchResult(
    val state: KeyboardRuntimeState,
    val lastShiftTapTime: Long
)

internal class KeyboardActionDispatcher(
    private val inputConnectionOps: InputConnectionOps,
    private val performHaptic: () -> Unit,
    private val telexProcessor: TelexProcessor? = null,
    private val isTelexEnabled: () -> Boolean = { false },
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val editorSessionProvider: () -> EditorSession = { EditorSession.Inactive },
    private val doubleTapTimeoutMillis: Long = DEFAULT_SHIFT_DOUBLE_TAP_TIMEOUT_MS,
    private val autoCorrectionUndoTracker: AutoCorrectionUndoTracker = AutoCorrectionUndoTracker(),
    private val templateInsertionUndoTracker: TemplateInsertionUndoTracker =
        TemplateInsertionUndoTracker()
) {
    fun dispatch(
        action: KeyboardAction,
        keyboardState: KeyboardState,
        lastShiftTapTime: Long
    ): KeyboardDispatchResult {
        val runtimeResult = dispatchRuntime(
            action = action,
            state = KeyboardRuntimeState.fromKeyboardState(keyboardState),
            lastShiftTapTime = lastShiftTapTime
        )
        return KeyboardDispatchResult(
            keyboardState = runtimeResult.state.toKeyboardState(),
            lastShiftTapTime = runtimeResult.lastShiftTapTime
        )
    }

    fun dispatchRuntime(
        action: KeyboardAction,
        state: KeyboardRuntimeState,
        lastShiftTapTime: Long
    ): KeyboardRuntimeDispatchResult {
        if (action !is KeyboardAction.MoveCursorTo &&
            action !is KeyboardAction.PreviewDeleteBackward &&
            action !is KeyboardAction.RestoreCursor) {
            performHaptic()
        }

        var nextState = state
        var nextLastShiftTapTime = lastShiftTapTime

        if (action !is KeyboardAction.Delete && action !is KeyboardAction.ApplyAutoCorrection) {
            autoCorrectionUndoTracker.clear()
        }
        if (action !is KeyboardAction.Delete && action !is KeyboardAction.InsertTemplate) {
            templateInsertionUndoTracker.clear()
        }

        when (action) {
            is KeyboardAction.KeyPress -> {
                if (handleTelexKeyPress(action.key)) {
                    if (state.shiftMode == ShiftMode.ONE_SHOT) {
                        nextState = state.copy(shiftMode = ShiftMode.LOWERCASE)
                    }
                    return KeyboardRuntimeDispatchResult(
                        state = nextState,
                        lastShiftTapTime = nextLastShiftTapTime
                    )
                }

                commitTelexComposingIfEnabled()
                inputConnectionOps.commitText(action.key)
                if (state.surface == KeyboardSurface.ALPHABET &&
                    state.shiftMode == ShiftMode.ONE_SHOT && action.key.any(Char::isLetter)) {
                    nextState = state.copy(shiftMode = ShiftMode.LOWERCASE)
                }
            }

            KeyboardAction.Delete -> {
                val sessionId = editorSessionProvider().id
                if (autoCorrectionUndoTracker.undoIfAvailable(sessionId, inputConnectionOps)) {
                    Unit
                } else if (templateInsertionUndoTracker.undoIfAvailable(sessionId, inputConnectionOps)) {
                    Unit
                } else if (!deleteTelexComposingIfEnabled()) {
                    inputConnectionOps.deleteBackward()
                }
            }

            KeyboardAction.Space -> {
                commitTelexComposingIfEnabled()
                inputConnectionOps.commitText(" ")
            }

            KeyboardAction.Enter -> {
                commitTelexComposingIfEnabled()
                val session = editorSessionProvider()
                inputConnectionOps.performEditorActionOrEnter(
                    actionId = session.actionId,
                    isMultiline = session.isMultiline,
                    noEnterAction = session.noEnterAction
                )
            }

            is KeyboardAction.MoveCursorTo -> {
                commitTelexComposingIfEnabled()
                inputConnectionOps.moveCursorTo(action.position)
            }

            is KeyboardAction.PreviewDeleteBackward -> {
                commitTelexComposingIfEnabled()
                inputConnectionOps.previewDeleteBackward(
                    graphemeCount = action.graphemeCount,
                    cursorPosition = action.cursorPosition
                )
            }

            is KeyboardAction.RestoreCursor -> {
                inputConnectionOps.restoreCursor(action.position)
            }

            is KeyboardAction.AcceptCandidate -> {
                commitTelexComposingIfEnabled()
                inputConnectionOps.replaceBeforeCursor(
                    beforeUtf16Length = action.replaceBeforeUtf16,
                    replacement = action.text
                )
            }


            is KeyboardAction.ApplyAutoCorrection -> {
                commitTelexComposingIfEnabled()
                val replacement = action.corrected + action.trailingText
                val applied = inputConnectionOps.replaceBeforeCursor(
                    beforeUtf16Length = action.original.length,
                    replacement = replacement
                )
                if (applied) {
                    val session = editorSessionProvider()
                    inputConnectionOps.notifyCorrection(
                        offset = (session.selectionStart - action.original.length).coerceAtLeast(0),
                        oldText = action.original,
                        newText = action.corrected
                    )
                    autoCorrectionUndoTracker.record(
                        sessionId = session.id,
                        original = action.original,
                        corrected = action.corrected,
                        trailingText = action.trailingText
                    )
                }
            }

            is KeyboardAction.InsertTemplate -> {
                commitTelexComposingIfEnabled()
                val inserted = inputConnectionOps.replaceBeforeCursor(
                    beforeUtf16Length = action.replaceBeforeUtf16,
                    replacement = action.text
                )
                if (inserted) {
                    templateInsertionUndoTracker.record(
                        sessionId = editorSessionProvider().id,
                        insertedText = action.text
                    )
                }
            }


            is KeyboardAction.ReplaceSelection -> {
                commitTelexComposingIfEnabled()
                inputConnectionOps.commitText(action.text)
            }

            is KeyboardAction.InsertTranslation -> {
                commitTelexComposingIfEnabled()
                inputConnectionOps.insertAt(action.selectionEnd, action.text)
            }

            is KeyboardAction.RestoreTranslation -> {
                val currentSuffix = inputConnectionOps.getTextBeforeCursor(action.translated.length)
                    ?.toString()
                if (currentSuffix == action.translated) {
                    inputConnectionOps.replaceBeforeCursor(
                        beforeUtf16Length = action.translated.length,
                        replacement = action.original
                    )
                }
            }

            KeyboardAction.Shift -> {
                val now = nowMillis()
                nextState = state.copy(
                    shiftMode = resolveShiftMode(
                    current = state.shiftMode,
                    timeSinceLastShift = now - lastShiftTapTime,
                    doubleTapTimeoutMillis = doubleTapTimeoutMillis
                    )
                )
                nextLastShiftTapTime = now
            }

            is KeyboardAction.SwitchState -> {
                if (action.newState == KeyboardState.NUMBERS || action.newState == KeyboardState.SYMBOLS) {
                    commitTelexComposingIfEnabled()
                }
                nextState = state.copy(
                    surface = when (action.newState) {
                        KeyboardState.NUMBERS -> KeyboardSurface.NUMBERS
                        KeyboardState.SYMBOLS -> KeyboardSurface.SYMBOLS
                        else -> KeyboardSurface.ALPHABET
                    }
                )
            }
        }

        return KeyboardRuntimeDispatchResult(
            state = nextState,
            lastShiftTapTime = nextLastShiftTapTime
        )
    }

    private fun handleTelexKeyPress(key: String): Boolean {
        val processor = telexProcessor ?: return false
        if (!isTelexEnabled()) {
            return false
        }

        return if (TelexProcessor.isLatinLetterKey(key)) {
            processor.inputLetter(key)
        } else {
            false
        }
    }

    private fun commitTelexComposingIfEnabled(): Boolean {
        val processor = telexProcessor ?: return false
        if (!isTelexEnabled()) {
            return false
        }

        return processor.commitComposing()
    }

    private fun deleteTelexComposingIfEnabled(): Boolean {
        val processor = telexProcessor ?: return false
        if (!isTelexEnabled()) {
            return false
        }

        return processor.backspace()
    }
}
