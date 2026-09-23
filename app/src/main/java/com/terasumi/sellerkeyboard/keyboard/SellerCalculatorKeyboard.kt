package com.terasumi.sellerkeyboard.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.seller.SellerCalculatorEngine
import java.math.BigDecimal

private enum class SellerCalculation(
    val label: String,
    val first: String,
    val second: String,
    val third: String? = null,
    val fourth: String? = null
) {
    DISCOUNT("Discount", "Price", "Discount %"),
    MARKUP("Markup", "Cost", "Markup %"),
    MARGIN("Margin", "Cost", "Target margin %"),
    PLATFORM_FEE("Platform fee", "Gross", "Fee %"),
    SHIPPING("Shipping/item", "Shipping total", "Quantity"),
    QUANTITY("Quantity total", "Unit price", "Quantity"),
    PROFIT("Profit estimate", "Revenue", "Product cost", "Platform fee", "Shipping cost")
}

private enum class SellerInputField { FIRST, SECOND, THIRD, FOURTH }

@Composable
fun CalculatorHub(
    myColor: com.terasumi.sellerkeyboard.keyboard.theme.KeyboardColors,
    isLandscape: Boolean,
    onAction: (KeyboardAction) -> Unit
) {
    var sellerMode by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = !sellerMode, onClick = { sellerMode = false }, label = { Text("General") })
            FilterChip(selected = sellerMode, onClick = { sellerMode = true }, label = { Text("Seller") })
        }
        if (sellerMode) {
            SellerCalculatorKeyboard(onInsert = { onAction(KeyboardAction.KeyPress(it)) })
        } else {
            CalculatorKeyboard(myColor = myColor, isLandscape = isLandscape, onAction = onAction)
        }
    }
}

@Composable
private fun SellerCalculatorKeyboard(onInsert: (String) -> Unit) {
    var calculation by remember { mutableStateOf(SellerCalculation.DISCOUNT) }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var third by remember { mutableStateOf("") }
    var fourth by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var activeField by remember { mutableStateOf<SellerInputField?>(null) }
    val context = LocalContext.current
    val clipboardManager = remember(context) {
        context.getSystemService(ClipboardManager::class.java)
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(SellerCalculation.entries, key = SellerCalculation::name) { item ->
                FilterChip(
                    selected = calculation == item,
                    onClick = { calculation = item; result = null },
                    label = { Text(item.label) }
                )
            }
        }
        val editing = activeField
        if (editing != null) {
            val value = when (editing) {
                SellerInputField.FIRST -> first
                SellerInputField.SECOND -> second
                SellerInputField.THIRD -> third
                SellerInputField.FOURTH -> fourth
            }
            val label = when (editing) {
                SellerInputField.FIRST -> calculation.first
                SellerInputField.SECOND -> calculation.second
                SellerInputField.THIRD -> calculation.third.orEmpty()
                SellerInputField.FOURTH -> calculation.fourth.orEmpty()
            }
            InImeTextField(
                value = value,
                label = label,
                accessibilityLabel = "Seller calculator $label",
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            )
            CompactInImeTextKeypad(
                value = value,
                onValueChange = { next ->
                    when (editing) {
                        SellerInputField.FIRST -> first = next
                        SellerInputField.SECOND -> second = next
                        SellerInputField.THIRD -> third = next
                        SellerInputField.FOURTH -> fourth = next
                    }
                    result = null
                },
                onDone = { activeField = null }
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InImeTextField(
                    value = first,
                    label = calculation.first,
                    accessibilityLabel = "Seller calculator ${calculation.first}",
                    onClick = { activeField = SellerInputField.FIRST },
                    modifier = Modifier.weight(1f)
                )
                InImeTextField(
                    value = second,
                    label = calculation.second,
                    accessibilityLabel = "Seller calculator ${calculation.second}",
                    onClick = { activeField = SellerInputField.SECOND },
                    modifier = Modifier.weight(1f)
                )
            }
            if (calculation.third != null && calculation.fourth != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InImeTextField(
                        value = third,
                        label = calculation.third.orEmpty(),
                        accessibilityLabel = "Seller calculator ${calculation.third.orEmpty()}",
                        onClick = { activeField = SellerInputField.THIRD },
                        modifier = Modifier.weight(1f)
                    )
                    InImeTextField(
                        value = fourth,
                        label = calculation.fourth.orEmpty(),
                        accessibilityLabel = "Seller calculator ${calculation.fourth.orEmpty()}",
                        onClick = { activeField = SellerInputField.FOURTH },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        result = calculate(calculation, first, second, third, fourth)
                            ?.let(SellerCalculatorEngine::formatVnd)
                    },
                    enabled = listOfNotNull(
                        first,
                        second,
                        calculation.third?.let { third },
                        calculation.fourth?.let { fourth }
                    ).all { it.toBigDecimalOrNull()?.let { value -> value >= BigDecimal.ZERO } == true }
                ) { Text("Calculate") }
                result?.let { output ->
                    Button(
                        onClick = {
                            clipboardManager?.setPrimaryClip(
                                ClipData.newPlainText("Seller calculation", output)
                            )
                        }
                    ) {
                        Text("Copy")
                    }
                    Button(onClick = { onInsert(output) }) { Text("Insert $output") }
                }
            }
            Text("Estimates only; not accounting or tax advice.")
        }
    }
}

private fun calculate(
    calculation: SellerCalculation,
    first: String,
    second: String,
    third: String = "",
    fourth: String = ""
): BigDecimal? {
    val a = first.toBigDecimalOrNull() ?: return null
    val b = second.toBigDecimalOrNull() ?: return null
    if (a < BigDecimal.ZERO || b < BigDecimal.ZERO) return null
    return when (calculation) {
        SellerCalculation.DISCOUNT -> SellerCalculatorEngine.discount(a, b)
        SellerCalculation.MARKUP -> SellerCalculatorEngine.markup(a, b)
        SellerCalculation.MARGIN -> SellerCalculatorEngine.priceForMargin(a, b)
        SellerCalculation.PLATFORM_FEE -> SellerCalculatorEngine.platformFee(a, b)
        SellerCalculation.SHIPPING -> SellerCalculatorEngine.shippingPerItem(a, b.toInt())
        SellerCalculation.QUANTITY -> SellerCalculatorEngine.quantityTotal(b.toInt(), a)
        SellerCalculation.PROFIT -> {
            val c = third.toBigDecimalOrNull() ?: return null
            val d = fourth.toBigDecimalOrNull() ?: return null
            if (c < BigDecimal.ZERO || d < BigDecimal.ZERO) return null
            SellerCalculatorEngine.profitEstimate(a, b, c, d)
        }
    }
}
