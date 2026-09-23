package com.terasumi.sellerkeyboard.ime

import android.text.InputType
import android.text.TextUtils

/** Maps editor-declared capitalization flags to the modes queried from InputConnection. */
object EditorCapsModeRequest {
    fun fromInputType(inputType: Int): Int {
        var requestedModes = 0
        if (inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0) {
            requestedModes = requestedModes or TextUtils.CAP_MODE_CHARACTERS
        }
        if (inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS != 0) {
            requestedModes = requestedModes or TextUtils.CAP_MODE_WORDS
        }
        if (inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0) {
            requestedModes = requestedModes or TextUtils.CAP_MODE_SENTENCES
        }
        return requestedModes
    }
}
