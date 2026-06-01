package com.terasumi.sellerkeyboard.keyboard

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.R
import com.terasumi.sellerkeyboard.keyboard.theme.LightKeyboardColors
import com.terasumi.sellerkeyboard.keyboard.theme.KeyboardColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FunctionKey(
    context: Context,
    key: String,
    myColor: KeyboardColors,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier,
) {
    //Map
    val map = mapOf(
        "numbers" to R.drawable.numbers
    )


    //TODO
    val iconSize = if (isLandscape) 20.dp else 24.dp

    Button(
        onClick = {
            //TODO

        },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = myColor.keyBackground,
            contentColor = myColor.iconColor
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
    )
    {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(id = map[key]!!),
                contentDescription = key,
                tint = myColor.iconColor,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Preview
@Composable
fun FunctionKeyPreview() {
    val context = LocalContext.current
    FunctionKey(context, "numbers", myColor = LightKeyboardColors)
}
