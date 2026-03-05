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
fun FunctionKeyIcon(
    action: String,
    myColor: KeyboardColors,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier,
    onAction: (KeyboardAction) -> Unit,
) {
    val map = mapOf(
        "shift" to R.drawable.arrow,
        "Shift" to R.drawable.arrow_up,
        "SHIFT" to R.drawable.arrow_up,
        "delete" to R.drawable.back,
        "symbols" to R.drawable.symbols,
        "numbers" to R.drawable.numbers,
        "123?" to R.drawable._23
    )

    val containerColor = when (action) {
        "shift", "Shift", "SHIFT", "delete" -> myColor.keyBackground.copy(alpha = 0.95f)
        else -> myColor.keyAltBackground.copy(alpha = 0.92f)
    }
    val cornerRadius = if (isLandscape) 10.dp else 14.dp

    Button(
        content = {
            val iconSize = if (isLandscape) 20.dp else 24.dp
            Icon(
                imageVector = ImageVector.vectorResource(id = map[action]!!),
                contentDescription = action,
                modifier = Modifier.size(iconSize),
                tint = myColor.iconColor
            )
        },
        shape = RoundedCornerShape(cornerRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = myColor.iconColor
        ),
        contentPadding = PaddingValues(0.dp),
        onClick = {
            when (action) {
                "shift", "Shift", "SHIFT" -> onAction(KeyboardAction.Shift)
                "delete" -> onAction(KeyboardAction.Delete)
                "symbols" -> onAction(KeyboardAction.SwitchState(KeyboardState.SYMBOLS))
                "123?" -> onAction(KeyboardAction.SwitchState(KeyboardState.NUMBERS))
                "numbers" -> onAction(KeyboardAction.SwitchState(KeyboardState.NOCAPS))
            }
        },
        modifier = modifier
    )
}

@Preview
@Composable
fun FunctionKeyIconPreview() {
    FunctionKeyIcon(
        action = "shift",
        myColor = LightKeyboardColors,
        onAction = {}
    )
}
