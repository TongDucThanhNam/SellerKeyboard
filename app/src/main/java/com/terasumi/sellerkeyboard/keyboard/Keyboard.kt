package com.terasumi.sellerkeyboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.R
import com.terasumi.sellerkeyboard.service.SellerKeyboard
import com.terasumi.sellerkeyboard.ui.theme.DefaultAccent4

@SuppressLint("InvalidColorHexValue")
@Composable
fun Keyboard(keyboardArray: Array<Array<String>>, keyboardState: MutableState<KeyboardState>) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val ratio = remember {
        val screenWidthDp = configuration.screenWidthDp
        screenWidthDp / 360f
    }
    Box(
        content = {
            Column {
                keyboardArray.forEach {
                    RowKeycap(context, it, ratio, keyboardState)
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp)
            .background(DefaultAccent4)
    )
}

@Composable
fun RowKeycap(
    context: Context,
    keyList: Array<String>,
    ratio: Float,
    keyboardState: MutableState<KeyboardState>
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
                        FunctionKeyIcon(context, ratio, "shift", keyboardState)
                    }

                    "Shift" -> {
                        FunctionKeyIcon(context, ratio, "Shift", keyboardState)
                    }

                    "SHIFT" -> {
                        FunctionKeyIcon(context, ratio, "SHIFT", keyboardState)
                    }

                    "=\\<" -> {
                        FunctionKeyIcon(context, ratio, "SHIFT", keyboardState)
                    }

                    "delete" -> {
                        FunctionKeyIcon(context, ratio, "delete", keyboardState)
                    }

                    "?123" -> {
                        SpecialFunctionKey(ratio, action = "?123", keyboardState = keyboardState)
                    }

                    "?ABC" -> {
                        SpecialFunctionKey(ratio, action = "?ABC", keyboardState = keyboardState)
                    }

                    "enter" -> {
                        SpecialFunctionKeyIcon(context, ratio, "ENTER")
                    }

                    "space" -> {
                        Button(
                            content = {
                                Image(
                                    painter = painterResource(id = R.drawable.space_bar),
                                    contentDescription = "a",
                                    modifier = Modifier.size((24 * ratio).dp)
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
                                containerColor = Color.White, contentColor = Color.Black
                            ),
                            modifier = Modifier
                                .height((42 * ratio).dp)
                                .width((125 * ratio).dp)
                        )
                    }

                    else -> {
                        Keycap(context, key = it, ratio = ratio)
                    }
                }

            }
        }
    }
    Spacer(modifier = Modifier.size(5.5.dp))
}

@Preview
@Composable
fun KeyboardPreview() {
    val keyboardArray = arrayOf(
        arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        arrayOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        arrayOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        arrayOf("shift", "z", "x", "c", "v", "b", "n", "m", "delete"),
        arrayOf("?123", "@", ",", "space", ".", "enter")
    )
    Keyboard(
        keyboardArray = keyboardArray,
        keyboardState = remember { mutableStateOf(KeyboardState.NOCAPS) }
    )
}