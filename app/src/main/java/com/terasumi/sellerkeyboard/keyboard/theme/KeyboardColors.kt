package com.terasumi.sellerkeyboard.keyboard.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.terasumi.sellerkeyboard.ui.theme.Base
import com.terasumi.sellerkeyboard.ui.theme.DarkAccent1
import com.terasumi.sellerkeyboard.ui.theme.DarkAccent2
import com.terasumi.sellerkeyboard.ui.theme.DarkAccent3
import com.terasumi.sellerkeyboard.ui.theme.DarkAccent4
import com.terasumi.sellerkeyboard.ui.theme.DefaultAccent1
import com.terasumi.sellerkeyboard.ui.theme.DefaultAccent2
import com.terasumi.sellerkeyboard.ui.theme.DefaultAccent3
import com.terasumi.sellerkeyboard.ui.theme.DefaultAccent4

@Immutable
data class KeyboardColors(
    val keyBackground: Color,
    val keyAltBackground: Color,
    val keyActionBackground: Color,
    val keyboardBackground: Color,
    val spaceBarBackground: Color,
    val iconColor: Color
)

val LightKeyboardColors = KeyboardColors(
    keyBackground = DefaultAccent1,
    keyAltBackground = DefaultAccent2,
    keyActionBackground = DefaultAccent3,
    keyboardBackground = DefaultAccent4,
    spaceBarBackground = Base,
    iconColor = DarkAccent3
)

val DarkKeyboardColors = KeyboardColors(
    keyBackground = DarkAccent2,
    keyAltBackground = DarkAccent2,
    keyActionBackground = DarkAccent1,
    keyboardBackground = DarkAccent4,
    spaceBarBackground = DarkAccent3,
    iconColor = Base
)

val HighContrastLightKeyboardColors = KeyboardColors(
    keyBackground = Color.White,
    keyAltBackground = Color(0xFFE6E6E6),
    keyActionBackground = Color(0xFFFFD600),
    keyboardBackground = Color(0xFFF2F2F2),
    spaceBarBackground = Color.White,
    iconColor = Color.Black
)

val HighContrastDarkKeyboardColors = KeyboardColors(
    keyBackground = Color.Black,
    keyAltBackground = Color(0xFF202020),
    keyActionBackground = Color(0xFF005FCC),
    keyboardBackground = Color.Black,
    spaceBarBackground = Color(0xFF101010),
    iconColor = Color.White
)
