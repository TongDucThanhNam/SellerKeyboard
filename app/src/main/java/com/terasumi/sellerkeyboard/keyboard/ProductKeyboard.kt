package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.seller.ProductReply
import com.terasumi.sellerkeyboard.seller.ProductReplyComposer
import com.terasumi.sellerkeyboard.seller.ProductImageSourceIssue
import com.terasumi.sellerkeyboard.seller.SellerSearchNormalizer
import com.terasumi.sellerkeyboard.seller.SharedPreferencesProductReplyStore

@Composable
fun ProductKeyboard(
    isLandscape: Boolean,
    canCommitImage: Boolean,
    onInsertText: (String) -> Unit,
    onCommitImageWithFallback: (imageUrl: String, fallbackText: String) -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { SharedPreferencesProductReplyStore(context) }
    val products = remember { store.list() }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<ProductReply?>(null) }
    val visible = remember(products, query) {
        val needle = SellerSearchNormalizer.normalize(query)
        products.filter { needle.isEmpty() || needle in SellerSearchNormalizer.normalize(it.name) }
    }

    val product = selected
    if (product != null) {
        val draft = ProductReplyComposer.compose(product, System.currentTimeMillis())
        Column(
            modifier = Modifier.height(if (isLandscape) 190.dp else 300.dp).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(product.name, fontWeight = FontWeight.SemiBold)
            if (draft?.isStale == true) Text("Review this product: its saved data is over 30 days old.")
            when (draft?.imageSourceIssue) {
                ProductImageSourceIssue.AUTHORIZATION_REQUIRED ->
                    Text("Image omitted: sharing authorization has not been confirmed.")
                ProductImageSourceIssue.HTTPS_REQUIRED,
                ProductImageSourceIssue.HOST_REQUIRED,
                ProductImageSourceIssue.EMBEDDED_CREDENTIALS_NOT_ALLOWED ->
                    Text("Image omitted: replace it with an authorized HTTPS image URL.")
                null -> Unit
            }
            Text(draft?.textFallback.orEmpty(), maxLines = 6, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { selected = null }) { Text("Back") }
                Button(
                    enabled = draft != null,
                    onClick = { draft?.let { onInsertText(it.textFallback) } }
                ) { Text("Insert text") }
                if (draft?.imageUrl != null && canCommitImage) {
                    Button(
                        onClick = {
                            onCommitImageWithFallback(draft.imageUrl, draft.textFallback)
                        }
                    ) { Text("Image + fallback") }
                } else if (draft?.imageUrl != null) {
                    Text("This app accepts text/link fallback only.")
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier.height(if (isLandscape) 190.dp else 300.dp).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search products") }
        )
        if (visible.isEmpty()) Text("Add product replies in the SellerKeyboard app.")
        LazyColumn {
            items(visible, key = ProductReply::id) { item ->
                Text(
                    text = item.name,
                    modifier = Modifier.fillMaxWidth().clickable { selected = item }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
