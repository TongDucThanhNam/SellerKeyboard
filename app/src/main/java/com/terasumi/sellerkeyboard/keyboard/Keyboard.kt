package com.terasumi.sellerkeyboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.R
import com.terasumi.sellerkeyboard.service.SellerKeyboard
import com.terasumi.sellerkeyboard.ui.theme.LightCustomColor

@SuppressLint("InvalidColorHexValue")
@Composable
fun Keyboard(
    keyboardArray: Array<Array<String>>,
    keyboardState: MutableState<KeyboardState>,
    myColor: Array<Color>,
    context: Context,
    isLandscape: Boolean,
    ratio: Float
) {
    Box(
        content = {
            if (isLandscape) {
                //reduce height of keyboard
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Column {
                        //skip 1st row (numbers) if keyboard has 5 rows
                        if (keyboardArray.size == 5) {
                            keyboardArray.drop(1).forEach {
                                RowKeycapLandscape(context, it, ratio, keyboardState, myColor)
                            }
                        } else {
                            keyboardArray.forEach {
                                RowKeycapLandscape(context, it, ratio, keyboardState, myColor)
                            }
                        }
                    }
                }

            } else {
                Column {
                    keyboardArray.forEach {
                        RowKeycap(context, it, ratio, keyboardState, myColor)
                    }
                }
            }
        },
        modifier = Modifier
            .padding(6.dp)
            .background(myColor[3])
    )
}

@Composable
fun RowKeycap(
    context: Context,
    keyList: Array<String>,
    ratio: Float,
    keyboardState: MutableState<KeyboardState>,
    myColor: Array<Color>
) {

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
        ) {
            keyList.forEach {
                when (it) {
                    "shift" -> {
                        FunctionKeyIcon(context, ratio, "shift", keyboardState, myColor)
                    }

                    "Shift" -> {
                        FunctionKeyIcon(context, ratio, "Shift", keyboardState, myColor)
                    }

                    "SHIFT" -> {
                        FunctionKeyIcon(context, ratio, "SHIFT", keyboardState, myColor)
                    }

                    "symbols" -> {
                        FunctionKeyIcon(context, ratio, "symbols", keyboardState, myColor)
                    }

                    "delete" -> {
                        FunctionKeyIcon(context, ratio, "delete", keyboardState, myColor)
                    }

                    "?123" -> {
                        SpecialFunctionKey(
                            ratio,
                            action = "?123",
                            keyboardState = keyboardState,
                            myColor
                        )
                    }

                    "123?" -> {
                        FunctionKeyIcon(
                            context = context,
                            action = "123?",
                            keyboardState = keyboardState,
                            ratio = ratio,
                            myColor = myColor,
                        )
                    }

                    "?ABC" -> {
                        SpecialFunctionKey(
                            ratio,
                            action = "?ABC",
                            keyboardState = keyboardState,
                            myColor
                        )
                    }

                    "enter" -> {
                        SpecialFunctionKeyIcon(context, ratio, "ENTER", myColor)
                    }

                    "numbers" -> {
                        FunctionKey(
                            context = context,
                            key = "numbers",
                            ratio = ratio,
                            myColor = myColor
                        )
                    }

                    "space" -> {
                        Button(
                            content = {
                                Icon(
                                    imageVector = ImageVector.vectorResource(id = R.drawable.space_bar),
                                    contentDescription = "Spacer",
                                    tint = myColor[5],
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            contentPadding = PaddingValues(
                                horizontal = 0.dp, vertical = 0.dp
                            ), // Custom content padding
                            onClick = {
                                (context as SellerKeyboard).currentInputConnection.commitText(
                                    " ",
                                    1
                                )
                            },
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = myColor[4],
                                contentColor = myColor[5]
                            ),
                            modifier = Modifier
                                .height((42 * ratio).dp)
                                .width((125 * ratio).dp)
                        )
                    }

                    else -> {
                        Keycap(context, key = it, ratio = ratio, myColor = myColor)
                    }
                }

            }
        }
    }
    Spacer(modifier = Modifier.size(5.5.dp))
}


@Composable
fun RowKeycapLandscape(
    context: Context,
    keyList: Array<String>,
    ratio: Float,
    keyboardState: MutableState<KeyboardState>,
    myColor: Array<Color>
) {
    val isLandscape = true

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
        ) {
            keyList.forEach {
                when (it) {
                    "shift" -> {
                        FunctionKeyIcon(
                            context,
                            ratio,
                            "shift",
                            keyboardState,
                            myColor,
                            isLandscape
                        )
                    }

                    "Shift" -> {
                        FunctionKeyIcon(
                            context,
                            ratio,
                            "Shift",
                            keyboardState,
                            myColor,
                            isLandscape
                        )
                    }

                    "SHIFT" -> {
                        FunctionKeyIcon(
                            context,
                            ratio,
                            "SHIFT",
                            keyboardState,
                            myColor,
                            isLandscape
                        )
                    }

                    "symbols" -> {
                        FunctionKeyIcon(
                            context,
                            ratio,
                            "symbols",
                            keyboardState,
                            myColor,
                            isLandscape
                        )
                    }

                    "delete" -> {
                        FunctionKeyIcon(
                            context,
                            ratio,
                            "delete",
                            keyboardState,
                            myColor,
                            isLandscape
                        )
                    }

                    "?123" -> {
                        SpecialFunctionKey(
                            ratio,
                            action = "?123",
                            keyboardState = keyboardState,
                            myColor,
                            isLandscape
                        )
                    }

                    "123?" -> {
                        FunctionKeyIcon(
                            context = context,
                            action = "123?",
                            keyboardState = keyboardState,
                            ratio = ratio,
                            myColor = myColor,
                            isLandscape = isLandscape
                        )
                    }

                    "?ABC" -> {
                        SpecialFunctionKey(
                            ratio,
                            action = "?ABC",
                            keyboardState = keyboardState,
                            myColor,
                            isLandscape
                        )
                    }

                    "enter" -> {
                        SpecialFunctionKeyIcon(context, ratio, "ENTER", myColor, isLandscape)
                    }

                    "numbers" -> {
//                        FunctionKey(
//                            context = context,
//                            key = "numbers",
//                            ratio = ratio,
//                            myColor = myColor,
//                            isLandscape = isLandscape
//                        )
                        FunctionKeyIcon(
                            context,
                            ratio,
                            "numbers",
                            keyboardState,
                            myColor,
                            isLandscape
                        )
                    }

                    "space" -> {
                        Button(
                            content = {
                                Icon(
                                    imageVector = ImageVector.vectorResource(id = R.drawable.space_bar),
                                    contentDescription = "Spacer",
                                    tint = myColor[5],
                                    modifier =
                                    if (isLandscape)
                                        Modifier.size(24.dp)
                                    else
                                        Modifier.size((24 * ratio).dp),
                                )
                            },
                            contentPadding = PaddingValues(
                                horizontal = 0.dp, vertical = 0.dp
                            ), // Custom content padding
                            onClick = {
                                (context as SellerKeyboard).currentInputConnection.commitText(
                                    " ",
                                    1
                                )
                            },
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = myColor[4],
                                contentColor = myColor[5]
                            ),
                            modifier =
                            if (isLandscape) Modifier
                                .height((35).dp)
                                .width((125 * ratio).dp)
                            else Modifier
                                .height((42).dp)
                                .width((125 * ratio).dp)
                        )
                    }

                    else -> {
                        Keycap(context, key = it, ratio = ratio, myColor = myColor, isLandscape)
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.size(6.dp))
}

@Preview
@Composable
fun KeyboardPreview() {
    val keyboardArray = arrayOf(
        arrayOf("~", "`", "|", "•", "√", "π", "÷", "×", "§", "∆"),
        arrayOf("£", "€", "$", "¢", "^", "°", "=", "{", "}", "\\"),
        arrayOf("123?", "%", "©", "®", "™", "✓", "[", "]", "delete"),
        arrayOf("?ABC", "<", "space", ">", "enter")
    )
    val myColor = LightCustomColor
    val context = LocalContext.current

    Keyboard(
        keyboardArray = keyboardArray,
        keyboardState = remember { mutableStateOf(KeyboardState.NOCAPS) },
        myColor = myColor,
        context,
        false,
        1.0f
    )
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=480")
@Composable
fun KeyboardLandscapePreview() {
    val keyboardArray = arrayOf(
        arrayOf("~", "`", "|", "•", "√", "π", "÷", "×", "§", "∆"),
        arrayOf("£", "€", "$", "¢", "^", "°", "=", "{", "}", "\\"),
        arrayOf("123?", "%", "©", "®", "™", "✓", "[", "]", "delete"),
        arrayOf("?ABC", "<", "space", ">", "enter")
    )
    val myColor = LightCustomColor
    val context = LocalContext.current;

    Keyboard(
        keyboardArray = keyboardArray,
        keyboardState = remember { mutableStateOf(KeyboardState.NOCAPS) },
        myColor = myColor,
        context,
        true,
        1280 / 360f
    )
}