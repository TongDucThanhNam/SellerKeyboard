package com.terasumi.sellerkeyboard.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.seller.ProductReply
import com.terasumi.sellerkeyboard.seller.ProductImageSourceIssue
import com.terasumi.sellerkeyboard.seller.ProductImageSourcePolicy
import com.terasumi.sellerkeyboard.seller.PromotionBlock
import java.math.BigDecimal
import java.util.UUID

@Composable
fun ProductManagementCard(
    products: List<ProductReply>,
    onSave: (ProductReply) -> Unit,
    onDelete: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var imageSourceAuthorized by remember { mutableStateOf(false) }
    var discountCode by remember { mutableStateOf("") }
    var validityNote by remember { mutableStateOf("") }
    var promotionTerms by remember { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Product replies")
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Product name") })
            OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Description") })
            OutlinedTextField(price, { price = it }, Modifier.fillMaxWidth(), label = { Text("Price (VND)") })
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Product URL") })
            val imageDecision = ProductImageSourcePolicy.evaluate(
                imageUrl,
                imageSourceAuthorized
            )
            OutlinedTextField(
                value = imageUrl,
                onValueChange = {
                    imageUrl = it
                    if (it.isBlank()) imageSourceAuthorized = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("HTTPS image URL (optional)") },
                isError = imageUrl.isNotBlank() && imageDecision.issue != null &&
                    imageDecision.issue != ProductImageSourceIssue.AUTHORIZATION_REQUIRED,
                supportingText = when (imageDecision.issue) {
                    ProductImageSourceIssue.HTTPS_REQUIRED -> {
                        { Text("Use a complete HTTPS image URL.") }
                    }
                    ProductImageSourceIssue.HOST_REQUIRED -> {
                        { Text("The image URL must include a host name.") }
                    }
                    ProductImageSourceIssue.EMBEDDED_CREDENTIALS_NOT_ALLOWED -> {
                        { Text("Image URLs cannot contain embedded credentials.") }
                    }
                    else -> null
                }
            )
            if (imageUrl.isNotBlank()) {
                Row {
                    Checkbox(
                        checked = imageSourceAuthorized,
                        onCheckedChange = { imageSourceAuthorized = it }
                    )
                    Text(
                        "I own this image or am authorized to share it.",
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            OutlinedTextField(discountCode, { discountCode = it }, Modifier.fillMaxWidth(), label = { Text("Promotion code (optional)") })
            OutlinedTextField(validityNote, { validityNote = it }, Modifier.fillMaxWidth(), label = { Text("Promotion validity") })
            OutlinedTextField(promotionTerms, { promotionTerms = it }, Modifier.fillMaxWidth(), label = { Text("Promotion terms") })
            Button(
                enabled = name.isNotBlank() && price.toBigDecimalOrNull() != null &&
                    imageDecision.issue == null,
                onClick = {
                    onSave(
                        ProductReply(
                            id = UUID.randomUUID().toString(),
                            name = name.trim(),
                            description = description.trim(),
                            price = price.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                            productUrl = url.trim().takeIf(String::isNotEmpty),
                            imageUrl = imageDecision.normalizedUrl,
                            imageSourceAuthorized = imageDecision.normalizedUrl != null,
                            promotion = discountCode.trim().takeIf(String::isNotEmpty)?.let { code ->
                                PromotionBlock(
                                    discountCode = code,
                                    validityNote = validityNote.trim(),
                                    terms = promotionTerms.trim()
                                )
                            },
                            updatedAtMillis = System.currentTimeMillis()
                        )
                    )
                    name = ""
                    description = ""
                    price = ""
                    url = ""
                    imageUrl = ""
                    imageSourceAuthorized = false
                    discountCode = ""
                    validityNote = ""
                    promotionTerms = ""
                }
            ) { Text("Save product") }
            products.forEach { product ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(product.name)
                    TextButton(onClick = { onDelete(product.id) }) { Text("Delete") }
                }
            }
        }
    }
}
