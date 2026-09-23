package com.terasumi.sellerkeyboard.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.PersistableBundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.CompletionInfo
import androidx.annotation.Keep
import com.google.gson.Gson
import com.terasumi.sellerkeyboard.ime.ActiveImeSurface
import com.terasumi.sellerkeyboard.ime.RichContentPolicy
import com.terasumi.sellerkeyboard.database.DatabaseHelper
import com.terasumi.sellerkeyboard.database.SellerReplyTemplateDao
import com.terasumi.sellerkeyboard.database.SellerReplyTemplateTable
import com.terasumi.sellerkeyboard.database.SnippetsTable
import com.terasumi.sellerkeyboard.clipboard.ClipboardTable
import com.terasumi.sellerkeyboard.emoji.LocalRecentEmojiProvider
import com.terasumi.sellerkeyboard.profile.AppProfileBinding
import com.terasumi.sellerkeyboard.profile.KeyboardProfile
import com.terasumi.sellerkeyboard.profile.SharedPreferencesAppProfileStore
import com.terasumi.sellerkeyboard.service.SellerKeyboard
import com.terasumi.sellerkeyboard.settings.KeyboardHeightPreset
import com.terasumi.sellerkeyboard.settings.KeyboardLanguageMode
import com.terasumi.sellerkeyboard.settings.OneHandedSide
import com.terasumi.sellerkeyboard.settings.KeyboardPreferences
import com.terasumi.sellerkeyboard.settings.KeyboardSettingsRepository
import com.terasumi.sellerkeyboard.settings.KeyboardThemeMode
import com.terasumi.sellerkeyboard.suggestion.CandidateQuery
import com.terasumi.sellerkeyboard.suggestion.SharedPreferencesPersonalDictionary
import com.terasumi.sellerkeyboard.seller.SellerReplyTemplate
import com.terasumi.sellerkeyboard.seller.ReplyPackCodec
import com.terasumi.sellerkeyboard.seller.ReplyPackImportResult
import com.terasumi.sellerkeyboard.translation.TranslationDirection
import com.terasumi.sellerkeyboard.translation.TranslationState
import com.terasumi.sellerkeyboard.voice.AndroidSpeechRecognizerRepository
import com.terasumi.sellerkeyboard.voice.VoiceTypingError
import com.terasumi.sellerkeyboard.voice.VoiceTypingController
import com.terasumi.sellerkeyboard.voice.VoiceTypingState
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ImeTestBridgeProvider : ContentProvider() {
    private var staleInlineGeneration = 0L
    private var originalTestProfiles: Map<String, KeyboardProfile?>? = null
    private var originalTestBindings: Map<String, AppProfileBinding?>? = null
    private var originalKeyboardPreferences: KeyboardPreferences? = null
    private var originalSellerWorkflowTemplates: Map<String, SellerReplyTemplate?>? = null
    private var originalSellerBenchmarkTemplates: Map<String, SellerReplyTemplate?>? = null
    private var originalSellerBenchmarkProfile: KeyboardProfile? = null
    private var originalSellerBenchmarkBinding: AppProfileBinding? = null
    private var sellerBenchmarkProfileCaptured = false
    private var originalSellerBenchmarkVietnameseEnabled: Boolean? = null
    private var providerAudioController: VoiceTypingController? = null
    private var providerAudioSource: ParcelFileDescriptor? = null

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        enforceTrustedCaller()
        return onMainThread {
            if (method == METHOD_PROVIDER_AUDIO_FIXTURE_SNAPSHOT) {
                return@onMainThread providerAudioFixtureSnapshot()
            }
            if (method == METHOD_DELETE_PROVIDER_AUDIO_FIXTURE) {
                resetProviderAudioFixture()
                val deleted = File(
                    requireNotNull(context).filesDir,
                    PROVIDER_AUDIO_FIXTURE_FILE
                ).let { !it.exists() || it.delete() }
                return@onMainThread Bundle().apply {
                    putBoolean(KEY_PROVIDER_AUDIO_FIXTURE_DELETED, deleted)
                }
            }
            val service = SellerKeyboard.activeInstanceForTests()
                ?: return@onMainThread Bundle().apply { putBoolean(KEY_ACTIVE, false) }
            when (method) {
                METHOD_SNAPSHOT -> clipboardSnapshot(service)
                METHOD_SEED_NORMAL_STATE -> {
                    val session = service.currentEditorSession()
                    service.candidateController().request(
                        sessionId = session.id,
                        query = CandidateQuery(currentToken = "shipping")
                    )
                    service.onDisplayCompletions(
                        arrayOf(CompletionInfo(41L, 0, "shipping today"))
                    )
                    staleInlineGeneration = service.inlineAutofillController().beginResponse()
                    val appended = service.inlineAutofillController().appendInflatedSuggestion(
                        staleInlineGeneration,
                        View(service)
                    )
                    snapshot(service).apply { putBoolean(KEY_INLINE_APPEND_ACCEPTED, appended) }
                }
                METHOD_SEND_LATE_RESULTS -> {
                    service.onDisplayCompletions(
                        arrayOf(CompletionInfo(42L, 0, "late completion"))
                    )
                    val appended = service.inlineAutofillController().appendInflatedSuggestion(
                        staleInlineGeneration,
                        View(service)
                    )
                    service.updateActiveSurface(ActiveImeSurface.REPLIES)
                    snapshot(service).apply { putBoolean(KEY_INLINE_APPEND_ACCEPTED, appended) }
                }
                METHOD_SEED_CANDIDATE_TRACE -> {
                    service.inlineAutofillController().clear()
                    service.editorCompletionController().clear()
                    val session = service.currentEditorSession()
                    service.candidateController().request(
                        sessionId = session.id,
                        query = CandidateQuery(currentToken = "shipping")
                    )
                    snapshot(service)
                }
                METHOD_COMMIT_TEST_PRODUCT -> {
                    service.doCommitTestContent(testImageUrl(service), TEST_PRODUCT_FALLBACK)
                    snapshot(service).apply { putBoolean(KEY_COMMIT_REQUESTED, true) }
                }
                METHOD_COMMIT_REMOTE_TEST_PRODUCT -> {
                    service.doCommitTestContent(
                        validatedRemoteImageUrl(arg),
                        TEST_PRODUCT_FALLBACK
                    )
                    snapshot(service).apply { putBoolean(KEY_COMMIT_REQUESTED, true) }
                }
                METHOD_COMMIT_PRODUCTION_PRODUCT -> {
                    service.doCommitContent(
                        validatedRemoteImageUrl(arg),
                        TEST_PRODUCT_FALLBACK
                    )
                    snapshot(service).apply { putBoolean(KEY_COMMIT_REQUESTED, true) }
                }
                METHOD_CLEAR_CLIPBOARD_HISTORY -> {
                    service.clipboardRepository().clear()
                    service.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                        ClipData.newPlainText("", "")
                    )
                    clipboardSnapshot(service).apply { putBoolean(KEY_CLIPBOARD_CLEARED, true) }
                }
                METHOD_SET_NORMAL_CLIP -> setTestClip(service, NORMAL_CLIP_TEXT)
                METHOD_SET_MARKED_SENSITIVE_CLIP -> setTestClip(
                    service,
                    MARKED_SENSITIVE_CLIP_TEXT,
                    markedSensitive = true
                )
                METHOD_SET_DISABLED_CLIP -> setTestClip(service, DISABLED_CLIP_TEXT)
                METHOD_SET_PASSWORD_CLIP -> setTestClip(service, PASSWORD_CLIP_TEXT)
                METHOD_ENABLE_CLIPBOARD_HISTORY -> {
                    KeyboardSettingsRepository(service).clipboardHistoryEnabled = true
                    service.clipboardRepository().refresh()
                    clipboardSnapshot(service)
                }
                METHOD_DISABLE_CLIPBOARD_HISTORY -> {
                    KeyboardSettingsRepository(service).clipboardHistoryEnabled = false
                    service.clipboardRepository().refresh()
                    clipboardSnapshot(service)
                }
                METHOD_SEED_APP_PROFILES -> seedAppProfiles(service)
                METHOD_UNBIND_HOST_PROFILE -> {
                    SharedPreferencesAppProfileStore(service).removeBinding(HOST_PACKAGE)
                    snapshot(service).apply { putBoolean(KEY_HOST_PROFILE_UNBOUND, true) }
                }
                METHOD_RESTORE_APP_PROFILES -> restoreAppProfiles(service)
                METHOD_SEED_SELLER_WORKFLOW -> seedSellerWorkflow(service)
                METHOD_SELLER_WORKFLOW_SNAPSHOT -> sellerWorkflowSnapshot(service)
                METHOD_RESTORE_SELLER_WORKFLOW -> restoreSellerWorkflow(service)
                METHOD_SEED_SELLER_BENCHMARK -> seedSellerBenchmark(service)
                METHOD_RESTORE_SELLER_BENCHMARK -> restoreSellerBenchmark(service)
                METHOD_CAPTURE_PERSONAL_DICTIONARY_FIXTURE -> {
                    capturePersonalDictionaryFixture(service)
                }
                METHOD_PERSONAL_DICTIONARY_SNAPSHOT -> personalDictionarySnapshot(service)
                METHOD_RESTORE_PERSONAL_DICTIONARY_FIXTURE -> {
                    restorePersonalDictionaryFixture(service)
                }
                METHOD_CAPTURE_EMOJI_RECENTS_FIXTURE -> captureEmojiRecentsFixture(service)
                METHOD_EMOJI_RECENTS_SNAPSHOT -> emojiRecentsSnapshot(service)
                METHOD_RESTORE_EMOJI_RECENTS_FIXTURE -> restoreEmojiRecentsFixture(service)
                METHOD_CAPTURE_AND_SEED_UPGRADE_FIXTURE -> {
                    captureAndSeedUpgradeFixture(service)
                }
                METHOD_UPGRADE_FIXTURE_SNAPSHOT -> upgradeFixtureSnapshot(service)
                METHOD_RESTORE_UPGRADE_FIXTURE -> restoreUpgradeFixture(service)
                METHOD_FEATURE_ATTRIBUTION_SNAPSHOT -> featureAttributionSnapshot(service)
                METHOD_START_VOICE_ATTRIBUTION -> {
                    service.voiceTypingController().start()
                    featureAttributionSnapshot(service)
                }
                METHOD_CANCEL_VOICE_ATTRIBUTION -> {
                    service.voiceTypingController().cancel()
                    featureAttributionSnapshot(service)
                }
                METHOD_START_PROVIDER_AUDIO_FIXTURE -> {
                    startProviderAudioFixture(service)
                    featureAttributionSnapshot(service)
                }
                METHOD_CANCEL_PROVIDER_AUDIO_FIXTURE -> {
                    resetProviderAudioFixture()
                    featureAttributionSnapshot(service)
                }
                METHOD_DELETE_TRANSLATION_MODEL -> {
                    service.translationController().deleteDownloadedModel(
                        TranslationDirection.VI_TO_EN
                    )
                    featureAttributionSnapshot(service)
                }
                METHOD_START_TRANSLATION_ATTRIBUTION -> {
                    service.translationController().translate(
                        TRANSLATION_ATTRIBUTION_CANARY,
                        TranslationDirection.VI_TO_EN,
                        isSensitive = false
                    )
                    featureAttributionSnapshot(service)
                }
                METHOD_DELETE_EN_TO_VI_MODEL -> {
                    service.translationController().deleteDownloadedModel(
                        TranslationDirection.EN_TO_VI
                    )
                    featureAttributionSnapshot(service)
                }
                METHOD_START_EN_TO_VI_UI_TRANSLATION -> {
                    service.translationController().translate(
                        TRANSLATION_UI_ORIGINAL,
                        TranslationDirection.EN_TO_VI,
                        isSensitive = false
                    )
                    featureAttributionSnapshot(service)
                }
                METHOD_CANCEL_TRANSLATION_ATTRIBUTION -> {
                    service.translationController().cancel()
                    featureAttributionSnapshot(service)
                }
                else -> throw IllegalArgumentException("Unknown debug IME bridge method: $method")
            }
        }
    }

    private fun featureAttributionSnapshot(service: SellerKeyboard): Bundle {
        val voiceState = service.voiceTypingController().state.value
        val translationState = service.translationController().state.value
        val translationPreview = translationState as? TranslationState.Preview
        return snapshot(service).apply {
            putString(KEY_VOICE_STATE, stableVoiceStateName(voiceState))
            putString(
                KEY_VOICE_ERROR,
                (voiceState as? VoiceTypingState.Error)?.error?.let(::stableVoiceErrorName)
                    .orEmpty()
            )
            putBoolean(
                KEY_VOICE_USING_ON_DEVICE,
                when (voiceState) {
                    is VoiceTypingState.Listening -> voiceState.usingOnDevice
                    is VoiceTypingState.Processing -> voiceState.usingOnDevice
                    is VoiceTypingState.Result -> voiceState.usingOnDevice
                    else -> false
                }
            )
            val providerAudioState = providerAudioController?.state?.value
                ?: VoiceTypingState.Idle
            val providerAudioResult = providerAudioState as? VoiceTypingState.Result
            putString(KEY_PROVIDER_AUDIO_STATE, stableVoiceStateName(providerAudioState))
            putString(
                KEY_PROVIDER_AUDIO_ERROR,
                (providerAudioState as? VoiceTypingState.Error)?.error
                    ?.let(::stableVoiceErrorName).orEmpty()
            )
            putString(KEY_PROVIDER_AUDIO_TRANSCRIPT, providerAudioResult?.transcript.orEmpty())
            putString(
                KEY_PROVIDER_AUDIO_TRANSCRIPT_SHA256,
                providerAudioResult?.transcript?.let(::sha256).orEmpty()
            )
            putBoolean(KEY_PROVIDER_AUDIO_REVIEW_REQUIRED, providerAudioResult != null)
            putBoolean(
                KEY_PROVIDER_AUDIO_USING_ON_DEVICE,
                when (providerAudioState) {
                    is VoiceTypingState.Listening -> providerAudioState.usingOnDevice
                    is VoiceTypingState.Processing -> providerAudioState.usingOnDevice
                    is VoiceTypingState.Result -> providerAudioState.usingOnDevice
                    else -> false
                }
            )
            putString(KEY_TRANSLATION_STATE, stableTranslationStateName(translationState))
            putString(
                KEY_TRANSLATION_ERROR,
                (translationState as? TranslationState.Error)?.message.orEmpty()
            )
            putBoolean(
                KEY_TRANSLATION_ORIGINAL_MATCHES_CANARY,
                translationPreview?.original == TRANSLATION_ATTRIBUTION_CANARY
            )
            putBoolean(
                KEY_TRANSLATION_ORIGINAL_MATCHES_UI_FIXTURE,
                translationPreview?.original == TRANSLATION_UI_ORIGINAL
            )
            putString(
                KEY_TRANSLATION_DIRECTION,
                translationPreview?.direction?.name.orEmpty()
            )
            putString(
                KEY_TRANSLATION_TRANSLATED_SHA256,
                translationPreview?.translated?.let(::sha256).orEmpty()
            )
            putString(
                KEY_TRANSLATION_ORIGINAL_PLUS_TRANSLATED_SHA256,
                translationPreview?.let { sha256(it.original + "\n" + it.translated) }.orEmpty()
            )
        }
    }

    private fun stableVoiceStateName(state: VoiceTypingState): String = when (state) {
        VoiceTypingState.Idle -> "Idle"
        VoiceTypingState.RequestingPermission -> "RequestingPermission"
        is VoiceTypingState.Listening -> "Listening"
        is VoiceTypingState.Processing -> "Processing"
        is VoiceTypingState.Result -> "Result"
        is VoiceTypingState.Error -> "Error"
    }

    private fun stableVoiceErrorName(error: VoiceTypingError): String = when (error) {
        VoiceTypingError.Disabled -> "Disabled"
        VoiceTypingError.DisabledForField -> "DisabledForField"
        VoiceTypingError.PermissionDenied -> "PermissionDenied"
        VoiceTypingError.NoRecognitionService -> "NoRecognitionService"
        VoiceTypingError.Busy -> "Busy"
        VoiceTypingError.NoMatch -> "NoMatch"
        VoiceTypingError.Network -> "Network"
        VoiceTypingError.Client -> "Client"
        VoiceTypingError.Unknown -> "Unknown"
    }

    private fun stableTranslationStateName(state: TranslationState): String = when (state) {
        TranslationState.Idle -> "Idle"
        is TranslationState.DownloadingModel -> "DownloadingModel"
        is TranslationState.Translating -> "Translating"
        is TranslationState.DeletingModel -> "DeletingModel"
        is TranslationState.ModelDeleted -> "ModelDeleted"
        is TranslationState.Preview -> "Preview"
        is TranslationState.Error -> "Error"
    }

    private fun startProviderAudioFixture(service: SellerKeyboard) {
        resetProviderAudioFixture()
        val fixtureFile = File(requireNotNull(context).filesDir, PROVIDER_AUDIO_FIXTURE_FILE)
        require(fixtureFile.isFile && fixtureFile.length() > 0L) {
            "Missing real-provider audio fixture: ${fixtureFile.absolutePath}"
        }
        val source = ParcelFileDescriptor.open(
            fixtureFile,
            ParcelFileDescriptor.MODE_READ_ONLY
        )
        val configuredComponent = requireNotNull(
            ComponentName.unflattenFromString(
                Settings.Secure.getString(
                    service.contentResolver,
                    PROVIDER_COMPONENT_SETTING
                ).orEmpty()
            )
        ) { "No configured real speech-recognition provider" }
        val controller = VoiceTypingController(
            speechRecognizerRepository = AndroidSpeechRecognizerRepository(
                context = service,
                recognitionServiceComponent = configuredComponent,
                injectedAudioSource = source
            ),
            hasRecordAudioPermission = { true },
            editorInfoPolicyProvider = service::currentEditorInfoPolicy,
            voiceTypingEnabled = { true },
            languageTagProvider = { PROVIDER_AUDIO_LANGUAGE_TAG }
        )
        providerAudioSource = source
        providerAudioController = controller
        controller.start()
    }

    private fun providerAudioFixtureSnapshot(): Bundle {
        val fixture = File(requireNotNull(context).filesDir, PROVIDER_AUDIO_FIXTURE_FILE)
        return Bundle().apply {
            putBoolean(KEY_PROVIDER_AUDIO_FIXTURE_EXISTS, fixture.isFile)
            putLong(KEY_PROVIDER_AUDIO_FIXTURE_BYTES, if (fixture.isFile) fixture.length() else 0L)
            putString(
                KEY_PROVIDER_AUDIO_FIXTURE_SHA256,
                if (fixture.isFile) {
                    MessageDigest.getInstance("SHA-256").digest(fixture.readBytes())
                        .joinToString("") { "%02x".format(it) }
                } else {
                    ""
                }
            )
        }
    }

    private fun resetProviderAudioFixture() {
        providerAudioController?.destroy()
        providerAudioController = null
        runCatching { providerAudioSource?.close() }
        providerAudioSource = null
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun capturePersonalDictionaryFixture(service: SellerKeyboard): Bundle {
        val backup = service.getSharedPreferences(
            PERSONAL_DICTIONARY_FIXTURE_PREFS,
            Context.MODE_PRIVATE
        )
        val dictionary = SharedPreferencesPersonalDictionary(service)
        val settings = KeyboardSettingsRepository(service)
        if (!backup.getBoolean(PERSONAL_DICTIONARY_FIXTURE_CAPTURED, false)) {
            check(
                backup.edit()
                    .putBoolean(PERSONAL_DICTIONARY_FIXTURE_CAPTURED, true)
                    .putStringSet(PERSONAL_DICTIONARY_FIXTURE_ORIGINAL_WORDS, dictionary.words())
                    .putBoolean(
                        PERSONAL_DICTIONARY_FIXTURE_ORIGINAL_LEARNING,
                        settings.learningEnabled
                    )
                    .putString(
                        PERSONAL_DICTIONARY_FIXTURE_ORIGINAL_LANGUAGE,
                        settings.languageMode.name
                    )
                    .commit()
            ) { "Could not persist the personal-dictionary test rollback state" }
        }
        dictionary.remove(PERSONAL_DICTIONARY_FIXTURE_WORD)
        settings.learningEnabled = true
        settings.languageMode = KeyboardLanguageMode.ENGLISH
        val originalWords = backup.getStringSet(
            PERSONAL_DICTIONARY_FIXTURE_ORIGINAL_WORDS,
            emptySet()
        ).orEmpty()
        val originalLearning = backup.getBoolean(
            PERSONAL_DICTIONARY_FIXTURE_ORIGINAL_LEARNING,
            true
        )
        val originalLanguage = backup.getString(
            PERSONAL_DICTIONARY_FIXTURE_ORIGINAL_LANGUAGE,
            KeyboardLanguageMode.VIETNAMESE_ENGLISH.name
        ) ?: KeyboardLanguageMode.VIETNAMESE_ENGLISH.name
        return personalDictionarySnapshot(service).apply {
            putBoolean(KEY_PERSONAL_DICTIONARY_FIXTURE_CAPTURED, true)
            putInt(KEY_PERSONAL_DICTIONARY_ORIGINAL_COUNT, originalWords.size)
            putString(
                KEY_PERSONAL_DICTIONARY_ORIGINAL_SHA256,
                personalDictionaryWordsSha256(originalWords)
            )
            putBoolean(KEY_PERSONAL_DICTIONARY_ORIGINAL_LEARNING, originalLearning)
            putString(KEY_PERSONAL_DICTIONARY_ORIGINAL_LANGUAGE, originalLanguage)
        }
    }

    private fun restorePersonalDictionaryFixture(service: SellerKeyboard): Bundle {
        val backup = service.getSharedPreferences(
            PERSONAL_DICTIONARY_FIXTURE_PREFS,
            Context.MODE_PRIVATE
        )
        check(backup.getBoolean(PERSONAL_DICTIONARY_FIXTURE_CAPTURED, false)) {
            "Personal-dictionary test rollback state was not captured"
        }
        val dictionary = SharedPreferencesPersonalDictionary(service)
        val originalWords = backup.getStringSet(
            PERSONAL_DICTIONARY_FIXTURE_ORIGINAL_WORDS,
            emptySet()
        ).orEmpty()
        dictionary.clear()
        originalWords.forEach(dictionary::add)
        KeyboardSettingsRepository(service).learningEnabled = backup.getBoolean(
            PERSONAL_DICTIONARY_FIXTURE_ORIGINAL_LEARNING,
            true
        )
        KeyboardSettingsRepository(service).languageMode = KeyboardLanguageMode.valueOf(
            backup.getString(
                PERSONAL_DICTIONARY_FIXTURE_ORIGINAL_LANGUAGE,
                KeyboardLanguageMode.VIETNAMESE_ENGLISH.name
            ) ?: KeyboardLanguageMode.VIETNAMESE_ENGLISH.name
        )
        check(backup.edit().clear().commit()) {
            "Could not clear the personal-dictionary test rollback state"
        }
        return personalDictionarySnapshot(service).apply {
            putBoolean(KEY_PERSONAL_DICTIONARY_FIXTURE_RESTORED, true)
            putInt(KEY_PERSONAL_DICTIONARY_ORIGINAL_COUNT, originalWords.size)
            putString(
                KEY_PERSONAL_DICTIONARY_ORIGINAL_SHA256,
                personalDictionaryWordsSha256(originalWords)
            )
        }
    }

    private fun personalDictionarySnapshot(service: SellerKeyboard): Bundle {
        val words = SharedPreferencesPersonalDictionary(service).words()
        val settings = KeyboardSettingsRepository(service)
        val backup = service.getSharedPreferences(
            PERSONAL_DICTIONARY_FIXTURE_PREFS,
            Context.MODE_PRIVATE
        )
        return snapshot(service).apply {
            putBoolean(
                KEY_PERSONAL_DICTIONARY_CONTAINS_FIXTURE,
                PERSONAL_DICTIONARY_FIXTURE_WORD in words
            )
            putInt(KEY_PERSONAL_DICTIONARY_COUNT, words.size)
            putString(KEY_PERSONAL_DICTIONARY_SHA256, personalDictionaryWordsSha256(words))
            putBoolean(
                KEY_PERSONAL_DICTIONARY_LEARNING_ENABLED,
                settings.learningEnabled
            )
            putString(KEY_PERSONAL_DICTIONARY_SETTINGS_LANGUAGE, settings.languageMode.name)
            putBoolean(
                KEY_PERSONAL_DICTIONARY_FIXTURE_CAPTURED,
                backup.getBoolean(PERSONAL_DICTIONARY_FIXTURE_CAPTURED, false)
            )
        }
    }

    private fun personalDictionaryWordsSha256(words: Set<String>): String =
        sha256(words.sorted().joinToString("\u0000"))

    private fun captureEmojiRecentsFixture(service: SellerKeyboard): Bundle {
        val backup = service.getSharedPreferences(EMOJI_RECENTS_FIXTURE_PREFS, Context.MODE_PRIVATE)
        val provider = LocalRecentEmojiProvider(service)
        val variantPreferences = service.getSharedPreferences(
            EMOJI_PICKER_VARIANT_PREFS,
            Context.MODE_PRIVATE
        )
        if (!backup.getBoolean(EMOJI_RECENTS_FIXTURE_CAPTURED, false)) {
            check(
                backup.edit()
                    .putBoolean(EMOJI_RECENTS_FIXTURE_CAPTURED, true)
                    .putString(
                        EMOJI_RECENTS_FIXTURE_ORIGINAL_ITEMS,
                        Gson().toJson(provider.current())
                    )
                    .putBoolean(
                        EMOJI_RECENTS_FIXTURE_ORIGINAL_VARIANT_PRESENT,
                        variantPreferences.contains(EMOJI_PICKER_VARIANT_KEY)
                    )
                    .putString(
                        EMOJI_RECENTS_FIXTURE_ORIGINAL_VARIANT,
                        variantPreferences.getString(EMOJI_PICKER_VARIANT_KEY, null)
                    )
                    .commit()
            ) { "Could not persist the emoji-recents test rollback state" }
        }
        check(provider.clearSynchronously()) { "Could not clear emoji recents for verification" }
        check(variantPreferences.edit().remove(EMOJI_PICKER_VARIANT_KEY).commit()) {
            "Could not clear the Emoji2 sticky variant for deterministic verification"
        }
        return emojiRecentsSnapshot(service).apply {
            val original = originalEmojiRecents(backup)
            putBoolean(KEY_EMOJI_RECENTS_FIXTURE_CAPTURED, true)
            putInt(KEY_EMOJI_RECENTS_ORIGINAL_COUNT, original.size)
            putString(KEY_EMOJI_RECENTS_ORIGINAL_SHA256, emojiRecentsSha256(original))
            putBoolean(
                KEY_EMOJI_VARIANT_ORIGINAL_PRESENT,
                backup.getBoolean(EMOJI_RECENTS_FIXTURE_ORIGINAL_VARIANT_PRESENT, false)
            )
            putString(
                KEY_EMOJI_VARIANT_ORIGINAL_SHA256,
                sha256(
                    backup.getString(EMOJI_RECENTS_FIXTURE_ORIGINAL_VARIANT, null).orEmpty()
                )
            )
        }
    }

    private fun restoreEmojiRecentsFixture(service: SellerKeyboard): Bundle {
        val backup = service.getSharedPreferences(EMOJI_RECENTS_FIXTURE_PREFS, Context.MODE_PRIVATE)
        check(backup.getBoolean(EMOJI_RECENTS_FIXTURE_CAPTURED, false)) {
            "Emoji-recents test rollback state was not captured"
        }
        val original = originalEmojiRecents(backup)
        check(LocalRecentEmojiProvider(service).replaceAll(original)) {
            "Could not restore emoji recents after verification"
        }
        val variantPreferences = service.getSharedPreferences(
            EMOJI_PICKER_VARIANT_PREFS,
            Context.MODE_PRIVATE
        )
        val variantEditor = variantPreferences.edit()
        if (backup.getBoolean(EMOJI_RECENTS_FIXTURE_ORIGINAL_VARIANT_PRESENT, false)) {
            variantEditor.putString(
                EMOJI_PICKER_VARIANT_KEY,
                backup.getString(EMOJI_RECENTS_FIXTURE_ORIGINAL_VARIANT, null).orEmpty()
            )
        } else {
            variantEditor.remove(EMOJI_PICKER_VARIANT_KEY)
        }
        check(variantEditor.commit()) {
            "Could not restore the Emoji2 sticky variant after verification"
        }
        check(backup.edit().clear().commit()) {
            "Could not clear the emoji-recents test rollback state"
        }
        return emojiRecentsSnapshot(service).apply {
            putBoolean(KEY_EMOJI_RECENTS_FIXTURE_RESTORED, true)
            putInt(KEY_EMOJI_RECENTS_ORIGINAL_COUNT, original.size)
            putString(KEY_EMOJI_RECENTS_ORIGINAL_SHA256, emojiRecentsSha256(original))
            putBoolean(
                KEY_EMOJI_VARIANT_ORIGINAL_PRESENT,
                variantPreferences.contains(EMOJI_PICKER_VARIANT_KEY)
            )
            putString(
                KEY_EMOJI_VARIANT_ORIGINAL_SHA256,
                sha256(variantPreferences.getString(EMOJI_PICKER_VARIANT_KEY, null).orEmpty())
            )
        }
    }

    private fun emojiRecentsSnapshot(service: SellerKeyboard): Bundle {
        val recents = LocalRecentEmojiProvider(service).current()
        val backup = service.getSharedPreferences(EMOJI_RECENTS_FIXTURE_PREFS, Context.MODE_PRIVATE)
        val variantPreferences = service.getSharedPreferences(
            EMOJI_PICKER_VARIANT_PREFS,
            Context.MODE_PRIVATE
        )
        return snapshot(service).apply {
            putBoolean(KEY_EMOJI_RECENTS_CONTAINS_FIXTURE, EMOJI_RECENTS_FIXTURE in recents)
            putInt(KEY_EMOJI_RECENTS_COUNT, recents.size)
            putString(KEY_EMOJI_RECENTS_SHA256, emojiRecentsSha256(recents))
            putBoolean(
                KEY_EMOJI_VARIANT_PRESENT,
                variantPreferences.contains(EMOJI_PICKER_VARIANT_KEY)
            )
            putString(
                KEY_EMOJI_VARIANT_SHA256,
                sha256(variantPreferences.getString(EMOJI_PICKER_VARIANT_KEY, null).orEmpty())
            )
            putBoolean(
                KEY_EMOJI_RECENTS_FIXTURE_CAPTURED,
                backup.getBoolean(EMOJI_RECENTS_FIXTURE_CAPTURED, false)
            )
        }
    }

    private fun originalEmojiRecents(backup: android.content.SharedPreferences): List<String> =
        Gson().fromJson(
            backup.getString(EMOJI_RECENTS_FIXTURE_ORIGINAL_ITEMS, "[]"),
            Array<String>::class.java
        )?.toList().orEmpty()

    private fun emojiRecentsSha256(recents: List<String>): String =
        sha256(recents.joinToString("\u0000"))

    private fun captureAndSeedUpgradeFixture(service: SellerKeyboard): Bundle {
        val fixturePreferences = service.getSharedPreferences(
            UPGRADE_FIXTURE_PREFS,
            Context.MODE_PRIVATE
        )
        val backup = if (fixturePreferences.getBoolean(UPGRADE_FIXTURE_CAPTURED, false)) {
            Gson().fromJson(
                fixturePreferences.getString(UPGRADE_FIXTURE_BACKUP, null),
                UpgradeFixtureBackup::class.java
            ) ?: error("The persisted upgrade rollback fixture is malformed")
        } else {
            val captured = readUpgradeFixtureBackup(service)
            check(
                fixturePreferences.edit()
                    .putBoolean(UPGRADE_FIXTURE_CAPTURED, true)
                    .putString(UPGRADE_FIXTURE_BACKUP, Gson().toJson(captured))
                    .commit()
            ) { "Could not persist the upgrade rollback fixture" }
            captured
        }

        val dbHelper = DatabaseHelper(service)
        try {
            val database = dbHelper.writableDatabase
            database.delete(
                SnippetsTable.TABLE_NAME,
                "${SnippetsTable.COLUMN_ID} = ?",
                arrayOf(UPGRADE_LEGACY_SNIPPET_ID.toString())
            )
            database.delete(
                SellerReplyTemplateTable.TABLE_NAME,
                "${SellerReplyTemplateTable.COLUMN_ID} = ?",
                arrayOf(UPGRADE_TEMPLATE_ID)
            )
            database.delete(
                ClipboardTable.TABLE_NAME,
                "${ClipboardTable.COLUMN_ID} = ?",
                arrayOf(UPGRADE_CLIPBOARD_ID.toString())
            )
            check(
                database.insertOrThrow(
                    SnippetsTable.TABLE_NAME,
                    null,
                    UPGRADE_LEGACY_SNIPPET.toContentValues()
                ) == UPGRADE_LEGACY_SNIPPET_ID
            ) { "Could not seed the legacy upgrade snippet" }
            check(
                database.insertOrThrow(
                    ClipboardTable.TABLE_NAME,
                    null,
                    UPGRADE_CLIPBOARD_ITEM.toContentValues()
                ) == UPGRADE_CLIPBOARD_ID
            ) { "Could not seed the pinned upgrade clipboard item" }
        } finally {
            dbHelper.close()
        }
        KeyboardSettingsRepository(service).save(UPGRADE_KEYBOARD_PREFERENCES)

        return upgradeFixtureSnapshot(service).apply {
            putBoolean(KEY_UPGRADE_FIXTURE_CAPTURED, true)
            putBoolean(KEY_UPGRADE_FIXTURE_SEEDED, upgradeSeedStateMatches(service))
            putString(KEY_UPGRADE_ORIGINAL_STATE_SHA256, sha256(Gson().toJson(backup)))
        }
    }

    private fun upgradeFixtureSnapshot(service: SellerKeyboard): Bundle {
        val fixturePreferences = service.getSharedPreferences(
            UPGRADE_FIXTURE_PREFS,
            Context.MODE_PRIVATE
        )
        val legacySnippet = readLegacySnippet(service)
        val template = SellerReplyTemplateDao(DatabaseHelper(service)).findById(UPGRADE_TEMPLATE_ID)
        val clipboardItem = readUpgradeClipboardItem(service)
        val settings = KeyboardSettingsRepository(service).snapshot()
        val export = template?.let {
            ReplyPackCodec.export(
                templates = listOf(it),
                nowMillis = UPGRADE_EXPORT_TIME_MILLIS
            )
        }.orEmpty()
        val decoded = ReplyPackCodec.validateAndDecode(export)
        val exportTemplate = (decoded as? ReplyPackImportResult.Valid)
            ?.templates
            ?.singleOrNull()

        return snapshot(service).apply {
            putBoolean(
                KEY_UPGRADE_FIXTURE_CAPTURED,
                fixturePreferences.getBoolean(UPGRADE_FIXTURE_CAPTURED, false)
            )
            putBoolean(KEY_UPGRADE_LEGACY_SNIPPET_EXACT, legacySnippet == UPGRADE_LEGACY_SNIPPET)
            putBoolean(KEY_UPGRADE_TEMPLATE_EXACT, template == UPGRADE_MIGRATED_TEMPLATE)
            putBoolean(
                KEY_UPGRADE_IMAGE_URL_PRESERVED,
                template?.mediaUrls == UPGRADE_MIGRATED_TEMPLATE.mediaUrls
            )
            putBoolean(KEY_UPGRADE_PINNED_CLIPBOARD_EXACT, clipboardItem == UPGRADE_CLIPBOARD_ITEM)
            putBoolean(KEY_UPGRADE_SETTINGS_EXACT, settings == UPGRADE_KEYBOARD_PREFERENCES)
            putBoolean(KEY_UPGRADE_EXPORT_VALID, decoded is ReplyPackImportResult.Valid)
            putBoolean(KEY_UPGRADE_EXPORT_EXACT, exportTemplate == UPGRADE_MIGRATED_TEMPLATE)
            putString(KEY_UPGRADE_EXPORT_SHA256, sha256(export))
        }
    }

    private fun restoreUpgradeFixture(service: SellerKeyboard): Bundle {
        val fixturePreferences = service.getSharedPreferences(
            UPGRADE_FIXTURE_PREFS,
            Context.MODE_PRIVATE
        )
        if (!fixturePreferences.getBoolean(UPGRADE_FIXTURE_CAPTURED, false)) {
            return snapshot(service).apply {
                putBoolean(KEY_UPGRADE_FIXTURE_CAPTURED, false)
                putBoolean(KEY_UPGRADE_FIXTURE_RESTORED, true)
                putBoolean(KEY_UPGRADE_ORIGINAL_STATE_EXACT, true)
            }
        }
        val backup = Gson().fromJson(
            fixturePreferences.getString(UPGRADE_FIXTURE_BACKUP, null),
            UpgradeFixtureBackup::class.java
        ) ?: error("The persisted upgrade rollback fixture is malformed")

        val dbHelper = DatabaseHelper(service)
        try {
            val database = dbHelper.writableDatabase
            database.delete(
                SnippetsTable.TABLE_NAME,
                "${SnippetsTable.COLUMN_ID} = ?",
                arrayOf(UPGRADE_LEGACY_SNIPPET_ID.toString())
            )
            backup.legacySnippet?.let {
                database.insertOrThrow(SnippetsTable.TABLE_NAME, null, it.toContentValues())
            }
            database.delete(
                ClipboardTable.TABLE_NAME,
                "${ClipboardTable.COLUMN_ID} = ?",
                arrayOf(UPGRADE_CLIPBOARD_ID.toString())
            )
            backup.clipboardItem?.let {
                database.insertOrThrow(ClipboardTable.TABLE_NAME, null, it.toContentValues())
            }
        } finally {
            dbHelper.close()
        }
        val templateDao = SellerReplyTemplateDao(DatabaseHelper(service))
        if (backup.template == null) {
            templateDao.delete(UPGRADE_TEMPLATE_ID)
        } else {
            check(templateDao.upsert(backup.template)) { "Could not restore the upgrade template" }
        }
        KeyboardSettingsRepository(service).save(backup.keyboardPreferences)

        val restoredExactly = readLegacySnippet(service) == backup.legacySnippet &&
            SellerReplyTemplateDao(DatabaseHelper(service)).findById(UPGRADE_TEMPLATE_ID) ==
            backup.template &&
            readUpgradeClipboardItem(service) == backup.clipboardItem &&
            KeyboardSettingsRepository(service).snapshot() == backup.keyboardPreferences
        check(fixturePreferences.edit().clear().commit()) {
            "Could not clear the upgrade rollback fixture"
        }
        return snapshot(service).apply {
            putBoolean(KEY_UPGRADE_FIXTURE_CAPTURED, false)
            putBoolean(KEY_UPGRADE_FIXTURE_RESTORED, true)
            putBoolean(KEY_UPGRADE_ORIGINAL_STATE_EXACT, restoredExactly)
            putString(KEY_UPGRADE_ORIGINAL_STATE_SHA256, sha256(Gson().toJson(backup)))
        }
    }

    private fun readUpgradeFixtureBackup(service: SellerKeyboard) = UpgradeFixtureBackup(
        legacySnippet = readLegacySnippet(service),
        template = SellerReplyTemplateDao(DatabaseHelper(service)).findById(UPGRADE_TEMPLATE_ID),
        clipboardItem = readUpgradeClipboardItem(service),
        keyboardPreferences = KeyboardSettingsRepository(service).snapshot()
    )

    private fun readLegacySnippet(service: SellerKeyboard): UpgradeLegacySnippet? {
        val dbHelper = DatabaseHelper(service)
        return try {
            dbHelper.readableDatabase.query(
                SnippetsTable.TABLE_NAME,
                arrayOf(
                    SnippetsTable.COLUMN_ID,
                    SnippetsTable.COLUMN_TITLE,
                    SnippetsTable.COLUMN_CONTENT,
                    SnippetsTable.COLUMN_IMAGE_URLS
                ),
                "${SnippetsTable.COLUMN_ID} = ?",
                arrayOf(UPGRADE_LEGACY_SNIPPET_ID.toString()),
                null,
                null,
                null
            ).use { cursor ->
                if (!cursor.moveToFirst()) null else UpgradeLegacySnippet(
                    id = cursor.getLong(0),
                    title = cursor.getString(1),
                    content = cursor.getString(2),
                    imageUrlsJson = cursor.getString(3)
                )
            }
        } finally {
            dbHelper.close()
        }
    }

    private fun readUpgradeClipboardItem(service: SellerKeyboard): UpgradeClipboardItem? {
        val dbHelper = DatabaseHelper(service)
        return try {
            dbHelper.readableDatabase.query(
                ClipboardTable.TABLE_NAME,
                arrayOf(
                    ClipboardTable.COLUMN_ID,
                    ClipboardTable.COLUMN_TEXT,
                    ClipboardTable.COLUMN_TYPE,
                    ClipboardTable.COLUMN_SOURCE,
                    ClipboardTable.COLUMN_CREATED_AT,
                    ClipboardTable.COLUMN_UPDATED_AT,
                    ClipboardTable.COLUMN_PINNED,
                    ClipboardTable.COLUMN_SENSITIVE
                ),
                "${ClipboardTable.COLUMN_ID} = ?",
                arrayOf(UPGRADE_CLIPBOARD_ID.toString()),
                null,
                null,
                null
            ).use { cursor ->
                if (!cursor.moveToFirst()) null else UpgradeClipboardItem(
                    id = cursor.getLong(0),
                    text = cursor.getString(1),
                    type = cursor.getString(2),
                    source = cursor.getString(3),
                    createdAtMillis = cursor.getLong(4),
                    updatedAtMillis = cursor.getLong(5),
                    pinned = cursor.getInt(6) == 1,
                    sensitive = cursor.getInt(7) == 1
                )
            }
        } finally {
            dbHelper.close()
        }
    }

    private fun upgradeSeedStateMatches(service: SellerKeyboard): Boolean =
        readLegacySnippet(service) == UPGRADE_LEGACY_SNIPPET &&
            SellerReplyTemplateDao(DatabaseHelper(service)).findById(UPGRADE_TEMPLATE_ID) ==
            UPGRADE_MIGRATED_TEMPLATE &&
            readUpgradeClipboardItem(service) == UPGRADE_CLIPBOARD_ITEM &&
            KeyboardSettingsRepository(service).snapshot() == UPGRADE_KEYBOARD_PREFERENCES

    private fun snapshot(service: SellerKeyboard): Bundle {
        val state = service.fieldScopedStateSnapshot()
        return Bundle().apply {
            putBoolean(KEY_ACTIVE, true)
            putLong(KEY_SESSION_ID, state.sessionId)
            putBoolean(KEY_SENSITIVE, state.isSensitive)
            putString(KEY_ACTIVE_SURFACE, state.activeSurface.name)
            putString(KEY_CANDIDATE_SOURCE, state.candidateSource.name)
            putInt(KEY_CANDIDATE_COUNT, state.candidateCount)
            putInt(KEY_COMPLETION_COUNT, state.completionCount)
            putInt(KEY_INLINE_COUNT, state.inlineSuggestionCount)
            putInt(KEY_SELLER_COUNT, state.sellerShortcutCount)
            putBoolean(KEY_CLIPBOARD_STARTED, state.clipboardReaderStarted)
            putLong(KEY_CLIPBOARD_OBSERVED_CHANGES, state.clipboardObservedChangeCount)
            putBoolean(KEY_VOICE_IDLE, state.voiceIdle)
            putBoolean(KEY_TRANSLATION_IDLE, state.translationIdle)
            val session = service.currentEditorSession()
            val preferences = service.keyboardPreferences()
            putString(KEY_EDITOR_PACKAGE, session.packageName)
            putString(KEY_ACTIVE_PROFILE_ID, session.activeProfileId)
            putString(KEY_ACTIVE_PROFILE_NAME, session.activeProfileName)
            putString(KEY_PREFERRED_REPLY_PACK_ID, session.preferredReplyPackId)
            putString(KEY_LANGUAGE_MODE, session.languageMode.name)
            putBoolean(KEY_NUMBER_ROW, preferences.numberRow)
            putString(KEY_HEIGHT_PRESET, preferences.heightPreset.name)
            putString(KEY_THEME_MODE, preferences.themeMode.name)
            putString(KEY_TOOLBAR_ORDER, preferences.toolbarOrder.joinToString("|"))
            putInt(KEY_SUPPORTED_MIME_COUNT, session.supportedMimeTypes.size)
            putBoolean(
                KEY_SUPPORTS_TEST_JPEG,
                session.supportedMimeTypes.any {
                    RichContentPolicy.mimeMatches(it, TEST_MIME_TYPE)
                }
            )
        }
    }

    private fun seedAppProfiles(service: SellerKeyboard): Bundle {
        val store = SharedPreferencesAppProfileStore(service)
        if (originalTestProfiles == null) {
            val profiles = store.profiles()
            originalTestProfiles = TEST_PROFILES.associate { profile ->
                profile.id to profiles.firstOrNull { it.id == profile.id }
            }
            val bindings = store.bindings()
            originalTestBindings = TEST_PACKAGES.associateWith { packageName ->
                bindings.firstOrNull { it.packageName == packageName }
            }
            val settings = KeyboardSettingsRepository(service)
            originalKeyboardPreferences = settings.snapshot()
            settings.save(KeyboardPreferences())
        }
        TEST_PROFILES.forEach(store::saveProfile)
        store.bind(SELLER_PACKAGE, SELLER_PROFILE_ID)
        store.bind(HOST_PACKAGE, HOST_PROFILE_ID)
        return snapshot(service).apply { putBoolean(KEY_APP_PROFILES_SEEDED, true) }
    }

    private fun restoreAppProfiles(service: SellerKeyboard): Bundle {
        val store = SharedPreferencesAppProfileStore(service)
        originalTestProfiles?.forEach { (profileId, profile) ->
            if (profile == null) store.deleteProfile(profileId) else store.saveProfile(profile)
        }
        originalTestBindings?.forEach { (packageName, binding) ->
            if (binding == null) store.removeBinding(packageName)
            else store.bind(binding.packageName, binding.profileId)
        }
        originalKeyboardPreferences?.let { KeyboardSettingsRepository(service).save(it) }
        originalTestProfiles = null
        originalTestBindings = null
        originalKeyboardPreferences = null
        return snapshot(service).apply { putBoolean(KEY_APP_PROFILES_RESTORED, true) }
    }

    private fun seedSellerWorkflow(service: SellerKeyboard): Bundle {
        val dao = SellerReplyTemplateDao(DatabaseHelper(service))
        if (originalSellerWorkflowTemplates == null) {
            originalSellerWorkflowTemplates = TEST_SELLER_TEMPLATES.associate { template ->
                template.id to dao.findById(template.id)
            }
        }
        val seeded = TEST_SELLER_TEMPLATES.all(dao::upsert)
        return snapshot(service).apply {
            putBoolean(KEY_SELLER_WORKFLOW_SEEDED, seeded)
            putInt(KEY_SELLER_WORKFLOW_TEMPLATE_COUNT, TEST_SELLER_TEMPLATES.size)
        }
    }

    private fun restoreSellerWorkflow(service: SellerKeyboard): Bundle {
        val dao = SellerReplyTemplateDao(DatabaseHelper(service))
        var restored = true
        originalSellerWorkflowTemplates?.forEach { (templateId, template) ->
            restored = if (template == null) {
                dao.delete(templateId) || dao.findById(templateId) == null
            } else {
                dao.upsert(template)
            } && restored
        }
        originalSellerWorkflowTemplates = null
        return snapshot(service).apply { putBoolean(KEY_SELLER_WORKFLOW_RESTORED, restored) }
    }

    private fun sellerWorkflowSnapshot(service: SellerKeyboard): Bundle {
        val dao = SellerReplyTemplateDao(DatabaseHelper(service))
        return snapshot(service).apply {
            putBoolean(
                KEY_CONNECTED_SHIPPING_FAVORITE,
                dao.findById(CONNECTED_SHIPPING_REPLY_ID)?.favorite == true
            )
        }
    }

    private fun seedSellerBenchmark(service: SellerKeyboard): Bundle {
        val fixtures = sellerBenchmarkFixtures(service)
        val templates = fixtures.map { fixture ->
            SellerReplyTemplate(
                id = "benchmark-${fixture.id}",
                title = fixture.title,
                body = fixture.body,
                category = fixture.category,
                tags = listOf("benchmark", fixture.category),
                shortcut = fixture.shortcut,
                updatedAtMillis = fixture.id.removePrefix("s").toLong()
            )
        }
        val dao = SellerReplyTemplateDao(DatabaseHelper(service))
        val profileStore = SharedPreferencesAppProfileStore(service)
        if (originalSellerBenchmarkTemplates == null) {
            originalSellerBenchmarkTemplates = templates.associate { template ->
                template.id to dao.findById(template.id)
            }
        }
        if (!sellerBenchmarkProfileCaptured) {
            originalSellerBenchmarkProfile = profileStore.profiles().firstOrNull {
                it.id == SELLER_BENCHMARK_PROFILE_ID
            }
            originalSellerBenchmarkBinding = profileStore.bindings().firstOrNull {
                it.packageName == HOST_PACKAGE
            }
            sellerBenchmarkProfileCaptured = true
        }
        val settings = KeyboardSettingsRepository(service)
        if (originalSellerBenchmarkVietnameseEnabled == null) {
            originalSellerBenchmarkVietnameseEnabled = settings.vietnameseInputEnabled
        }
        settings.vietnameseInputEnabled = false
        profileStore.saveProfile(SELLER_BENCHMARK_PROFILE)
        profileStore.bind(HOST_PACKAGE, SELLER_BENCHMARK_PROFILE_ID)
        val seeded = templates.all(dao::upsert)
        return snapshot(service).apply {
            putBoolean(KEY_SELLER_BENCHMARK_SEEDED, seeded)
            putInt(KEY_SELLER_BENCHMARK_TEMPLATE_COUNT, templates.size)
        }
    }

    private fun restoreSellerBenchmark(service: SellerKeyboard): Bundle {
        val dao = SellerReplyTemplateDao(DatabaseHelper(service))
        var restored = true
        originalSellerBenchmarkTemplates?.forEach { (templateId, template) ->
            restored = if (template == null) {
                dao.delete(templateId) || dao.findById(templateId) == null
            } else {
                dao.upsert(template)
            } && restored
        }
        originalSellerBenchmarkTemplates = null
        val profileStore = SharedPreferencesAppProfileStore(service)
        if (sellerBenchmarkProfileCaptured) {
            originalSellerBenchmarkProfile?.let(profileStore::saveProfile)
                ?: profileStore.deleteProfile(SELLER_BENCHMARK_PROFILE_ID)
            originalSellerBenchmarkBinding?.let { binding ->
                profileStore.bind(binding.packageName, binding.profileId)
            } ?: profileStore.removeBinding(HOST_PACKAGE)
        }
        originalSellerBenchmarkProfile = null
        originalSellerBenchmarkBinding = null
        sellerBenchmarkProfileCaptured = false
        originalSellerBenchmarkVietnameseEnabled?.let { enabled ->
            KeyboardSettingsRepository(service).vietnameseInputEnabled = enabled
        }
        originalSellerBenchmarkVietnameseEnabled = null
        return snapshot(service).apply {
            putBoolean(KEY_SELLER_BENCHMARK_RESTORED, restored)
        }
    }

    private fun sellerBenchmarkFixtures(service: SellerKeyboard): List<SellerBenchmarkFixture> =
        service.assets.open(SELLER_BENCHMARK_FIXTURE).bufferedReader().use { reader ->
            Gson().fromJson(reader, Array<SellerBenchmarkFixture>::class.java).toList()
        }

    private fun setTestClip(
        service: SellerKeyboard,
        text: String,
        markedSensitive: Boolean = false
    ): Bundle {
        val clip = ClipData.newPlainText("SellerKeyboard connected test", text)
        if (markedSensitive) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        service.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
        return clipboardSnapshot(service).apply { putBoolean(KEY_CLIP_SET, true) }
    }

    private fun clipboardSnapshot(service: SellerKeyboard): Bundle {
        val items = service.clipboardRepository().listRecent()
        return snapshot(service).apply {
            putBoolean(KEY_CLIPBOARD_HISTORY_ENABLED, service.isClipboardHistoryEnabled())
            putInt(KEY_CLIPBOARD_ITEM_COUNT, items.size)
            putBoolean(KEY_CONTAINS_NORMAL_CLIP, items.any { it.text == NORMAL_CLIP_TEXT })
            putBoolean(
                KEY_CONTAINS_MARKED_SENSITIVE_CLIP,
                items.any { it.text == MARKED_SENSITIVE_CLIP_TEXT }
            )
            putBoolean(KEY_CONTAINS_DISABLED_CLIP, items.any { it.text == DISABLED_CLIP_TEXT })
            putBoolean(KEY_CONTAINS_PASSWORD_CLIP, items.any { it.text == PASSWORD_CLIP_TEXT })
        }
    }

    private fun testImageUrl(service: SellerKeyboard): String {
        val image = File(service.cacheDir, "ime_test_product.jpg")
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(42, 111, 214))
        }
        FileOutputStream(image).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
        }
        bitmap.recycle()
        return Uri.fromFile(image).toString()
    }

    private fun validatedRemoteImageUrl(value: String?): String {
        val uri = Uri.parse(value ?: error("Remote image URL is required"))
        check(
            uri.scheme == "http" && uri.host == "127.0.0.1" &&
                uri.port == REMOTE_IMAGE_TEST_PORT && uri.path == "/logo.png"
        ) {
            "Remote image test URL must target the adb-reversed loopback fixture"
        }
        return uri.toString()
    }

    private fun <T> onMainThread(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val latch = CountDownLatch(1)
        var result: Result<T>? = null
        Handler(Looper.getMainLooper()).post {
            result = runCatching(block)
            latch.countDown()
        }
        check(latch.await(MAIN_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "Timed out waiting for the IME main thread"
        }
        return result!!.getOrThrow()
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        enforceTrustedCaller()
        check(uri.pathSegments == listOf(PROVIDER_AUDIO_WRITE_PATH)) {
            "Unsupported debug bridge file path: $uri"
        }
        check(mode == "w" || mode == "wt") {
            "The provider-audio fixture only supports truncate/write mode"
        }
        resetProviderAudioFixture()
        val fixture = File(requireNotNull(context).filesDir, PROVIDER_AUDIO_FIXTURE_FILE)
        return ParcelFileDescriptor.open(
            fixture,
            ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE or
                ParcelFileDescriptor.MODE_WRITE_ONLY
        )
    }

    private fun enforceTrustedCaller() {
        val caller = Binder.getCallingUid()
        val providerContext = requireNotNull(context)
        val callerPackages = providerContext.packageManager.getPackagesForUid(caller).orEmpty()
        val signedBenchmarkRunner = BENCHMARK_RUNNER_PACKAGE in callerPackages &&
            providerContext.packageManager.checkSignatures(
                providerContext.packageName,
                BENCHMARK_RUNNER_PACKAGE
            ) == PackageManager.SIGNATURE_MATCH
        check(
            caller == Process.SHELL_UID ||
                caller == providerContext.applicationInfo.uid ||
                signedBenchmarkRunner
        ) {
            "The debug IME bridge only accepts adb shell, same-app, or the " +
                "same-signature benchmark runner"
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        // Optimized benchmark fixtures intentionally write 40 reply templates before measurement.
        // Emulator storage can take longer than the normal connected-test bridge operations.
        private const val MAIN_THREAD_TIMEOUT_SECONDS = 30L
        private const val BENCHMARK_RUNNER_PACKAGE =
            "com.terasumi.sellerkeyboard.benchmark"
        const val METHOD_SNAPSHOT = "snapshot"
        const val METHOD_SEED_NORMAL_STATE = "seed_normal_state"
        const val METHOD_SEND_LATE_RESULTS = "send_late_results"
        const val METHOD_SEED_CANDIDATE_TRACE = "seed_candidate_trace"
        const val METHOD_COMMIT_TEST_PRODUCT = "commit_test_product"
        const val METHOD_COMMIT_REMOTE_TEST_PRODUCT = "commit_remote_test_product"
        const val METHOD_COMMIT_PRODUCTION_PRODUCT = "commit_production_product"
        const val METHOD_CLEAR_CLIPBOARD_HISTORY = "clear_clipboard_history"
        const val METHOD_SET_NORMAL_CLIP = "set_normal_clip"
        const val METHOD_SET_MARKED_SENSITIVE_CLIP = "set_marked_sensitive_clip"
        const val METHOD_SET_DISABLED_CLIP = "set_disabled_clip"
        const val METHOD_SET_PASSWORD_CLIP = "set_password_clip"
        const val METHOD_ENABLE_CLIPBOARD_HISTORY = "enable_clipboard_history"
        const val METHOD_DISABLE_CLIPBOARD_HISTORY = "disable_clipboard_history"
        const val METHOD_SEED_APP_PROFILES = "seed_app_profiles"
        const val METHOD_UNBIND_HOST_PROFILE = "unbind_host_profile"
        const val METHOD_RESTORE_APP_PROFILES = "restore_app_profiles"
        const val METHOD_SEED_SELLER_WORKFLOW = "seed_seller_workflow"
        const val METHOD_SELLER_WORKFLOW_SNAPSHOT = "seller_workflow_snapshot"
        const val METHOD_RESTORE_SELLER_WORKFLOW = "restore_seller_workflow"
        const val METHOD_SEED_SELLER_BENCHMARK = "seed_seller_benchmark"
        const val METHOD_RESTORE_SELLER_BENCHMARK = "restore_seller_benchmark"
        const val METHOD_CAPTURE_PERSONAL_DICTIONARY_FIXTURE =
            "capture_personal_dictionary_fixture"
        const val METHOD_PERSONAL_DICTIONARY_SNAPSHOT = "personal_dictionary_snapshot"
        const val METHOD_RESTORE_PERSONAL_DICTIONARY_FIXTURE =
            "restore_personal_dictionary_fixture"
        const val METHOD_CAPTURE_EMOJI_RECENTS_FIXTURE = "capture_emoji_recents_fixture"
        const val METHOD_EMOJI_RECENTS_SNAPSHOT = "emoji_recents_snapshot"
        const val METHOD_RESTORE_EMOJI_RECENTS_FIXTURE = "restore_emoji_recents_fixture"
        const val METHOD_CAPTURE_AND_SEED_UPGRADE_FIXTURE =
            "capture_and_seed_upgrade_fixture"
        const val METHOD_UPGRADE_FIXTURE_SNAPSHOT = "upgrade_fixture_snapshot"
        const val METHOD_RESTORE_UPGRADE_FIXTURE = "restore_upgrade_fixture"
        const val METHOD_FEATURE_ATTRIBUTION_SNAPSHOT = "feature_attribution_snapshot"
        const val METHOD_START_VOICE_ATTRIBUTION = "start_voice_attribution"
        const val METHOD_CANCEL_VOICE_ATTRIBUTION = "cancel_voice_attribution"
        const val METHOD_START_PROVIDER_AUDIO_FIXTURE = "start_provider_audio_fixture"
        const val METHOD_PROVIDER_AUDIO_FIXTURE_SNAPSHOT =
            "provider_audio_fixture_snapshot"
        const val METHOD_CANCEL_PROVIDER_AUDIO_FIXTURE = "cancel_provider_audio_fixture"
        const val METHOD_DELETE_PROVIDER_AUDIO_FIXTURE = "delete_provider_audio_fixture"
        const val KEY_PROVIDER_AUDIO_FIXTURE_DELETED = "provider_audio_fixture_deleted"
        const val KEY_PROVIDER_AUDIO_FIXTURE_EXISTS = "provider_audio_fixture_exists"
        const val KEY_PROVIDER_AUDIO_FIXTURE_BYTES = "provider_audio_fixture_bytes"
        const val KEY_PROVIDER_AUDIO_FIXTURE_SHA256 = "provider_audio_fixture_sha256"
        const val METHOD_DELETE_TRANSLATION_MODEL = "delete_translation_model"
        const val METHOD_START_TRANSLATION_ATTRIBUTION = "start_translation_attribution"
        const val METHOD_DELETE_EN_TO_VI_MODEL = "delete_en_to_vi_model"
        const val METHOD_START_EN_TO_VI_UI_TRANSLATION = "start_en_to_vi_ui_translation"
        const val METHOD_CANCEL_TRANSLATION_ATTRIBUTION = "cancel_translation_attribution"
        const val KEY_ACTIVE = "active"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_SENSITIVE = "sensitive"
        const val KEY_ACTIVE_SURFACE = "active_surface"
        const val KEY_CANDIDATE_SOURCE = "candidate_source"
        const val KEY_CANDIDATE_COUNT = "candidate_count"
        const val KEY_PERSONAL_DICTIONARY_CONTAINS_FIXTURE =
            "personal_dictionary_contains_fixture"
        const val KEY_PERSONAL_DICTIONARY_COUNT = "personal_dictionary_count"
        const val KEY_PERSONAL_DICTIONARY_SHA256 = "personal_dictionary_sha256"
        const val KEY_PERSONAL_DICTIONARY_ORIGINAL_COUNT =
            "personal_dictionary_original_count"
        const val KEY_PERSONAL_DICTIONARY_ORIGINAL_SHA256 =
            "personal_dictionary_original_sha256"
        const val KEY_PERSONAL_DICTIONARY_ORIGINAL_LEARNING =
            "personal_dictionary_original_learning"
        const val KEY_PERSONAL_DICTIONARY_ORIGINAL_LANGUAGE =
            "personal_dictionary_original_language"
        const val KEY_PERSONAL_DICTIONARY_LEARNING_ENABLED =
            "personal_dictionary_learning_enabled"
        const val KEY_PERSONAL_DICTIONARY_SETTINGS_LANGUAGE =
            "personal_dictionary_settings_language"
        const val KEY_PERSONAL_DICTIONARY_FIXTURE_CAPTURED =
            "personal_dictionary_fixture_captured"
        const val KEY_PERSONAL_DICTIONARY_FIXTURE_RESTORED =
            "personal_dictionary_fixture_restored"
        const val KEY_EMOJI_RECENTS_CONTAINS_FIXTURE = "emoji_recents_contains_fixture"
        const val KEY_EMOJI_RECENTS_COUNT = "emoji_recents_count"
        const val KEY_EMOJI_RECENTS_SHA256 = "emoji_recents_sha256"
        const val KEY_EMOJI_RECENTS_ORIGINAL_COUNT = "emoji_recents_original_count"
        const val KEY_EMOJI_RECENTS_ORIGINAL_SHA256 = "emoji_recents_original_sha256"
        const val KEY_EMOJI_RECENTS_FIXTURE_CAPTURED = "emoji_recents_fixture_captured"
        const val KEY_EMOJI_RECENTS_FIXTURE_RESTORED = "emoji_recents_fixture_restored"
        const val KEY_EMOJI_VARIANT_PRESENT = "emoji_variant_present"
        const val KEY_EMOJI_VARIANT_SHA256 = "emoji_variant_sha256"
        const val KEY_EMOJI_VARIANT_ORIGINAL_PRESENT = "emoji_variant_original_present"
        const val KEY_EMOJI_VARIANT_ORIGINAL_SHA256 = "emoji_variant_original_sha256"
        const val KEY_UPGRADE_FIXTURE_CAPTURED = "upgrade_fixture_captured"
        const val KEY_UPGRADE_FIXTURE_SEEDED = "upgrade_fixture_seeded"
        const val KEY_UPGRADE_FIXTURE_RESTORED = "upgrade_fixture_restored"
        const val KEY_UPGRADE_ORIGINAL_STATE_EXACT = "upgrade_original_state_exact"
        const val KEY_UPGRADE_ORIGINAL_STATE_SHA256 = "upgrade_original_state_sha256"
        const val KEY_UPGRADE_LEGACY_SNIPPET_EXACT = "upgrade_legacy_snippet_exact"
        const val KEY_UPGRADE_TEMPLATE_EXACT = "upgrade_template_exact"
        const val KEY_UPGRADE_IMAGE_URL_PRESERVED = "upgrade_image_url_preserved"
        const val KEY_UPGRADE_PINNED_CLIPBOARD_EXACT = "upgrade_pinned_clipboard_exact"
        const val KEY_UPGRADE_SETTINGS_EXACT = "upgrade_settings_exact"
        const val KEY_UPGRADE_EXPORT_VALID = "upgrade_export_valid"
        const val KEY_UPGRADE_EXPORT_EXACT = "upgrade_export_exact"
        const val KEY_UPGRADE_EXPORT_SHA256 = "upgrade_export_sha256"
        const val KEY_COMPLETION_COUNT = "completion_count"
        const val KEY_INLINE_COUNT = "inline_count"
        const val KEY_SELLER_COUNT = "seller_count"
        const val KEY_CLIPBOARD_STARTED = "clipboard_started"
        const val KEY_CLIPBOARD_OBSERVED_CHANGES = "clipboard_observed_changes"
        const val KEY_VOICE_IDLE = "voice_idle"
        const val KEY_TRANSLATION_IDLE = "translation_idle"
        const val KEY_INLINE_APPEND_ACCEPTED = "inline_append_accepted"
        const val KEY_SUPPORTED_MIME_COUNT = "supported_mime_count"
        const val KEY_SUPPORTS_TEST_JPEG = "supports_test_jpeg"
        const val KEY_COMMIT_REQUESTED = "commit_requested"
        const val KEY_CLIPBOARD_CLEARED = "clipboard_cleared"
        const val KEY_CLIP_SET = "clip_set"
        const val KEY_CLIPBOARD_HISTORY_ENABLED = "clipboard_history_enabled"
        const val KEY_CLIPBOARD_ITEM_COUNT = "clipboard_item_count"
        const val KEY_CONTAINS_NORMAL_CLIP = "contains_normal_clip"
        const val KEY_CONTAINS_MARKED_SENSITIVE_CLIP = "contains_marked_sensitive_clip"
        const val KEY_CONTAINS_DISABLED_CLIP = "contains_disabled_clip"
        const val KEY_CONTAINS_PASSWORD_CLIP = "contains_password_clip"
        const val KEY_EDITOR_PACKAGE = "editor_package"
        const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        const val KEY_ACTIVE_PROFILE_NAME = "active_profile_name"
        const val KEY_PREFERRED_REPLY_PACK_ID = "preferred_reply_pack_id"
        const val KEY_LANGUAGE_MODE = "language_mode"
        const val KEY_NUMBER_ROW = "number_row"
        const val KEY_HEIGHT_PRESET = "height_preset"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_TOOLBAR_ORDER = "toolbar_order"
        const val KEY_APP_PROFILES_SEEDED = "app_profiles_seeded"
        const val KEY_HOST_PROFILE_UNBOUND = "host_profile_unbound"
        const val KEY_APP_PROFILES_RESTORED = "app_profiles_restored"
        const val KEY_SELLER_WORKFLOW_SEEDED = "seller_workflow_seeded"
        const val KEY_SELLER_WORKFLOW_TEMPLATE_COUNT = "seller_workflow_template_count"
        const val KEY_CONNECTED_SHIPPING_FAVORITE = "connected_shipping_favorite"
        const val KEY_SELLER_WORKFLOW_RESTORED = "seller_workflow_restored"
        const val KEY_SELLER_BENCHMARK_SEEDED = "seller_benchmark_seeded"
        const val KEY_SELLER_BENCHMARK_TEMPLATE_COUNT = "seller_benchmark_template_count"
        const val KEY_SELLER_BENCHMARK_RESTORED = "seller_benchmark_restored"
        const val KEY_VOICE_STATE = "voice_state"
        const val KEY_VOICE_ERROR = "voice_error"
        const val KEY_VOICE_USING_ON_DEVICE = "voice_using_on_device"
        const val KEY_PROVIDER_AUDIO_STATE = "provider_audio_state"
        const val KEY_PROVIDER_AUDIO_ERROR = "provider_audio_error"
        const val KEY_PROVIDER_AUDIO_TRANSCRIPT = "provider_audio_transcript"
        const val KEY_PROVIDER_AUDIO_TRANSCRIPT_SHA256 = "provider_audio_transcript_sha256"
        const val KEY_PROVIDER_AUDIO_REVIEW_REQUIRED = "provider_audio_review_required"
        const val KEY_PROVIDER_AUDIO_USING_ON_DEVICE = "provider_audio_using_on_device"
        const val KEY_TRANSLATION_STATE = "translation_state"
        const val KEY_TRANSLATION_ERROR = "translation_error"
        const val KEY_TRANSLATION_ORIGINAL_MATCHES_CANARY =
            "translation_original_matches_canary"
        const val KEY_TRANSLATION_ORIGINAL_MATCHES_UI_FIXTURE =
            "translation_original_matches_ui_fixture"
        const val KEY_TRANSLATION_DIRECTION = "translation_direction"
        const val KEY_TRANSLATION_TRANSLATED_SHA256 = "translation_translated_sha256"
        const val KEY_TRANSLATION_ORIGINAL_PLUS_TRANSLATED_SHA256 =
            "translation_original_plus_translated_sha256"
        const val TEST_PRODUCT_FALLBACK =
            "Seller product | VND 100000 | https://example.invalid/product"
        private const val PERSONAL_DICTIONARY_FIXTURE_WORD = "parcelnova"
        private const val PERSONAL_DICTIONARY_FIXTURE_PREFS =
            "ime_test_bridge_personal_dictionary_fixture"
        private const val PERSONAL_DICTIONARY_FIXTURE_CAPTURED = "captured"
        private const val PERSONAL_DICTIONARY_FIXTURE_ORIGINAL_WORDS = "original_words"
        private const val PERSONAL_DICTIONARY_FIXTURE_ORIGINAL_LEARNING = "original_learning"
        private const val PERSONAL_DICTIONARY_FIXTURE_ORIGINAL_LANGUAGE = "original_language"
        private const val EMOJI_RECENTS_FIXTURE = "🚚"
        private const val EMOJI_RECENTS_FIXTURE_PREFS = "ime_test_bridge_emoji_recents_fixture"
        private const val EMOJI_RECENTS_FIXTURE_CAPTURED = "captured"
        private const val EMOJI_RECENTS_FIXTURE_ORIGINAL_ITEMS = "original_items"
        private const val EMOJI_RECENTS_FIXTURE_ORIGINAL_VARIANT_PRESENT =
            "original_variant_present"
        private const val EMOJI_RECENTS_FIXTURE_ORIGINAL_VARIANT = "original_variant"
        private const val EMOJI_PICKER_VARIANT_PREFS = "androidx.emoji2.emojipicker.preferences"
        private const val EMOJI_PICKER_VARIANT_KEY = "pref_key_sticky_variant"
        private const val UPGRADE_FIXTURE_PREFS = "ime_test_bridge_upgrade_fixture"
        private const val UPGRADE_FIXTURE_CAPTURED = "captured"
        private const val UPGRADE_FIXTURE_BACKUP = "backup"
        private const val UPGRADE_LEGACY_SNIPPET_ID = -731991L
        private const val UPGRADE_CLIPBOARD_ID = -731992L
        private const val UPGRADE_TEMPLATE_ID = "legacy--731991"
        private const val UPGRADE_EXPORT_TIME_MILLIS = 1_720_000_000_000L
        private const val TEST_MIME_TYPE = "image/jpeg"
        private const val REMOTE_IMAGE_TEST_PORT = 18080
        private const val NORMAL_CLIP_TEXT = "Connected clipboard order reference"
        private const val MARKED_SENSITIVE_CLIP_TEXT = "Connected marked sensitive clipboard"
        private const val DISABLED_CLIP_TEXT = "Connected disabled clipboard"
        private const val PASSWORD_CLIP_TEXT = "Connected password clipboard"
        const val TRANSLATION_ATTRIBUTION_CANARY = "Don hang attribution rieng tu"
        const val TRANSLATION_UI_ORIGINAL = "Thank you for your order"
        private const val PROVIDER_AUDIO_FIXTURE_FILE = "voice-provider-fixture.pcm"
        private const val PROVIDER_AUDIO_WRITE_PATH = "provider-audio"
        private const val PROVIDER_AUDIO_LANGUAGE_TAG = "en-US"
        private const val PROVIDER_COMPONENT_SETTING = "voice_recognition_service"
        private const val SELLER_PACKAGE = "com.terasumi.sellerkeyboard"
        private const val HOST_PACKAGE = "com.terasumi.sellerkeyboard.benchmarkhost"
        private const val SELLER_PROFILE_ID = "connected_seller_profile"
        private const val HOST_PROFILE_ID = "connected_host_profile"
        private const val CONNECTED_SHIPPING_REPLY_ID = "connected-shipping-reply"
        private const val SELLER_BENCHMARK_FIXTURE = "seller-workflow-scenarios.json"
        private const val SELLER_BENCHMARK_PROFILE_ID = "seller_benchmark_profile"
        private val UPGRADE_LEGACY_SNIPPET = UpgradeLegacySnippet(
            id = UPGRADE_LEGACY_SNIPPET_ID,
            title = "Physical upgrade fixture",
            content = "Preserve this seller reply across the installed update.",
            imageUrlsJson = "[\"https://example.invalid/upgrade-fixture.jpg\"]"
        )
        private val UPGRADE_MIGRATED_TEMPLATE = SellerReplyTemplate(
            id = UPGRADE_TEMPLATE_ID,
            title = UPGRADE_LEGACY_SNIPPET.title.orEmpty(),
            body = UPGRADE_LEGACY_SNIPPET.content.orEmpty(),
            mediaUrls = listOf("https://example.invalid/upgrade-fixture.jpg")
        )
        private val UPGRADE_CLIPBOARD_ITEM = UpgradeClipboardItem(
            id = UPGRADE_CLIPBOARD_ID,
            text = "SellerKeyboard physical upgrade pinned fixture",
            type = "PlainText",
            source = "Manual",
            createdAtMillis = 1_720_000_000_001L,
            updatedAtMillis = 1_720_000_000_002L,
            pinned = true,
            sensitive = false
        )
        private val UPGRADE_KEYBOARD_PREFERENCES = KeyboardPreferences(
            themeMode = KeyboardThemeMode.DARK,
            keyBorders = true,
            highContrast = true,
            numberRow = false,
            popupPreview = false,
            soundEnabled = false,
            soundVolume = 0.25f,
            hapticEnabled = false,
            hapticStrength = 77,
            learningEnabled = false,
            heightPreset = KeyboardHeightPreset.TALL,
            oneHandedSide = OneHandedSide.LEFT,
            languageMode = KeyboardLanguageMode.ENGLISH,
            toolbarOrder = KeyboardPreferences.DEFAULT_TOOLBAR_ORDER.reversed()
        )
        private val TEST_PACKAGES = listOf(SELLER_PACKAGE, HOST_PACKAGE)
        private val TEST_PROFILES = listOf(
            KeyboardProfile(
                id = SELLER_PROFILE_ID,
                name = "Connected Seller",
                toolbarOrder = listOf("replies", "calculator"),
                preferredReplyPackId = "connected-seller-pack",
                languageMode = KeyboardLanguageMode.VIETNAMESE,
                numberRow = true,
                themeOverride = KeyboardThemeMode.LIGHT,
                heightOverride = KeyboardHeightPreset.TALL
            ),
            KeyboardProfile(
                id = HOST_PROFILE_ID,
                name = "Connected Host",
                toolbarOrder = listOf("products", "clipboard"),
                preferredReplyPackId = "connected-host-pack",
                languageMode = KeyboardLanguageMode.ENGLISH,
                numberRow = false,
                themeOverride = KeyboardThemeMode.DARK,
                heightOverride = KeyboardHeightPreset.COMPACT
            )
        )
        private val SELLER_BENCHMARK_PROFILE = KeyboardProfile(
            id = SELLER_BENCHMARK_PROFILE_ID,
            name = "Seller benchmark",
            toolbarOrder = listOf("replies"),
            preferredReplyPackId = "seller-benchmark-pack",
            languageMode = KeyboardLanguageMode.ENGLISH,
            numberRow = false,
            themeOverride = KeyboardThemeMode.DARK,
            heightOverride = KeyboardHeightPreset.COMPACT
        )
        private val TEST_SELLER_TEMPLATES = listOf(
            SellerReplyTemplate(
                id = CONNECTED_SHIPPING_REPLY_ID,
                title = "Connected Shipping Reply",
                body = "Your order ships today.",
                category = "Shipping",
                tags = listOf("connected", "shipping"),
                favorite = true,
                shortcut = "/ship-connected",
                updatedAtMillis = 2L
            ),
            SellerReplyTemplate(
                id = "connected-tracking-reply",
                title = "Connected Tracking Reply",
                body = "Tracking code: {{tracking_code}}",
                category = "Orders",
                tags = listOf("connected", "tracking"),
                shortcut = "/track-connected",
                updatedAtMillis = 1L
            ),
            SellerReplyTemplate(
                id = "connected-warranty-reply",
                title = "Connected Warranty Reply",
                body = "Your warranty remains active.",
                category = "Returns",
                tags = listOf("connected", "warranty"),
                usageCount = 10L,
                shortcut = "/warranty-connected",
                updatedAtMillis = 3L
            )
        )
    }
}

@Keep
private data class SellerBenchmarkFixture(
    val id: String,
    val category: String,
    val title: String,
    val body: String,
    val shortcut: String
)

@Keep
private data class UpgradeFixtureBackup(
    val legacySnippet: UpgradeLegacySnippet?,
    val template: SellerReplyTemplate?,
    val clipboardItem: UpgradeClipboardItem?,
    val keyboardPreferences: KeyboardPreferences
)

@Keep
private data class UpgradeLegacySnippet(
    val id: Long,
    val title: String?,
    val content: String?,
    val imageUrlsJson: String?
) {
    fun toContentValues() = ContentValues().apply {
        put(SnippetsTable.COLUMN_ID, id)
        put(SnippetsTable.COLUMN_TITLE, title)
        put(SnippetsTable.COLUMN_CONTENT, content)
        put(SnippetsTable.COLUMN_IMAGE_URLS, imageUrlsJson)
    }
}

@Keep
private data class UpgradeClipboardItem(
    val id: Long,
    val text: String,
    val type: String,
    val source: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val pinned: Boolean,
    val sensitive: Boolean
) {
    fun toContentValues() = ContentValues().apply {
        put(ClipboardTable.COLUMN_ID, id)
        put(ClipboardTable.COLUMN_TEXT, text)
        put(ClipboardTable.COLUMN_TYPE, type)
        put(ClipboardTable.COLUMN_SOURCE, source)
        put(ClipboardTable.COLUMN_CREATED_AT, createdAtMillis)
        put(ClipboardTable.COLUMN_UPDATED_AT, updatedAtMillis)
        put(ClipboardTable.COLUMN_PINNED, if (pinned) 1 else 0)
        put(ClipboardTable.COLUMN_SENSITIVE, if (sensitive) 1 else 0)
    }
}
