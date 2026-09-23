package com.terasumi.sellerkeyboard.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.content.ClipDescription
import com.terasumi.sellerkeyboard.ime.EditorInfoPolicy

class ActiveClipboardReader(
    context: Context,
    private val repository: ClipboardRepository,
    private val policyProvider: () -> EditorInfoPolicy
) {
    private val appContext = context.applicationContext
    private val clipboardManager = appContext.getSystemService(ClipboardManager::class.java)
    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        observedChangeCount += 1
        readCurrentPrimaryClipIfAllowed()
    }
    private var active = false
    private var observedChangeCount = 0L
    internal val isStarted: Boolean
        get() = active
    internal val observedPrimaryClipChanges: Long
        get() = observedChangeCount

    fun start() {
        if (active) {
            readCurrentPrimaryClipIfAllowed()
            return
        }

        active = true
        clipboardManager?.addPrimaryClipChangedListener(listener)
        readCurrentPrimaryClipIfAllowed()
    }

    fun stop() {
        if (!active) {
            return
        }

        active = false
        clipboardManager?.removePrimaryClipChangedListener(listener)
    }

    fun readCurrentPrimaryClipIfAllowed() {
        if (!active || !policyProvider().canShowClipboard) {
            return
        }

        var isSensitive = false
        val text = runCatching {
            val clip = clipboardManager?.primaryClip ?: return
            isSensitive = clipboardManager?.primaryClipDescription?.extras
                ?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true
            if (clip.itemCount <= 0) {
                return
            }
            clip.getItemAt(0).coerceToText(appContext)
        }.getOrNull()

        repository.saveText(
            text,
            ClipboardSource.SystemClipboard,
            isMarkedSensitive = isSensitive
        )
    }
}
