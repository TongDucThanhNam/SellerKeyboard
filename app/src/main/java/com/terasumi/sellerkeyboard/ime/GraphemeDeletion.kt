package com.terasumi.sellerkeyboard.ime

/** Small, deterministic backward-grapheme scanner for IME deletion. */
object GraphemeDeletion {
    fun previousClusterUtf16Length(text: CharSequence): Int {
        if (text.isEmpty()) return 0

        var start = previousCodePointStart(text, text.length)
        var codePoint = Character.codePointAt(text, start)

        if (isRegionalIndicator(codePoint) && start > 0) {
            val previousStart = previousCodePointStart(text, start)
            if (isRegionalIndicator(Character.codePointAt(text, previousStart))) {
                start = previousStart
            }
            return text.length - start
        }

        while (start > 0 && isExtending(codePoint)) {
            start = previousCodePointStart(text, start)
            codePoint = Character.codePointAt(text, start)
        }

        while (start > 0) {
            val joinerStart = previousCodePointStart(text, start)
            if (Character.codePointAt(text, joinerStart) != ZERO_WIDTH_JOINER) break
            start = joinerStart
            if (start == 0) break
            start = previousCodePointStart(text, start)
            codePoint = Character.codePointAt(text, start)
            while (start > 0 && isExtending(codePoint)) {
                start = previousCodePointStart(text, start)
                codePoint = Character.codePointAt(text, start)
            }
        }

        return text.length - start
    }

    private fun previousCodePointStart(text: CharSequence, exclusiveEnd: Int): Int {
        val last = exclusiveEnd - 1
        return if (last > 0 && Character.isLowSurrogate(text[last]) &&
            Character.isHighSurrogate(text[last - 1])) {
            last - 1
        } else {
            last
        }
    }

    private fun isExtending(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        return type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt() ||
            codePoint in VARIATION_SELECTOR_START..VARIATION_SELECTOR_END ||
            codePoint in SUPPLEMENTARY_VARIATION_SELECTOR_START..SUPPLEMENTARY_VARIATION_SELECTOR_END ||
            codePoint in EMOJI_MODIFIER_START..EMOJI_MODIFIER_END ||
            codePoint in TAG_CHARACTER_START..TAG_CHARACTER_END ||
            codePoint == COMBINING_KEYCAP
    }

    private fun isRegionalIndicator(codePoint: Int): Boolean =
        codePoint in REGIONAL_INDICATOR_START..REGIONAL_INDICATOR_END

    private const val ZERO_WIDTH_JOINER = 0x200D
    private const val COMBINING_KEYCAP = 0x20E3
    private const val VARIATION_SELECTOR_START = 0xFE00
    private const val VARIATION_SELECTOR_END = 0xFE0F
    private const val SUPPLEMENTARY_VARIATION_SELECTOR_START = 0xE0100
    private const val SUPPLEMENTARY_VARIATION_SELECTOR_END = 0xE01EF
    private const val EMOJI_MODIFIER_START = 0x1F3FB
    private const val EMOJI_MODIFIER_END = 0x1F3FF
    private const val REGIONAL_INDICATOR_START = 0x1F1E6
    private const val REGIONAL_INDICATOR_END = 0x1F1FF
    private const val TAG_CHARACTER_START = 0xE0020
    private const val TAG_CHARACTER_END = 0xE007F
}
