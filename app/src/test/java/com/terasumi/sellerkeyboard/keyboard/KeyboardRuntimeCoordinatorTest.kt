package com.terasumi.sellerkeyboard.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardRuntimeCoordinatorTest {
    @Test
    fun stateSurvivesConsumersAndResetReturnsSafeDefault() {
        val coordinator = KeyboardRuntimeCoordinator()
        val holderSeenByFirstView = coordinator.state
        coordinator.update(
            KeyboardRuntimeState(
                shiftMode = ShiftMode.CAPS_LOCK,
                surface = KeyboardSurface.SYMBOLS
            )
        )

        val holderSeenByRecreatedView = coordinator.state
        assertEquals(holderSeenByFirstView, holderSeenByRecreatedView)
        assertEquals(ShiftMode.CAPS_LOCK, holderSeenByRecreatedView.value.shiftMode)
        assertEquals(KeyboardSurface.SYMBOLS, holderSeenByRecreatedView.value.surface)

        coordinator.reset()
        assertEquals(KeyboardRuntimeState(), coordinator.state.value)
    }
}
