package com.terasumi.sellerkeyboard.benchmark

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.tracing.Trace

class ImeBenchmarkActivity : Activity() {
    lateinit var editor: EditText
        private set
    private var imeShowTraceOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editor = EditText(this).apply {
            hint = "IME benchmark editor"
            isSingleLine = false
            minLines = 4
        }
        applyEditorConfiguration(intent)
        setContentView(editor)
        val decorView = window.decorView
        ViewCompat.setOnApplyWindowInsetsListener(decorView) { _, insets ->
            if (insets.isVisible(WindowInsetsCompat.Type.ime())) {
                finishImeShowTrace()
            }
            insets
        }
        editor.requestFocus()
        beginImeShowTrace()
        editor.post {
            getSystemService(InputMethodManager::class.java)?.showSoftInput(
                editor,
                InputMethodManager.SHOW_IMPLICIT
            )
            ViewCompat.requestApplyInsets(decorView)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyEditorConfiguration(intent)
        getSystemService(InputMethodManager::class.java)?.restartInput(editor)
        editor.post {
            getSystemService(InputMethodManager::class.java)?.showSoftInput(
                editor,
                InputMethodManager.SHOW_IMPLICIT
            )
        }
    }

    private fun applyEditorConfiguration(intent: Intent) {
        configureEditorField(
            inputType = intent.getIntExtra(
            EXTRA_INPUT_TYPE,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            ),
            imeOptions = intent.getIntExtra(EXTRA_IME_OPTIONS, EditorInfo.IME_ACTION_DONE)
        )
        editor.hint = intent.getStringExtra(EXTRA_HINT_TEXT) ?: DEFAULT_HINT
        editor.privateImeOptions = intent.getStringExtra(EXTRA_PRIVATE_IME_OPTIONS)
    }

    fun configureEditor(inputType: Int, imeOptions: Int) {
        configureEditorField(inputType, imeOptions)
        getSystemService(InputMethodManager::class.java)?.restartInput(editor)
    }

    private fun configureEditorField(inputType: Int, imeOptions: Int) {
        val isMultiline = inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        val textVariation = inputType and InputType.TYPE_MASK_VARIATION
        val isMaskedPassword = inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
            textVariation in MASKED_PASSWORD_VARIATIONS
        editor.inputType = inputType
        editor.isSingleLine = !isMultiline
        editor.minLines = if (isMultiline) 4 else 1
        editor.transformationMethod = if (isMaskedPassword) {
            PasswordTransformationMethod.getInstance()
        } else {
            null
        }
        editor.imeOptions = imeOptions
        editor.setText("")
        editor.requestFocus()
    }

    private fun beginImeShowTrace() {
        if (!imeShowTraceOpen) {
            // Both calls run on the activity main thread. A synchronous section is
            // intentionally used because Macrobenchmark's TraceSectionMetric reads
            // thread slices, while an async section is not reported by that metric.
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

    companion object {
        const val IME_SHOW_TRACE_SECTION = "SellerKeyboard.ImeShowToInteractive"
        const val EXTRA_INPUT_TYPE = "inputType"
        const val EXTRA_IME_OPTIONS = "imeOptions"
        const val EXTRA_HINT_TEXT = "hintText"
        const val EXTRA_PRIVATE_IME_OPTIONS = "privateImeOptions"
        private const val DEFAULT_HINT = "IME benchmark editor"
        private val MASKED_PASSWORD_VARIATIONS = setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
    }
}
