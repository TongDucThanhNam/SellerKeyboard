package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terasumi.sellerkeyboard.keyboard.theme.LightKeyboardColors
import com.terasumi.sellerkeyboard.keyboard.theme.KeyboardColors

@Composable
fun SpecialFunctionKey(
    onPress: () -> Unit,
    action: String,
    myColor: KeyboardColors,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var isPressed by remember { mutableStateOf(false) }
    val backgroundColor = if (isPressed) {
        myColor.keyActionBackground.copy(alpha = 0.5f)
    } else {
        myColor.keyAltBackground
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(if (isLandscape) 10.dp else 14.dp))
            .background(backgroundColor)
            .pointerInput(action) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    isPressed = true
                    onPress()
                    val up = waitForUpOrCancellation()
                    isPressed = false
                    up?.consume()
                }
            }
            .padding(
                horizontal = if (isLandscape) 4.dp else 8.dp,
                vertical = 0.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = action,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = if (isLandscape) 14.sp else 16.sp,
            fontStyle = FontStyle.Normal,
            fontWeight = FontWeight.Medium,
            color = myColor.iconColor
        )
    }
}

@Preview
@Composable
fun SpecialFunctionKeyPreview() {
    SpecialFunctionKey(
        onPress = {},
        action = "?123",
        myColor = LightKeyboardColors
    )
}
