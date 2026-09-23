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
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.keyboard.theme.KeyboardColors
import com.terasumi.sellerkeyboard.seller.SellerReplyTemplate

@Composable
fun SellerShortcutBar(
    templates: List<SellerReplyTemplate>,
    myColor: KeyboardColors,
    isLandscape: Boolean,
    onTemplate: (SellerReplyTemplate) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(templates, key = SellerReplyTemplate::id) { template ->
                Text(
                    text = "${template.shortcut} · ${template.title}",
                    color = myColor.iconColor,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        .clickable { onTemplate(template) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
