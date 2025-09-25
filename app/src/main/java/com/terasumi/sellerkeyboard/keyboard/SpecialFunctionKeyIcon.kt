package com.terasumi.sellerkeyboard.keyboard

import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
    action: String,
    myColor: Array<Color>,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val map = mapOf(
        "ENTER" to R.drawable.enter,
    )

    val iconSize = if (isLandscape) 22.dp else 24.dp

    Button(
        colors = ButtonDefaults.buttonColors(
            contentColor = Color(0xFF000000),
            containerColor = myColor[2]
        ),
        onClick = {
            when (action) {
                "ENTER" -> (context as SellerKeyboard).currentInputConnection.sendKeyEvent(
                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
                )
            }
        },
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(id = map[action]!!),
            contentDescription = null,
            tint = myColor[5],
            modifier = Modifier.size(iconSize)
        )
    }
}

@Preview
@Composable
fun SpecialFunctionKeyIconPreview() {
    val context = LocalContext.current
    SpecialFunctionKeyIcon(
        context = context,
        action = "ENTER",
        myColor = LightCustomColor
    )
}
