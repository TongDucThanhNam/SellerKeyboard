package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.R
import com.terasumi.sellerkeyboard.keyboard.theme.LightKeyboardColors
import com.terasumi.sellerkeyboard.keyboard.theme.KeyboardColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FunctionKeyIcon(
    key: KeyboardKeyDefinition,
    myColor: KeyboardColors,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier,
    cursorPosition: Int = -1,
    onAction: (KeyboardAction) -> Unit,
) {
    val action = key.label
    val map = mapOf(
        "shift" to R.drawable.arrow,
        "Shift" to R.drawable.arrow_up,
        "SHIFT" to R.drawable.arrow_up,
        "delete" to R.drawable.back,
        "symbols" to R.drawable.symbols,
        "numbers" to R.drawable.numbers,
        "123?" to R.drawable._23
    )

    val containerColor = when (action) {
        "Shift" -> myColor.keyActionBackground.copy(alpha = 0.42f)
        "SHIFT" -> myColor.keyActionBackground.copy(alpha = 0.72f)
        "shift", "delete" -> myColor.keyBackground.copy(alpha = 0.95f)
        else -> myColor.keyAltBackground.copy(alpha = 0.92f)
    }
    val cornerRadius = if (isLandscape) 6.dp else 8.dp
    val iconSize = if (isLandscape) 20.dp else 24.dp
    val scope = rememberCoroutineScope()
    val viewConfiguration = LocalViewConfiguration.current
    val density = LocalDensity.current
    val deleteSelectionStepPx = with(density) { 24.dp.toPx() }
    val currentCursorPosition by rememberUpdatedState(cursorPosition)
    val currentOnAction by rememberUpdatedState(onAction)
    var isPressed by remember { mutableStateOf(false) }

    val activeContainerColor = if (isPressed) {
        myColor.keyActionBackground.copy(alpha = 0.5f)
    } else {
        containerColor
    }

    val pointerModifier = if (key.role == KeyboardKeyRole.DELETE) {
        Modifier.pointerInput(action) {
            awaitEachGesture {
                val gestureCursorPosition = currentCursorPosition
                var repeatJob: Job? = null
                var swiping = false
                var repeatStarted = false
                var finished = false
                var canceled = false
                var previewCount = 0
                val down = awaitFirstDown(requireUnconsumed = false)
                val startX = down.position.x
                isPressed = true
                down.consume()

                repeatJob = scope.launch {
                    delay(viewConfiguration.longPressTimeoutMillis)
                    if (!swiping && !finished) {
                        repeatStarted = true
                        while (!finished) {
                            currentOnAction(key.action)
                            delay(DELETE_REPEAT_INTERVAL_MS)
                        }
                    }
                }

                try {
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
                        if (!repeatStarted && gestureCursorPosition >= 0 &&
                            horizontalDistance < -viewConfiguration.touchSlop) {
                            swiping = true
                            repeatJob?.cancel()
                            val count = KeyboardGesturePolicy.deletePreviewCount(
                                horizontalDistancePx = horizontalDistance,
                                touchSlopPx = viewConfiguration.touchSlop,
                                stepPx = deleteSelectionStepPx
                            )
                            if (count != previewCount) {
                                previewCount = count
                                currentOnAction(
                                    KeyboardAction.PreviewDeleteBackward(
                                        graphemeCount = count,
                                        cursorPosition = gestureCursorPosition
                                    )
                                )
                            }
                        }
                        if (!change.pressed) {
                            finished = true
                            when {
                                canceled -> Unit
                                swiping -> currentOnAction(key.action)
                                !repeatStarted -> currentOnAction(key.action)
                            }
                            change.consume()
                            break
                        }
                        change.consume()
                    }
                } finally {
                    finished = true
                    repeatJob?.cancel()
                    if (canceled && swiping) {
                        currentOnAction(KeyboardAction.RestoreCursor(gestureCursorPosition))
                    }
                    isPressed = false
                }
            }
        }
    } else {
        Modifier.pointerInput(action) {
            awaitEachGesture {
                try {
                    awaitFirstDown().consume()
                    isPressed = true
                    val up = waitForUpOrCancellation()
                    isPressed = false
                    if (up != null) {
                        up.consume()
                        currentOnAction(key.action)
                    }
                } finally {
                    isPressed = false
                }
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(activeContainerColor)
            .semantics {
                contentDescription = key.contentDescription
                role = Role.Button
                onClick {
                    onAction(key.action)
                    true
                }
            }
            .then(pointerModifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(id = map[action]!!),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = myColor.iconColor
        )

        if (action == "SHIFT") {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (isLandscape) 5.dp else 6.dp)
                    .size(width = if (isLandscape) 10.dp else 12.dp, height = 3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(myColor.iconColor.copy(alpha = 0.86f))
            )
        }
    }
}

private const val DELETE_REPEAT_INTERVAL_MS = 50L

@Preview
@Composable
fun FunctionKeyIconPreview() {
    FunctionKeyIcon(
        key = KeyboardKeyDefinition(
            id = "preview-shift",
            label = "shift",
            contentDescription = "Shift off",
            role = KeyboardKeyRole.SHIFT,
            action = KeyboardAction.Shift
        ),
        myColor = LightKeyboardColors,
        onAction = {}
    )
}
