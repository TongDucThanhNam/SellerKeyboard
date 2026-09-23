package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Text entry hosted by the IME cannot rely on another software keyboard. This
 * field and keypad keep editing local while the target editor connection stays
 * active, so reply search and template variables remain usable on touch devices.
 */
@Composable
internal fun InImeTextField(
    value: String,
    label: String,
    accessibilityLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(56.dp)
            .semantics {
                contentDescription = accessibilityLabel
                stateDescription = value.ifBlank { "Empty" }
            }
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value.ifBlank { "Tap to enter text" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (value.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun CompactInImeTextKeypad(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    var numericMode by remember { mutableStateOf(false) }
    val rows = if (numericMode) NUMERIC_KEYPAD_ROWS else ALPHABETIC_KEYPAD_ROWS
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                row.forEach { key ->
                    CompactEntryButton(
                        label = key,
                        accessibilityLabel = "Text entry key $key",
                        onClick = { onValueChange(value + key) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            CompactEntryButton(
                label = if (numericMode) "ABC" else "123",
                accessibilityLabel = if (numericMode) {
                    "Show alphabetic text entry"
                } else {
                    "Show numeric text entry"
                },
                onClick = { numericMode = !numericMode },
                modifier = Modifier.weight(1f)
            )
            CompactEntryButton(
                label = "Space",
                accessibilityLabel = "Text entry space",
                onClick = { onValueChange(value + " ") },
                modifier = Modifier.weight(1.7f)
            )
            CompactEntryButton(
                label = "⌫",
                accessibilityLabel = "Text entry backspace",
                onClick = { if (value.isNotEmpty()) onValueChange(value.dropLast(1)) },
                modifier = Modifier.weight(1f)
            )
            CompactEntryButton(
                label = "Clear",
                accessibilityLabel = "Clear text entry",
                onClick = { onValueChange("") },
                modifier = Modifier.weight(1.2f)
            )
            CompactEntryButton(
                label = "Done",
                accessibilityLabel = "Finish text entry",
                onClick = onDone,
                modifier = Modifier.weight(1.2f)
            )
        }
    }
}

@Composable
private fun CompactEntryButton(
    label: String,
    accessibilityLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .semantics { contentDescription = accessibilityLabel },
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

private val ALPHABETIC_KEYPAD_ROWS = listOf(
    "qwertyuiop".map(Char::toString),
    "asdfghjkl".map(Char::toString),
    listOf("z", "x", "c", "v", "b", "n", "m", "-", ".", "/")
)

private val NUMERIC_KEYPAD_ROWS = listOf(
    "1234567890".map(Char::toString)
)
