package com.terasumi.sellerkeyboard.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Spacing scale for consistent paddings/margins
data class Spacing(
    val xSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val xLarge: Dp = 32.dp,
)

// Size tokens for common component dimensions
data class Sizes(
    val inputHeight: Dp = 56.dp,
    val imageThumb: Dp = 110.dp,
    val iconMedium: Dp = 28.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
val LocalSizes = staticCompositionLocalOf { Sizes() }

val MaterialTheme.spacing: Spacing
    @Composable get() = LocalSpacing.current

val MaterialTheme.sizes: Sizes
    @Composable get() = LocalSizes.current

