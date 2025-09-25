package com.terasumi.sellerkeyboard.keyboard

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terasumi.sellerkeyboard.service.SellerKeyboard
import com.terasumi.sellerkeyboard.ui.theme.LightCustomColor

@Composable
fun Keycap(
    context: Context,
    key: String,
    myColor: Array<Color>,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false
) {
    Button(
        onClick = {
            (context as SellerKeyboard).currentInputConnection.commitText(key, 1)
        },
        shape = RoundedCornerShape(7.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = myColor[4],
            contentColor = myColor[5]
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
                fontSize = if (isLandscape) 20.sp else 22.sp,
                color = myColor[5],
                fontFamily = FontFamily.SansSerif,
                lineHeight = if (isLandscape) 24.sp else 26.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Preview()
@Composable
fun KeycapPreview() {
    val context = LocalContext.current
    Keycap(context, "A", myColor = LightCustomColor)
}
