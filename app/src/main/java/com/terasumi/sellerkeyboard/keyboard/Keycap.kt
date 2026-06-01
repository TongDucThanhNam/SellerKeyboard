package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.terasumi.sellerkeyboard.keyboard.theme.LightKeyboardColors
import com.terasumi.sellerkeyboard.keyboard.theme.KeyboardColors

@Composable
fun Keycap(
    key: String,
    myColor: KeyboardColors,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false,
    onAction: (KeyboardAction) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val cornerRadius = if (isLandscape) 6.dp else 8.dp
    val backgroundColor = if (isPressed) {
        myColor.keyActionBackground.copy(alpha = 0.5f)
    } else {
        myColor.spaceBarBackground
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .pointerInput(key) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    isPressed = true
                    onAction(KeyboardAction.KeyPress(key))
                    val up = waitForUpOrCancellation()
                    isPressed = false
                    up?.consume()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = key,
            fontSize = if (isLandscape) 18.sp else 22.sp,
            color = myColor.iconColor,
            fontFamily = FontFamily.SansSerif,
            lineHeight = if (isLandscape) 22.sp else 26.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Normal
        )

        if (isPressed) {
            Popup(
                alignment = Alignment.TopCenter,
                properties = PopupProperties(
                    excludeFromSystemGesture = true,
                    clippingEnabled = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .offset(y = if (isLandscape) (-36).dp else (-46).dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(myColor.keyboardBackground)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = key,
                        fontSize = if (isLandscape) 28.sp else 34.sp,
                        color = myColor.iconColor,
                        fontFamily = FontFamily.SansSerif,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun KeycapPreview() {
    Keycap("A", myColor = LightKeyboardColors, onAction = {})
}
