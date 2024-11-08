package com.terasumi.sellerkeyboard.keyboard

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.R
import com.terasumi.sellerkeyboard.ui.theme.LightCustomColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FunctionKey(
    context: Context,
    key: String,
    ratio: Float = 1.0f,
    myColor: Array<Color>,
    isLandscape: Boolean = false,
) {
    //Map
    val map = mapOf(
        "numbers" to R.drawable.numbers
    )


    //TODO
    Button(
//        border = BorderStroke(1.dp, Color.Black),
        content = {
            Box(
                contentAlignment = Alignment.Center,
                content = {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = map[key]!!),
                        contentDescription = key,
                        modifier = Modifier.size(24.dp),
                        tint = myColor[5]
                    )
                },
                modifier = Modifier
                    .width((30 * ratio).dp)
                    .height((37 * ratio).dp)
            )
        },
        contentPadding = PaddingValues(
            horizontal = 0.dp, vertical = 0.dp
        ), // Custom content padding
        onClick = {
            //TODO

        },
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = myColor[0],
            contentColor = Color(0xFF000000)
        ),
        modifier =
        if (isLandscape) Modifier
            .height((35).dp)
            .width((30).dp)
        else Modifier
            .height((42 * ratio).dp)
            .width((30 * ratio).dp)
    )
}

@Preview
@Composable
fun FunctionKeyPreview() {
    val context = LocalContext.current
    FunctionKey(context, "numbers", myColor = LightCustomColor)
}