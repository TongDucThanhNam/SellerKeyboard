package com.terasumi.sellerkeyboard.keyboard

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terasumi.sellerkeyboard.R
import com.terasumi.sellerkeyboard.service.SellerKeyboard
import com.terasumi.sellerkeyboard.ui.theme.LightCustomColor
import net.objecthunter.exp4j.ExpressionBuilder

@Composable
fun CalculatorKeyboard(
    myColor: Array<Color>,
    context: Context,
    isLandscape: Boolean,
) {
    Column(
        modifier = Modifier
    ) {
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

        //get light or dark mode

        if (isLandscape) {
            //TODO
            LandscapeCalculatorKeyboard(
                context = context,
                myColor = myColor,
                expression = expression,
                result = result,
                sendExpression = sendExpression,
                sendResult = sendResult,
                expressionText = expressionText
            )
            return
        }

        //TextField
        OutlinedTextField(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = null
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = myColor[5],
                unfocusedTextColor = myColor[5],
            ),
            readOnly = true,
            textStyle = MaterialTheme.typography.titleMedium,
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
            items(2) {
                when (it) {
                    0 -> {
                        // Calculate
                        Button(
                            onClick = {
                                // Input Method Service
                                val inputConnection =
                                    (context as SellerKeyboard).currentInputConnection
                                inputConnection?.commitText(expression.value, 1)
                            },
                            colors = ButtonDefaults.buttonColors(
                                contentColor = myColor[5],
                                containerColor = myColor[4]
                            ),
                            modifier = Modifier
                        ) {
                            Text(text = sendExpression)
                        }
                    }

                    1 -> {
                        // Send
                        Button(
                            onClick = {
                                // Input Method Service
                                val inputConnection =
                                    (context as SellerKeyboard).currentInputConnection
                                inputConnection?.commitText(result.value, 1)
                            },
                            colors = ButtonDefaults.buttonColors(
                                contentColor = myColor[5],
                                containerColor = myColor[4]
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
                myColor[0],
                myColor[4],
                myColor[4],
                myColor[4],
                myColor[0],
                materialColors.error
            ),
            listOf(
                myColor[0],
                myColor[4],
                myColor[4],
                myColor[4],
                myColor[0],
                materialColors.tertiaryContainer
            ),
            listOf(
                myColor[0],
                myColor[4],
                myColor[4],
                myColor[4],
                myColor[0],
                myColor[0],
            ),
            listOf(
                myColor[0],
                myColor[4],
                myColor[4],
                myColor[4],
                myColor[0],
                myColor[2]
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
                        when (buttonText) {
                            "D" -> {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Backspace",
                                    tint = myColor[5],
                                    modifier = Modifier.size(40.dp)

                                )
                            }

                            "E" -> {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                                    contentDescription = "Enter",
                                    tint = myColor[5],
                                    modifier = Modifier.size(40.dp)

                                )
                            }

                            "C" -> {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = myColor[5],
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            else -> {
                                Text(
                                    text = buttonText,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = myColor[5]
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LandscapeCalculatorKeyboard(
    context: Context,
    myColor: Array<Color>,
    expression: MutableState<String>,
    result: MutableState<String>,
    sendExpression: String,
    sendResult: String,
    expressionText: String
) {
    //Grid Horizontal 6x4
    // Combine rows into a single list of lists
    val calculatorButtonsLandscape = listOf(
//        listOf("(", "1", "2", "3", ")", "D", "-", "4", "5", "6", "+", "C"),
//        listOf("/", "7", "8", "9", "*", "E", "√", ";", "0", ".", "^", "=")
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("+", "-", "*", "/", "(", ")", "^", "√", ";", "."),
        listOf("E", "=", "D", "C"),
    )

    //Color
    val materialColors = MaterialTheme.colorScheme

    val colorCalculatorButtonLandscape = listOf(
        listOf(
            myColor[4],
            myColor[4],
            myColor[4],
            myColor[4],
            myColor[4],
            myColor[4],
            myColor[4],
            myColor[4],
            myColor[4],
            myColor[4],
        ),
        listOf(
            myColor[0],
            myColor[0],
            myColor[0],
            myColor[0],
            myColor[0],
            myColor[0],
            myColor[0],
            myColor[0],
            myColor[0],
            myColor[0],
        ),
        listOf(
            myColor[0],
            myColor[0],
            materialColors.error,
            materialColors.tertiaryContainer
        )
    )

    Box(
        modifier = Modifier
            .height(164.dp)
            .padding(6.dp)
    ) {
        Row() {
            Column {
                //TextField
                OutlinedTextField(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = null
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = myColor[5],
                        unfocusedTextColor = myColor[5],
                    ),
                    readOnly = true,
                    textStyle = MaterialTheme.typography.titleMedium,
                    value = expression.value,
                    onValueChange = { /*TODO*/ },
                    label = { Text(text = expressionText) },
                    modifier = Modifier
                        .padding(16.dp)
                )
                //Button Calculate and Send
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(2) {
                        when (it) {
                            0 -> {
                                // Calculate
                                Button(
                                    onClick = {
                                        // Input Method Service
                                        val inputConnection =
                                            (context as SellerKeyboard).currentInputConnection
                                        inputConnection?.commitText(expression.value, 1)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        contentColor = myColor[5],
                                        containerColor = myColor[4]
                                    ),
                                    modifier = Modifier
                                ) {
                                    Text(text = sendExpression)
                                }
                            }

                            1 -> {
                                // Send
                                Button(
                                    onClick = {
                                        // Input Method Service
                                        val inputConnection =
                                            (context as SellerKeyboard).currentInputConnection
                                        inputConnection?.commitText(result.value, 1)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        contentColor = myColor[5],
                                        containerColor = myColor[4]
                                    ),
                                    modifier = Modifier
                                ) {
                                    Text(text = sendResult)
                                }
                            }
                        }
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(10),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxSize()
            ) {
                calculatorButtonsLandscape.forEach { row ->
                    items(row.size) { index ->
                        val buttonText = row[index]
                        //Square button
                        Button(
                            onClick = {
                                handleCalculatorButton(buttonText, expression, result)
                            },
                            colors = ButtonDefaults.buttonColors(
                                contentColor = Color.Black,
                                containerColor = colorCalculatorButtonLandscape[calculatorButtonsLandscape.indexOf(
                                    row
                                )][index]
                            ),
                            shape = MaterialTheme.shapes.small,
                            contentPadding = PaddingValues(
                                horizontal = 6.dp,
                                vertical = 6.dp
                            ), // Custom content padding
                            modifier = Modifier
                                .fillMaxSize()


                        ) {
                            when (buttonText) {
                                "D" -> {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "Backspace",
                                        tint = myColor[5],
                                        modifier =
                                        Modifier
                                            .size(24.dp)
                                            .align(Alignment.CenterVertically)
                                    )
                                }

                                "E" -> {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                                        contentDescription = "Enter",
                                        tint = myColor[5],
                                        modifier =
                                        Modifier
                                            .size(24.dp)
                                            .align(Alignment.CenterVertically)

                                    )
                                }

                                "C" -> {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = myColor[5],
                                        modifier =
                                        Modifier
                                            .size(24.dp)
                                            .align(Alignment.CenterVertically)
                                            .align(Alignment.CenterVertically)

                                    )
                                }

                                else -> {
                                    Text(
                                        text = buttonText,
                                        fontSize = 24.sp,
                                        color = myColor[5]
                                    )
                                }
                            }
                        }
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
    val myColor = LightCustomColor
    val context = LocalContext.current
    CalculatorKeyboard(myColor, context, false)
}

@Preview(showSystemUi = true, device = "spec:width=2400px,height=1080px")
@Composable
fun CalculateContentLandscapePreview() {
    val myColor = LightCustomColor
    val context = LocalContext.current
    CalculatorKeyboard(myColor, context, true)
}