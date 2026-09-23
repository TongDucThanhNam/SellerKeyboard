package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.util.Consumer
import androidx.emoji2.emojipicker.EmojiPickerView
import com.terasumi.sellerkeyboard.emoji.EmojiSearchIndex
import com.terasumi.sellerkeyboard.emoji.LocalRecentEmojiProvider

@Composable
fun EmojiKeyboard(
    isLandscape: Boolean,
    onEmoji: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var queryEditing by remember { mutableStateOf(false) }
    var pickerGeneration by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val recentProvider = remember(context) { LocalRecentEmojiProvider(context) }
    val searchResults = remember(query) { EmojiSearchIndex.search(query) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InImeTextField(
                value = query,
                label = "Search emoji",
                accessibilityLabel = "Emoji search",
                onClick = { queryEditing = true },
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                recentProvider.clear()
                pickerGeneration += 1
            }) {
                Text("Clear recents")
            }
        }

        if (queryEditing) {
            CompactInImeTextKeypad(
                value = query,
                onValueChange = { query = it },
                onDone = { queryEditing = false },
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        } else if (query.isNotBlank()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(if (isLandscape) 110.dp else 150.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(searchResults, key = { it }) { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 30.sp,
                        modifier = Modifier
                            .semantics { contentDescription = "$emoji emoji" }
                            .clickable {
                                recentProvider.recordSelection(emoji)
                                onEmoji(emoji)
                            }
                            .padding(10.dp)
                    )
                }
            }
        } else {
            key(pickerGeneration) {
                AndroidView(
                    factory = { context ->
                        EmojiPickerView(context).apply {
                            emojiGridColumns = if (isLandscape) 14 else 9
                            emojiGridRows = if (isLandscape) 2.5f else 3.5f
                            setRecentEmojiProvider(recentProvider)
                            setOnEmojiPickedListener(Consumer { item -> onEmoji(item.emoji) })
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isLandscape) 130.dp else 220.dp)
                )
            }
        }
    }
}
