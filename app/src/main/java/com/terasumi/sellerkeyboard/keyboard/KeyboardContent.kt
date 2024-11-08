package com.terasumi.sellerkeyboard.keyboard

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.R
import com.terasumi.sellerkeyboard.service.SellerKeyboard
import com.terasumi.sellerkeyboard.ui.theme.DarkCustomColor
import com.terasumi.sellerkeyboard.ui.theme.LightCustomColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun KeyboardContent() {
    val context = LocalContext.current
    val resources = context.resources
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val ratio = configuration.screenWidthDp / 360f


    val keyboard = remember { resources.getString(R.string.keyboard) }
    val snippetText = remember { resources.getString(R.string.snippet_list) }
    val calculatorText = remember { resources.getString(R.string.calculator) }

    val keyboardState = remember { mutableStateOf(KeyboardState.NOCAPS) }
    val selectedIcon = remember { mutableIntStateOf(0) }

    val myColor = if (isSystemInDarkTheme()) DarkCustomColor else LightCustomColor

    var state by remember { mutableIntStateOf(0) }

    //Keyboard Array
    val keyboardArray: Array<Array<String>> = getKeyboardArray(keyboardState.value)

    Column(
        modifier = Modifier
            .background(myColor[3])

    ) {
        //Toolbar
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Keyboard
            Box(
                modifier = Modifier
                    .clickable {
                        selectedIcon.intValue = 0
                        state = 0
                    }
                    .background(
                        color = if (selectedIcon.intValue == 0) myColor[0] else Color.Transparent,
                        shape = CircleShape
                    )
                    .padding(8.dp) // Adjust padding as needed
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = keyboard,
                    tint = myColor[5]
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            // Snippets
            Box(
                modifier = Modifier
                    .clickable {
                        selectedIcon.intValue = 0
                        state = 0
                    }
                    .background(
                        color = if (selectedIcon.value == 1) myColor[0] else Color.Transparent,
                        shape = CircleShape
                    )
                    .padding(8.dp) // Adjust padding as needed
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TextSnippet,
                    contentDescription = snippetText,
                    tint = myColor[5],
                    modifier = Modifier
                        .clickable {
                            selectedIcon.intValue = 1
                            state = 1
                        })
            }
            Spacer(modifier = Modifier.weight(1f))

            // Calculator
            Box(
                modifier = Modifier
                    .clickable {
                        selectedIcon.intValue = 0
                        state = 0
                    }
                    .background(
                        color = if (selectedIcon.intValue == 2) myColor[0] else Color.Transparent,
                        shape = CircleShape
                    )
                    .padding(8.dp) // Adjust padding as needed
            ) {
                Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = calculatorText,
                    tint = myColor[5],
                    modifier = Modifier
                        .clickable {
                            selectedIcon.intValue = 2
                            state = 2
                        })
            }
            Spacer(modifier = Modifier.weight(1f))

            // Voice
            Box(
                modifier = Modifier
                    .clickable {
                        selectedIcon.intValue = 0
                        state = 0
                    }
                    .background(
                        color = if (selectedIcon.intValue == 3) myColor[0] else Color.Transparent,
                        shape = CircleShape
                    )
                    .padding(8.dp) // Adjust padding as needed
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardVoice,
                    contentDescription = "voice",
                    tint = myColor[5],
                    modifier = Modifier
                        .clickable {
                            selectedIcon.intValue = 3
                            // Input Method Service
                            (context as SellerKeyboard).mVoiceRecognitionTrigger!!.startVoiceRecognition(
                                context.imeLanguage
                            )
                        }
                )
            }
        }

        //Content of Keyboard
        when (state) {
            0 -> {
                if (keyboardState.value == KeyboardState.SNIPPETS || keyboardState.value == KeyboardState.CALCULATOR) {
                    keyboardState.value = KeyboardState.NOCAPS
                }
            }

            1 -> {
                keyboardState.value = KeyboardState.SNIPPETS
            }

            2 -> {
                // Calculate
                keyboardState.value = KeyboardState.CALCULATOR
            }
        }

//        Log.d("KeyboardContent", "KeyboardState: ${keyboardState.value}")
        // Keyboard
        //Using AnimatedContent to animate the keyboard change when the keyboard state changes
        AnimatedContent<KeyboardState>(
            targetState = keyboardState.value,
            transitionSpec = {
                // Tùy chỉnh hiệu ứng chuyển tiếp
                fadeIn(
                    animationSpec = tween(
                        durationMillis = 500, // Tăng thời gian hiệu ứng (500ms)
                        easing = FastOutSlowInEasing // Đường cong chuyển động mượt mà
                    )
                ) togetherWith fadeOut(
                    animationSpec = tween(
                        durationMillis = 500, // Tăng thời gian fadeOut
                        easing = FastOutSlowInEasing // Dùng easing để chuyển động mềm mại
                    )
                )
            },
            label = "Switch Keyboard",
        ) { page ->
            when (page) {
                KeyboardState.SNIPPETS -> {
                    SnippetsKeyboard(
                        myColor = myColor,
                        context = context,
                        isLandscape = isLandscape,
                    )
                }

                KeyboardState.CALCULATOR -> {
                    CalculatorKeyboard(
                        myColor = myColor,
                        context = context,
                        isLandscape = isLandscape,
                    )
                }

                else -> {
                    Keyboard(
                        myColor = myColor,
                        keyboardArray = keyboardArray,
                        keyboardState = keyboardState,
                        context = context,
                        isLandscape = isLandscape,
                        ratio = ratio
                    )
                }
            }
        }
    }
}


fun getKeyboardArray(keyboardState: KeyboardState): Array<Array<String>> {
    return when (keyboardState) {
        KeyboardState.CAPS -> arrayOf(
            arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            arrayOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            arrayOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
            arrayOf("Shift", "Z", "X", "C", "V", "B", "N", "M", "delete"),
            arrayOf("?123", "@", ",", "space", ".", "enter")
        )

        KeyboardState.NOCAPS -> arrayOf(
            arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            arrayOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            arrayOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            arrayOf("shift", "z", "x", "c", "v", "b", "n", "m", "delete"),
            arrayOf("?123", "@", ",", "space", ".", "enter")
        )

        KeyboardState.DOUBLECAPS -> arrayOf(
            arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            arrayOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            arrayOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
            arrayOf("SHIFT", "Z", "X", "C", "V", "B", "N", "M", "delete"),
            arrayOf("?123", "@", ",", "space", ".", "enter")
        )

        KeyboardState.NUMBERS -> arrayOf(
            arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            arrayOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/"),
            arrayOf("symbols", "*", "\"", "'", ":", ";", "!", "?", "delete"),
            arrayOf("?ABC", ",", "space", ".", "enter")
        )
        //~`|•√π÷×§∆£€$¢^°={}\%©®™✓[]
        KeyboardState.SYMBOLS -> arrayOf(
            arrayOf("~", "`", "|", "•", "√", "π", "÷", "×", "§", "∆"),
            arrayOf("£", "€", "$", "¢", "^", "°", "=", "{", "}", "\\"),
            arrayOf("123?", "%", "©", "®", "™", "✓", "[", "]", "delete"),
            arrayOf("?ABC", "<", "space", ">", "enter")
        )

        else -> arrayOf()
    }
}

@Preview(showSystemUi = true)
@Composable
fun KeyboardContentPreview() {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
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
            modifier = Modifier
                .align(Alignment.BottomCenter)
        ) {
            KeyboardContent()
        }
    }
}