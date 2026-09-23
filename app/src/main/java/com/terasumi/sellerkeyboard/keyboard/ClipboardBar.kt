package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.clipboard.ClipboardItem
import com.terasumi.sellerkeyboard.clipboard.ClipboardItemType
import com.terasumi.sellerkeyboard.clipboard.ClipboardEntityParser
import com.terasumi.sellerkeyboard.clipboard.ClipboardEntityType
import com.terasumi.sellerkeyboard.keyboard.theme.KeyboardColors

@Composable
fun ClipboardBar(
    items: List<ClipboardItem>,
    myColor: KeyboardColors,
    isLandscape: Boolean,
    onCommitText: (ClipboardItem, String) -> Unit,
    onTogglePin: (ClipboardItem) -> Unit,
    onDeleteItem: (ClipboardItem) -> Unit,
    onClear: () -> Unit
) {
    if (items.isEmpty()) {
        return
    }

    val parser = remember { ClipboardEntityParser() }
    val displayEntries = remember(items) {
        items.flatMap { item ->
            listOf(ClipboardDisplayEntry(item, item.text, item.type.shortLabel(), true)) +
                parser.parse(item.text).map { entity ->
                    ClipboardDisplayEntry(
                        item = item,
                        text = entity.value,
                        label = entity.type.shortLabel(),
                        isWholeItem = false
                    )
                }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(myColor.keyboardBackground.copy(alpha = 0.98f))
            .padding(horizontal = if (isLandscape) 6.dp else 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        IconButton(
            onClick = onClear,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "Clear clipboard history",
                tint = myColor.iconColor.copy(alpha = 0.72f),
                modifier = Modifier.size(if (isLandscape) 18.dp else 20.dp)
            )
        }

        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(
                displayEntries,
                key = { entry -> "${entry.item.id}:${entry.label}:${entry.text}" }
            ) { entry ->
                ClipboardChip(
                    item = entry.item,
                    displayText = entry.text,
                    typeLabel = entry.label,
                    showControls = entry.isWholeItem,
                    myColor = myColor,
                    isLandscape = isLandscape,
                    onCommit = { onCommitText(entry.item, entry.text) },
                    onTogglePin = { onTogglePin(entry.item) },
                    onDelete = { onDeleteItem(entry.item) }
                )
            }
        }
    }
}

@Composable
private fun ClipboardChip(
    item: ClipboardItem,
    displayText: String,
    typeLabel: String,
    showControls: Boolean,
    myColor: KeyboardColors,
    isLandscape: Boolean,
    onCommit: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(48.dp)
            .widthIn(min = 112.dp, max = if (isLandscape) 190.dp else 230.dp)
            .background(
                color = if (item.pinned) {
                    myColor.keyActionBackground.copy(alpha = 0.3f)
                } else {
                    myColor.spaceBarBackground.copy(alpha = 0.24f)
                },
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onCommit)
            .padding(start = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (item.sensitive) {
                    "•••• " + displayText.takeLast(4)
                } else {
                    displayText
                },
                color = myColor.iconColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = typeLabel,
                color = myColor.iconColor.copy(alpha = 0.58f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall
            )
        }

        if (showControls) {
            IconButton(
                onClick = onTogglePin,
                modifier = Modifier.size(if (isLandscape) 28.dp else 30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = if (item.pinned) "Unpin clipboard item" else "Pin clipboard item",
                    tint = myColor.iconColor.copy(alpha = if (item.pinned) 0.95f else 0.48f),
                    modifier = Modifier.size(if (isLandscape) 15.dp else 17.dp)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(if (isLandscape) 28.dp else 30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete clipboard item",
                    tint = myColor.iconColor.copy(alpha = 0.56f),
                    modifier = Modifier.size(if (isLandscape) 15.dp else 17.dp)
                )
            }
        }
    }
}

private fun ClipboardItemType.shortLabel(): String {
    return when (this) {
        ClipboardItemType.PhoneNumber -> "Phone"
        ClipboardItemType.Url -> "URL"
        ClipboardItemType.Price -> "Price"
        ClipboardItemType.TrackingCode -> "Code"
        ClipboardItemType.PlainText -> "Text"
    }
}

private data class ClipboardDisplayEntry(
    val item: ClipboardItem,
    val text: String,
    val label: String,
    val isWholeItem: Boolean
)

private fun ClipboardEntityType.shortLabel(): String = name
    .lowercase()
    .replace('_', ' ')
    .replaceFirstChar(Char::uppercase)
