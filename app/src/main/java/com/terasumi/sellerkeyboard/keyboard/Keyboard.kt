package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terasumi.sellerkeyboard.R
import com.terasumi.sellerkeyboard.keyboard.theme.KeyboardColors
import com.terasumi.sellerkeyboard.keyboard.theme.LightKeyboardColors
import com.terasumi.sellerkeyboard.settings.KeyboardHeightPreset
import com.terasumi.sellerkeyboard.settings.OneHandedSide

@Composable
fun Keyboard(
    keyboardRows: List<List<KeyboardKeyDefinition>>,
    keyboardState: KeyboardState,
    myColor: KeyboardColors,
    isLandscape: Boolean,
    renderEpoch: Long = 0L,
    languageLabel: String = "",
    languageToggleLabel: String = "",
    canToggleLanguage: Boolean = false,
    onToggleLanguage: () -> Unit = {},
    selectionEnd: Int = -1,
    popupPreviewEnabled: Boolean = true,
    keyBorders: Boolean = false,
    heightPreset: KeyboardHeightPreset = KeyboardHeightPreset.STANDARD,
    oneHandedSide: OneHandedSide = OneHandedSide.OFF,
    onAction: (KeyboardAction) -> Unit
) {
    val rows = remember(keyboardRows, isLandscape) {
        if (isLandscape && keyboardRows.count() == 5) {
            keyboardRows.drop(1)
        } else {
            keyboardRows
        }
    }

    val keyboardAlignment = when (oneHandedSide) {
        OneHandedSide.RIGHT -> Alignment.CenterEnd
        OneHandedSide.LEFT, OneHandedSide.OFF -> Alignment.CenterStart
    }
    val keyboardWidthFraction = if (oneHandedSide == OneHandedSide.OFF) 1f else 0.78f

    Box(
        modifier = Modifier
            .fillMaxWidth(),
        contentAlignment = keyboardAlignment
    ) {
        // Recreate the key subtree when restartInput establishes a new editor
        // session. This reliably refreshes equal-shaped layouts without adding
        // a custom draw layer that can interfere with accessibility focus.
        key(renderEpoch) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(keyboardWidthFraction)
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
                        languageLabel = languageLabel,
                        languageToggleLabel = languageToggleLabel,
                        canToggleLanguage = canToggleLanguage,
                        onToggleLanguage = onToggleLanguage,
                        selectionEnd = selectionEnd,
                        popupPreviewEnabled = popupPreviewEnabled,
                        keyBorders = keyBorders,
                        heightPreset = heightPreset,
                        onAction = onAction
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyboardRow(
    keyList: List<KeyboardKeyDefinition>,
    rowCount: Int,
    myColor: KeyboardColors,
    isLandscape: Boolean,
    languageLabel: String,
    languageToggleLabel: String,
    canToggleLanguage: Boolean,
    onToggleLanguage: () -> Unit,
    selectionEnd: Int,
    popupPreviewEnabled: Boolean,
    keyBorders: Boolean,
    heightPreset: KeyboardHeightPreset,
    onAction: (KeyboardAction) -> Unit
) {
    val rowHeight = when (heightPreset) {
        KeyboardHeightPreset.COMPACT -> if (isLandscape) 40.dp else 44.dp
        KeyboardHeightPreset.STANDARD -> 48.dp
        KeyboardHeightPreset.TALL -> if (isLandscape) 52.dp else 56.dp
    }
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
                keyList = keyList.map { it.label },
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
                    val keyModifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (keyBorders) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = myColor.iconColor.copy(alpha = 0.42f),
                                    shape = RoundedCornerShape(if (isLandscape) 6.dp else 8.dp)
                                )
                            } else {
                                Modifier
                            }
                        )
                    when (key.role) {
                        KeyboardKeyRole.SHIFT, KeyboardKeyRole.DELETE -> {
                            FunctionKeyIcon(
                                key = key,
                                myColor = myColor,
                                isLandscape = isLandscape,
                                onAction = onAction,
                                cursorPosition = selectionEnd,
                                modifier = keyModifier
                            )
                        }

                        KeyboardKeyRole.LAYOUT_SWITCH -> {
                            SpecialFunctionKey(
                                onPress = { onAction(key.action) },
                                action = key.label,
                                myColor = myColor,
                                isLandscape = isLandscape,
                                modifier = keyModifier,
                                accessibilityLabel = key.contentDescription
                            )
                        }

                        KeyboardKeyRole.ENTER -> {
                            SpecialFunctionKeyIcon(
                                contentDescription = key.contentDescription,
                                myColor = myColor,
                                isLandscape = isLandscape,
                                onPress = { onAction(key.action) },
                                modifier = keyModifier
                            )
                        }

                        KeyboardKeyRole.SPACE -> {
                            SpaceKey(
                                languageLabel = languageLabel,
                                languageToggleLabel = languageToggleLabel,
                                canToggleLanguage = canToggleLanguage,
                                myColor = myColor,
                                isLandscape = isLandscape,
                                onSpace = { onAction(key.action) },
                                onToggleLanguage = onToggleLanguage,
                                selectionEnd = selectionEnd,
                                onMoveCursorTo = { position ->
                                    onAction(KeyboardAction.MoveCursorTo(position))
                                },
                                modifier = keyModifier
                            )
                        }

                        KeyboardKeyRole.CHARACTER -> {
                            Keycap(
                                key = key.label,
                                myColor = myColor,
                                isLandscape = isLandscape,
                                onPress = { onAction(key.action) },
                                onAlternativePress = { alternative ->
                                    onAction(KeyboardAction.KeyPress(alternative))
                                },
                                popupPreviewEnabled = popupPreviewEnabled,
                                modifier = keyModifier
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpaceKey(
    languageLabel: String,
    languageToggleLabel: String,
    canToggleLanguage: Boolean,
    myColor: KeyboardColors,
    isLandscape: Boolean,
    onSpace: () -> Unit,
    onToggleLanguage: () -> Unit,
    selectionEnd: Int,
    onMoveCursorTo: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val cornerRadius = if (isLandscape) 6.dp else 8.dp
    val containerColor = if (isPressed) {
        myColor.keyActionBackground.copy(alpha = 0.5f)
    } else {
        myColor.spaceBarBackground
    }
    val description = if (canToggleLanguage && languageLabel.isNotBlank()) {
        "Space. $languageToggleLabel $languageLabel"
    } else {
        "Space"
    }
    val viewConfiguration = LocalViewConfiguration.current
    val density = LocalDensity.current
    val cursorStepPx = with(density) { 12.dp.toPx() }
    val currentSelectionEnd by rememberUpdatedState(selectionEnd)
    val currentOnSpace by rememberUpdatedState(onSpace)
    val currentOnToggleLanguage by rememberUpdatedState(onToggleLanguage)
    val currentOnMoveCursorTo by rememberUpdatedState(onMoveCursorTo)

    Box(
        modifier = modifier
            .semantics {
                contentDescription = description
                role = Role.Button
                onClick {
                    onSpace()
                    true
                }
                if (canToggleLanguage) {
                    onLongClick {
                        onToggleLanguage()
                        true
                    }
                }
            }
            .background(
                color = containerColor,
                shape = RoundedCornerShape(cornerRadius)
            )
            .pointerInput(canToggleLanguage, languageLabel) {
                awaitEachGesture {
                    val gestureSelectionEnd = currentSelectionEnd
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isPressed = true
                    val startX = down.position.x
                    val startTime = down.uptimeMillis
                    var emittedSteps = 0
                    var moved = false
                    var canceled = false

                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.size != 1) {
                            canceled = true
                            event.changes.forEach { it.consume() }
                            break
                        }
                        val change = event.changes.first()
                        val horizontalDistance = change.position.x - startX
                        val outsideBounds = change.position.x < -viewConfiguration.touchSlop ||
                            change.position.x > size.width + viewConfiguration.touchSlop ||
                            change.position.y < -viewConfiguration.touchSlop ||
                            change.position.y > size.height + viewConfiguration.touchSlop
                        if (outsideBounds) {
                            canceled = true
                            change.consume()
                            break
                        }
                        val steps = KeyboardGesturePolicy.cursorSteps(
                            horizontalDistancePx = horizontalDistance,
                            touchSlopPx = viewConfiguration.touchSlop,
                            stepPx = cursorStepPx
                        )
                        if (steps != 0 && gestureSelectionEnd >= 0) {
                            moved = true
                            if (steps != emittedSteps) {
                                emittedSteps = steps
                                currentOnMoveCursorTo(
                                    (gestureSelectionEnd + steps).coerceAtLeast(0)
                                )
                            }
                        }
                        if (!change.pressed) {
                            if (!canceled && !moved) {
                                if (canToggleLanguage &&
                                    change.uptimeMillis - startTime >=
                                    viewConfiguration.longPressTimeoutMillis) {
                                    currentOnToggleLanguage()
                                } else {
                                    currentOnSpace()
                                }
                            }
                            change.consume()
                            break
                        }
                        change.consume()
                    }
                    if (canceled && moved && gestureSelectionEnd >= 0) {
                        currentOnMoveCursorTo(gestureSelectionEnd)
                    }
                    isPressed = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (canToggleLanguage && languageLabel.isNotBlank()) {
            Text(
                text = languageLabel,
                color = myColor.iconColor.copy(alpha = 0.86f),
                fontSize = if (isLandscape) 12.sp else 13.sp,
                fontWeight = FontWeight.Medium
            )
        } else {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.space_bar),
                contentDescription = null,
                tint = myColor.iconColor,
                modifier = Modifier.size(if (isLandscape) 20.dp else 24.dp)
            )
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
    val keyboardRows = KeyboardLayoutResolver.resolve(
        com.terasumi.sellerkeyboard.ime.EditorSession.Inactive,
        KeyboardState.SYMBOLS
    ).rows
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
    val keyboardRows = KeyboardLayoutResolver.resolve(
        com.terasumi.sellerkeyboard.ime.EditorSession.Inactive,
        KeyboardState.SYMBOLS
    ).rows
    val myColor = LightKeyboardColors

    Keyboard(
        keyboardRows = keyboardRows,
        keyboardState = KeyboardState.NOCAPS,
        myColor = myColor,
        isLandscape = true,
        onAction = {}
    )
}
