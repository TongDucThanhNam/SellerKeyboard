package com.terasumi.sellerkeyboard

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.objecthunter.exp4j.ExpressionBuilder

@Composable
fun CalculateContent() {
    Column(
//        modifier = Modifier.fillMaxSize()
    ) {
        val context = LocalContext.current
        val expression = remember { mutableStateOf("") }
        val result = remember { mutableStateOf("") }


        //TextField
        TextField(
            value = expression.value,
            onValueChange = { /*TODO*/ },
            modifier = Modifier
                .fillMaxWidth()
//                .padding(16.dp)
        )

        //Button Calculate and Send
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            //Calculate
            Button(
                onClick = {
                    //Input Method Service
                    val inputConnection = (context as SellerKeyboard).currentInputConnection
                    inputConnection?.commitText(expression.value, 1)
                },
                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(16.dp)
            ) {
                Text(text = "Send Expression")
            }

            //Send
            Button(
                onClick = {
                    //Input Method Service
                    val inputConnection = (context as SellerKeyboard).currentInputConnection
                    inputConnection?.commitText(result.value, 1)
                },
                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(16.dp)
            ) {
                Text(text = "Send Result")
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
//            listOf(Color.Cyan, Color.DarkGray, Color.DarkGray, Color.DarkGray, Color.Cyan, Color.Magenta),
//            listOf(Color.Yellow, Color.DarkGray, Color.DarkGray, Color.DarkGray, Color.Yellow, Color.Red),
//            listOf(Color.Yellow, Color.DarkGray, Color.DarkGray, Color.DarkGray, Color.Yellow, Color.Gray),
//            listOf(Color.Yellow, Color.Yellow, Color.DarkGray, Color.DarkGray, Color.Yellow, Color.Blue)
            listOf(
                materialColors.secondary,
                materialColors.onPrimary,
                materialColors.onPrimary,
                materialColors.onPrimary,
                materialColors.secondary,
                materialColors.error
            ),
            listOf(
                materialColors.secondary,
                materialColors.onPrimary,
                materialColors.onPrimary,
                materialColors.onPrimary,
                materialColors.secondary,
                materialColors.tertiaryContainer
            ),
            listOf(
                materialColors.secondary,
                materialColors.onPrimary,
                materialColors.onPrimary,
                materialColors.onPrimary,
                materialColors.secondary,
                materialColors.secondary
            ),
            listOf(
                materialColors.secondary,
                materialColors.onPrimary,
                materialColors.onPrimary,
                materialColors.onPrimary,
                materialColors.secondary,
                materialColors.primary
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
                    OutlinedButton(
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
    Log.d("handleCalculatorButton", "buttonText: $buttonText, expression: ${expression.value}")
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
    CalculateContent()
}