package com.terasumi.sellerkeyboard.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardRepositoryTest {
    @Test
    fun saveText_savesAndObservesRecentItems() {
        val fixture = repositoryFixture()

        val item = fixture.repository.saveText("hello world")

        assertNotNull(item)
        assertEquals(listOf("hello world"), fixture.repository.observeRecent().value.map { it.text })
        assertEquals(ClipboardItemType.PlainText, fixture.repository.observeRecent().value.first().type)
    }

    @Test
    fun saveText_ignoresDuplicateConsecutiveText() {
        val fixture = repositoryFixture()

        fixture.repository.saveText("0912345678")
        fixture.clock += 1_000L
        fixture.repository.saveText("0912345678")

        assertEquals(1, fixture.repository.observeRecent().value.size)
        assertEquals(ClipboardItemType.PhoneNumber, fixture.repository.observeRecent().value.first().type)
    }

    @Test
    fun pinnedItems_areOrderedBeforeUnpinnedItems() {
        val fixture = repositoryFixture()
        val first = fixture.repository.saveText("hello world")!!
        fixture.clock += 1_000L
        val second = fixture.repository.saveText("https://shopee.vn/product/1")!!

        fixture.repository.setPinned(first.id, true)

        assertEquals(
            listOf(first.text, second.text),
            fixture.repository.observeRecent().value.map { it.text }
        )
    }

    @Test
    fun markUsed_movesUnpinnedItemToTopWithinUnpinnedGroup() {
        val fixture = repositoryFixture()
        val first = fixture.repository.saveText("hello world")!!
        fixture.clock += 1_000L
        fixture.repository.saveText("SPXVN12345678")
        fixture.clock += 1_000L

        fixture.repository.markUsed(first.id)

        assertEquals(
            listOf("hello world", "SPXVN12345678"),
            fixture.repository.observeRecent().value.map { it.text }
        )
    }

    @Test
    fun saveText_allowsSameTextAgainWhenItIsNotConsecutive() {
        val fixture = repositoryFixture()

        fixture.repository.saveText("hello world")
        fixture.clock += 1_000L
        fixture.repository.saveText("SPXVN12345678")
        fixture.clock += 1_000L
        fixture.repository.saveText("hello world")

        assertEquals(
            listOf("hello world", "SPXVN12345678", "hello world"),
            fixture.repository.observeRecent().value.map { it.text }
        )
    }

    @Test
    fun deleteAndClear_updateObservedHistory() {
        val fixture = repositoryFixture()
        val first = fixture.repository.saveText("hello world")!!
        fixture.repository.saveText("SPXVN12345678")

        fixture.repository.delete(first.id)
        assertEquals(listOf("SPXVN12345678"), fixture.repository.observeRecent().value.map { it.text })

        fixture.repository.clear()
        assertTrue(fixture.repository.observeRecent().value.isEmpty())
    }

    @Test
    fun disabledHistory_doesNotStoreOrRenderItems() {
        val fixture = repositoryFixture(historyEnabled = false)

        val item = fixture.repository.saveText("hello world")

        assertNull(item)
        assertTrue(fixture.repository.observeRecent().value.isEmpty())
        assertTrue(fixture.store.items.isEmpty())
    }

    @Test
    fun autoDelete_removesExpiredUnpinnedItemsAndKeepsPinnedItems() {
        val fixture = repositoryFixture(autoDeleteDays = 1)
        val old = fixture.repository.saveText("old")!!
        val oldPinned = fixture.repository.saveText("old pinned")!!
        fixture.repository.setPinned(oldPinned.id, true)
        fixture.clock += 2L * 24L * 60L * 60L * 1_000L
        fixture.repository.saveText("new")

        assertEquals(
            listOf("old pinned", "new"),
            fixture.repository.observeRecent().value.map { it.text }
        )
        assertTrue(fixture.store.items.none { it.id == old.id })
    }

    @Test
    fun sensitiveContent_isNotStored() {
        val fixture = repositoryFixture()

        assertNull(fixture.repository.saveText("Your OTP is 123456"))
        assertTrue(fixture.repository.observeRecent().value.isEmpty())
    }

    @Test
    fun systemMarkedSensitiveClip_isNeverRetained() {
        val fixture = repositoryFixture()

        assertNull(
            fixture.repository.saveText(
                text = "secret supplied by another app",
                isMarkedSensitive = true
            )
        )
        assertTrue(fixture.store.items.isEmpty())
    }

    @Test
    fun unpinnedHistory_isBoundedWhilePinnedItemsArePreserved() {
        val fixture = repositoryFixture(maxItems = 2)
        val pinned = fixture.repository.saveText("keep pinned")!!
        fixture.repository.setPinned(pinned.id, true)
        fixture.clock += 1
        fixture.repository.saveText("one")
        fixture.clock += 1
        fixture.repository.saveText("two")
        fixture.clock += 1
        fixture.repository.saveText("three")

        assertEquals(listOf("keep pinned", "two", "three"), fixture.store.items.map { it.text })
    }

    private fun repositoryFixture(
        historyEnabled: Boolean = true,
        autoDeleteDays: Int = 30,
        maxItems: Int = 20
    ): RepositoryFixture {
        val store = InMemoryClipboardStore()
        val fixture = RepositoryFixture(
            store = store,
            clock = 1_000L,
            historyEnabled = historyEnabled,
            autoDeleteDays = autoDeleteDays
        )
        fixture.repository = ClipboardRepository(
            store = store,
            nowMillis = { fixture.clock },
            historyEnabled = { fixture.historyEnabled },
            autoDeleteDays = { fixture.autoDeleteDays },
            maxItems = maxItems
        )
        return fixture
    }

    private data class RepositoryFixture(
        val store: InMemoryClipboardStore,
        var clock: Long,
        var historyEnabled: Boolean,
        var autoDeleteDays: Int
    ) {
        lateinit var repository: ClipboardRepository
    }

    private class InMemoryClipboardStore : ClipboardStore {
        val items = mutableListOf<ClipboardItem>()
        private var nextId = 1L

        override fun ensureReady() = Unit

        override fun listRecent(limit: Int): List<ClipboardItem> {
            return items
                .sortedWith(compareByDescending<ClipboardItem> { it.pinned }.thenByDescending { it.updatedAtMillis })
                .take(limit)
        }

        override fun latestByCreatedAt(): ClipboardItem? {
            return items.maxByOrNull { it.createdAtMillis }
        }

        override fun insert(item: ClipboardItem): ClipboardItem {
            val inserted = item.copy(id = nextId++)
            items += inserted
            return inserted
        }

        override fun setPinned(id: Long, pinned: Boolean, updatedAtMillis: Long): Boolean {
            return update(id) { it.copy(pinned = pinned, updatedAtMillis = updatedAtMillis) }
        }

        override fun markUsed(id: Long, updatedAtMillis: Long): Boolean {
            return update(id) { it.copy(updatedAtMillis = updatedAtMillis) }
        }

        override fun delete(id: Long): Boolean {
            return items.removeIf { it.id == id }
        }

        override fun clear(): Boolean {
            items.clear()
            return true
        }

        override fun deleteOlderThan(cutoffMillis: Long): Int {
            val before = items.size
            items.removeIf { !it.pinned && it.createdAtMillis < cutoffMillis }
            return before - items.size
        }

        override fun pruneUnpinned(maxItems: Int): Int {
            val removable = items.filterNot { it.pinned }
                .sortedByDescending { it.updatedAtMillis }
                .drop(maxItems.coerceAtLeast(0))
                .map { it.id }
                .toSet()
            items.removeIf { it.id in removable }
            return removable.size
        }

        private fun update(id: Long, transform: (ClipboardItem) -> ClipboardItem): Boolean {
            val index = items.indexOfFirst { it.id == id }
            if (index == -1) {
                return false
            }
            items[index] = transform(items[index])
            return true
        }
    }
}
