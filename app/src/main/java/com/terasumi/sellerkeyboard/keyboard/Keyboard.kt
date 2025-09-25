package com.terasumi.sellerkeyboard.keyboard

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.R
import com.terasumi.sellerkeyboard.service.SellerKeyboard
import com.terasumi.sellerkeyboard.ui.theme.LightCustomColor

@Composable
fun Keyboard(
    keyboardArray: Array<Array<String>>,
    keyboardState: MutableState<KeyboardState>,
    myColor: Array<Color>,
    context: Context,
    isLandscape: Boolean,
    ratio: Float
) {
    val density = LocalDensity.current
    val metrics = with(density) {
        val scale = ratio
        KeyboardLayoutMetrics(
            keyWidth = (94f * scale).toDp(),
            keyHeight = (112f * scale).toDp(),
            bottomKeyHeight = (120f * scale).toDp(),
            shiftWidth = (148f * scale).toDp(),
            bottomFunctionWidth = (149f * scale).toDp(),
            spaceWidth = (417f * scale).toDp(),
            spaceHeight = (120f * scale).toDp(),
            horizontalSpacing = (12f * scale).toDp(),
            verticalSpacing = (22f * scale).toDp(),
            horizontalPadding = (12f * scale).toDp(),
            verticalPadding = (12f * scale).toDp()
        )
    }

    val rows = remember(keyboardArray, isLandscape) {
        if (isLandscape && keyboardArray.size == 5) {
            keyboardArray.drop(1)
        } else {
            keyboardArray.asIterable()
        }
    }

    val rowCount = rows.count()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(myColor[3])
            .padding(
                horizontal = metrics.horizontalPadding,
                vertical = metrics.verticalPadding
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(metrics.verticalSpacing)
        ) {
            rows.forEachIndexed { index, row ->
                KeyboardRow(
                    context = context,
                    keyList = row,
                    rowIndex = index,
                    rowCount = rowCount,
                    keyboardState = keyboardState,
                    myColor = myColor,
                    metrics = metrics,
                    isLandscape = isLandscape
                )
            }
        }
    }
}

@Composable
private fun KeyboardRow(
    context: Context,
    keyList: Array<String>,
    rowIndex: Int,
    rowCount: Int,
    keyboardState: MutableState<KeyboardState>,
    myColor: Array<Color>,
    metrics: KeyboardLayoutMetrics,
    isLandscape: Boolean
) {
    val isBottomRow = rowIndex == rowCount - 1
    val rowHeight = if (isBottomRow) metrics.bottomKeyHeight else metrics.keyHeight

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.horizontalSpacing)
        ) {
            keyList.forEach { key ->
                when (key) {
                    "shift", "Shift", "SHIFT" -> {
                        FunctionKeyIcon(
                            context = context,
                            action = key,
                            keyboardState = keyboardState,
                            myColor = myColor,
                            isLandscape = isLandscape,
                            modifier = Modifier
                                .width(metrics.shiftWidth)
                                .height(rowHeight)
                        )
                    }

                    "delete" -> {
                        FunctionKeyIcon(
                            context = context,
                            action = key,
                            keyboardState = keyboardState,
                            myColor = myColor,
                            isLandscape = isLandscape,
                            modifier = Modifier
                                .width(metrics.shiftWidth)
                                .height(rowHeight)
                        )
                    }

                    "symbols", "numbers", "123?" -> {
                        FunctionKeyIcon(
                            context = context,
                            action = key,
                            keyboardState = keyboardState,
                            myColor = myColor,
                            isLandscape = isLandscape,
                            modifier = Modifier
                                .width(metrics.keyWidth)
                                .height(rowHeight)
                        )
                    }

                    "?123", "?ABC" -> {
                        val width = if (isBottomRow) metrics.bottomFunctionWidth else metrics.shiftWidth
                        val height = if (isBottomRow) metrics.bottomKeyHeight else metrics.keyHeight
                        SpecialFunctionKey(
                            action = key,
                            keyboardState = keyboardState,
                            myColor = myColor,
                            isLandscape = isLandscape,
                            modifier = Modifier
                                .width(width)
                                .height(height)
                        )
                    }

                    "enter" -> {
                        SpecialFunctionKeyIcon(
                            context = context,
                            action = "ENTER",
                            myColor = myColor,
                            isLandscape = isLandscape,
                            modifier = Modifier
                                .width(metrics.bottomFunctionWidth)
                                .height(metrics.spaceHeight)
                        )
                    }

                    "space" -> {
                        Button(
                            onClick = {
                                (context as SellerKeyboard).currentInputConnection.commitText(" ", 1)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = myColor[4],
                                contentColor = myColor[5]
                            ),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .width(metrics.spaceWidth)
                                .height(metrics.spaceHeight)
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.space_bar),
                                contentDescription = "Space",
                                tint = myColor[5],
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    else -> {
                        val keyHeight = if (isBottomRow) metrics.bottomKeyHeight else metrics.keyHeight
                        Keycap(
                            context = context,
                            key = key,
                            myColor = myColor,
                            isLandscape = isLandscape,
                            modifier = Modifier
                                .width(metrics.keyWidth)
                                .height(keyHeight)
                        )
                    }
                }
            }
        }
    }
}

private data class KeyboardLayoutMetrics(
    val keyWidth: Dp,
    val keyHeight: Dp,
    val bottomKeyHeight: Dp,
    val shiftWidth: Dp,
    val bottomFunctionWidth: Dp,
    val spaceWidth: Dp,
    val spaceHeight: Dp,
    val horizontalSpacing: Dp,
    val verticalSpacing: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
)

@Preview
@Composable
fun KeyboardPreview() {
    val keyboardArray = arrayOf(
        arrayOf("~", "`", "|", "•", "√", "π", "÷", "×", "§", "∆"),
        arrayOf("£", "€", "$", "¢", "^", "°", "=", "{", "}", "\\"),
        arrayOf("123?", "%", "©", "®", "™", "✓", "[", "]", "delete"),
        arrayOf("?ABC", "<", "space", ">", "enter")
    )
    val myColor = LightCustomColor
    val context = LocalContext.current

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val ratio = configuration.screenWidthDp * density.density / 1080f

    Keyboard(
        keyboardArray = keyboardArray,
        keyboardState = remember { mutableStateOf(KeyboardState.NOCAPS) },
        myColor = myColor,
        context = context,
        isLandscape = false,
        ratio = ratio
    )
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=480")
@Composable
fun KeyboardLandscapePreview() {
    val keyboardArray = arrayOf(
        arrayOf("~", "`", "|", "•", "√", "π", "÷", "×", "§", "∆"),
        arrayOf("£", "€", "$", "¢", "^", "°", "=", "{", "}", "\\"),
        arrayOf("123?", "%", "©", "®", "™", "✓", "[", "]", "delete"),
        arrayOf("?ABC", "<", "space", ">", "enter")
    )
    val myColor = LightCustomColor
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val ratio = configuration.screenWidthDp * density.density / 1080f

    Keyboard(
        keyboardArray = keyboardArray,
        keyboardState = remember { mutableStateOf(KeyboardState.NOCAPS) },
        myColor = myColor,
        context = context,
        isLandscape = true,
        ratio = ratio
    )
}
