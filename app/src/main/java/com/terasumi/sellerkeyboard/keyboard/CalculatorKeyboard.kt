package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.R
import com.terasumi.sellerkeyboard.service.SellerKeyboard
import com.terasumi.sellerkeyboard.ui.theme.DefaultAccent1
import com.terasumi.sellerkeyboard.ui.theme.DefaultAccent3
import net.objecthunter.exp4j.ExpressionBuilder

@Composable
fun CalculatorKeyboard() {
    Column(
        modifier = Modifier
    ) {
        val context = LocalContext.current
        val expression = remember { mutableStateOf("") }
        val result = remember { mutableStateOf("") }

        //stringResource
        val resources = context.resources
        val expressionText = remember {
            resources.getString(R.string.enter_expression)
        }

        val sendExpression = remember {
            resources.getString(R.string.send_expression)
        }

        val sendResult = remember {
            resources.getString(R.string.send_result)
        }


        //TextField
        OutlinedTextField(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = null
                )
            },
            readOnly = true,
            value = expression.value,
            onValueChange = { /*TODO*/ },
            label = { Text(text = expressionText) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        //Button Calculate and Send
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(3) {
                when (it) {
                    0 -> {
                        // Delete
                        Button(
                            onClick = {
                                // Input Method Service
                                val inputConnection =
                                    (context as SellerKeyboard).currentInputConnection
                                inputConnection?.deleteSurroundingText(1, 0)
                            },
                            colors = ButtonDefaults.buttonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            content = {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                                Text(text = "Delete")
                            },
                            modifier = Modifier
                        )
                    }

                    1 -> {
                        // Calculate
                        Button(
                            onClick = {
                                // Input Method Service
                                val inputConnection =
                                    (context as SellerKeyboard).currentInputConnection
                                inputConnection?.commitText(expression.value, 1)
                            },
                            colors = ButtonDefaults.buttonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                        ) {
                            Text(text = sendExpression)
                        }
                    }

                    2 -> {
                        // Send
                        Button(
                            onClick = {
                                // Input Method Service
                                val inputConnection =
                                    (context as SellerKeyboard).currentInputConnection
                                inputConnection?.commitText(result.value, 1)
                            },
                            colors = ButtonDefaults.buttonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                        ) {
                            Text(text = sendResult)
                        }
                    }
                }
            }
        }


        //Grid Horizontal 6x4
        // Combine rows into a single list of lists
        val calculatorButtons = listOf(
            listOf("(", "1", "2", "3", ")", "D"),
            listOf("-", "4", "5", "6", "+", "C"),
            listOf("/", "7", "8", "9", "*", "E"),
            listOf("√", ";", "0", ".", "^", "=")
        )

        //Color
        val materialColors = MaterialTheme.colorScheme

        val colorCalculatorButton = listOf(
            listOf(
                DefaultAccent1,
                materialColors.onPrimary,
                materialColors.onPrimary,
                materialColors.onPrimary,
                DefaultAccent1,
                materialColors.error
            ),
            listOf(
                DefaultAccent1,
                materialColors.onPrimary,
                materialColors.onPrimary,
                materialColors.onPrimary,
                DefaultAccent1,
                materialColors.tertiaryContainer
            ),
            listOf(
                DefaultAccent1,
                materialColors.onPrimary,
                materialColors.onPrimary,
                materialColors.onPrimary,
                DefaultAccent1,
                DefaultAccent1,
            ),
            listOf(
                DefaultAccent1,
                materialColors.onPrimary,
                materialColors.onPrimary,
                materialColors.onPrimary,
                DefaultAccent1,
                DefaultAccent3
            )
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = Modifier
        ) {
            calculatorButtons.forEach { row ->
                items(row.size) { index ->
                    val buttonText = row[index]
                    //Square button
                    Button(
                        onClick = {
                            /*TODO*/
                            handleCalculatorButton(buttonText, expression, result)
                        },
                        colors = ButtonDefaults.buttonColors(
                            contentColor = Color.Black,
                            containerColor = colorCalculatorButton[calculatorButtons.indexOf(row)][index]
                        ),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(3.dp)
                            .fillMaxWidth()
                    ) {
                        Text(text = buttonText, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

fun handleCalculatorButton(
    buttonText: String,
    expression: MutableState<String>,
    result: MutableState<String>
) {
//    Log.d("handleCalculatorButton", "buttonText: $buttonText, expression: ${expression.value}")
    when (buttonText) {
        "C" -> {
            // Clear
            expression.value = ""
        }

        "D" -> {
            // Delete
            if (expression.value.isNotEmpty()) {
                expression.value = expression.value.dropLast(1)
            }
        }

        "=" -> {
            // Calculate
            // Add calculation logic here
            try {
                val cal = ExpressionBuilder(expression.value).build().evaluate()
                val formattedResult = if (cal % 1 == 0.0) {
                    cal.toInt().toString() // Convert to integer if it's a whole number
                } else {
                    cal.toString() // Keep as double otherwise
                }
                expression.value += " = $formattedResult"
                result.value = formattedResult
            } catch (e: Exception) {
                expression.value = "Error"
            }
        }

        "E" -> {
            // Enter
            expression.value += "\n"
        }

        else -> {
            // Append
            expression.value += buttonText
        }
    }
}

// Calculate
@Preview()
@Composable
fun CalculateContentPreview() {
    CalculatorKeyboard()
}