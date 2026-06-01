package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(if (isLandscape) 10.dp else 14.dp))
            .background(myColor.keyActionBackground)
            .pointerInput(action) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (action == "ENTER") onAction(KeyboardAction.Enter)
                    down.consume()
                }
            },
        contentAlignment = Alignment.Center
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
