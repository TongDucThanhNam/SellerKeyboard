package com.terasumi.sellerkeyboard.service

import android.Manifest
import android.content.Context
import android.content.ClipDescription
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.View
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputMethodSubtype
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.CompletionInfo
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.terasumi.sellerkeyboard.HomeActivity
import com.terasumi.sellerkeyboard.autofill.AndroidInlineAutofillRequestFactory
import com.terasumi.sellerkeyboard.autofill.InlineAutofillController
import com.terasumi.sellerkeyboard.autofill.EditorCompletionController
import com.terasumi.sellerkeyboard.autofill.EditorCompletionItem
import com.terasumi.sellerkeyboard.clipboard.ActiveClipboardReader
import com.terasumi.sellerkeyboard.clipboard.ClipboardRepository
import com.terasumi.sellerkeyboard.clipboard.SQLiteClipboardStore
import com.terasumi.sellerkeyboard.database.DatabaseHelper
import com.terasumi.sellerkeyboard.ime.EditorInfoPolicy
import com.terasumi.sellerkeyboard.ime.EditorCapsModeRequest
import com.terasumi.sellerkeyboard.ime.AuthenticationFieldDetector
import com.terasumi.sellerkeyboard.ime.EditorSession
import com.terasumi.sellerkeyboard.ime.EditorSessionCoordinator
import com.terasumi.sellerkeyboard.ime.ActiveImeSurface
import com.terasumi.sellerkeyboard.ime.ContextStripSource
import com.terasumi.sellerkeyboard.ime.EditorSessionRequest
import com.terasumi.sellerkeyboard.ime.EditorSessionResolver
import com.terasumi.sellerkeyboard.ime.InputConnectionOps
import com.terasumi.sellerkeyboard.ime.RichContentDecision
import com.terasumi.sellerkeyboard.ime.RichContentPolicy
import com.terasumi.sellerkeyboard.keyboard.KeyboardContentView
import com.terasumi.sellerkeyboard.keyboard.KeyboardRuntimeCoordinator
import com.terasumi.sellerkeyboard.keyboard.KeyboardRuntimeState
import com.terasumi.sellerkeyboard.settings.KeyboardSettingsRepository
import com.terasumi.sellerkeyboard.settings.KeyboardPreferences
import com.terasumi.sellerkeyboard.settings.KeyboardLanguageMode
import com.terasumi.sellerkeyboard.suggestion.CandidateController
import com.terasumi.sellerkeyboard.suggestion.CandidateQuery
import com.terasumi.sellerkeyboard.suggestion.LocalCandidateEngine
import com.terasumi.sellerkeyboard.suggestion.SharedPreferencesPersonalDictionary
import com.terasumi.sellerkeyboard.suggestion.TokenContextParser
import com.terasumi.sellerkeyboard.translation.MlKitTranslationController
import com.terasumi.sellerkeyboard.translation.TranslationState
import com.terasumi.sellerkeyboard.profile.AppProfileResolver
import com.terasumi.sellerkeyboard.profile.KeyboardProfile
import com.terasumi.sellerkeyboard.profile.SharedPreferencesAppProfileStore
import com.terasumi.sellerkeyboard.profile.applyTo
import com.terasumi.sellerkeyboard.performance.AndroidPerformanceTracer
import com.terasumi.sellerkeyboard.seller.SellerShortcutController
import com.terasumi.sellerkeyboard.seller.ProductImageSourcePolicy
import com.terasumi.sellerkeyboard.database.SellerReplyTemplateDao
import com.terasumi.sellerkeyboard.voice.AndroidSpeechRecognizerRepository
import com.terasumi.sellerkeyboard.voice.VoiceTypingController
import com.terasumi.sellerkeyboard.voice.VoiceTypingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.function.Consumer

internal data class ImeFieldScopedStateSnapshot(
    val sessionId: Long,
    val isSensitive: Boolean,
    val activeSurface: ActiveImeSurface,
    val candidateSource: ContextStripSource,
    val candidateCount: Int,
    val completionCount: Int,
    val inlineSuggestionCount: Int,
    val sellerShortcutCount: Int,
    val clipboardReaderStarted: Boolean,
    val clipboardObservedChangeCount: Long,
    val voiceIdle: Boolean,
    val translationIdle: Boolean
)

class SellerKeyboard : LifecycleInputMethodService(), ViewModelStoreOwner, SavedStateRegistryOwner {
    private val settingsRepository: KeyboardSettingsRepository by lazy {
        KeyboardSettingsRepository(this)
    }

    private val clipboardHistoryRepository: ClipboardRepository by lazy {
        ClipboardRepository(
            store = SQLiteClipboardStore(DatabaseHelper(this)),
            historyEnabled = { settingsRepository.clipboardHistoryEnabled },
            autoDeleteDays = { settingsRepository.clipboardAutoDeleteDays }
        )
    }

    private val activeClipboardReaderDelegate = lazy {
        ActiveClipboardReader(
            context = this,
            repository = clipboardHistoryRepository,
            policyProvider = ::currentEditorInfoPolicy
        )
    }
    private val activeClipboardReader by activeClipboardReaderDelegate

    private val voiceTypingControllerDelegate = lazy {
        VoiceTypingController(
            speechRecognizerRepository = AndroidSpeechRecognizerRepository(this),
            hasRecordAudioPermission = {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            },
            requestRecordAudioPermission = {
                requestRecordAudioPermissionFromActivity()
            },
            editorInfoPolicyProvider = ::currentEditorInfoPolicy,
            voiceTypingEnabled = { settingsRepository.voiceTypingEnabled },
            languageTagProvider = { imeLanguage }
        )
    }
    private val voiceTypingController by voiceTypingControllerDelegate

    private val inlineAutofillControllerDelegate = lazy {
        InlineAutofillController<View>(
            enabledProvider = { settingsRepository.inlineAutofillEnabled },
            editorInfoPolicyProvider = ::currentEditorInfoPolicy
        )
    }
    private val inlineAutofillController by inlineAutofillControllerDelegate
    private val editorCompletionController = EditorCompletionController()
    private val translationController = MlKitTranslationController()
    private val appProfileStore by lazy { SharedPreferencesAppProfileStore(this) }
    private val sellerShortcutControllerDelegate = lazy {
        val dao = SellerReplyTemplateDao(DatabaseHelper(this))
        SellerShortcutController(serviceScope) { query ->
            val session = currentEditorSessionState.value
            dao.listActive(
                query = query,
                limit = 20,
                profileId = session.activeProfileId,
                preferredReplyPackId = session.preferredReplyPackId
            )
        }
    }
    private val sellerShortcutController by sellerShortcutControllerDelegate

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val personalDictionaryStore by lazy {
        SharedPreferencesPersonalDictionary(this)
    }

    private val textCandidateControllerDelegate = lazy {
        CandidateController(
            scope = serviceScope,
            provider = LocalCandidateEngine(
                personalWords = {
                    if (settingsRepository.learningEnabled) personalDictionaryStore.words()
                    else emptySet()
                }
            ),
            performanceTracer = AndroidPerformanceTracer
        )
    }
    private val textCandidateController by textCandidateControllerDelegate

    val currentInputTypeState = androidx.compose.runtime.mutableStateOf(0)
    private val editorSessionCoordinator = EditorSessionCoordinator()
    private val keyboardRuntimeCoordinator = KeyboardRuntimeCoordinator()
    val currentEditorSessionState get() = editorSessionCoordinator.state
    private var nextEditorSessionId = 0L

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }


    override fun onCreateInputView(): View {
        val view = KeyboardContentView(this)
        view.setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this))

        // Set tree owners on the *window's decor view* so that Compose's
        // upward ViewTree walk always finds them, even from ancestor
        // views (e.g. the parentPanel LinearLayout) that InputMethodService
        // inserts above our input view.
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }

        // Also set directly on our view as a fallback.
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        return view
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        nextEditorSessionId += 1
        keyboardRuntimeCoordinator.reset()
        editorSessionCoordinator.start(resolveEditorSession(attribute, nextEditorSessionId))
        currentInputTypeState.value = attribute?.inputType ?: 0
        invalidateFieldScopedState()
    }
    private val audioManager: AudioManager? by lazy {
        getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentInputTypeState.value = info?.inputType ?: 0
        if (currentEditorSessionState.value == EditorSession.Inactive) {
            nextEditorSessionId += 1
            editorSessionCoordinator.start(resolveEditorSession(info, nextEditorSessionId))
        }
        invalidateFieldScopedState()
        val policy = currentEditorInfoPolicy()
        seedInitialCandidates(info, currentEditorSessionState.value)
        if (policy.canShowClipboard) {
            activeClipboardReader.start()
            clipboardHistoryRepository.refresh()
        } else if (activeClipboardReaderDelegate.isInitialized()) {
            activeClipboardReader.stop()
        }
    }

    private fun seedInitialCandidates(info: EditorInfo?, session: EditorSession) {
        if (info == null || session.id == 0L || !session.privacy.canOfferCandidates) return
        val initialText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            info.getInitialTextBeforeCursor(INITIAL_CONTEXT_UTF16, 0)
        } else {
            currentInputConnection?.getTextBeforeCursor(INITIAL_CONTEXT_UTF16, 0)
        }
        val context = TokenContextParser.parse(initialText)
        if (context.currentToken.isBlank() && context.previousToken.isNullOrBlank()) return
        textCandidateController.request(
            sessionId = session.id,
            query = CandidateQuery(
                currentToken = context.currentToken,
                previousToken = context.previousToken,
                isSensitive = false
            )
        )
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        invalidateFieldScopedState()
        super.onFinishInputView(finishingInput)
        currentInputTypeState.value = 0
    }

    override fun onFinishInput() {
        editorSessionCoordinator.finish()
        keyboardRuntimeCoordinator.reset()
        invalidateFieldScopedState()
        currentInputTypeState.value = 0
        super.onFinishInput()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd
        )
        val session = currentEditorSessionState.value
        if (session.id == 0L) {
            return
        }
        val capsMode = createInputConnectionOps().getCursorCapsMode(
            EditorCapsModeRequest.fromInputType(session.inputType)
        )
        editorSessionCoordinator.updateSelection(newSelStart, newSelEnd, capsMode)
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype?) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        settingsRepository.languageMode = when {
            newSubtype?.languageTag?.startsWith("en", ignoreCase = true) == true ->
                KeyboardLanguageMode.ENGLISH
            newSubtype?.languageTag?.startsWith("vi", ignoreCase = true) == true ->
                KeyboardLanguageMode.VIETNAMESE
            else -> KeyboardLanguageMode.VIETNAMESE_ENGLISH
        }
    }

    override fun onDisplayCompletions(completions: Array<out CompletionInfo>?) {
        super.onDisplayCompletions(completions)
        val session = currentEditorSessionState.value
        if (session.privacy.isSensitive) {
            editorCompletionController.clear()
        } else {
            editorCompletionController.update(session.id, completions)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        if (!inlineAutofillController.shouldCreateRequest()) {
            inlineAutofillController.clear()
            return null
        }

        return AndroidInlineAutofillRequestFactory.create(
            context = this,
            uiExtras = uiExtras
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        if (!inlineAutofillController.shouldCreateRequest()) {
            inlineAutofillController.clear()
            return false
        }

        val generation = inlineAutofillController.beginResponse()
        val suggestions = response.inlineSuggestions
        if (suggestions.isEmpty()) {
            return true
        }

        val inflateSize = AndroidInlineAutofillRequestFactory.inflateSize()
        suggestions
            .take(com.terasumi.sellerkeyboard.autofill.InlineAutofillRequestConfig.MAX_SUGGESTION_COUNT)
            .forEach { suggestion ->
                runCatching {
                    suggestion.inflate(
                        this,
                        inflateSize,
                        mainExecutor,
                        Consumer { view ->
                            inlineAutofillController.appendInflatedSuggestion(generation, view)
                        }
                    )
                }
            }

        return true
    }

    fun getCurrentView(): View {
        return KeyboardContentView(this)
    }

    override fun onCreate() {
        super.onCreate()
        activeInstanceReference = WeakReference(this)
        savedStateRegistryController.performRestore(null)
    }

    val imeLanguage: String
        /**
         * Returns the language of the IME. The language is used in voice recognition to match the
         * current language of the IME.
         */
        get() = when (keyboardPreferences().languageMode) {
            KeyboardLanguageMode.ENGLISH -> "en-US"
            KeyboardLanguageMode.VIETNAMESE,
            KeyboardLanguageMode.VIETNAMESE_ENGLISH -> "vi-VN"
        }

    private val flag: Int
        get() = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION // or 0

    fun doCommitContent(imageUrl: String, fallbackText: String = imageUrl) {
        doCommitContentInternal(imageUrl, fallbackText, allowTestSource = false)
    }

    internal fun doCommitTestContent(imageUrl: String, fallbackText: String = imageUrl) {
        doCommitContentInternal(imageUrl, fallbackText, allowTestSource = true)
    }

    private fun doCommitContentInternal(
        imageUrl: String,
        fallbackText: String,
        allowTestSource: Boolean
    ) {
        val session = currentEditorSessionState.value
        val sessionId = session.id
        fun commitFallbackIfCurrent() {
            if (currentEditorSessionState.value.id == sessionId) {
                createInputConnectionOps().commitText(fallbackText)
            }
        }
        if (!isAllowedProductImageLoadSource(imageUrl, allowTestSource)) {
            commitFallbackIfCurrent()
            return
        }
        val decision = RichContentPolicy.decide(
            privacy = session.privacy,
            supportedMimeTypes = session.supportedMimeTypes,
            requestedMimeType = IMAGE_MIME_TYPE
        )
        if (decision == RichContentDecision.TEXT_FALLBACK) {
            commitFallbackIfCurrent()
            return
        }
        Glide.with(this).load(imageUrl).into(object : CustomTarget<Drawable>() {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                val bitmap = (resource as? BitmapDrawable)?.bitmap
                if (bitmap == null) {
                    commitFallbackIfCurrent()
                    return
                }
                serviceScope.launch {
                    try {
                        val tempFile = withContext(Dispatchers.IO) {
                            val shareCache = File(cacheDir, "seller_product_shares").apply {
                                mkdirs()
                            }
                            shareCache.listFiles()
                                ?.sortedByDescending(File::lastModified)
                                ?.drop(MAX_SHARED_PRODUCT_CACHE_FILES - 1)
                                ?.forEach(File::delete)
                            File.createTempFile("product_", ".jpg", shareCache).apply {
                                FileOutputStream(this).use {
                                    bitmap.compress(
                                        Bitmap.CompressFormat.JPEG,
                                        100,
                                        it
                                    )
                                }
                            }
                        }
                        val uri = FileProvider.getUriForFile(
                            this@SellerKeyboard,
                            "com.terasumi.sellerkeyboard.fileprovider",
                            tempFile
                        )
                        val inputContentInfo = InputContentInfoCompat(
                            uri,
                            ClipDescription("Seller product image", arrayOf(IMAGE_MIME_TYPE)),
                            null
                        )
                        val committed = currentEditorSessionState.value.id == sessionId &&
                            InputConnectionCompat.commitContent(
                                currentInputConnection,
                                currentInputEditorInfo,
                                inputContentInfo,
                                flag,
                                null
                            )
                        if (!committed) {
                            commitFallbackIfCurrent()
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Content commit failed: ${e.javaClass.simpleName}")
                        commitFallbackIfCurrent()
                    } catch (e: RuntimeException) {
                        Log.e(TAG, "Target editor rejected content: ${e.javaClass.simpleName}")
                        commitFallbackIfCurrent()
                    }
                }
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                commitFallbackIfCurrent()
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                // Clean up the resources
            }
        })
    }

    private fun isAllowedProductImageLoadSource(
        imageUrl: String,
        allowTestSource: Boolean
    ): Boolean {
        if (ProductImageSourcePolicy.evaluate(imageUrl, sellerAuthorized = true).canShare) {
            return true
        }
        if (!allowTestSource) return false

        val uri = Uri.parse(imageUrl)
        if (uri.scheme.equals("http", ignoreCase = true) &&
            uri.host == "127.0.0.1" && uri.port == DEBUG_REMOTE_IMAGE_TEST_PORT) {
            return true
        }
        if (!uri.scheme.equals("file", ignoreCase = true) || uri.path.isNullOrBlank()) {
            return false
        }
        return runCatching {
            File(uri.path!!).canonicalFile.toPath().startsWith(cacheDir.canonicalFile.toPath())
        }.getOrDefault(false)
    }

    fun isKeyHapticEnabled(): Boolean {
        return settingsRepository.hapticEnabled
    }

    fun setKeyHapticEnabled(enabled: Boolean) {
        settingsRepository.hapticEnabled = enabled
    }

    fun isVietnameseInputEnabled(): Boolean {
        return settingsRepository.vietnameseInputEnabled
    }

    fun setVietnameseInputEnabled(enabled: Boolean) {
        settingsRepository.vietnameseInputEnabled = enabled
    }

    fun isClipboardHistoryEnabled(): Boolean {
        return settingsRepository.clipboardHistoryEnabled
    }

    fun clipboardRepository(): ClipboardRepository {
        return clipboardHistoryRepository
    }

    fun isVoiceTypingEnabled(): Boolean {
        return settingsRepository.voiceTypingEnabled
    }

    fun voiceTypingController(): VoiceTypingController {
        return voiceTypingController
    }

    private fun requestRecordAudioPermissionFromActivity() {
        runCatching {
            startActivity(HomeActivity.recordAudioPermissionIntent(this))
        }.onFailure { error ->
            Log.e(TAG, "Microphone permission request failed: ${error.javaClass.simpleName}")
        }
    }

    fun isInlineAutofillEnabled(): Boolean {
        return settingsRepository.inlineAutofillEnabled
    }

    fun inlineAutofillController(): InlineAutofillController<View> {
        return inlineAutofillController
    }

    fun keyboardPreferences(): KeyboardPreferences {
        val base = settingsRepository.snapshot()
        val activeProfileId = currentEditorSessionState.value.activeProfileId
        if (activeProfileId == KeyboardProfile.Global.id) return base
        val activeProfile = appProfileStore.profiles().firstOrNull { it.id == activeProfileId }
            ?: return base
        return activeProfile.applyTo(base)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    fun candidateController(): CandidateController = textCandidateController

    fun editorCompletionController(): EditorCompletionController = editorCompletionController

    fun translationController(): MlKitTranslationController = translationController

    fun sellerShortcutController(): SellerShortcutController = sellerShortcutController

    fun commitEditorCompletion(item: EditorCompletionItem): Boolean {
        val session = currentEditorSessionState.value
        if (session.id == 0L || session.privacy.isSensitive) return false
        val committed = currentInputConnection?.commitCompletion(item.completionInfo) ?: false
        return committed || createInputConnectionOps().commitText(item.text)
    }

    fun createInputConnectionOps(): InputConnectionOps {
        return InputConnectionOps.fromInputConnection { currentInputConnection }
    }

    fun currentEditorInfoPolicy(): EditorInfoPolicy {
        val session = currentEditorSessionState.value
        return if (session.id != 0L) {
            EditorInfoPolicy(inputType = session.inputType, privacy = session.privacy)
        } else {
            EditorInfoPolicy.fromInputType(currentInputEditorInfo?.inputType ?: currentInputTypeState.value)
        }
    }

    fun currentEditorSession(): EditorSession {
        return currentEditorSessionState.value
    }

    fun keyboardRuntimeState(): androidx.compose.runtime.MutableState<KeyboardRuntimeState> {
        return keyboardRuntimeCoordinator.state
    }

    fun updateActiveSurface(surface: ActiveImeSurface) {
        val allowedSurface = if (currentEditorSessionState.value.privacy.isSensitive) {
            ActiveImeSurface.KEYBOARD
        } else {
            surface
        }
        editorSessionCoordinator.updateSurface(allowedSurface)
    }

    fun updateCandidateSource(source: ContextStripSource) {
        editorSessionCoordinator.updateCandidateSource(source)
    }

    fun updateCompositionState(text: String) {
        val cursor = currentEditorSessionState.value.selectionEnd.coerceAtLeast(0)
        editorSessionCoordinator.updateComposition(
            text = text,
            start = (cursor - text.length).coerceAtLeast(0),
            end = cursor
        )
    }

    internal fun fieldScopedStateSnapshot(): ImeFieldScopedStateSnapshot {
        val session = currentEditorSessionState.value
        return ImeFieldScopedStateSnapshot(
            sessionId = session.id,
            isSensitive = session.privacy.isSensitive,
            activeSurface = session.activeSurface,
            candidateSource = session.candidateSource,
            candidateCount = if (textCandidateControllerDelegate.isInitialized()) {
                textCandidateController.state.value.candidates.size
            } else {
                0
            },
            completionCount = editorCompletionController.state.value.items.size,
            inlineSuggestionCount = if (inlineAutofillControllerDelegate.isInitialized()) {
                inlineAutofillController.state.value.suggestions.size
            } else {
                0
            },
            sellerShortcutCount = if (sellerShortcutControllerDelegate.isInitialized()) {
                sellerShortcutController.state.value.templates.size
            } else {
                0
            },
            clipboardReaderStarted = activeClipboardReaderDelegate.isInitialized() &&
                activeClipboardReader.isStarted,
            clipboardObservedChangeCount = if (activeClipboardReaderDelegate.isInitialized()) {
                activeClipboardReader.observedPrimaryClipChanges
            } else {
                0L
            },
            voiceIdle = !voiceTypingControllerDelegate.isInitialized() ||
                voiceTypingController.state.value is VoiceTypingState.Idle,
            translationIdle = translationController.state.value is TranslationState.Idle
        )
    }

    private fun invalidateFieldScopedState() {
        if (activeClipboardReaderDelegate.isInitialized()) activeClipboardReader.stop()
        if (inlineAutofillControllerDelegate.isInitialized()) inlineAutofillController.clear()
        editorCompletionController.clear()
        if (textCandidateControllerDelegate.isInitialized()) textCandidateController.clear()
        translationController.cancel()
        if (sellerShortcutControllerDelegate.isInitialized()) sellerShortcutController.clear()
        if (voiceTypingControllerDelegate.isInitialized()) voiceTypingController.resetForFieldChange()
    }

    private fun resolveEditorSession(info: EditorInfo?, sessionId: Long): EditorSession {
        if (info == null) {
            return EditorSession.Inactive
        }
        val writingToolsEnabled = if (Build.VERSION.SDK_INT >= 36) {
            info.isWritingToolsEnabled
        } else {
            true
        }
        val activeProfile = AppProfileResolver.resolve(
            packageName = info.packageName.orEmpty(),
            profiles = appProfileStore.profiles(),
            bindings = appProfileStore.bindings()
        )
        val activePreferences = if (activeProfile.id == KeyboardProfile.Global.id) {
            settingsRepository.snapshot()
        } else {
            activeProfile.applyTo(settingsRepository.snapshot())
        }
        return EditorSessionResolver.resolve(
            EditorSessionRequest(
                id = sessionId,
                inputType = info.inputType,
                imeOptions = info.imeOptions,
                actionId = info.actionId,
                actionLabel = info.actionLabel?.toString(),
                hasCustomActionLabel = info.actionLabel != null,
                initialCapsMode = info.initialCapsMode,
                selectionStart = info.initialSelStart,
                selectionEnd = info.initialSelEnd,
                packageName = info.packageName.orEmpty(),
                activeProfileId = activeProfile.id,
                activeProfileName = activeProfile.name,
                preferredReplyPackId = activeProfile.preferredReplyPackId,
                languageMode = activePreferences.languageMode,
                supportedMimeTypes = info.contentMimeTypes?.toSet().orEmpty(),
                isAuthenticationValue = AuthenticationFieldDetector.isAuthenticationValue(
                    info.hintText,
                    info.privateImeOptions
                ),
                writingToolsEnabled = writingToolsEnabled,
                isPrivateProfile = activeProfile.isPrivate
            )
        )
    }

    fun performKeyHapticFeedback() {
        if (settingsRepository.soundEnabled) {
            audioManager?.playSoundEffect(
                AudioManager.FX_KEYPRESS_STANDARD,
                settingsRepository.soundVolume
            )
        }
        if (!isKeyHapticEnabled()) {
            return
        }
        val targetVibrator = vibrator ?: return
        if (!targetVibrator.hasVibrator()) {
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                targetVibrator.vibrate(
                    VibrationEffect.createOneShot(
                        HAPTIC_DURATION_MS,
                        settingsRepository.hapticStrength
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                targetVibrator.vibrate(HAPTIC_DURATION_MS)
            }
        } catch (_: SecurityException) {
            // Ignore when device policy blocks haptics for current user/profile.
        }
    }

    override fun onDestroy() {
        if (activeClipboardReaderDelegate.isInitialized()) activeClipboardReader.stop()
        if (inlineAutofillControllerDelegate.isInitialized()) inlineAutofillController.clear()
        editorCompletionController.clear()
        if (textCandidateControllerDelegate.isInitialized()) textCandidateController.clear()
        if (voiceTypingControllerDelegate.isInitialized()) voiceTypingController.destroy()
        translationController.close()
        if (sellerShortcutControllerDelegate.isInitialized()) sellerShortcutController.clear()
        serviceScope.cancel()
        if (activeInstanceReference?.get() === this) activeInstanceReference = null
        super.onDestroy()
    }

    //--------------------------//


    override val viewModelStore: ViewModelStore
        get() = store
    override val lifecycle: Lifecycle
        get() = serviceLifecycleDispatcher.lifecycle


    //ViewModelStore Methods
    private val store = ViewModelStore()

    //SaveStateRegistry Methods

    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    companion object {
        @Volatile
        private var activeInstanceReference: WeakReference<SellerKeyboard>? = null

        internal fun activeInstanceForTests(): SellerKeyboard? = activeInstanceReference?.get()

        private const val HAPTIC_DURATION_MS = 12L
        private const val INITIAL_CONTEXT_UTF16 = 128
        private const val MAX_SHARED_PRODUCT_CACHE_FILES = 5
        private const val DEBUG_REMOTE_IMAGE_TEST_PORT = 18080
        private const val IMAGE_MIME_TYPE = "image/jpeg"
    }
}
