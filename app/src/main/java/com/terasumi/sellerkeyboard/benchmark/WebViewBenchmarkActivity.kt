package com.terasumi.sellerkeyboard.benchmark

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.inputmethod.InputMethodManager

/**
 * Local-only contenteditable host used by connected IME compatibility tests.
 * It is registered only by debug/benchmark manifests and never loads a URL.
 */
class WebViewBenchmarkActivity : Activity() {
    lateinit var editor: WebView
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editor = WebView(this).apply {
            settings.javaScriptEnabled = true
            isFocusable = true
            isFocusableInTouchMode = true
            webViewClient = WebViewClient()
            loadDataWithBaseURL(
                null,
                HTML,
                "text/html",
                "UTF-8",
                null
            )
        }
        setContentView(editor)
    }

    override fun onDestroy() {
        editor.destroy()
        super.onDestroy()
    }

    fun focusEditor(callback: (String) -> Unit) {
        editor.requestFocus()
        editor.evaluateJavascript(
            "document.getElementById('editor').focus(); document.activeElement.id",
        ) { value ->
            getSystemService(InputMethodManager::class.java)?.let { manager ->
                manager.restartInput(editor)
                manager.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
            }
            callback(value)
        }
    }

    fun readEditorText(callback: (String) -> Unit) {
        editor.evaluateJavascript(
            "document.getElementById('editor').textContent",
            callback
        )
    }

    private companion object {
        const val HTML = """
            <!doctype html>
            <html>
              <body>
                <div id="editor" contenteditable="true" role="textbox"
                     aria-label="Seller Web editor"></div>
              </body>
            </html>
        """
    }
}
