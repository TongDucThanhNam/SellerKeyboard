package com.terasumi.sellerkeyboard.keyboard

import android.content.Context
import android.view.View
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.terasumi.sellerkeyboard.autofill.InlineAutofillController
import com.terasumi.sellerkeyboard.clipboard.ClipboardRepository
import com.terasumi.sellerkeyboard.clipboard.SQLiteClipboardStore
import com.terasumi.sellerkeyboard.database.DatabaseHelper
import com.terasumi.sellerkeyboard.ime.EditorInfoPolicy
import com.terasumi.sellerkeyboard.ime.EditorSession
import com.terasumi.sellerkeyboard.ime.InputConnectionOps
import com.terasumi.sellerkeyboard.settings.KeyboardSettingsRepository
import com.terasumi.sellerkeyboard.settings.KeyboardPreferences
import com.terasumi.sellerkeyboard.ime.ActiveImeSurface
import com.terasumi.sellerkeyboard.ime.ContextStripSource
import com.terasumi.sellerkeyboard.suggestion.CandidateController
import com.terasumi.sellerkeyboard.autofill.EditorCompletionController
import com.terasumi.sellerkeyboard.autofill.EditorCompletionItem
import com.terasumi.sellerkeyboard.translation.MlKitTranslationController
import com.terasumi.sellerkeyboard.seller.SellerShortcutController
import com.terasumi.sellerkeyboard.service.SellerKeyboard
import com.terasumi.sellerkeyboard.voice.VoiceTypingController

internal fun Context.inputConnectionOps(): InputConnectionOps {
    return (this as? SellerKeyboard)?.createInputConnectionOps() ?: InputConnectionOps.empty()
}

internal fun Context.currentInputType(): Int {
    return (this as? SellerKeyboard)?.currentInputTypeState?.value ?: 0
}

internal fun Context.currentEditorInfoPolicy(): EditorInfoPolicy {
    return (this as? SellerKeyboard)?.currentEditorInfoPolicy()
        ?: EditorInfoPolicy.fromInputType(currentInputType())
}

internal fun Context.currentEditorSession(): EditorSession {
    return (this as? SellerKeyboard)?.currentEditorSession() ?: EditorSession.Inactive
}

internal fun Context.keyboardRuntimeStateHolder(): MutableState<KeyboardRuntimeState> {
    return (this as? SellerKeyboard)?.keyboardRuntimeState()
        ?: mutableStateOf(KeyboardRuntimeState())
}

internal fun Context.performKeyboardHaptic() {
    (this as? SellerKeyboard)?.performKeyHapticFeedback()
}

internal fun Context.isVietnameseInputEnabled(): Boolean {
    return (this as? SellerKeyboard)?.isVietnameseInputEnabled()
        ?: KeyboardSettingsRepository(this).vietnameseInputEnabled
}

internal fun Context.setVietnameseInputEnabled(enabled: Boolean) {
    (this as? SellerKeyboard)?.setVietnameseInputEnabled(enabled)
        ?: run { KeyboardSettingsRepository(this).vietnameseInputEnabled = enabled }
}

internal fun Context.isClipboardHistoryEnabled(): Boolean {
    return (this as? SellerKeyboard)?.isClipboardHistoryEnabled()
        ?: KeyboardSettingsRepository(this).clipboardHistoryEnabled
}

internal fun Context.clipboardRepository(): ClipboardRepository {
    return (this as? SellerKeyboard)?.clipboardRepository()
        ?: ClipboardRepository(
            store = SQLiteClipboardStore(DatabaseHelper(this)),
            historyEnabled = { KeyboardSettingsRepository(this).clipboardHistoryEnabled },
            autoDeleteDays = { KeyboardSettingsRepository(this).clipboardAutoDeleteDays }
        )
}

internal fun Context.isVoiceTypingEnabled(): Boolean {
    return (this as? SellerKeyboard)?.isVoiceTypingEnabled()
        ?: KeyboardSettingsRepository(this).voiceTypingEnabled
}

internal fun Context.voiceTypingControllerOrNull(): VoiceTypingController? {
    return (this as? SellerKeyboard)?.voiceTypingController()
}

internal fun Context.isInlineAutofillEnabled(): Boolean {
    return (this as? SellerKeyboard)?.isInlineAutofillEnabled()
        ?: KeyboardSettingsRepository(this).inlineAutofillEnabled
}

internal fun Context.inlineAutofillControllerOrNull(): InlineAutofillController<View>? {
    return (this as? SellerKeyboard)?.inlineAutofillController()
}

internal fun Context.keyboardPreferences(): KeyboardPreferences {
    return (this as? SellerKeyboard)?.keyboardPreferences()
        ?: KeyboardSettingsRepository(this).snapshot()
}

internal fun Context.updateActiveImeSurface(surface: ActiveImeSurface) {
    (this as? SellerKeyboard)?.updateActiveSurface(surface)
}

internal fun Context.updateContextStripSource(source: ContextStripSource) {
    (this as? SellerKeyboard)?.updateCandidateSource(source)
}

internal fun Context.updateImeComposition(text: String) {
    (this as? SellerKeyboard)?.updateCompositionState(text)
}

internal fun Context.candidateControllerOrNull(): CandidateController? {
    return (this as? SellerKeyboard)?.candidateController()
}

internal fun Context.editorCompletionControllerOrNull(): EditorCompletionController? {
    return (this as? SellerKeyboard)?.editorCompletionController()
}

internal fun Context.commitEditorCompletion(item: EditorCompletionItem): Boolean {
    return (this as? SellerKeyboard)?.commitEditorCompletion(item) ?: false
}

internal fun Context.translationControllerOrNull(): MlKitTranslationController? {
    return (this as? SellerKeyboard)?.translationController()
}

internal fun Context.commitRichProduct(imageUrl: String, fallbackText: String) {
    (this as? SellerKeyboard)?.doCommitContent(imageUrl, fallbackText)
        ?: inputConnectionOps().commitText(fallbackText)
}

internal fun Context.sellerShortcutControllerOrNull(): SellerShortcutController? {
    return (this as? SellerKeyboard)?.sellerShortcutController()
}
