package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val cornerRadius = if (isLandscape) 10.dp else 14.dp
    Button(
        onClick = { onAction(KeyboardAction.KeyPress(key)) },
        shape = RoundedCornerShape(cornerRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = myColor.spaceBarBackground,
            contentColor = myColor.iconColor
        ),
        modifier = modifier,
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
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
        }
    }
}

@Preview
@Composable
fun KeycapPreview() {
    Keycap("A", myColor = LightKeyboardColors, onAction = {})
}
