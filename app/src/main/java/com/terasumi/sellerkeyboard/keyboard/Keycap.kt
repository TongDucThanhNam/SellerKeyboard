package com.terasumi.sellerkeyboard.keyboard

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terasumi.sellerkeyboard.service.SellerKeyboard

@Composable
fun Keycap(context: Context, key: String, ratio: Float = 1.0f) {
    //TODO
    Button(
//        border = BorderStroke(1.dp, Color.Black),
        content = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width((30 * ratio).dp)
                    .height((37 * ratio).dp)
            ) {
                Text(
                    text = key,
                    fontSize = (22 * ratio).sp,
                    color = Color(0xFF1B1B1D),
                    fontFamily = FontFamily.SansSerif, // Sử dụng Roboto mặc định
                    lineHeight = (26 * ratio).sp,
                    textAlign = TextAlign.Center
                )
            }
        },
        contentPadding = PaddingValues(
            horizontal = 0.dp, vertical = 0.dp
        ), // Custom content padding
        onClick = {
            //IME
            (context as SellerKeyboard).currentInputConnection.commitText(key, 1)

        },
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White, contentColor = Color.Black
        ),
        modifier = Modifier
            .height((42 * ratio).dp)
            .width((30 * ratio).dp)
    )
}

@Preview
@Composable
fun KeycapPreview() {
    val context = LocalContext.current
    Keycap(context, "A")
}