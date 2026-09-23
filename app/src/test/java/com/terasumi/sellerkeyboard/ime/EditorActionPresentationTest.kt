package com.terasumi.sellerkeyboard.ime

import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorActionPresentationTest {
    @Test
    fun standardActions_haveSemanticDescriptions() {
        val expected = mapOf(
            EditorInfo.IME_ACTION_GO to "Go",
            EditorInfo.IME_ACTION_SEARCH to "Search",
            EditorInfo.IME_ACTION_SEND to "Send",
            EditorInfo.IME_ACTION_NEXT to "Next",
            EditorInfo.IME_ACTION_PREVIOUS to "Previous",
            EditorInfo.IME_ACTION_DONE to "Done"
        )

        expected.forEach { (action, label) ->
            assertEquals(
                label,
                EditorActionPresentation.contentDescription(EditorSession.Inactive.copy(actionId = action))
            )
        }
    }

    @Test
    fun customActionLabel_takesPriority() {
        val session = EditorSession.Inactive.copy(actionId = 42, actionLabel = "Publish draft")
        assertEquals("Publish draft", EditorActionPresentation.contentDescription(session))
    }
}
