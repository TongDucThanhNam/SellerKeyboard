package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.autofill.EditorCompletionItem
import com.terasumi.sellerkeyboard.keyboard.theme.KeyboardColors

@Composable
fun EditorCompletionBar(
    items: List<EditorCompletionItem>,
    myColor: KeyboardColors,
    isLandscape: Boolean,
    onCompletion: (EditorCompletionItem) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = EditorCompletionItem::stableId) { item ->
                Text(
                    text = item.label ?: item.text,
                    color = myColor.iconColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .semantics { contentDescription = "Editor completion ${item.text}" }
                        .clickable { onCompletion(item) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}
