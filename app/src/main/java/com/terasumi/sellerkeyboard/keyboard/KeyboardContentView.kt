package com.terasumi.sellerkeyboard.keyboard

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.Density

internal const val MAX_KEYBOARD_FONT_SCALE = 1.15f

internal fun cappedKeyboardFontScale(systemFontScale: Float): Float =
    systemFontScale.coerceAtMost(MAX_KEYBOARD_FONT_SCALE)

class KeyboardContentView(context: Context) : AbstractComposeView(context) {
    @Composable
    override fun Content() {
        val systemDensity = LocalDensity.current
        val keyboardDensity = remember(systemDensity.density, systemDensity.fontScale) {
            Density(
                density = systemDensity.density,
                fontScale = cappedKeyboardFontScale(systemDensity.fontScale)
            )
        }
        // An IME is a fixed-height surface: allowing unbounded system font scale
        // makes keys overlap and hides editor actions. Cap visual glyph scaling
        // while retaining full-size touch targets and uncapped TalkBack semantics.
        CompositionLocalProvider(LocalDensity provides keyboardDensity) {
            KeyboardContent()
        }
    }
}
