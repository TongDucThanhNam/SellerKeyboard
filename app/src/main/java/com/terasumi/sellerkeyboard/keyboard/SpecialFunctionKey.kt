package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    Button(
        colors = ButtonDefaults.buttonColors(
            contentColor = Color(0xFF000000),
            containerColor = myColor.keyAltBackground
        ),
        shape = RoundedCornerShape(999.dp),
        onClick = {
            onPress()
        },
        contentPadding = PaddingValues(
            horizontal = if (isLandscape) 4.dp else 8.dp,
            vertical = 0.dp
        ),
        modifier = modifier
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
