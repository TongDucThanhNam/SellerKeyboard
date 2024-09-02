package com.terasumi.sellerkeyboard.keyboard

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terasumi.sellerkeyboard.ui.theme.LightCustomColor

@Composable
fun SpecialFunctionKey(
    ratio: Float = 1.0f, action: String, keyboardState: MutableState<KeyboardState>,
    myColor: Array<Color>
) {
    //Number
    Button(
        colors = ButtonDefaults.buttonColors(
            contentColor = Color(0xFF000000),
            containerColor = myColor[1]
        ),
        onClick = { /*TODO*/
            when (action) {
                "?123" -> {
                    //TODO
                    keyboardState.value = KeyboardState.NUMBERS
                }

                "?ABC" -> {
                    //TODO
                    keyboardState.value = KeyboardState.NOCAPS
                }
            }
        },
        contentPadding = PaddingValues(
            horizontal = 6.dp,
            vertical = 6.dp
        ), // Custom content padding
        modifier = Modifier
            .height((42 * ratio).dp)
    ) {
        Text(
            text = action,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = (16 * ratio).sp,
            fontStyle = FontStyle.Normal,
            fontWeight = FontWeight.Normal,
            color = myColor[5],
            modifier = Modifier
        )
    }
}

@SuppressLint("UnrememberedMutableState")
@Preview
@Composable
fun SpecialFunctionKeyPreview() {
    SpecialFunctionKey(1.0f, "?123", mutableStateOf(KeyboardState.NOCAPS), LightCustomColor)
}