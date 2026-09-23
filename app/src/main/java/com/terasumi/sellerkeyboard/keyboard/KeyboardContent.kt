package com.terasumi.sellerkeyboard.keyboard

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.View
import android.view.ViewConfiguration
import android.os.Trace
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.autofill.InlineAutofillState
import com.terasumi.sellerkeyboard.autofill.EditorCompletionState
import com.terasumi.sellerkeyboard.HomeActivity
import com.terasumi.sellerkeyboard.R
import com.terasumi.sellerkeyboard.ime.ContextStripArbitrator
import com.terasumi.sellerkeyboard.ime.ActiveImeSurface
import com.terasumi.sellerkeyboard.ime.ContextStripAvailability
import com.terasumi.sellerkeyboard.ime.ContextStripSource
import com.terasumi.sellerkeyboard.ime.EditorPrivacyPolicy
import com.terasumi.sellerkeyboard.ime.RichContentDecision
import com.terasumi.sellerkeyboard.ime.RichContentPolicy
import com.terasumi.sellerkeyboard.keyboard.theme.LightKeyboardColors
import com.terasumi.sellerkeyboard.keyboard.theme.DarkKeyboardColors
import com.terasumi.sellerkeyboard.keyboard.theme.HighContrastDarkKeyboardColors
import com.terasumi.sellerkeyboard.keyboard.theme.HighContrastLightKeyboardColors
import com.terasumi.sellerkeyboard.keyboard.theme.KeyboardColors
import com.terasumi.sellerkeyboard.settings.KeyboardThemeMode
import com.terasumi.sellerkeyboard.settings.KeyboardLanguageMode
import com.terasumi.sellerkeyboard.vietnamese.TelexProcessor
import com.terasumi.sellerkeyboard.voice.VoiceTypingState
import com.terasumi.sellerkeyboard.suggestion.CandidateQuery
import com.terasumi.sellerkeyboard.suggestion.CandidateState
import com.terasumi.sellerkeyboard.suggestion.TokenContextParser
import com.terasumi.sellerkeyboard.seller.SellerShortcutState
import com.terasumi.sellerkeyboard.seller.SellerTopicClassifier
import com.terasumi.sellerkeyboard.seller.SellerTopicMatch
import com.terasumi.sellerkeyboard.seller.TemplateRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items

internal enum class KeyboardPage {
    Keyboard,
    Snippets,
    Calculator,
    Emoji,
    Translate,
    Products,
    Clipboard,
}

private fun ActiveImeSurface.toKeyboardPage(): KeyboardPage = when (this) {
    ActiveImeSurface.KEYBOARD -> KeyboardPage.Keyboard
    ActiveImeSurface.REPLIES -> KeyboardPage.Snippets
    ActiveImeSurface.CALCULATOR -> KeyboardPage.Calculator
    ActiveImeSurface.EMOJI -> KeyboardPage.Emoji
    ActiveImeSurface.TRANSLATE -> KeyboardPage.Translate
    ActiveImeSurface.PRODUCTS -> KeyboardPage.Products
    ActiveImeSurface.CLIPBOARD -> KeyboardPage.Clipboard
}

private fun KeyboardPage.toActiveImeSurface(): ActiveImeSurface = when (this) {
    KeyboardPage.Keyboard -> ActiveImeSurface.KEYBOARD
    KeyboardPage.Snippets -> ActiveImeSurface.REPLIES
    KeyboardPage.Calculator -> ActiveImeSurface.CALCULATOR
    KeyboardPage.Emoji -> ActiveImeSurface.EMOJI
    KeyboardPage.Translate -> ActiveImeSurface.TRANSLATE
    KeyboardPage.Products -> ActiveImeSurface.PRODUCTS
    KeyboardPage.Clipboard -> ActiveImeSurface.CLIPBOARD
}

internal data class KeyboardPageItem(
    val page: KeyboardPage?,
    val toolId: String,
    val icon: ImageVector,
    val label: String
)

internal fun toolbarItemsForPrivacy(
    pageItems: List<KeyboardPageItem>,
    privacy: EditorPrivacyPolicy
): List<KeyboardPageItem> = if (privacy.isSensitive) {
    pageItems.filter { it.toolId == "keyboard" || it.toolId == "settings" }
} else {
    pageItems
}

@Composable
fun KeyboardContent() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val preferences = context.keyboardPreferences()
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val useDarkTheme = when (preferences.themeMode) {
        KeyboardThemeMode.SYSTEM -> systemDark
        KeyboardThemeMode.LIGHT -> false
        KeyboardThemeMode.DARK -> true
    }
    val myColor = when {
        preferences.highContrast && useDarkTheme -> HighContrastDarkKeyboardColors
        preferences.highContrast -> HighContrastLightKeyboardColors
        useDarkTheme -> DarkKeyboardColors
        else -> LightKeyboardColors
    }

    val keyboardLabel = stringResource(R.string.keyboard)
    val snippetLabel = stringResource(R.string.snippet_list)
    val calculatorLabel = stringResource(R.string.calculator)
    val emojiLabel = stringResource(R.string.emoji)
    val translateLabel = stringResource(R.string.translate)
    val productsLabel = stringResource(R.string.products)
    val voiceLabel = stringResource(R.string.voice)
    val settingsLabel = stringResource(R.string.settings)
    val vietnameseInputLabel = stringResource(R.string.vietnamese_input)
    val voiceCancelText = stringResource(R.string.voice_cancel)

    val pageItems = remember(
        keyboardLabel, snippetLabel, calculatorLabel, emojiLabel, translateLabel, productsLabel,
        voiceLabel, settingsLabel,
        preferences.toolbarOrder
    ) {
        val items = listOf(
            KeyboardPageItem(
                page = KeyboardPage.Keyboard,
                toolId = "keyboard",
                icon = Icons.Default.Keyboard,
                label = keyboardLabel
            ),
            KeyboardPageItem(
                page = KeyboardPage.Snippets,
                toolId = "replies",
                icon = Icons.AutoMirrored.Filled.TextSnippet,
                label = snippetLabel
            ),
            KeyboardPageItem(
                page = KeyboardPage.Calculator,
                toolId = "calculator",
                icon = Icons.Default.Calculate,
                label = calculatorLabel
            ),
            KeyboardPageItem(
                page = KeyboardPage.Emoji,
                toolId = "emoji",
                icon = Icons.Default.EmojiEmotions,
                label = emojiLabel
            ),
            KeyboardPageItem(
                page = KeyboardPage.Translate,
                toolId = "translate",
                icon = Icons.Default.Translate,
                label = translateLabel
            ),
            KeyboardPageItem(
                page = KeyboardPage.Products,
                toolId = "products",
                icon = Icons.Default.ShoppingBag,
                label = productsLabel
            ),
            KeyboardPageItem(
                page = KeyboardPage.Clipboard,
                toolId = "clipboard",
                icon = Icons.Default.ContentPaste,
                label = "Clipboard"
            ),
            KeyboardPageItem(
                page = null,
                toolId = "voice",
                icon = Icons.Default.KeyboardVoice,
                label = voiceLabel
            ),
            KeyboardPageItem(
                page = null,
                toolId = "settings",
                icon = Icons.Default.Settings,
                label = settingsLabel
            )
        )
        items.filter { it.toolId == "keyboard" || it.toolId in preferences.toolbarOrder }
            .sortedBy { item ->
                if (item.toolId == "keyboard") -1
                else preferences.toolbarOrder.indexOf(item.toolId).takeIf { it >= 0 } ?: Int.MAX_VALUE
            }
    }

    val editorSession = context.currentEditorSession()
    val visiblePageItems = remember(pageItems, editorSession.privacy) {
        toolbarItemsForPrivacy(pageItems, editorSession.privacy)
    }
    val currentPage = editorSession.activeSurface.toKeyboardPage()
    var sellerTopicMatch by remember { mutableStateOf<SellerTopicMatch?>(null) }
    var voiceReturnSurface by remember { mutableStateOf<ActiveImeSurface?>(null) }
    var keyboardRuntimeState by remember(context) { context.keyboardRuntimeStateHolder() }
    val keyboardState = keyboardRuntimeState.toKeyboardState()
    var lastShiftTapTime by remember { mutableLongStateOf(0L) }
    var vietnameseInputEnabled by rememberSaveable { mutableStateOf(context.isVietnameseInputEnabled()) }
    var clipboardHistoryEnabled by rememberSaveable { mutableStateOf(context.isClipboardHistoryEnabled()) }
    var voiceTypingEnabled by rememberSaveable { mutableStateOf(context.isVoiceTypingEnabled()) }
    var inlineAutofillEnabled by rememberSaveable { mutableStateOf(context.isInlineAutofillEnabled()) }
    val inputConnectionOps = remember(context) { context.inputConnectionOps() }
    val telexProcessor = remember(inputConnectionOps) { TelexProcessor(inputConnectionOps) }
    val clipboardRepository = remember(context) { context.clipboardRepository() }
    val clipboardItems by clipboardRepository.observeRecent().collectAsState()
    val voiceTypingController = remember(context) { context.voiceTypingControllerOrNull() }
    val fallbackVoiceState = remember { MutableStateFlow<VoiceTypingState>(VoiceTypingState.Idle) }
    val voiceState by (voiceTypingController?.state ?: fallbackVoiceState).collectAsState()
    val inlineAutofillController = remember(context) { context.inlineAutofillControllerOrNull() }
    val fallbackInlineAutofillState = remember {
        MutableStateFlow<InlineAutofillState<View>>(InlineAutofillState())
    }
    val inlineAutofillState by (
        inlineAutofillController?.state ?: fallbackInlineAutofillState
        ).collectAsState()
    val candidateController = remember(context) { context.candidateControllerOrNull() }
    val fallbackCandidateState = remember { MutableStateFlow(CandidateState()) }
    val candidateState by (candidateController?.state ?: fallbackCandidateState).collectAsState()
    val editorCompletionController = remember(context) { context.editorCompletionControllerOrNull() }
    val fallbackCompletionState = remember { MutableStateFlow(EditorCompletionState()) }
    val completionState by (
        editorCompletionController?.state ?: fallbackCompletionState
        ).collectAsState()
    val translationController = remember(context) { context.translationControllerOrNull() }
    val shortcutController = remember(context) { context.sellerShortcutControllerOrNull() }
    val fallbackShortcutState = remember { MutableStateFlow(SellerShortcutState()) }
    val shortcutState by (shortcutController?.state ?: fallbackShortcutState).collectAsState()

    val currentInputType = editorSession.inputType.takeIf { editorSession.id != 0L }
        ?: context.currentInputType()
    val editorPolicy = remember(editorSession.id, currentInputType, editorSession.privacy) {
        context.currentEditorInfoPolicy()
    }
    val keyboardLayout = remember(editorSession, keyboardState, preferences.numberRow) {
        KeyboardLayoutResolver.resolve(
            session = editorSession,
            state = keyboardState,
            includeNumberRow = preferences.numberRow
        )
    }
    val isPasswordField = editorPolicy.isSensitiveField
    val canToggleVietnameseInput = editorPolicy.canUseTelex &&
        editorSession.languageMode == KeyboardLanguageMode.VIETNAMESE_ENGLISH
    val effectiveVietnameseInputEnabled = when (editorSession.languageMode) {
        KeyboardLanguageMode.VIETNAMESE -> true
        KeyboardLanguageMode.ENGLISH -> false
        KeyboardLanguageMode.VIETNAMESE_ENGLISH -> vietnameseInputEnabled
    }
    val canUseVietnameseInput = currentPage == KeyboardPage.Keyboard &&
        effectiveVietnameseInputEnabled &&
        editorPolicy.canUseTelex
    val contextStripSource = ContextStripArbitrator.select(
        privacy = editorSession.privacy,
        availability = ContextStripAvailability(
            hasInlineAutofill = currentPage == KeyboardPage.Keyboard && inlineAutofillEnabled &&
                inlineAutofillState.hasSuggestions,
            hasEditorCompletions = currentPage == KeyboardPage.Keyboard &&
                completionState.sessionId == editorSession.id && completionState.items.isNotEmpty(),
            hasExplicitVoice = currentPage == KeyboardPage.Keyboard &&
                voiceState !is VoiceTypingState.Idle,
            hasExplicitSellerWorkflow = currentPage == KeyboardPage.Keyboard &&
                shortcutState.sessionId == editorSession.id && shortcutState.templates.isNotEmpty(),
            hasClipboardSuggestion = currentPage == KeyboardPage.Keyboard && clipboardHistoryEnabled &&
                clipboardItems.isNotEmpty(),
            hasTextCandidates = currentPage == KeyboardPage.Keyboard &&
                candidateState.sessionId == editorSession.id && candidateState.candidates.isNotEmpty(),
            hasExplicitSourceSellerTopic = currentPage == KeyboardPage.Keyboard &&
                sellerTopicMatch != null
        )
    )
    val canShowInlineAutofillBar = contextStripSource == ContextStripSource.INLINE_AUTOFILL
    val canShowClipboardBar = contextStripSource == ContextStripSource.CLIPBOARD
    val canShowCandidateBar = contextStripSource == ContextStripSource.TEXT_CANDIDATE
    val canShowCompletionBar = contextStripSource == ContextStripSource.EDITOR_COMPLETION
    val canShowVoiceBar = contextStripSource == ContextStripSource.EXPLICIT_VOICE
    val canShowShortcutBar = contextStripSource == ContextStripSource.EXPLICIT_SELLER_WORKFLOW
    val canShowSellerTopicBar =
        contextStripSource == ContextStripSource.EXPLICIT_SOURCE_SELLER_TOPIC
    val canUseVoiceInput = voiceTypingEnabled &&
        editorPolicy.canUseVoiceTyping &&
        voiceTypingController != null
    val isVoiceActive = voiceState is VoiceTypingState.Listening ||
        voiceState is VoiceTypingState.Processing

    androidx.compose.runtime.LaunchedEffect(editorSession.id, contextStripSource) {
        context.updateContextStripSource(contextStripSource)
    }

    val keyboardActionDispatcher = remember(inputConnectionOps, context, telexProcessor, canUseVietnameseInput) {
        KeyboardActionDispatcher(
            inputConnectionOps = inputConnectionOps,
            performHaptic = { context.performKeyboardHaptic() },
            telexProcessor = telexProcessor,
            isTelexEnabled = { canUseVietnameseInput },
            editorSessionProvider = { context.currentEditorSession() },
            doubleTapTimeoutMillis = ViewConfiguration.getDoubleTapTimeout().toLong()
        )
    }

    androidx.compose.runtime.LaunchedEffect(editorSession.id, currentInputType) {
        telexProcessor.resetComposing()
        context.updateImeComposition("")
        clipboardHistoryEnabled = context.isClipboardHistoryEnabled()
        voiceTypingEnabled = context.isVoiceTypingEnabled()
        inlineAutofillEnabled = context.isInlineAutofillEnabled()
        voiceTypingController?.resetForFieldChange()
        candidateController?.clear()
        editorCompletionController?.clear()
        shortcutController?.clear()
        sellerTopicMatch = null
        voiceReturnSurface = null
        clipboardRepository.refresh()
        val resolvedState = editorPolicy.resolveKeyboardState(keyboardState)
        keyboardRuntimeState = KeyboardRuntimeState.fromKeyboardState(resolvedState).copy(
            shiftMode = if (editorPolicy.canAutoCapitalize && editorSession.currentCapsMode != 0) {
                ShiftMode.ONE_SHOT
            } else {
                ShiftMode.LOWERCASE
            }
        )
    }

    androidx.compose.runtime.LaunchedEffect(
        editorSession.id,
        editorSession.currentCapsMode,
        editorSession.selectionStart,
        editorSession.selectionEnd,
        editorSession.composingText
    ) {
        val reconciledState = ShiftStateReconciler.reconcile(
            current = keyboardState,
            currentCapsMode = editorSession.currentCapsMode,
            policy = editorPolicy,
            hasActiveComposition = editorSession.composingText.isNotEmpty()
        )
        keyboardRuntimeState = keyboardRuntimeState.copy(
            shiftMode = KeyboardRuntimeState.fromKeyboardState(reconciledState).shiftMode
        )
    }

    androidx.compose.runtime.LaunchedEffect(currentPage) {
        if (currentPage != KeyboardPage.Keyboard) {
            telexProcessor.commitComposing()
            context.updateImeComposition("")
        }
    }

    fun handleKeyboardAction(action: KeyboardAction) {
        Trace.beginSection("SellerKeyboard.keyDispatch")
        try {
        val tokenBeforeAction = if (action == KeyboardAction.Space) {
            TokenContextParser.parse(inputConnectionOps.getTextBeforeCursor())
        } else {
            null
        }
        val correction = candidateState.autoCorrection
            ?.takeIf { decision ->
                candidateState.sessionId == editorSession.id &&
                    decision.original == tokenBeforeAction?.currentToken
            }
        val effectiveAction = if (action == KeyboardAction.Space && correction != null) {
            KeyboardAction.ApplyAutoCorrection(
                original = correction.original,
                corrected = correction.replacement
            )
        } else {
            action
        }
        val result = keyboardActionDispatcher.dispatchRuntime(
            action = effectiveAction,
            state = keyboardRuntimeState,
            lastShiftTapTime = lastShiftTapTime
        )
        keyboardRuntimeState = result.state
        lastShiftTapTime = result.lastShiftTapTime
        if (effectiveAction is KeyboardAction.KeyPress || effectiveAction == KeyboardAction.Delete ||
            effectiveAction == KeyboardAction.Space || effectiveAction is KeyboardAction.AcceptCandidate ||
            effectiveAction is KeyboardAction.ApplyAutoCorrection) {
            val tokenContext = TokenContextParser.parse(inputConnectionOps.getTextBeforeCursor())
            candidateController?.request(
                sessionId = editorSession.id,
                query = CandidateQuery(
                    currentToken = tokenContext.currentToken,
                    previousToken = tokenContext.previousToken,
                    rawTelexToken = telexProcessor.rawSyllable.takeIf(String::isNotBlank),
                    isSensitive = editorSession.privacy.isSensitive
                )
            )
            shortcutController?.request(
                sessionId = editorSession.id,
                token = tokenContext.currentToken,
                isSensitive = editorSession.privacy.isSensitive
            )
            sellerTopicMatch = if (editorSession.privacy.canUseSellerTools) {
                SellerTopicClassifier.classifyExplicitText(
                    inputConnectionOps.getTextBeforeCursor()?.toString().orEmpty().takeLast(240)
                )
            } else {
                null
            }
        }
        } finally {
            context.updateImeComposition(telexProcessor.composingText)
            Trace.endSection()
        }
    }

    fun toggleVietnameseInput() {
        if (!canToggleVietnameseInput) {
            return
        }

        val nextEnabled = !vietnameseInputEnabled
        context.performKeyboardHaptic()
        if (!nextEnabled) {
            telexProcessor.commitComposing()
        }
        vietnameseInputEnabled = nextEnabled
        context.setVietnameseInputEnabled(nextEnabled)
    }

    fun restoreSurfaceAfterVoice() {
        context.updateActiveImeSurface(voiceReturnSurface ?: ActiveImeSurface.KEYBOARD)
        voiceReturnSurface = null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(myColor.keyboardBackground)
            .navigationBarsPadding()
            // Gesture navigation reports a 24 dp inset, while the IME-owned
            // Back/switcher bar occupies 48 dp. Reserve the remaining half.
            .padding(bottom = 24.dp)
    ) {
        KeyboardToolbar(
            myColor = myColor,
            pageItems = visiblePageItems,
            currentPage = currentPage,
            voiceCancelLabel = voiceCancelText,
            isLandscape = isLandscape,
            isPasswordField = isPasswordField,
            canUseVoiceInput = canUseVoiceInput,
            isVoiceActive = isVoiceActive,
            activeProfileName = editorSession.activeProfileName,
            onSelectPage = { selectedPage ->
                Trace.beginSection("SellerKeyboard.surfaceSwitch")
                try {
                    context.performKeyboardHaptic()
                    context.updateActiveImeSurface(selectedPage.toActiveImeSurface())
                } finally {
                    Trace.endSection()
                }
            },
            onOpenSettings = {
                context.performKeyboardHaptic()
                context.openKeyboardSettings()
            },
            onVoiceInput = {
                context.performKeyboardHaptic()
                if (isVoiceActive) {
                    voiceTypingController?.cancel()
                    restoreSurfaceAfterVoice()
                } else {
                    telexProcessor.commitComposing()
                    voiceReturnSurface = editorSession.activeSurface
                    context.updateActiveImeSurface(ActiveImeSurface.KEYBOARD)
                    voiceTypingController?.start()
                }
            }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(myColor.keyboardBackground.copy(alpha = 0.98f))
        ) {
            when {
                canShowInlineAutofillBar -> InlineAutofillBar(
                    suggestions = inlineAutofillState.suggestions,
                    myColor = myColor,
                    isLandscape = isLandscape
                )
                canShowCompletionBar -> EditorCompletionBar(
                    items = completionState.items,
                    myColor = myColor,
                    isLandscape = isLandscape,
                    onCompletion = { item ->
                        context.performKeyboardHaptic()
                        telexProcessor.commitComposing()
                        context.commitEditorCompletion(item)
                    }
                )
                canShowVoiceBar -> VoiceReviewBar(
                    state = voiceState,
                    myColor = myColor,
                    onInsert = { transcript ->
                        handleKeyboardAction(KeyboardAction.KeyPress(transcript))
                        voiceTypingController?.discardResult()
                        restoreSurfaceAfterVoice()
                    },
                    onRetry = { voiceTypingController?.start() },
                    onCancel = {
                        voiceTypingController?.cancel()
                        restoreSurfaceAfterVoice()
                    },
                    onDiscard = {
                        voiceTypingController?.discardResult()
                        restoreSurfaceAfterVoice()
                    }
                )
                canShowShortcutBar -> SellerShortcutBar(
                    templates = shortcutState.templates,
                    myColor = myColor,
                    isLandscape = isLandscape,
                    onTemplate = { template ->
                        if (TemplateRenderer.variables(template.body).isEmpty()) {
                            handleKeyboardAction(
                                KeyboardAction.InsertTemplate(
                                    text = template.body,
                                    replaceBeforeUtf16 = shortcutState.typedShortcut.length
                                )
                            )
                        } else {
                            context.updateActiveImeSurface(ActiveImeSurface.REPLIES)
                        }
                    }
                )
                canShowClipboardBar -> ClipboardBar(
                    items = clipboardItems,
                    myColor = myColor,
                    isLandscape = isLandscape,
                    onCommitText = { item, text ->
                        context.performKeyboardHaptic()
                        telexProcessor.commitComposing()
                        inputConnectionOps.commitText(text)
                        clipboardRepository.markUsed(item.id)
                    },
                    onTogglePin = { item ->
                        context.performKeyboardHaptic()
                        clipboardRepository.setPinned(item.id, !item.pinned)
                    },
                    onDeleteItem = { item ->
                        context.performKeyboardHaptic()
                        clipboardRepository.delete(item.id)
                    },
                    onClear = {
                        context.performKeyboardHaptic()
                        clipboardRepository.clear()
                    }
                )
                canShowCandidateBar -> CandidateBar(
                    candidates = candidateState.candidates,
                    myColor = myColor,
                    isLandscape = isLandscape,
                    onDisplayed = {
                        candidateState.displayTraceCookie?.let { cookie ->
                            candidateController?.markCandidatesDisplayed(cookie)
                        }
                    },
                    onCandidate = { candidate ->
                        handleKeyboardAction(
                            KeyboardAction.AcceptCandidate(
                                text = candidate.text,
                                replaceBeforeUtf16 = candidate.replaceBeforeUtf16
                            )
                        )
                    }
                )
                canShowSellerTopicBar -> TextButton(
                    onClick = { context.updateActiveImeSurface(ActiveImeSurface.REPLIES) },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Text(
                        text = "Find seller replies: " +
                            sellerTopicMatch?.topic?.name.orEmpty().replace('_', ' '),
                        color = myColor.iconColor
                    )
                }
            }
        }

        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                (fadeIn(animationSpec = tween(durationMillis = 180)) +
                    scaleIn(
                        animationSpec = tween(durationMillis = 180),
                        initialScale = 0.99f
                    )) togetherWith
                    fadeOut(animationSpec = tween(durationMillis = 120))
            },
            label = "Keyboard page switch"
        ) { page ->
            when (page) {
                KeyboardPage.Snippets -> {
                    SnippetsKeyboard(
                        myColor = myColor,
                        isLandscape = isLandscape,
                        activeProfileId = editorSession.activeProfileId,
                        preferredReplyPackId = editorSession.preferredReplyPackId,
                        onOpenTool = { toolId ->
                            when (toolId) {
                                "products" -> context.updateActiveImeSurface(ActiveImeSurface.PRODUCTS)
                                "clipboard" -> context.updateActiveImeSurface(ActiveImeSurface.CLIPBOARD)
                                "calculator" -> context.updateActiveImeSurface(ActiveImeSurface.CALCULATOR)
                                "translate" -> context.updateActiveImeSurface(ActiveImeSurface.TRANSLATE)
                                "emoji" -> context.updateActiveImeSurface(ActiveImeSurface.EMOJI)
                                "voice" -> if (canUseVoiceInput) {
                                    voiceReturnSurface = editorSession.activeSurface
                                    context.updateActiveImeSurface(ActiveImeSurface.KEYBOARD)
                                    voiceTypingController?.start()
                                }
                                "settings" -> context.openKeyboardSettings()
                            }
                        },
                        onAction = ::handleKeyboardAction
                    )
                }

                KeyboardPage.Calculator -> {
                    CalculatorHub(
                        myColor = myColor,
                        isLandscape = isLandscape,
                        onAction = ::handleKeyboardAction
                    )
                }

                KeyboardPage.Keyboard -> {
                    Keyboard(
                        keyboardRows = keyboardLayout.rows,
                        keyboardState = keyboardState,
                        myColor = myColor,
                        isLandscape = isLandscape,
                        renderEpoch = editorSession.id,
                        languageLabel = if (canToggleVietnameseInput) {
                            if (effectiveVietnameseInputEnabled) "VI" else "EN"
                        } else {
                            ""
                        },
                        languageToggleLabel = vietnameseInputLabel,
                        canToggleLanguage = canToggleVietnameseInput,
                        onToggleLanguage = ::toggleVietnameseInput,
                        selectionEnd = editorSession.selectionEnd,
                        popupPreviewEnabled = preferences.popupPreview,
                        keyBorders = preferences.keyBorders,
                        heightPreset = preferences.heightPreset,
                        oneHandedSide = preferences.oneHandedSide,
                        onAction = ::handleKeyboardAction
                    )
                }

                KeyboardPage.Emoji -> {
                    EmojiKeyboard(
                        isLandscape = isLandscape,
                        onEmoji = { emoji ->
                            handleKeyboardAction(KeyboardAction.KeyPress(emoji))
                        }
                    )
                }

                KeyboardPage.Translate -> {
                    if (translationController != null) {
                        TranslationKeyboard(
                            controller = translationController,
                            selectedText = inputConnectionOps.getSelectedText()?.toString().orEmpty(),
                            selectionEnd = editorSession.selectionEnd,
                            isSensitive = editorSession.privacy.isSensitive,
                            onInsert = { text, selectionEnd ->
                                handleKeyboardAction(
                                    KeyboardAction.InsertTranslation(text, selectionEnd)
                                )
                            },
                            onReplaceSelection = { text ->
                                handleKeyboardAction(KeyboardAction.ReplaceSelection(text))
                            },
                            onRestoreOriginal = { original, translated ->
                                handleKeyboardAction(
                                    KeyboardAction.RestoreTranslation(original, translated)
                                )
                            }
                        )
                    }
                }

                KeyboardPage.Products -> {
                    ProductKeyboard(
                        isLandscape = isLandscape,
                        canCommitImage = RichContentPolicy.decide(
                            privacy = editorSession.privacy,
                            supportedMimeTypes = editorSession.supportedMimeTypes,
                            requestedMimeType = "image/jpeg"
                        ) == RichContentDecision.COMMIT_RICH_CONTENT,
                        onInsertText = { text -> handleKeyboardAction(KeyboardAction.KeyPress(text)) },
                        onCommitImageWithFallback = context::commitRichProduct
                    )
                }

                KeyboardPage.Clipboard -> {
                    ClipboardBar(
                        items = clipboardItems,
                        myColor = myColor,
                        isLandscape = isLandscape,
                        onCommitText = { item, text ->
                            context.performKeyboardHaptic()
                            inputConnectionOps.commitText(text)
                            clipboardRepository.markUsed(item.id)
                        },
                        onTogglePin = { item ->
                            clipboardRepository.setPinned(item.id, !item.pinned)
                        },
                        onDeleteItem = { item -> clipboardRepository.delete(item.id) },
                        onClear = clipboardRepository::clear
                    )
                }
            }
        }
    }
}

@Composable
internal fun KeyboardToolbar(
    myColor: KeyboardColors,
    pageItems: List<KeyboardPageItem>,
    currentPage: KeyboardPage,
    voiceCancelLabel: String,
    isLandscape: Boolean,
    isPasswordField: Boolean,
    canUseVoiceInput: Boolean,
    isVoiceActive: Boolean,
    activeProfileName: String,
    onSelectPage: (KeyboardPage) -> Unit,
    onOpenSettings: () -> Unit,
    onVoiceInput: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isLandscape) 8.dp else 10.dp,
                vertical = if (isLandscape) 3.dp else 6.dp
            )
            .background(
                color = myColor.keyboardBackground.copy(alpha = if (isLandscape) 0.92f else 0.96f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(
                horizontal = if (isLandscape) 4.dp else 6.dp,
                vertical = 2.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 6.dp)
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 6.dp)
        ) {
            items(pageItems, key = { it.toolId }) { item ->
                val isVoice = item.toolId == "voice"
                val isSettings = item.toolId == "settings"
                val enabled = when {
                    isVoice -> !isPasswordField && canUseVoiceInput
                    isSettings -> true
                    else -> !isPasswordField || item.page == KeyboardPage.Keyboard
                }
                ToolbarIconButton(
                    icon = if (isVoice && isVoiceActive) Icons.Default.Stop else item.icon,
                    contentDescription = if (isVoice && isVoiceActive) voiceCancelLabel else item.label,
                    selected = if (isVoice) isVoiceActive else currentPage == item.page,
                    myColor = myColor,
                    enabled = enabled,
                    emphasize = isVoice,
                    onClick = {
                        when {
                            isVoice -> onVoiceInput()
                            isSettings -> onOpenSettings()
                            item.page != null -> onSelectPage(item.page)
                        }
                    }
                )
            }
            if (activeProfileName != "Global") {
                item {
                    Text(
                        text = activeProfileName,
                        color = myColor.iconColor,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        modifier = Modifier.semantics {
                            contentDescription = "Active profile $activeProfileName"
                        }
                    )
                }
            }
        }

    }
}

@Composable
private fun ToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    myColor: KeyboardColors,
    onClick: () -> Unit,
    emphasize: Boolean = false,
    enabled: Boolean = true
) {
    val container = when {
        !enabled -> Color.Transparent
        emphasize -> myColor.keyBackground.copy(alpha = 0.92f)
        selected -> myColor.keyActionBackground.copy(alpha = 0.36f)
        else -> Color.Transparent
    }
    val tint = if (!enabled) {
        myColor.iconColor.copy(alpha = 0.3f)
    } else if (selected || emphasize) {
        myColor.iconColor
    } else {
        myColor.iconColor.copy(alpha = 0.72f)
    }

    Box(
        modifier = Modifier
            .background(
                color = container,
                shape = CircleShape
            )
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint
            )
        }
    }
}

private fun Context.openKeyboardSettings() {
    runCatching {
        startActivity(
            Intent(this, HomeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}

/**
 * Pure function that resolves the next shift state.
 * - NOCAPS → CAPS (single shift, next key only)
 * - CAPS + second tap inside the injected platform double-tap timeout → DOUBLECAPS
 * - DOUBLECAPS → NOCAPS (turn off caps lock)
 */
internal const val DEFAULT_SHIFT_DOUBLE_TAP_TIMEOUT_MS = 300L

internal fun resolveShiftState(
    current: KeyboardState,
    timeSinceLastShift: Long = Long.MAX_VALUE,
    doubleTapTimeoutMillis: Long = DEFAULT_SHIFT_DOUBLE_TAP_TIMEOUT_MS
): KeyboardState {
    return when (current) {
        KeyboardState.NOCAPS -> KeyboardState.CAPS
        KeyboardState.CAPS -> {
            if (timeSinceLastShift in 0 until doubleTapTimeoutMillis) {
                KeyboardState.DOUBLECAPS
            } else {
                // Single tap again while in CAPS → turn off
                KeyboardState.NOCAPS
            }
        }
        KeyboardState.DOUBLECAPS -> KeyboardState.NOCAPS
        else -> current
    }
}

internal fun isPasswordInputType(inputType: Int): Boolean {
    return com.terasumi.sellerkeyboard.ime.EditorInfoPolicy.fromInputType(inputType).isPasswordField
}

internal fun resolveKeyboardStateForInputType(inputType: Int, currentState: KeyboardState): KeyboardState {
    return com.terasumi.sellerkeyboard.ime.EditorInfoPolicy.fromInputType(inputType)
        .resolveKeyboardState(currentState)
}

@Preview(showSystemUi = true)
@Composable
fun KeyboardContentPreview() {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            KeyboardContent()
        }
    }
}

@Preview(showSystemUi = true, device = "spec:width=1280dp,height=800dp,dpi=480")
@Composable
fun KeyboardContentLandscapePreview() {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            KeyboardContent()
        }
    }
}
