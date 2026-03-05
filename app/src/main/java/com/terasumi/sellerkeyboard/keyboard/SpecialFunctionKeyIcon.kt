package com.terasumi.sellerkeyboard.keyboard

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
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.R
import com.terasumi.sellerkeyboard.keyboard.theme.LightKeyboardColors
import com.terasumi.sellerkeyboard.keyboard.theme.KeyboardColors

@Composable
fun SpecialFunctionKeyIcon(
    action: String,
    myColor: KeyboardColors,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier,
    onAction: (KeyboardAction) -> Unit,
) {
    val map = mapOf(
        "ENTER" to R.drawable.enter,
    )

    val iconSize = if (isLandscape) 20.dp else 22.dp

    Button(
        colors = ButtonDefaults.buttonColors(
            containerColor = myColor.keyActionBackground,
            contentColor = myColor.iconColor
        ),
        onClick = {
            if (action == "ENTER") onAction(KeyboardAction.Enter)
        },
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(if (isLandscape) 12.dp else 15.dp),
        modifier = modifier
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(id = map[action]!!),
            contentDescription = null,
            tint = myColor.iconColor,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Preview
@Composable
fun SpecialFunctionKeyIconPreview() {
    SpecialFunctionKeyIcon(
        action = "ENTER",
        myColor = LightKeyboardColors,
        onAction = {}
    )
}
