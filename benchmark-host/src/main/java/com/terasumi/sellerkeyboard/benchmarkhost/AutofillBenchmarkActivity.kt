package com.terasumi.sellerkeyboard.benchmarkhost

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.security.MessageDigest

/** Distinct-UID login editor used only for deterministic platform autofill evidence. */
class AutofillBenchmarkActivity : Activity() {
    private lateinit var username: EditText
    private lateinit var password: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeInstance = this

        username = EditText(this).apply {
            id = R.id.autofill_username
            hint = "Username"
            setAutofillHints(View.AUTOFILL_HINT_USERNAME)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            minHeight = 56.dp
        }
        password = EditText(this).apply {
            id = R.id.autofill_password
            hint = "Password"
            setAutofillHints(View.AUTOFILL_HINT_PASSWORD)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            minHeight = 56.dp
        }
        val title = TextView(this).apply {
            text = "Deterministic inline autofill host"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24.dp, 32.dp, 24.dp, 24.dp)
                addView(title)
                addView(username)
                addView(password)
            }
        )
        username.requestFocus()
        username.post {
            getSystemService(InputMethodManager::class.java)?.showSoftInput(
                username,
                InputMethodManager.SHOW_IMPLICIT
            )
        }
    }

    override fun onDestroy() {
        if (activeInstance === this) activeInstance = null
        super.onDestroy()
    }

    internal fun snapshot(): AutofillEditorSnapshot = AutofillEditorSnapshot(
        usernameLength = username.text.length,
        usernameSha256 = sha256(username.text.toString()),
        passwordLength = password.text.length,
        passwordSha256 = sha256(password.text.toString()),
        usernameFocused = username.hasFocus(),
        passwordFocused = password.hasFocus()
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    internal companion object {
        @Volatile
        var activeInstance: AutofillBenchmarkActivity? = null
    }
}

internal data class AutofillEditorSnapshot(
    val usernameLength: Int,
    val usernameSha256: String,
    val passwordLength: Int,
    val passwordSha256: String,
    val usernameFocused: Boolean,
    val passwordFocused: Boolean
)
