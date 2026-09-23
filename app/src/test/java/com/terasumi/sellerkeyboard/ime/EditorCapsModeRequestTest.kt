package com.terasumi.sellerkeyboard.ime

import android.text.InputType
import android.text.TextUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorCapsModeRequestTest {
    @Test
    fun plainText_requestsNoAutomaticCapitalization() {
        assertEquals(
            0,
            EditorCapsModeRequest.fromInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            )
        )
    }

    @Test
    fun characterCapitalization_requestsOnlyCharacters() {
        assertEquals(
            TextUtils.CAP_MODE_CHARACTERS,
            EditorCapsModeRequest.fromInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            )
        )
    }

    @Test
    fun wordCapitalization_requestsOnlyWords() {
        assertEquals(
            TextUtils.CAP_MODE_WORDS,
            EditorCapsModeRequest.fromInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            )
        )
    }

    @Test
    fun sentenceCapitalization_requestsOnlySentences() {
        assertEquals(
            TextUtils.CAP_MODE_SENTENCES,
            EditorCapsModeRequest.fromInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            )
        )
    }
}
