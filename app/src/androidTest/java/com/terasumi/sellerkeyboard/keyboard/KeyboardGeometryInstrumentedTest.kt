package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.ime.EditorSession
import com.terasumi.sellerkeyboard.ime.EditorPrivacyPolicy
import com.terasumi.sellerkeyboard.keyboard.theme.LightKeyboardColors
import com.terasumi.sellerkeyboard.settings.KeyboardHeightPreset
import com.terasumi.sellerkeyboard.settings.OneHandedSide
import org.junit.Rule
import org.junit.Test

class KeyboardGeometryInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun standardPortraitAndOneHanded_primaryKeysRemainAtLeast48DpTall() {
        val initialLayout = KeyboardLayoutResolver.resolve(
            session = EditorSession.Inactive,
            state = KeyboardState.NOCAPS,
            includeNumberRow = true
        )
        val keyboardState = mutableStateOf(KeyboardState.NOCAPS)
        val sensitiveMode = mutableStateOf(false)
        val dispatchedActions = mutableListOf<KeyboardAction>()
        val toolbarItems = listOf(
            KeyboardPageItem(KeyboardPage.Keyboard, "keyboard", Icons.Default.Keyboard, "Keyboard"),
            KeyboardPageItem(KeyboardPage.Snippets, "replies", Icons.Default.Keyboard, "Replies"),
            KeyboardPageItem(KeyboardPage.Calculator, "calculator", Icons.Default.Keyboard, "Calculator"),
            KeyboardPageItem(KeyboardPage.Emoji, "emoji", Icons.Default.Keyboard, "Emoji"),
            KeyboardPageItem(KeyboardPage.Translate, "translate", Icons.Default.Keyboard, "Translate"),
            KeyboardPageItem(KeyboardPage.Products, "products", Icons.Default.Keyboard, "Products"),
            KeyboardPageItem(KeyboardPage.Clipboard, "clipboard", Icons.Default.Keyboard, "Clipboard"),
            KeyboardPageItem(null, "voice", Icons.Default.Keyboard, "Voice"),
            KeyboardPageItem(null, "settings", Icons.Default.Keyboard, "Settings")
        )
        val normalToolbarLabels = toolbarItemsForPrivacy(
            toolbarItems,
            EditorPrivacyPolicy.resolve(isPassword = false)
        ).map { it.label }
        val sensitiveToolbarLabels = toolbarItemsForPrivacy(
            toolbarItems,
            EditorPrivacyPolicy.resolve(isPassword = true)
        ).map { it.label }
        check(normalToolbarLabels.containsAll(prohibitedToolbarLabels))
        check(sensitiveToolbarLabels.none(prohibitedToolbarLabels::contains))
        composeRule.setContent {
            val currentState = keyboardState.value
            val privacy = EditorPrivacyPolicy.resolve(isPassword = sensitiveMode.value)
            val layout = KeyboardLayoutResolver.resolve(
                session = EditorSession.Inactive,
                state = currentState,
                includeNumberRow = true
            )
            Column {
                KeyboardToolbar(
                    myColor = LightKeyboardColors,
                    pageItems = toolbarItemsForPrivacy(toolbarItems, privacy),
                    currentPage = KeyboardPage.Keyboard,
                    voiceCancelLabel = "Cancel voice",
                    isLandscape = false,
                    isPasswordField = privacy.isSensitive,
                    canUseVoiceInput = true,
                    isVoiceActive = false,
                    activeProfileName = "Global",
                    onSelectPage = {},
                    onOpenSettings = {},
                    onVoiceInput = {}
                )
                Keyboard(
                    keyboardRows = layout.rows,
                    keyboardState = currentState,
                    myColor = LightKeyboardColors,
                    isLandscape = false,
                    heightPreset = KeyboardHeightPreset.STANDARD,
                    oneHandedSide = OneHandedSide.RIGHT,
                    onAction = { dispatchedActions += it }
                )
            }
        }

        initialLayout.rows.flatten().forEach { key ->
            composeRule.onNodeWithContentDescription(key.contentDescription)
                .assertHasClickAction()
                .assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.Role,
                        Role.Button
                    )
                )
        }
        composeRule.onNodeWithContentDescription("q")
            .assertHeightIsAtLeast(48.dp)
            .assertHasClickAction()
            .performClick()
        composeRule.onNodeWithContentDescription("a")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
        composeRule.onNodeWithContentDescription("Space")
            .assertHeightIsAtLeast(48.dp)
            .assertHasClickAction()
        composeRule.onNodeWithContentDescription("Shift off").assertHasClickAction()
        composeRule.runOnIdle {
            check(dispatchedActions == listOf(KeyboardAction.KeyPress("q")))
            keyboardState.value = KeyboardState.DOUBLECAPS
            sensitiveMode.value = true
        }
        composeRule.onNodeWithContentDescription("Caps Lock on").assertHasClickAction()
        composeRule.onNodeWithContentDescription("Keyboard").assertHasClickAction()
        composeRule.onNodeWithContentDescription("Settings").assertHasClickAction()
    }

    private companion object {
        val prohibitedToolbarLabels = listOf(
            "Replies", "Calculator", "Emoji", "Translate", "Products", "Clipboard", "Voice"
        )
    }
}
