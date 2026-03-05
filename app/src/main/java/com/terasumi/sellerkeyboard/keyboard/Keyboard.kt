package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.R
import com.terasumi.sellerkeyboard.keyboard.theme.LightKeyboardColors
import com.terasumi.sellerkeyboard.keyboard.theme.KeyboardColors

@Composable
fun Keyboard(
    keyboardRows: List<List<String>>,
    keyboardState: KeyboardState,
    myColor: KeyboardColors,
    isLandscape: Boolean,
    onAction: (KeyboardAction) -> Unit
) {
    val rows = remember(keyboardRows, isLandscape) {
        if (isLandscape && keyboardRows.count() == 5) {
            keyboardRows.drop(1)
        } else {
            keyboardRows
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(myColor.keyboardBackground)
            .padding(vertical = if (isLandscape) 4.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 6.dp)
    ) {
        val rowCount = rows.count()
        for (index in 0 until rowCount) {
            val row = rows[index]
            KeyboardRow(
                keyList = row,
                rowIndex = index,
                rowCount = rowCount,
                myColor = myColor,
                isLandscape = isLandscape,
                onAction = onAction
            )
        }
    }
}

@Composable
private fun KeyboardRow(
    keyList: List<String>,
    rowIndex: Int,
    rowCount: Int,
    myColor: KeyboardColors,
    isLandscape: Boolean,
    onAction: (KeyboardAction) -> Unit
) {
    val isBottomRow = rowIndex == rowCount - 1
    val rowHeight = if (isLandscape) 48.dp else 54.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        for (key in keyList) {
            val weight = keyWidthUnits(
                key = key,
                isBottomRow = isBottomRow,
                isLandscape = isLandscape
            )

            Box(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxSize()
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                when (key) {
                    "shift", "Shift", "SHIFT", "delete", "symbols", "numbers", "123?" -> {
                        FunctionKeyIcon(
                            action = key,
                            myColor = myColor,
                            isLandscape = isLandscape,
                            onAction = onAction,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    "?123", "?ABC" -> {
                        SpecialFunctionKey(
                            onPress = {
                                val state = if (key == "?123") KeyboardState.NUMBERS else KeyboardState.NOCAPS
                                onAction(KeyboardAction.SwitchState(state))
                            },
                            action = key,
                            myColor = myColor,
                            isLandscape = isLandscape,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    "enter" -> {
                        SpecialFunctionKeyIcon(
                            action = "ENTER",
                            myColor = myColor,
                            isLandscape = isLandscape,
                            onAction = onAction,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    "space" -> {
                        Button(
                            onClick = { onAction(KeyboardAction.Space) },
                            shape = RoundedCornerShape(if (isLandscape) 10.dp else 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = myColor.spaceBarBackground,
                                contentColor = myColor.iconColor
                            ),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.space_bar),
                                contentDescription = "Space",
                                tint = myColor.iconColor,
                                modifier = Modifier.size(if (isLandscape) 20.dp else 24.dp)
                            )
                        }
                    }

                    else -> {
                        Keycap(
                            key = key,
                            myColor = myColor,
                            isLandscape = isLandscape,
                            onAction = onAction,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

private fun keyWidthUnits(
    key: String,
    isBottomRow: Boolean,
    isLandscape: Boolean
): Float {
    if (isLandscape) {
        return when (key) {
            "shift", "Shift", "SHIFT", "delete" -> 1.28f
            "symbols", "numbers", "123?" -> 1.2f
            "?123", "?ABC" -> if (isBottomRow) 1.46f else 1f
            "enter" -> if (isBottomRow) 1.46f else 1f
            "space" -> if (isBottomRow) 3.28f else 1f
            else -> 1f
        }
    }

    return when (key) {
        "shift", "Shift", "SHIFT", "delete" -> 1.38f
        "symbols", "numbers", "123?" -> 1.28f
        "?123", "?ABC" -> if (isBottomRow) 1.65f else 1f
        "enter" -> if (isBottomRow) 1.35f else 1f
        "space" -> if (isBottomRow) 3.85f else 1f
        else -> 1f
    }
}

@Preview
@Composable
fun KeyboardPreview() {
    val keyboardRows = listOf(
        listOf("~", "`", "|", "•", "√", "π", "÷", "×", "§", "∆"),
        listOf("£", "€", "$", "¢", "^", "°", "=", "{", "}", "\\"),
        listOf("123?", "%", "©", "®", "™", "✓", "[", "]", "delete"),
        listOf("?ABC", "<", "space", ">", "enter")
    )
    val myColor = LightKeyboardColors

    Keyboard(
        keyboardRows = keyboardRows,
        keyboardState = KeyboardState.NOCAPS,
        myColor = myColor,
        isLandscape = false,
        onAction = {}
    )
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=480")
@Composable
fun KeyboardLandscapePreview() {
    val keyboardRows = listOf(
        listOf("~", "`", "|", "•", "√", "π", "÷", "×", "§", "∆"),
        listOf("£", "€", "$", "¢", "^", "°", "=", "{", "}", "\\"),
        listOf("123?", "%", "©", "®", "™", "✓", "[", "]", "delete"),
        listOf("?ABC", "<", "space", ">", "enter")
    )
    val myColor = LightKeyboardColors

    Keyboard(
        keyboardRows = keyboardRows,
        keyboardState = KeyboardState.NOCAPS,
        myColor = myColor,
        isLandscape = true,
        onAction = {}
    )
}

