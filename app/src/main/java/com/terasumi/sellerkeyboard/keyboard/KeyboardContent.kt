package com.terasumi.sellerkeyboard.keyboard

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.HomeActivity
import com.terasumi.sellerkeyboard.R
import com.terasumi.sellerkeyboard.service.SellerKeyboard
import com.terasumi.sellerkeyboard.keyboard.theme.LightKeyboardColors
import com.terasumi.sellerkeyboard.keyboard.theme.DarkKeyboardColors
import com.terasumi.sellerkeyboard.keyboard.theme.KeyboardColors

private enum class KeyboardPage {
    Keyboard,
    Snippets,
    Calculator,
}

private data class KeyboardPageItem(
    val page: KeyboardPage,
    val icon: ImageVector,
    val label: String
)

@Composable
fun KeyboardContent() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val myColor = if (androidx.compose.foundation.isSystemInDarkTheme()) {
        DarkKeyboardColors
    } else {
        LightKeyboardColors
    }

    val keyboardLabel = stringResource(R.string.keyboard)
    val snippetLabel = stringResource(R.string.snippet_list)
    val calculatorLabel = stringResource(R.string.calculator)
    val voiceLabel = stringResource(R.string.voice)
    val settingsLabel = stringResource(R.string.settings)

    val pageItems = remember(keyboardLabel, snippetLabel, calculatorLabel) {
        listOf(
            KeyboardPageItem(
                page = KeyboardPage.Keyboard,
                icon = Icons.Default.Keyboard,
                label = keyboardLabel
            ),
            KeyboardPageItem(
                page = KeyboardPage.Snippets,
                icon = Icons.AutoMirrored.Filled.TextSnippet,
                label = snippetLabel
            ),
            KeyboardPageItem(
                page = KeyboardPage.Calculator,
                icon = Icons.Default.Calculate,
                label = calculatorLabel
            )
        )
    }

    var currentPage by rememberSaveable { mutableStateOf(KeyboardPage.Keyboard) }
    var keyboardState by rememberSaveable { mutableStateOf(KeyboardState.NOCAPS) }
    var lastShiftTapTime by remember { mutableStateOf(0L) }
    val keyboardRows = remember(keyboardState) { getKeyboardRows(keyboardState) }

    val currentInputType = (context as? SellerKeyboard)?.currentInputTypeState?.value ?: 0
    val isPasswordField = remember(currentInputType) {
        isPasswordInputType(currentInputType)
    }

    androidx.compose.runtime.LaunchedEffect(currentInputType) {
        keyboardState = resolveKeyboardStateForInputType(currentInputType, keyboardState)
    }

    fun handleKeyboardAction(action: KeyboardAction) {
        context.performKeyboardHaptic()
        val inputConnection = context.currentInputConnectionOrNull()
        when (action) {
            is KeyboardAction.KeyPress -> {
                inputConnection?.commitText(action.key, 1)
                // Single-shift: after typing one character, revert to NOCAPS
                if (keyboardState == KeyboardState.CAPS) {
                    keyboardState = KeyboardState.NOCAPS
                }
            }
            KeyboardAction.Delete -> {
                inputConnection?.deleteSurroundingText(1, 0)
            }
            KeyboardAction.Space -> {
                inputConnection?.commitText(" ", 1)
                // Single-shift: space also reverts to NOCAPS
                if (keyboardState == KeyboardState.CAPS) {
                    keyboardState = KeyboardState.NOCAPS
                }
            }
            KeyboardAction.Enter -> {
                inputConnection?.sendKeyEvent(
                    android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER)
                )
                // Single-shift: enter also reverts to NOCAPS
                if (keyboardState == KeyboardState.CAPS) {
                    keyboardState = KeyboardState.NOCAPS
                }
            }
            KeyboardAction.Shift -> {
                val now = System.currentTimeMillis()
                keyboardState = resolveShiftState(keyboardState, now - lastShiftTapTime)
                lastShiftTapTime = now
            }
            is KeyboardAction.SwitchState -> {
                keyboardState = action.newState
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(myColor.keyboardBackground)
    ) {
        KeyboardToolbar(
            myColor = myColor,
            pageItems = pageItems,
            currentPage = currentPage,
            settingsLabel = settingsLabel,
            voiceLabel = voiceLabel,
            isLandscape = isLandscape,
            isPasswordField = isPasswordField,
            onSelectPage = { selectedPage ->
                context.performKeyboardHaptic()
                currentPage = selectedPage
            },
            onOpenSettings = {
                context.performKeyboardHaptic()
                context.openKeyboardSettings()
            },
            onVoiceInput = {
                context.performKeyboardHaptic()
                val imeService = context as? SellerKeyboard
                imeService?.mVoiceRecognitionTrigger?.startVoiceRecognition(imeService.imeLanguage)
            }
        )

        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                (fadeIn(animationSpec = tween(durationMillis = 180)) +
                    scaleIn(
                        animationSpec = tween(durationMillis = 180),
                        initialScale = 0.99f
                    )) togetherWith
                    fadeOut(animationSpec = tween(durationMillis = 120))
            },
            label = "Keyboard page switch"
        ) { page ->
            when (page) {
                KeyboardPage.Snippets -> {
                    SnippetsKeyboard(
                        myColor = myColor,
                        isLandscape = isLandscape,
                        onAction = ::handleKeyboardAction
                    )
                }

                KeyboardPage.Calculator -> {
                    CalculatorKeyboard(
                        myColor = myColor,
                        isLandscape = isLandscape,
                        onAction = ::handleKeyboardAction
                    )
                }

                KeyboardPage.Keyboard -> {
                    Keyboard(
                        keyboardRows = keyboardRows,
                        keyboardState = keyboardState,
                        myColor = myColor,
                        isLandscape = isLandscape,
                        onAction = ::handleKeyboardAction
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyboardToolbar(
    myColor: KeyboardColors,
    pageItems: List<KeyboardPageItem>,
    currentPage: KeyboardPage,
    settingsLabel: String,
    voiceLabel: String,
    isLandscape: Boolean,
    isPasswordField: Boolean,
    onSelectPage: (KeyboardPage) -> Unit,
    onOpenSettings: () -> Unit,
    onVoiceInput: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isLandscape) 8.dp else 10.dp,
                vertical = if (isLandscape) 3.dp else 6.dp
            )
            .background(
                color = myColor.keyboardBackground.copy(alpha = if (isLandscape) 0.92f else 0.96f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(
                horizontal = if (isLandscape) 4.dp else 6.dp,
                vertical = 2.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 6.dp)
    ) {
        pageItems.forEach { item ->
            ToolbarIconButton(
                icon = item.icon,
                contentDescription = item.label,
                selected = currentPage == item.page,
                myColor = myColor,
                onClick = { onSelectPage(item.page) }
            )
        }

        Box(modifier = Modifier.weight(1f))

        ToolbarIconButton(
            icon = Icons.Default.Settings,
            contentDescription = settingsLabel,
            selected = false,
            myColor = myColor,
            onClick = onOpenSettings
        )

        ToolbarIconButton(
            icon = Icons.Default.KeyboardVoice,
            contentDescription = voiceLabel,
            selected = true,
            myColor = myColor,
            emphasize = true,
            enabled = !isPasswordField,
            onClick = onVoiceInput
        )
    }
}

@Composable
private fun ToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    myColor: KeyboardColors,
    onClick: () -> Unit,
    emphasize: Boolean = false,
    enabled: Boolean = true
) {
    val container = when {
        !enabled -> Color.Transparent
        emphasize -> myColor.keyBackground.copy(alpha = 0.92f)
        selected -> myColor.keyActionBackground.copy(alpha = 0.36f)
        else -> Color.Transparent
    }
    val tint = if (!enabled) {
        myColor.iconColor.copy(alpha = 0.3f)
    } else if (selected || emphasize) {
        myColor.iconColor
    } else {
        myColor.iconColor.copy(alpha = 0.72f)
    }

    Box(
        modifier = Modifier
            .background(
                color = container,
                shape = CircleShape
            )
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(if (emphasize) 44.dp else 40.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint
            )
        }
    }
}

private fun Context.openKeyboardSettings() {
    runCatching {
        startActivity(
            Intent(this, HomeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}

fun getKeyboardRows(keyboardState: KeyboardState): List<List<String>> {
    return when (keyboardState) {
        KeyboardState.CAPS -> CAPS_LAYOUT
        KeyboardState.NOCAPS -> NO_CAPS_LAYOUT
        KeyboardState.DOUBLECAPS -> DOUBLE_CAPS_LAYOUT
        KeyboardState.NUMBERS -> NUMBER_LAYOUT
        KeyboardState.SYMBOLS -> SYMBOL_LAYOUT
    }
}

/**
 * Pure function that resolves the next shift state.
 * - NOCAPS → CAPS (single shift, next key only)
 * - CAPS + quick double-tap (< 400ms) → DOUBLECAPS (caps lock)
 * - DOUBLECAPS → NOCAPS (turn off caps lock)
 */
private const val DOUBLE_TAP_THRESHOLD_MS = 400L

internal fun resolveShiftState(current: KeyboardState, timeSinceLastShift: Long = Long.MAX_VALUE): KeyboardState {
    return when (current) {
        KeyboardState.NOCAPS -> KeyboardState.CAPS
        KeyboardState.CAPS -> {
            if (timeSinceLastShift < DOUBLE_TAP_THRESHOLD_MS) {
                KeyboardState.DOUBLECAPS
            } else {
                // Single tap again while in CAPS → turn off
                KeyboardState.NOCAPS
            }
        }
        KeyboardState.DOUBLECAPS -> KeyboardState.NOCAPS
        else -> current
    }
}

internal fun isPasswordInputType(inputType: Int): Boolean {
    val variation = inputType and android.text.InputType.TYPE_MASK_VARIATION
    return variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
}

internal fun resolveKeyboardStateForInputType(inputType: Int, currentState: KeyboardState): KeyboardState {
    val mask = inputType and android.text.InputType.TYPE_MASK_CLASS
    return if (mask == android.text.InputType.TYPE_CLASS_NUMBER ||
        mask == android.text.InputType.TYPE_CLASS_PHONE ||
        mask == android.text.InputType.TYPE_CLASS_DATETIME
    ) {
        KeyboardState.NUMBERS
    } else {
        if (currentState == KeyboardState.NUMBERS || currentState == KeyboardState.SYMBOLS) {
            KeyboardState.NOCAPS
        } else {
            currentState
        }
    }
}

private val CAPS_LAYOUT = listOf(
    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
    listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
    listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
    listOf("Shift", "Z", "X", "C", "V", "B", "N", "M", "delete"),
    listOf("?123", "@", ",", "space", ".", "enter")
)

private val NO_CAPS_LAYOUT = listOf(
    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
    listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
    listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
    listOf("shift", "z", "x", "c", "v", "b", "n", "m", "delete"),
    listOf("?123", "@", ",", "space", ".", "enter")
)

private val DOUBLE_CAPS_LAYOUT = listOf(
    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
    listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
    listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
    listOf("SHIFT", "Z", "X", "C", "V", "B", "N", "M", "delete"),
    listOf("?123", "@", ",", "space", ".", "enter")
)

private val NUMBER_LAYOUT = listOf(
    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
    listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/"),
    listOf("symbols", "*", "\"", "'", ":", ";", "!", "?", "delete"),
    listOf("?ABC", ",", "space", ".", "enter")
)

private val SYMBOL_LAYOUT = listOf(
    listOf("~", "`", "|", "•", "√", "π", "÷", "×", "§", "∆"),
    listOf("£", "€", "$", "¢", "^", "°", "=", "{", "}", "\\"),
    listOf("123?", "%", "©", "®", "™", "✓", "[", "]", "delete"),
    listOf("?ABC", "<", "space", ">", "enter")
)

@Preview(showSystemUi = true)
@Composable
fun KeyboardContentPreview() {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            KeyboardContent()
        }
    }
}

@Preview(showSystemUi = true, device = "spec:width=1280dp,height=800dp,dpi=480")
@Composable
fun KeyboardContentLandscapePreview() {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            KeyboardContent()
        }
    }
}
