package com.terasumi.sellerkeyboard.keyboard

import kotlin.math.ceil
import kotlin.math.abs

object KeyboardGesturePolicy {
    @Suppress("UNUSED_PARAMETER")
    fun cursorSteps(
        horizontalDistancePx: Float,
        touchSlopPx: Float,
        stepPx: Float,
        horizontalVelocityPxPerSecond: Float = 0f
    ): Int {
        if (stepPx <= 0f || abs(horizontalDistancePx) <= touchSlopPx) return 0
        return (horizontalDistancePx / stepPx).toInt()
    }

    fun deletePreviewCount(
        horizontalDistancePx: Float,
        touchSlopPx: Float,
        stepPx: Float,
        @Suppress("UNUSED_PARAMETER") horizontalVelocityPxPerSecond: Float = 0f
    ): Int {
        if (stepPx <= 0f || horizontalDistancePx >= -touchSlopPx) return 0
        return ceil((-horizontalDistancePx - touchSlopPx) / stepPx)
            .toInt()
            .coerceAtLeast(1)
    }
}
