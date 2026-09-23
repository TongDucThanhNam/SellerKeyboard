package com.terasumi.sellerkeyboard.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class SpecialFunctionKeySizingTest {
    @Test
    fun actionLabels_shrinkByLengthAndLandscapeConstraint() {
        assertEquals(16, actionKeyFontSizeSp("Go", isLandscape = false))
        assertEquals(14, actionKeyFontSizeSp("Done", isLandscape = false))
        assertEquals(12, actionKeyFontSizeSp("Search", isLandscape = false))
        assertEquals(12, actionKeyFontSizeSp("Done", isLandscape = true))
        assertEquals(10, actionKeyFontSizeSp("Search", isLandscape = true))
    }
}
