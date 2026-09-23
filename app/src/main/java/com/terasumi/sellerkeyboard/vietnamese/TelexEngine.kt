package com.terasumi.sellerkeyboard.vietnamese

class TelexEngine {
    fun transform(rawSyllable: String): String {
        if (rawSyllable.isEmpty()) {
            return ""
        }

        val letters = mutableListOf<Char>()
        var tone: Tone? = null
        var lastToneCommand: Char? = null

        rawSyllable.forEach { char ->
            val lower = char.lowercaseChar()
            val toneCommand = Tone.fromCommand(lower)

            when {
                lower == REMOVE_TONE_KEY -> {
                    if (tone != null) {
                        tone = null
                        lastToneCommand = null
                    } else {
                        letters += char
                    }
                }

                toneCommand != null && letters.any(::isVietnameseVowel) -> {
                    if (lastToneCommand?.lowercaseChar() == lower && tone == toneCommand) {
                        tone = null
                        letters += char
                        lastToneCommand = null
                    } else {
                        tone = toneCommand
                        lastToneCommand = char
                    }
                }

                tryApplyShapeCommand(letters, char) -> {
                    lastToneCommand = null
                }

                else -> {
                    letters += char
                    lastToneCommand = null
                }
            }
        }

        return applyTone(letters, tone).joinToString(separator = "")
    }

    private fun tryApplyShapeCommand(letters: MutableList<Char>, command: Char): Boolean {
        if (letters.isEmpty()) {
            return false
        }

        val lastIndex = letters.lastIndex
        val previous = letters[lastIndex]
        val previousLower = previous.lowercaseChar()
        val commandLower = command.lowercaseChar()
        val replacement = when {
            previousLower == 'a' && commandLower == 'a' -> 'â'
            previousLower == 'a' && commandLower == 'w' -> 'ă'
            previousLower == 'e' && commandLower == 'e' -> 'ê'
            previousLower == 'o' && commandLower == 'o' -> 'ô'
            previousLower == 'o' && commandLower == 'w' -> 'ơ'
            previousLower == 'u' && commandLower == 'w' -> 'ư'
            previousLower == 'd' && commandLower == 'd' -> 'đ'
            else -> null
        } ?: return false

        letters[lastIndex] = replacement.matchCase(previous)
        return true
    }

    private fun applyTone(letters: List<Char>, tone: Tone?): List<Char> {
        if (tone == null) {
            return letters
        }

        val toneIndex = resolveToneIndex(letters) ?: return letters
        return letters.mapIndexed { index, char ->
            if (index == toneIndex) {
                tone.applyTo(char)
            } else {
                char
            }
        }
    }

    private fun resolveToneIndex(letters: List<Char>): Int? {
        val vowelGroups = mutableListOf<List<Int>>()
        var currentGroup = mutableListOf<Int>()

        letters.forEachIndexed { index, char ->
            if (isVietnameseVowel(char)) {
                currentGroup += index
            } else if (currentGroup.isNotEmpty()) {
                vowelGroups += currentGroup
                currentGroup = mutableListOf()
            }
        }
        if (currentGroup.isNotEmpty()) {
            vowelGroups += currentGroup
        }

        val group = vowelGroups.lastOrNull() ?: return null
        return group.firstOrNull { letters[it].lowercaseChar() == 'ơ' }
            ?: group.firstOrNull { letters[it].lowercaseChar() == 'ê' }
            ?: group.firstOrNull { letters[it].lowercaseChar() == 'ô' }
            ?: group.firstOrNull {
                val lower = letters[it].lowercaseChar()
                lower == 'â' || lower == 'ă'
            }
            ?: resolvePlainVowelToneIndex(group, letters)
    }

    private fun resolvePlainVowelToneIndex(group: List<Int>, letters: List<Char>): Int {
        if (group.size == 1) {
            return group.first()
        }

        if (group.size == 2) {
            val first = letters[group[0]].lowercaseChar()
            val second = letters[group[1]].lowercaseChar()
            if (first == 'u' && second == 'a' && !isQuConsonantCluster(group, letters)) {
                return group[0]
            }
            return if (second == 'i' || second == 'y' || second == 'u' || second == 'o') {
                group[0]
            } else {
                group[1]
            }
        }

        return group[1]
    }

    private fun isQuConsonantCluster(group: List<Int>, letters: List<Char>): Boolean {
        val firstVowelIndex = group.firstOrNull() ?: return false
        if (firstVowelIndex == 0) {
            return false
        }

        return letters[firstVowelIndex - 1].lowercaseChar() == 'q' &&
            letters[firstVowelIndex].lowercaseChar() == 'u'
    }

    private enum class Tone(
        private val command: Char,
        private val replacements: Map<Char, Char>
    ) {
        Acute(
            command = 's',
            replacements = mapOf(
                'a' to 'á',
                'ă' to 'ắ',
                'â' to 'ấ',
                'e' to 'é',
                'ê' to 'ế',
                'i' to 'í',
                'o' to 'ó',
                'ô' to 'ố',
                'ơ' to 'ớ',
                'u' to 'ú',
                'ư' to 'ứ',
                'y' to 'ý'
            )
        ),
        Grave(
            command = 'f',
            replacements = mapOf(
                'a' to 'à',
                'ă' to 'ằ',
                'â' to 'ầ',
                'e' to 'è',
                'ê' to 'ề',
                'i' to 'ì',
                'o' to 'ò',
                'ô' to 'ồ',
                'ơ' to 'ờ',
                'u' to 'ù',
                'ư' to 'ừ',
                'y' to 'ỳ'
            )
        ),
        Hook(
            command = 'r',
            replacements = mapOf(
                'a' to 'ả',
                'ă' to 'ẳ',
                'â' to 'ẩ',
                'e' to 'ẻ',
                'ê' to 'ể',
                'i' to 'ỉ',
                'o' to 'ỏ',
                'ô' to 'ổ',
                'ơ' to 'ở',
                'u' to 'ủ',
                'ư' to 'ử',
                'y' to 'ỷ'
            )
        ),
        Tilde(
            command = 'x',
            replacements = mapOf(
                'a' to 'ã',
                'ă' to 'ẵ',
                'â' to 'ẫ',
                'e' to 'ẽ',
                'ê' to 'ễ',
                'i' to 'ĩ',
                'o' to 'õ',
                'ô' to 'ỗ',
                'ơ' to 'ỡ',
                'u' to 'ũ',
                'ư' to 'ữ',
                'y' to 'ỹ'
            )
        ),
        Dot(
            command = 'j',
            replacements = mapOf(
                'a' to 'ạ',
                'ă' to 'ặ',
                'â' to 'ậ',
                'e' to 'ẹ',
                'ê' to 'ệ',
                'i' to 'ị',
                'o' to 'ọ',
                'ô' to 'ộ',
                'ơ' to 'ợ',
                'u' to 'ụ',
                'ư' to 'ự',
                'y' to 'ỵ'
            )
        );

        fun applyTo(char: Char): Char {
            val replacement = replacements[char.lowercaseChar()] ?: return char
            return replacement.matchCase(char)
        }

        companion object {
            fun fromCommand(command: Char): Tone? {
                return entries.firstOrNull { tone -> tone.command == command }
            }
        }
    }

    private companion object {
        private const val REMOVE_TONE_KEY = 'z'

        private val VietnameseVowels = setOf(
            'a',
            'ă',
            'â',
            'e',
            'ê',
            'i',
            'o',
            'ô',
            'ơ',
            'u',
            'ư',
            'y'
        )

        private fun isVietnameseVowel(char: Char): Boolean {
            return char.lowercaseChar() in VietnameseVowels
        }

        private fun Char.matchCase(source: Char): Char {
            return if (source.isUpperCase()) {
                uppercaseChar()
            } else {
                this
            }
        }
    }
}
