package com.terasumi.sellerkeyboard.benchmarkhost

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.tracing.Trace

/** Neutral editor host used to measure installed IMEs without force-stopping them. */
class ImeBenchmarkActivity : Activity() {
    private var imeShowTraceOpen = false
    private lateinit var editor: RichContentBenchmarkEditor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeInstance = this
        beginImeShowTrace()
        editor = RichContentBenchmarkEditor(this)
        setContentView(editor)

        val decorView = window.decorView
        ViewCompat.setOnApplyWindowInsetsListener(decorView) { _, insets ->
            if (insets.isVisible(WindowInsetsCompat.Type.ime())) {
                finishImeShowTrace()
            }
            insets
        }

        applyMode(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyMode(intent)
    }

    override fun onDestroy() {
        if (activeInstance === this) activeInstance = null
        super.onDestroy()
    }

    internal fun seedTranslationSelection() {
        editor.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        editor.setText(TRANSLATION_TEST_ORIGINAL)
        editor.setSelection(0, editor.text.length)
        restartEditorSession()
    }

    internal fun seedCoreFixture(name: String) {
        val fixture = when (name) {
            CORE_FIXTURE_EMPTY_SENTENCE -> EditorTextSnapshot("", 0, 0)
            CORE_FIXTURE_PUNCTUATION_SENTENCE -> {
                EditorTextSnapshot(CORE_PUNCTUATION_TEXT, CORE_PUNCTUATION_TEXT.length,
                    CORE_PUNCTUATION_TEXT.length)
            }
            CORE_FIXTURE_CURSOR_MIDDLE -> EditorTextSnapshot(
                CORE_CURSOR_TEXT,
                CORE_CURSOR_MIDDLE_POSITION,
                CORE_CURSOR_MIDDLE_POSITION
            )
            CORE_FIXTURE_CURSOR_END -> EditorTextSnapshot(
                CORE_CURSOR_TEXT,
                CORE_CURSOR_TEXT.length,
                CORE_CURSOR_TEXT.length
            )
            CORE_FIXTURE_DELETE_REPEAT -> EditorTextSnapshot(
                CORE_DELETE_REPEAT_TEXT,
                CORE_DELETE_REPEAT_TEXT.length,
                CORE_DELETE_REPEAT_TEXT.length
            )
            else -> throw IllegalArgumentException("Unknown core fixture: $name")
        }
        editor.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        editor.setText(fixture.text)
        editor.setSelection(fixture.selectionStart, fixture.selectionEnd)
        restartEditorSession()
    }

    internal fun seedSellerBenchmarkEmpty() {
        editor.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE
        editor.setText("")
        editor.setSelection(0)
        restartEditorSession()
    }

    private fun restartEditorSession() {
        getSystemService(InputMethodManager::class.java)?.restartInput(editor)
        editor.post {
            getSystemService(InputMethodManager::class.java)?.showSoftInput(
                editor,
                InputMethodManager.SHOW_IMPLICIT
            )
        }
    }

    internal fun editorTextSnapshot(): EditorTextSnapshot = EditorTextSnapshot(
        text = editor.text.toString(),
        selectionStart = editor.selectionStart,
        selectionEnd = editor.selectionEnd
    )

    private fun applyMode(intent: Intent) {
        editor.mode = RichContentTargetMode.fromWireValue(intent.getStringExtra(EXTRA_MODE))
        editor.setText("")
        editor.hint = if (intent.hasExtra(EXTRA_MODE)) {
            "Rich content ${editor.mode.wireValue} editor"
        } else {
            "IME benchmark editor"
        }
        editor.requestFocus()
        val inputMethodManager = getSystemService(InputMethodManager::class.java)
        inputMethodManager?.restartInput(editor)
        editor.post {
            inputMethodManager?.showSoftInput(
                editor,
                InputMethodManager.SHOW_IMPLICIT
            )
            ViewCompat.requestApplyInsets(window.decorView)
        }
    }

    private fun beginImeShowTrace() {
        if (!imeShowTraceOpen) {
            Trace.beginSection(IME_SHOW_TRACE_SECTION)
            imeShowTraceOpen = true
        }
    }

    private fun finishImeShowTrace() {
        if (imeShowTraceOpen) {
            Trace.endSection()
            imeShowTraceOpen = false
        }
    }

    internal companion object {
        const val EXTRA_MODE = "mode"
        const val IME_SHOW_TRACE_SECTION = "SellerKeyboard.ImeShowToInteractive"
        const val TRANSLATION_TEST_ORIGINAL = "Thank you for your order"
        const val CORE_FIXTURE_EMPTY_SENTENCE = "empty_sentence"
        const val CORE_FIXTURE_PUNCTUATION_SENTENCE = "punctuation_sentence"
        const val CORE_FIXTURE_CURSOR_MIDDLE = "cursor_middle"
        const val CORE_FIXTURE_CURSOR_END = "cursor_end"
        const val CORE_FIXTURE_DELETE_REPEAT = "delete_repeat"
        private const val CORE_PUNCTUATION_TEXT = "done. "
        private const val CORE_CURSOR_TEXT = "abcdef"
        private const val CORE_DELETE_REPEAT_TEXT = "abcdefghijklmnopqrst"
        private const val CORE_CURSOR_MIDDLE_POSITION = 3
        @Volatile
        var activeInstance: ImeBenchmarkActivity? = null
    }
}

internal data class EditorTextSnapshot(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int
)

private enum class RichContentTargetMode(val wireValue: String, val advertisesJpeg: Boolean) {
    ACCEPT("accept", true),
    REJECT("reject", true),
    UNSUPPORTED("unsupported", false);

    companion object {
        fun fromWireValue(value: String?): RichContentTargetMode = entries.firstOrNull {
            it.wireValue == value
        } ?: UNSUPPORTED
    }
}

private class RichContentBenchmarkEditor(context: Context) : EditText(context) {
    var mode: RichContentTargetMode = RichContentTargetMode.UNSUPPORTED

    init {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        imeOptions = EditorInfo.IME_ACTION_NONE
        minLines = 4
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        outAttrs.contentMimeTypes = if (mode.advertisesJpeg) {
            arrayOf(TEST_MIME_TYPE)
        } else {
            emptyArray()
        }
        return object : InputConnectionWrapper(base, false) {
            override fun commitContent(
                inputContentInfo: InputContentInfo,
                flags: Int,
                opts: Bundle?
            ): Boolean {
                if (mode != RichContentTargetMode.ACCEPT ||
                    !inputContentInfo.description.hasMimeType(TEST_MIME_TYPE)
                ) {
                    return false
                }
                val permissionRequested = flags and
                    InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION != 0
                return try {
                    if (permissionRequested) inputContentInfo.requestPermission()
                    val jpegReadable = context.contentResolver
                        .openInputStream(inputContentInfo.contentUri)
                        ?.use { input ->
                            input.read() == JPEG_START_BYTE_1 && input.read() == JPEG_START_BYTE_2
                        } == true
                    if (!jpegReadable) return false
                    post {
                        setText(RICH_ACCEPTED_MARKER)
                        setSelection(text.length)
                    }
                    true
                } catch (_: SecurityException) {
                    false
                } finally {
                    if (permissionRequested) runCatching { inputContentInfo.releasePermission() }
                }
            }
        }
    }

    private companion object {
        const val TEST_MIME_TYPE = "image/jpeg"
        const val RICH_ACCEPTED_MARKER = "RICH_ACCEPTED:image/jpeg"
        const val JPEG_START_BYTE_1 = 0xFF
        const val JPEG_START_BYTE_2 = 0xD8
    }
}
