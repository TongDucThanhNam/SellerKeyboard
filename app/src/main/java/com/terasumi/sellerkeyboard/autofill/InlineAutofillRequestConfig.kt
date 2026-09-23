package com.terasumi.sellerkeyboard.autofill

import kotlin.math.roundToInt

object InlineAutofillRequestConfig {
    const val MAX_SUGGESTION_COUNT = 3
    const val BAR_HEIGHT_PORTRAIT_DP = 52
    const val BAR_HEIGHT_LANDSCAPE_DP = 52

    private const val MIN_WIDTH_DP = 112
    private const val MIN_HEIGHT_PORTRAIT_DP = 48
    private const val MIN_HEIGHT_LANDSCAPE_DP = 48
    private const val MAX_WIDTH_PORTRAIT_DP = 260
    private const val MAX_WIDTH_LANDSCAPE_DP = 320
    private const val MAX_HEIGHT_PORTRAIT_DP = 48
    private const val MAX_HEIGHT_LANDSCAPE_DP = 48

    fun presentationBounds(density: Float, isLandscape: Boolean): InlineAutofillPresentationBounds {
        val safeDensity = if (density > 0f) density else 1f
        return InlineAutofillPresentationBounds(
            minWidthPx = dpToPx(MIN_WIDTH_DP, safeDensity),
            minHeightPx = dpToPx(
                if (isLandscape) MIN_HEIGHT_LANDSCAPE_DP else MIN_HEIGHT_PORTRAIT_DP,
                safeDensity
            ),
            maxWidthPx = dpToPx(
                if (isLandscape) MAX_WIDTH_LANDSCAPE_DP else MAX_WIDTH_PORTRAIT_DP,
                safeDensity
            ),
            maxHeightPx = dpToPx(
                if (isLandscape) MAX_HEIGHT_LANDSCAPE_DP else MAX_HEIGHT_PORTRAIT_DP,
                safeDensity
            )
        )
    }

    private fun dpToPx(dp: Int, density: Float): Int {
        return (dp * density).roundToInt().coerceAtLeast(1)
    }
}

data class InlineAutofillPresentationBounds(
    val minWidthPx: Int,
    val minHeightPx: Int,
    val maxWidthPx: Int,
    val maxHeightPx: Int
) {
    init {
        require(minWidthPx > 0)
        require(minHeightPx > 0)
        require(maxWidthPx >= minWidthPx)
        require(maxHeightPx >= minHeightPx)
    }
}
