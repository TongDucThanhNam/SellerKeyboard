package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    action: String,
    myColor: KeyboardColors,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier,
    onAction: (KeyboardAction) -> Unit,
) {
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
        "shift", "Shift", "SHIFT", "delete" -> myColor.keyBackground.copy(alpha = 0.95f)
        else -> myColor.keyAltBackground.copy(alpha = 0.92f)
    }
    val cornerRadius = if (isLandscape) 6.dp else 8.dp
    val iconSize = if (isLandscape) 20.dp else 24.dp
    val scope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }

    val activeContainerColor = if (isPressed) {
        myColor.keyActionBackground.copy(alpha = 0.5f)
    } else {
        containerColor
    }

    val pointerModifier = if (action == "delete") {
        Modifier.pointerInput(action) {
            var repeatJob: Job? = null
            awaitEachGesture {
                val down = awaitFirstDown()
                isPressed = true
                down.consume()
                onAction(KeyboardAction.Delete)

                repeatJob = scope.launch {
                    delay(400L)
                    while (true) {
                        onAction(KeyboardAction.Delete)
                        delay(50L)
                    }
                }

                val up = waitForUpOrCancellation()
                isPressed = false
                repeatJob.cancel()
                up?.consume()
            }
        }
    } else {
        Modifier.pointerInput(action) {
            awaitEachGesture {
                val down = awaitFirstDown()
                isPressed = true
                when (action) {
                    "shift", "Shift", "SHIFT" -> onAction(KeyboardAction.Shift)
                    "symbols" -> onAction(KeyboardAction.SwitchState(KeyboardState.SYMBOLS))
                    "123?" -> onAction(KeyboardAction.SwitchState(KeyboardState.NUMBERS))
                    "numbers" -> onAction(KeyboardAction.SwitchState(KeyboardState.NOCAPS))
                }
                down.consume()
                val up = waitForUpOrCancellation()
                isPressed = false
                up?.consume()
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(activeContainerColor)
            .then(pointerModifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(id = map[action]!!),
            contentDescription = action,
            modifier = Modifier.size(iconSize),
            tint = myColor.iconColor
        )
    }
}

@Preview
@Composable
fun FunctionKeyIconPreview() {
    FunctionKeyIcon(
        action = "shift",
        myColor = LightKeyboardColors,
        onAction = {}
    )
}
