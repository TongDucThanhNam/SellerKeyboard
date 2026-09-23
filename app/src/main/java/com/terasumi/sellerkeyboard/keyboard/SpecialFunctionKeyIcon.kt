package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.R
import com.terasumi.sellerkeyboard.keyboard.theme.LightKeyboardColors
import com.terasumi.sellerkeyboard.keyboard.theme.KeyboardColors

@Composable
fun SpecialFunctionKeyIcon(
    contentDescription: String,
    myColor: KeyboardColors,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier,
    onPress: () -> Unit,
) {
    val iconSize = if (isLandscape) 20.dp else 22.dp

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(if (isLandscape) 10.dp else 14.dp))
            .background(myColor.keyActionBackground)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
                onClick {
                    onPress()
                    true
                }
            }
            .pointerInput(contentDescription) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        up.consume()
                        onPress()
                    }
                    down.consume()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (contentDescription == "Enter" || contentDescription == "Return") {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.enter),
                contentDescription = null,
                tint = myColor.iconColor,
                modifier = Modifier.size(iconSize)
            )
        } else {
            Text(
                text = contentDescription,
                color = myColor.iconColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview
@Composable
fun SpecialFunctionKeyIconPreview() {
    SpecialFunctionKeyIcon(
        contentDescription = "Editor action",
        myColor = LightKeyboardColors,
        onPress = {}
    )
}
