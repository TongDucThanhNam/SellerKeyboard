package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.R
import com.terasumi.sellerkeyboard.keyboard.theme.KeyboardColors
import com.terasumi.sellerkeyboard.keyboard.theme.LightKeyboardColors

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
            .padding(
                horizontal = if (isLandscape) 2.dp else 3.dp,
                vertical = if (isLandscape) 3.dp else 4.dp
            ),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 5.dp else 6.dp)
    ) {
        val rowCount = rows.count()
        for (index in 0 until rowCount) {
            KeyboardRow(
                keyList = rows[index],
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
    rowCount: Int,
    myColor: KeyboardColors,
    isLandscape: Boolean,
    onAction: (KeyboardAction) -> Unit
) {
    val rowHeight = if (isLandscape) 36.dp else 42.dp
    val keyGap = if (isLandscape) 3.dp else 4.dp

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
    ) {
        val baseUnit = remember(maxWidth, keyGap) {
            ((maxWidth - (keyGap * 9f)).coerceAtLeast(0.dp)) / 10f
        }
        val metrics = remember(keyList, baseUnit, keyGap, rowCount) {
            resolveKeyboardRowMetrics(
                keyList = keyList,
                baseUnit = baseUnit,
                keyGap = keyGap,
                rowCount = rowCount
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = metrics.leadingMargin, end = metrics.trailingMargin),
            horizontalArrangement = Arrangement.spacedBy(keyGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            keyList.forEachIndexed { index, key ->
                Box(
                    modifier = Modifier
                        .width(metrics.keyWidths[index])
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        key in SpecialKeys.SHIFT_VARIANTS || key == SpecialKeys.DELETE ||
                            key == SpecialKeys.SYMBOLS || key == SpecialKeys.NUMBERS ||
                            key == SpecialKeys.NUMBERS_SWITCH -> {
                            FunctionKeyIcon(
                                action = key,
                                myColor = myColor,
                                isLandscape = isLandscape,
                                onAction = onAction,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        key == SpecialKeys.TO_NUMBERS || key == SpecialKeys.TO_ALPHA -> {
                            SpecialFunctionKey(
                                onPress = {
                                    val state = if (key == SpecialKeys.TO_NUMBERS) {
                                        KeyboardState.NUMBERS
                                    } else {
                                        KeyboardState.NOCAPS
                                    }
                                    onAction(KeyboardAction.SwitchState(state))
                                },
                                action = key,
                                myColor = myColor,
                                isLandscape = isLandscape,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        key == SpecialKeys.ENTER -> {
                            SpecialFunctionKeyIcon(
                                action = "ENTER",
                                myColor = myColor,
                                isLandscape = isLandscape,
                                onAction = onAction,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        key == SpecialKeys.SPACE -> {
                            Button(
                                onClick = { onAction(KeyboardAction.Space) },
                                shape = RoundedCornerShape(if (isLandscape) 6.dp else 8.dp),
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
}

/** Well-known special key names used in keyboard layouts. */
internal object SpecialKeys {
    val SHIFT_VARIANTS = setOf("shift", "Shift", "SHIFT")
    const val DELETE = "delete"
    const val SYMBOLS = "symbols"
    const val NUMBERS = "numbers"
    const val NUMBERS_SWITCH = "123?"
    const val TO_NUMBERS = "?123"
    const val TO_ALPHA = "?ABC"
    const val ENTER = "enter"
    const val SPACE = "space"
}

internal data class KeySpan(
    val unitMultiplier: Float,
    val gapMultiplier: Float = 0f
) {
    fun toDp(baseUnit: Dp, keyGap: Dp): Dp {
        return (baseUnit * unitMultiplier) + (keyGap * gapMultiplier)
    }
}

internal data class KeyboardRowMetrics(
    val keyWidths: List<Dp>,
    val leadingMargin: Dp,
    val trailingMargin: Dp
)

private val standardKeySpan = KeySpan(unitMultiplier = 1f, gapMultiplier = 0f)
private val wideEdgeKeySpan = KeySpan(unitMultiplier = 1.5f, gapMultiplier = 0.5f)
private val wideEnterFiveKeyRowSpan = KeySpan(unitMultiplier = 2.5f, gapMultiplier = 1.5f)
private val spaceKeySpan = KeySpan(unitMultiplier = 4f, gapMultiplier = 3f)

internal fun resolveKeyboardRowMetrics(
    keyList: List<String>,
    baseUnit: Dp,
    keyGap: Dp,
    rowCount: Int
): KeyboardRowMetrics {
    if (keyList.isEmpty()) {
        return KeyboardRowMetrics(
            keyWidths = emptyList(),
            leadingMargin = 0.dp,
            trailingMargin = 0.dp
        )
    }

    val keySpans = keyList.map { key -> resolveKeySpan(key, keyList.size, rowCount) }
    val keyWidths = keySpans.map { span -> span.toDp(baseUnit = baseUnit, keyGap = keyGap) }

    val occupiedUnitTotal = keySpans.sumOf { it.unitMultiplier.toDouble() }.toFloat()
    val occupiedGapTotal =
        keySpans.sumOf { it.gapMultiplier.toDouble() }.toFloat() + (keyList.size - 1).coerceAtLeast(0)

    val remainingUnits = (10f - occupiedUnitTotal).coerceAtLeast(0f)
    val remainingGaps = (9f - occupiedGapTotal).coerceAtLeast(0f)

    val shouldCenter = shouldCenterRow(keyList = keyList, keySpans = keySpans)
    val marginSpan = if (shouldCenter || remainingUnits > 0f || remainingGaps > 0f) {
        KeySpan(
            unitMultiplier = remainingUnits / 2f,
            gapMultiplier = remainingGaps / 2f
        )
    } else {
        KeySpan(unitMultiplier = 0f, gapMultiplier = 0f)
    }

    val marginWidth = marginSpan.toDp(baseUnit = baseUnit, keyGap = keyGap)
    return KeyboardRowMetrics(
        keyWidths = keyWidths,
        leadingMargin = marginWidth,
        trailingMargin = marginWidth
    )
}

private fun shouldCenterRow(keyList: List<String>, keySpans: List<KeySpan>): Boolean {
    return keyList.size == 9 && keySpans.all { span -> span == standardKeySpan }
}

private fun resolveKeySpan(key: String, rowSize: Int, rowCount: Int): KeySpan {
    return when {
        key == SpecialKeys.SPACE -> spaceKeySpan
        key in wideEdgeKeys -> wideEdgeKeySpan
        key == SpecialKeys.ENTER -> {
            if (rowSize == 5 && rowCount <= 4) {
                wideEnterFiveKeyRowSpan
            } else {
                wideEdgeKeySpan
            }
        }
        else -> standardKeySpan
    }
}

private val wideEdgeKeys = setOf(
    SpecialKeys.DELETE,
    SpecialKeys.SYMBOLS,
    SpecialKeys.NUMBERS,
    SpecialKeys.NUMBERS_SWITCH,
    SpecialKeys.TO_NUMBERS,
    SpecialKeys.TO_ALPHA,
    *SpecialKeys.SHIFT_VARIANTS.toTypedArray()
)

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
