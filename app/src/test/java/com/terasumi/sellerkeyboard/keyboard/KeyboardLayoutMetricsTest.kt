package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardLayoutMetricsTest {

    private val baseUnit = 30.dp
    private val keyGap = 6.dp

    @Test
    fun resolveKeyboardRowMetrics_alphaHomeRow_centersWithHalfUnitAndHalfGapMargins() {
        val metrics = resolveKeyboardRowMetrics(
            keyList = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            baseUnit = baseUnit,
            keyGap = keyGap,
            rowCount = 5
        )

        assertEquals(18f, metrics.leadingMargin.value, 0.001f)
        assertEquals(18f, metrics.trailingMargin.value, 0.001f)
        assertEquals(30f, metrics.keyWidths.first().value, 0.001f)
        assertEquals(30f, metrics.keyWidths.last().value, 0.001f)
    }

    @Test
    fun resolveKeyboardRowMetrics_shiftRow_usesWideEdgeKeysAndFullRowWidth() {
        val keys = listOf("shift", "z", "x", "c", "v", "b", "n", "m", "delete")
        val metrics = resolveKeyboardRowMetrics(
            keyList = keys,
            baseUnit = baseUnit,
            keyGap = keyGap,
            rowCount = 5
        )

        assertEquals(48f, metrics.keyWidths.first().value, 0.001f)
        assertEquals(48f, metrics.keyWidths.last().value, 0.001f)
        assertEquals(0f, metrics.leadingMargin.value, 0.001f)
        assertEquals(0f, metrics.trailingMargin.value, 0.001f)

        val total = metrics.leadingMargin +
            metrics.trailingMargin +
            metrics.keyWidths.reduce { acc, dp -> acc + dp } +
            (keyGap * (keys.size - 1).toFloat())

        assertEquals((baseUnit * 10f + keyGap * 9f).value, total.value, 0.001f)
    }

    @Test
    fun resolveKeyboardRowMetrics_bottomAlphaRow_usesCanonicalSpaceWidthAndFullRowWidth() {
        val keys = listOf("?123", "@", ",", "space", ".", "enter")
        val metrics = resolveKeyboardRowMetrics(
            keyList = keys,
            baseUnit = baseUnit,
            keyGap = keyGap,
            rowCount = 5
        )

        assertEquals(48f, metrics.keyWidths.first().value, 0.001f)
        assertEquals(48f, metrics.keyWidths.last().value, 0.001f)
        assertEquals(138f, metrics.keyWidths[3].value, 0.001f)

        val total = metrics.leadingMargin +
            metrics.trailingMargin +
            metrics.keyWidths.reduce { acc, dp -> acc + dp } +
            (keyGap * (keys.size - 1).toFloat())

        assertEquals((baseUnit * 10f + keyGap * 9f).value, total.value, 0.001f)
    }
}
