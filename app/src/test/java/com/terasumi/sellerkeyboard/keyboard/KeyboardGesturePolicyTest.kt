package com.terasumi.sellerkeyboard.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardGesturePolicyTest {
    @Test
    fun cursorMovement_requiresTouchSlopAndSupportsBothDirections() {
        assertEquals(0, KeyboardGesturePolicy.cursorSteps(7f, touchSlopPx = 8f, stepPx = 12f))
        assertEquals(2, KeyboardGesturePolicy.cursorSteps(25f, touchSlopPx = 8f, stepPx = 12f))
        assertEquals(-2, KeyboardGesturePolicy.cursorSteps(-25f, touchSlopPx = 8f, stepPx = 12f))
    }

    @Test
    fun deletePreview_onlyActivatesToTheLeftAndGrowsDeterministically() {
        assertEquals(0, KeyboardGesturePolicy.deletePreviewCount(20f, 8f, 24f))
        assertEquals(0, KeyboardGesturePolicy.deletePreviewCount(-8f, 8f, 24f))
        assertEquals(1, KeyboardGesturePolicy.deletePreviewCount(-9f, 8f, 24f))
        assertEquals(2, KeyboardGesturePolicy.deletePreviewCount(-40f, 8f, 24f))
    }

    @Test
    fun velocity_neverBypassesTouchSlopOrChangesDistanceBasedSelection() {
        assertEquals(
            0,
            KeyboardGesturePolicy.cursorSteps(
                horizontalDistancePx = 7f,
                touchSlopPx = 8f,
                stepPx = 12f,
                horizontalVelocityPxPerSecond = 20_000f
            )
        )
        assertEquals(
            KeyboardGesturePolicy.deletePreviewCount(-40f, 8f, 24f, 50f),
            KeyboardGesturePolicy.deletePreviewCount(-40f, 8f, 24f, 20_000f)
        )
    }
}
