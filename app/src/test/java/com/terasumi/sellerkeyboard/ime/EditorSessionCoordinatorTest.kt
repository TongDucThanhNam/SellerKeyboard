package com.terasumi.sellerkeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorSessionCoordinatorTest {
    @Test
    fun callbacksAndUiEvents_updateOneImmutableSessionModel() {
        val coordinator = EditorSessionCoordinator()
        coordinator.start(EditorSession.Inactive.copy(id = 41L, selectionStart = 3, selectionEnd = 3))

        coordinator.updateSelection(start = 7, end = 7, capsMode = 9)
        coordinator.updateSurface(ActiveImeSurface.REPLIES)
        coordinator.updateCandidateSource(ContextStripSource.EXPLICIT_SELLER_WORKFLOW)
        coordinator.updateComposition(text = "xin", start = 4, end = 7)

        assertEquals(41L, coordinator.state.value.id)
        assertEquals(7, coordinator.state.value.selectionEnd)
        assertEquals(9, coordinator.state.value.currentCapsMode)
        assertEquals(ActiveImeSurface.REPLIES, coordinator.state.value.activeSurface)
        assertEquals(
            ContextStripSource.EXPLICIT_SELLER_WORKFLOW,
            coordinator.state.value.candidateSource
        )
        assertEquals("xin", coordinator.state.value.composingText)
        assertEquals(4, coordinator.state.value.composingStart)
        assertEquals(7, coordinator.state.value.composingEnd)

        coordinator.finish()
        assertEquals(EditorSession.Inactive, coordinator.state.value)
    }

    @Test
    fun inactiveSession_ignoresStaleUpdates() {
        val coordinator = EditorSessionCoordinator()

        coordinator.updateSelection(1, 1, 1)
        coordinator.updateSurface(ActiveImeSurface.PRODUCTS)
        coordinator.updateComposition("stale", 0, 5)

        assertEquals(EditorSession.Inactive, coordinator.state.value)
    }
}
