package com.terasumi.sellerkeyboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
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

@Composable
fun FunctionKeyIcon(
    context: Context,
    ratio: Float = 1.0f,
    action: String,
    keyboardState: MutableState<KeyboardState>,
    myColor: Array<Color>
) {
    //Map
    val map = mapOf(
        "shift" to R.drawable.arrow,
        "Shift" to R.drawable.arrow_up,
        "SHIFT" to R.drawable.arrow_up,
        "delete" to R.drawable.back,
        "symbols" to R.drawable.symbols,
        "numbers" to R.drawable.numbers,
        "123?" to R.drawable._23
    )


    Button(
        content = {
            Icon(
                imageVector = ImageVector.vectorResource(id = map[action]!!),
                contentDescription = action,
                modifier = Modifier.size((24 * ratio).dp),
                tint = myColor[5]
            )
        },
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = myColor[0],
            contentColor = Color(0xFF000000)
        ),
        contentPadding = PaddingValues(
            horizontal = 0.dp, vertical = 0.dp
        ), // Custom content padding
        onClick = {
            when (action) {
                "shift" -> {
                    //TODO
                    keyboardState.value = KeyboardState.CAPS
                }

                "Shift" -> {
                    //TODO
                    keyboardState.value = KeyboardState.NOCAPS
                }

                "SHIFT" -> {
                    //TODO
                    keyboardState.value = KeyboardState.CAPS
                }

                "delete" -> {
                    //TODO
                    (context as SellerKeyboard).currentInputConnection.deleteSurroundingText(1, 0)
                }

                "symbols" -> {
                    //TODO
                    keyboardState.value = KeyboardState.SYMBOLS
                }

                "123?" -> {
                    //TODO
                    keyboardState.value = KeyboardState.NUMBERS
                }
            }
        },
        modifier = Modifier
            .height((42 * ratio).dp)
            .width((46 * ratio).dp)
    )
}

@SuppressLint("UnrememberedMutableState")
@Preview
@Composable
fun FunctionKeyIconPreview() {
    val context = LocalContext.current
    FunctionKeyIcon(context, 1f, "shift", mutableStateOf(KeyboardState.NOCAPS), LightCustomColor)
}