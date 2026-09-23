package com.terasumi.sellerkeyboard.keyboard

import com.terasumi.sellerkeyboard.ime.EditorSession
import com.terasumi.sellerkeyboard.ime.EditorActionPresentation
import com.terasumi.sellerkeyboard.ime.InputFieldKind

enum class KeyboardKeyRole {
    CHARACTER,
    SHIFT,
    DELETE,
    SPACE,
    ENTER,
    LAYOUT_SWITCH
}

data class KeyboardKeyDefinition(
    val id: String,
    val label: String,
    val contentDescription: String,
    val role: KeyboardKeyRole,
    val action: KeyboardAction
)

data class KeyboardLayoutModel(
    val id: String,
    val rows: List<List<KeyboardKeyDefinition>>
)

object KeyboardLayoutResolver {
    fun resolve(
        session: EditorSession,
        state: KeyboardState,
        includeNumberRow: Boolean = true
    ): KeyboardLayoutModel {
        val labels = when {
            session.fieldKind == InputFieldKind.PHONE -> PHONE_LAYOUT
            session.fieldKind == InputFieldKind.INTEGER -> INTEGER_LAYOUT
            session.fieldKind == InputFieldKind.DECIMAL -> DECIMAL_LAYOUT
            session.fieldKind == InputFieldKind.SIGNED_NUMBER -> SIGNED_NUMBER_LAYOUT
            session.fieldKind == InputFieldKind.SIGNED_DECIMAL -> SIGNED_NUMBER_LAYOUT
            session.fieldKind == InputFieldKind.DATE_TIME -> DATE_TIME_LAYOUT
            state == KeyboardState.NUMBERS -> NUMBER_LAYOUT
            state == KeyboardState.SYMBOLS -> SYMBOL_LAYOUT
            session.fieldKind == InputFieldKind.EMAIL -> textLayout(state, EMAIL_BOTTOM_ROW, includeNumberRow)
            session.fieldKind == InputFieldKind.URI -> textLayout(state, URI_BOTTOM_ROW, includeNumberRow)
            else -> textLayout(state, DEFAULT_BOTTOM_ROW, includeNumberRow)
        }

        return KeyboardLayoutModel(
            id = "${session.fieldKind.name.lowercase()}-${state.name.lowercase()}",
            rows = labels.mapIndexed { rowIndex, row ->
                row.mapIndexed { columnIndex, label ->
                    definitionFor(
                        label = label,
                        row = rowIndex,
                        column = columnIndex,
                        enterDescription = EditorActionPresentation.contentDescription(session)
                    )
                }
            }
        )
    }

    private fun definitionFor(
        label: String,
        row: Int,
        column: Int,
        enterDescription: String
    ): KeyboardKeyDefinition {
        val stableId = "$row-$column-$label"
        return when (label) {
            "shift", "Shift", "SHIFT" -> KeyboardKeyDefinition(
                id = stableId,
                label = label,
                contentDescription = when (label) {
                    "SHIFT" -> "Caps Lock on"
                    "Shift" -> "Shift on"
                    else -> "Shift off"
                },
                role = KeyboardKeyRole.SHIFT,
                action = KeyboardAction.Shift
            )
            "delete" -> KeyboardKeyDefinition(
                id = stableId,
                label = label,
                contentDescription = "Delete",
                role = KeyboardKeyRole.DELETE,
                action = KeyboardAction.Delete
            )
            "space" -> KeyboardKeyDefinition(
                id = stableId,
                label = label,
                contentDescription = "Space",
                role = KeyboardKeyRole.SPACE,
                action = KeyboardAction.Space
            )
            "enter" -> KeyboardKeyDefinition(
                id = stableId,
                label = label,
                contentDescription = enterDescription,
                role = KeyboardKeyRole.ENTER,
                action = KeyboardAction.Enter
            )
            "?123", "123?" -> switchKey(stableId, label, KeyboardState.NUMBERS)
            "symbols" -> switchKey(stableId, label, KeyboardState.SYMBOLS)
            "numbers", "?ABC" -> switchKey(stableId, label, KeyboardState.NOCAPS)
            else -> KeyboardKeyDefinition(
                id = stableId,
                label = label,
                contentDescription = label,
                role = KeyboardKeyRole.CHARACTER,
                action = KeyboardAction.KeyPress(label)
            )
        }
    }

    private fun switchKey(id: String, label: String, target: KeyboardState) = KeyboardKeyDefinition(
        id = id,
        label = label,
        contentDescription = "Switch keyboard layout: $label",
        role = KeyboardKeyRole.LAYOUT_SWITCH,
        action = KeyboardAction.SwitchState(target)
    )

    private fun textLayout(
        state: KeyboardState,
        bottomRow: List<String>,
        includeNumberRow: Boolean
    ): List<List<String>> {
        val uppercase = state == KeyboardState.CAPS || state == KeyboardState.DOUBLECAPS
        val shiftLabel = when (state) {
            KeyboardState.CAPS -> "Shift"
            KeyboardState.DOUBLECAPS -> "SHIFT"
            else -> "shift"
        }
        fun row(value: String): List<String> = value.map { character ->
            if (uppercase) character.uppercase() else character.lowercase()
        }
        val rows = listOf(
            row("qwertyuiop"),
            row("asdfghjkl"),
            listOf(shiftLabel) + row("zxcvbnm") + "delete",
            bottomRow
        )
        return if (includeNumberRow) {
            listOf(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")) + rows
        } else {
            rows
        }
    }

    private val DEFAULT_BOTTOM_ROW = listOf("?123", "@", ",", "space", ".", "enter")
    private val EMAIL_BOTTOM_ROW = listOf("?123", "@", "space", ".", "enter")
    private val URI_BOTTOM_ROW = listOf("?123", "/", "space", ".", "enter")

    private val NUMBER_LAYOUT = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/"),
        listOf("symbols", "*", "\"", "'", ":", ";", "!", "?", "delete"),
        listOf("?ABC", ",", "space", ".", "enter")
    )
    private val SYMBOL_LAYOUT = listOf(
        listOf("~", "`", "|", "•", "√", "π", "÷", "×", "§", "∆"),
        listOf("£", "€", "$", "¢", "^", "°", "=", "{", "}", "\\"),
        listOf("123?", "%", "©", "®", "™", "✓", "[", "]", "delete"),
        listOf("?ABC", "<", "space", ">", "enter")
    )
    private val INTEGER_LAYOUT = listOf(
        listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"),
        listOf("delete", "0", "enter")
    )
    private val DECIMAL_LAYOUT = listOf(
        listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"),
        listOf("delete", "0", ".", "enter")
    )
    private val SIGNED_NUMBER_LAYOUT = listOf(
        listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"),
        listOf("-", "0", ".", "delete", "enter")
    )
    private val PHONE_LAYOUT = listOf(
        listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"),
        listOf("+", "0", "#", "delete", "enter")
    )
    private val DATE_TIME_LAYOUT = listOf(
        listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"),
        listOf("/", "0", ":", "delete", "enter")
    )
}
