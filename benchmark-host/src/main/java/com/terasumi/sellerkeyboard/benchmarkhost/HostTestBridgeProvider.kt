package com.terasumi.sellerkeyboard.benchmarkhost

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Shell-only deterministic editor fixture for connected cross-UID IME tests. */
class HostTestBridgeProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val caller = Binder.getCallingUid()
        check(caller == Process.SHELL_UID || caller == context?.applicationInfo?.uid) {
            "The benchmark host bridge only accepts adb shell or same-app calls"
        }
        return onMainThread {
            if (method == METHOD_AUTOFILL_SNAPSHOT) {
                val activity = AutofillBenchmarkActivity.activeInstance
                    ?: return@onMainThread Bundle().apply { putBoolean(KEY_ACTIVE, false) }
                val editor = activity.snapshot()
                return@onMainThread Bundle().apply {
                    putBoolean(KEY_ACTIVE, true)
                    putInt(KEY_AUTOFILL_USERNAME_LENGTH, editor.usernameLength)
                    putString(KEY_AUTOFILL_USERNAME_SHA256, editor.usernameSha256)
                    putInt(KEY_AUTOFILL_PASSWORD_LENGTH, editor.passwordLength)
                    putString(KEY_AUTOFILL_PASSWORD_SHA256, editor.passwordSha256)
                    putBoolean(KEY_AUTOFILL_USERNAME_FOCUSED, editor.usernameFocused)
                    putBoolean(KEY_AUTOFILL_PASSWORD_FOCUSED, editor.passwordFocused)
                }
            }
            val activity = ImeBenchmarkActivity.activeInstance
                ?: return@onMainThread Bundle().apply { putBoolean(KEY_ACTIVE, false) }
            when (method) {
                METHOD_SEED_TRANSLATION_SELECTION -> {
                    activity.seedTranslationSelection()
                    snapshot(activity).apply { putBoolean(KEY_SEEDED, true) }
                }
                METHOD_SEED_CORE_FIXTURE -> {
                    val fixture = requireNotNull(arg) { "Core fixture name is required" }
                    activity.seedCoreFixture(fixture)
                    snapshot(activity).apply {
                        putBoolean(KEY_SEEDED, true)
                        putString(KEY_FIXTURE, fixture)
                    }
                }
                METHOD_SEED_SELLER_BENCHMARK_EMPTY -> {
                    activity.seedSellerBenchmarkEmpty()
                    snapshot(activity).apply { putBoolean(KEY_SEEDED, true) }
                }
                METHOD_SELLER_BENCHMARK_SNAPSHOT -> snapshot(activity).apply {
                    putString(KEY_BENCHMARK_TEXT, activity.editorTextSnapshot().text)
                }
                METHOD_SNAPSHOT -> snapshot(activity)
                else -> throw IllegalArgumentException("Unknown host bridge method: $method")
            }
        }
    }

    private fun snapshot(activity: ImeBenchmarkActivity): Bundle {
        val editor = activity.editorTextSnapshot()
        return Bundle().apply {
            putBoolean(KEY_ACTIVE, true)
            putBoolean(
                KEY_MATCHES_ORIGINAL,
                editor.text == ImeBenchmarkActivity.TRANSLATION_TEST_ORIGINAL
            )
            putString(KEY_TEXT_SHA256, sha256(editor.text))
            putInt(KEY_TEXT_LENGTH, editor.text.length)
            putInt(KEY_SELECTION_START, editor.selectionStart)
            putInt(KEY_SELECTION_END, editor.selectionEnd)
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun <T> onMainThread(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val latch = CountDownLatch(1)
        var result: Result<T>? = null
        Handler(Looper.getMainLooper()).post {
            result = runCatching(block)
            latch.countDown()
        }
        check(latch.await(MAIN_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "Timed out waiting for the benchmark-host main thread"
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
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private companion object {
        const val METHOD_SEED_TRANSLATION_SELECTION = "seed_translation_selection"
        const val METHOD_SEED_CORE_FIXTURE = "seed_core_fixture"
        const val METHOD_SEED_SELLER_BENCHMARK_EMPTY = "seed_seller_benchmark_empty"
        const val METHOD_SELLER_BENCHMARK_SNAPSHOT = "seller_benchmark_snapshot"
        const val METHOD_SNAPSHOT = "snapshot"
        const val METHOD_AUTOFILL_SNAPSHOT = "autofill_snapshot"
        const val KEY_ACTIVE = "active"
        const val KEY_SEEDED = "seeded"
        const val KEY_FIXTURE = "fixture"
        const val KEY_MATCHES_ORIGINAL = "matches_original"
        const val KEY_TEXT_SHA256 = "text_sha256"
        const val KEY_TEXT_LENGTH = "text_length"
        const val KEY_SELECTION_START = "selection_start"
        const val KEY_SELECTION_END = "selection_end"
        const val KEY_BENCHMARK_TEXT = "benchmark_text"
        const val KEY_AUTOFILL_USERNAME_LENGTH = "autofill_username_length"
        const val KEY_AUTOFILL_USERNAME_SHA256 = "autofill_username_sha256"
        const val KEY_AUTOFILL_PASSWORD_LENGTH = "autofill_password_length"
        const val KEY_AUTOFILL_PASSWORD_SHA256 = "autofill_password_sha256"
        const val KEY_AUTOFILL_USERNAME_FOCUSED = "autofill_username_focused"
        const val KEY_AUTOFILL_PASSWORD_FOCUSED = "autofill_password_focused"
        const val MAIN_THREAD_TIMEOUT_SECONDS = 5L
    }
}
