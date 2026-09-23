package com.terasumi.sellerkeyboard.vietnamese

import com.terasumi.sellerkeyboard.ime.InputConnectionOps

class TelexProcessor(
    private val inputConnectionOps: InputConnectionOps,
    private val engine: TelexEngine = TelexEngine()
) {
    var rawSyllable: String = ""
        private set

    var composingText: String = ""
        private set

    val isComposing: Boolean
        get() = rawSyllable.isNotEmpty()

    fun inputLetter(key: String): Boolean {
        if (!key.isSingleLatinLetter()) {
            return false
        }

        rawSyllable += key
        composingText = engine.transform(rawSyllable)
        return inputConnectionOps.setComposingText(composingText)
    }

    fun backspace(): Boolean {
        if (rawSyllable.isEmpty()) {
            return false
        }

        rawSyllable = rawSyllable.dropLast(1)
        composingText = engine.transform(rawSyllable)

        return if (rawSyllable.isEmpty()) {
            inputConnectionOps.clearComposingText()
        } else {
            inputConnectionOps.setComposingText(composingText)
        }
    }

    fun commitComposing(): Boolean {
        if (rawSyllable.isEmpty()) {
            return false
        }

        rawSyllable = ""
        composingText = ""
        return inputConnectionOps.finishComposingText()
    }

    fun resetComposing() {
        rawSyllable = ""
        composingText = ""
    }

    companion object {
        fun isLatinLetterKey(key: String): Boolean {
            return key.isSingleLatinLetter()
        }
    }
}

private fun String.isSingleLatinLetter(): Boolean {
    if (length != 1) {
        return false
    }

    val char = this[0]
    return char in 'a'..'z' || char in 'A'..'Z'
}
