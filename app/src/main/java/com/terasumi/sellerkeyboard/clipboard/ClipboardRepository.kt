package com.terasumi.sellerkeyboard.clipboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ClipboardRepository(
    private val store: ClipboardStore,
    private val classifier: ClipboardClassifier = ClipboardClassifier(),
    private val contentPolicy: ClipboardContentPolicy = ClipboardContentPolicy(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val historyEnabled: () -> Boolean = { true },
    private val autoDeleteDays: () -> Int = { DEFAULT_AUTO_DELETE_DAYS },
    private val maxItems: Int = DEFAULT_MAX_ITEMS
) {
    private val itemsState = MutableStateFlow<List<ClipboardItem>>(emptyList())

    init {
        store.ensureReady()
        refresh()
    }

    fun observeRecent(): StateFlow<List<ClipboardItem>> = itemsState.asStateFlow()

    fun listRecent(): List<ClipboardItem> {
        refresh()
        return itemsState.value
    }

    fun saveText(
        text: CharSequence?,
        source: ClipboardSource = ClipboardSource.SystemClipboard,
        isMarkedSensitive: Boolean = false
    ): ClipboardItem? {
        if (!historyEnabled()) {
            itemsState.value = emptyList()
            return null
        }
        if (isMarkedSensitive) {
            return null
        }

        val normalized = contentPolicy.normalizeForStorage(text) ?: return null
        if (!contentPolicy.canStore(normalized)) {
            return null
        }

        cleanupExpired()
        val latest = store.latestByCreatedAt()
        if (latest?.text == normalized) {
            refresh()
            return latest
        }

        val now = nowMillis()
        val type = classifier.classify(normalized)
        val inserted = store.insert(
            ClipboardItem(
                text = normalized,
                type = type,
                source = source,
                createdAtMillis = now,
                updatedAtMillis = now,
                sensitive = type == ClipboardItemType.PhoneNumber ||
                    type == ClipboardItemType.TrackingCode
            )
        )
        store.pruneUnpinned(maxItems)
        refresh()
        return inserted
    }

    fun setPinned(id: Long, pinned: Boolean): Boolean {
        val changed = store.setPinned(id = id, pinned = pinned, updatedAtMillis = nowMillis())
        refresh()
        return changed
    }

    fun markUsed(id: Long): Boolean {
        val changed = store.markUsed(id = id, updatedAtMillis = nowMillis())
        refresh()
        return changed
    }

    fun delete(id: Long): Boolean {
        val changed = store.delete(id)
        refresh()
        return changed
    }

    fun clear(): Boolean {
        val changed = store.clear()
        refresh()
        return changed
    }

    fun refresh() {
        if (!historyEnabled()) {
            itemsState.value = emptyList()
            return
        }

        cleanupExpired()
        store.pruneUnpinned(maxItems)
        itemsState.value = store.listRecent(maxItems)
    }

    private fun cleanupExpired() {
        val days = autoDeleteDays()
        if (days <= 0) {
            return
        }

        val cutoff = nowMillis() - days * MILLIS_PER_DAY
        store.deleteOlderThan(cutoff)
    }

    private companion object {
        private const val DEFAULT_AUTO_DELETE_DAYS = 30
        private const val DEFAULT_MAX_ITEMS = 20
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L
    }
}
