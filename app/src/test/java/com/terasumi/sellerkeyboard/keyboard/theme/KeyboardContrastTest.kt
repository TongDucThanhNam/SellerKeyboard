package com.terasumi.sellerkeyboard.keyboard.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class KeyboardContrastTest {
    @Test
    fun highContrastThemes_keepTextAndIconsAtLeastWcagAa() {
        listOf(HighContrastLightKeyboardColors, HighContrastDarkKeyboardColors).forEach { colors ->
            listOf(
                colors.keyBackground,
                colors.keyAltBackground,
                colors.keyActionBackground,
                colors.keyboardBackground,
                colors.spaceBarBackground
            ).forEach { background ->
                val ratio = contrastRatio(colors.iconColor, background)
                assertTrue("contrast=$ratio for $colors on $background", ratio >= 4.5)
            }
        }
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val lighter = max(relativeLuminance(foreground), relativeLuminance(background))
        val darker = min(relativeLuminance(foreground), relativeLuminance(background))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val component = value.toDouble()
            return if (component <= 0.04045) component / 12.92
            else Math.pow((component + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }
}
