package com.terasumi.sellerkeyboard.keyboard

import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
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
fun SpecialFunctionKeyIcon(
    context: Context,
    ratio: Float = 1.0f,
    action: String,
    myColor: Array<Color>,
    isLandscape: Boolean = false,
) {
    val map = mapOf(
        "ENTER" to R.drawable.enter,
    )

    Button(
        colors = ButtonDefaults.buttonColors(
            contentColor = Color(0xFF000000),
            containerColor = myColor[2]
        ),
        onClick = {
            /*TODO*/
            when (action) {
                "ENTER" -> {
                    //TODO
                    (context as SellerKeyboard).currentInputConnection.sendKeyEvent(
                        KeyEvent(
                            KeyEvent.ACTION_DOWN,
                            KeyEvent.KEYCODE_ENTER
                        )
                    )
                }
            }
        },
        contentPadding = PaddingValues(
            horizontal = 0.dp,
            vertical = 0.dp
        ), // Custom content padding
        modifier =
        if (isLandscape) Modifier
            .height((35).dp)
            .width((46 * ratio).dp)
        else Modifier
            .height((42 * ratio).dp)
            .width((46 * ratio).dp)

    ) {
        Icon(
            imageVector = ImageVector.vectorResource(id = map[action]!!),
            contentDescription = null,
            tint = myColor[0],
            modifier = Modifier.size(24.dp)
        )
    }
}

@Preview
@Composable
fun SpecialFunctionKeyIconPreview() {
    val context = LocalContext.current
    SpecialFunctionKeyIcon(context, 1f, "ENTER", LightCustomColor)
}