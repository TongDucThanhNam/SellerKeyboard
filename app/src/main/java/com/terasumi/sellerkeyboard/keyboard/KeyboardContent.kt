package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.R
import com.terasumi.sellerkeyboard.ui.theme.DarkCustomColor
import com.terasumi.sellerkeyboard.ui.theme.LightCustomColor


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardContent() {
    val context = LocalContext.current
    val resources = context.resources

    val keyboard = remember {
        resources.getString(R.string.keyboard)
    }

    val snippetText = remember {
        resources.getString(R.string.snippet_list)
    }
    val calculatorText = remember {
        resources.getString(R.string.calculator)
    }

    val keyboardState = remember {
        mutableStateOf(
            KeyboardState.NOCAPS
        )
    }

    val myColor = if (isSystemInDarkTheme()) {
        DarkCustomColor
    } else {
        LightCustomColor
    }

    var state by remember { mutableIntStateOf(0) }
    val titles = listOf(keyboard, snippetText, calculatorText)

    //Keyboard Array
    val keyboardArray: Array<Array<String>> = getKeyboardArray(keyboardState.value)

    Column(
        modifier = Modifier
            .background(myColor[3])

    ) {
        //TabLayout of Keyboard
        PrimaryTabRow(
            selectedTabIndex = state,
            modifier = Modifier.background(myColor[3]),
            contentColor = myColor[5]
        ) {
            titles.forEachIndexed { index, title ->
                Tab(

                    selected = state == index,
                    onClick = { state = index },
                    text = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.background(myColor[3]),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

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
        when (keyboardState.value) {
            KeyboardState.SNIPPETS -> {
                SnippetsKeyboard(myColor = myColor)
            }

            KeyboardState.CALCULATOR -> {
                CalculatorKeyboard(myColor = myColor)
            }

            else -> {
                Keyboard(
                    myColor = myColor,
                    keyboardArray = keyboardArray,
                    keyboardState = keyboardState
                )
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
            arrayOf("?ABC", ",", "numbers", "space", ".", "enter")
        )
        //~`|•√π÷×§∆£€$¢^°={}\%©®™✓[]
        KeyboardState.SYMBOLS -> arrayOf(
            arrayOf("~", "`", "|", "•", "√", "π", "÷", "×", "§", "∆"),
            arrayOf("£", "€", "$", "¢", "^", "°", "=", "{", "}", "\\"),
            arrayOf("123?", "%", "©", "®", "™", "✓", "[", "]", "delete"),
            arrayOf("?ABC", "<", "numbers", "space", ">", "enter")
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