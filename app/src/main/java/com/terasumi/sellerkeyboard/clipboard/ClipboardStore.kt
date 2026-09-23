package com.terasumi.sellerkeyboard.clipboard

interface ClipboardStore {
    fun ensureReady()
    fun listRecent(limit: Int): List<ClipboardItem>
    fun latestByCreatedAt(): ClipboardItem?
    fun insert(item: ClipboardItem): ClipboardItem
    fun setPinned(id: Long, pinned: Boolean, updatedAtMillis: Long): Boolean
    fun markUsed(id: Long, updatedAtMillis: Long): Boolean
    fun delete(id: Long): Boolean
    fun clear(): Boolean
    fun deleteOlderThan(cutoffMillis: Long): Int
    fun pruneUnpinned(maxItems: Int): Int
}
