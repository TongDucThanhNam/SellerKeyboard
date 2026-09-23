package com.terasumi.sellerkeyboard.autofill

import com.terasumi.sellerkeyboard.ime.EditorInfoPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineAutofillControllerTest {
    private val textInputType = 1
    private val passwordInputType = 1 or 128

    @Test
    fun disabledSetting_preventsInlineAutofillRequest() {
        val controller = InlineAutofillController<String>(
            enabledProvider = { false },
            editorInfoPolicyProvider = { EditorInfoPolicy.fromInputType(textInputType) }
        )

        assertFalse(controller.shouldCreateRequest())
    }

    @Test
    fun invalidEditorState_preventsInlineAutofillRequest() {
        val controller = InlineAutofillController<String>(
            enabledProvider = { true },
            editorInfoPolicyProvider = { EditorInfoPolicy.fromInputType(0) }
        )

        assertFalse(controller.shouldCreateRequest())
    }

    @Test
    fun passwordEditor_preservesSystemOwnedInlineAutofillBoundary() {
        val controller = InlineAutofillController<String>(
            enabledProvider = { true },
            editorInfoPolicyProvider = { EditorInfoPolicy.fromInputType(passwordInputType) }
        )

        assertTrue(controller.shouldCreateRequest())
    }

    @Test
    fun emptyResponse_clearsExistingSuggestions() {
        val controller = InlineAutofillController<String>(
            enabledProvider = { true },
            editorInfoPolicyProvider = { EditorInfoPolicy.fromInputType(textInputType) }
        )
        val generation = controller.beginResponse()
        assertTrue(controller.appendInflatedSuggestion(generation, "suggestion"))

        controller.beginResponse()

        assertFalse(controller.state.value.hasSuggestions)
        assertEquals(emptyList<InlineAutofillSuggestion<String>>(), controller.state.value.suggestions)
    }

    @Test
    fun staleInflationCallback_isIgnoredAfterNewResponse() {
        val controller = InlineAutofillController<String>(
            enabledProvider = { true },
            editorInfoPolicyProvider = { EditorInfoPolicy.fromInputType(textInputType) }
        )
        val oldGeneration = controller.beginResponse()
        val newGeneration = controller.beginResponse()

        assertFalse(controller.appendInflatedSuggestion(oldGeneration, "old"))
        assertTrue(controller.appendInflatedSuggestion(newGeneration, "new"))

        assertEquals(listOf("new"), controller.state.value.suggestions.map { it.content })
    }

    @Test
    fun clearInvalidatesPendingInflationCallback() {
        val controller = InlineAutofillController<String>(
            enabledProvider = { true },
            editorInfoPolicyProvider = { EditorInfoPolicy.fromInputType(textInputType) }
        )
        val generation = controller.beginResponse()

        controller.clear()

        assertFalse(controller.appendInflatedSuggestion(generation, "stale"))
        assertFalse(controller.state.value.hasSuggestions)
    }

    @Test
    fun suggestions_areBoundedByConfiguredMaximum() {
        val controller = InlineAutofillController<String>(
            enabledProvider = { true },
            editorInfoPolicyProvider = { EditorInfoPolicy.fromInputType(textInputType) },
            maxSuggestionCount = 2
        )
        val generation = controller.beginResponse()

        assertTrue(controller.appendInflatedSuggestion(generation, "one"))
        assertTrue(controller.appendInflatedSuggestion(generation, "two"))
        assertFalse(controller.appendInflatedSuggestion(generation, "three"))

        assertEquals(listOf("one", "two"), controller.state.value.suggestions.map { it.content })
    }

    @Test
    fun presentationBounds_convertDpToPxAndKeepMaxAtLeastMin() {
        val portrait = InlineAutofillRequestConfig.presentationBounds(
            density = 2f,
            isLandscape = false
        )
        val landscape = InlineAutofillRequestConfig.presentationBounds(
            density = 2f,
            isLandscape = true
        )

        assertEquals(224, portrait.minWidthPx)
        assertEquals(96, portrait.minHeightPx)
        assertEquals(520, portrait.maxWidthPx)
        assertEquals(96, portrait.maxHeightPx)
        assertEquals(640, landscape.maxWidthPx)
        assertEquals(96, landscape.maxHeightPx)
        assertTrue(portrait.maxWidthPx >= portrait.minWidthPx)
        assertTrue(landscape.maxHeightPx >= landscape.minHeightPx)
    }
}
