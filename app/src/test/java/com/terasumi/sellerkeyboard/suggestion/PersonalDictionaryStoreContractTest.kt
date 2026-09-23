package com.terasumi.sellerkeyboard.suggestion

import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalDictionaryStoreContractTest {
    @Test
    fun inMemoryContract_supportsAddRemoveAndReset() {
        val store = InMemoryPersonalDictionaryStore()

        assertTrue(store.add("ShopName"))
        assertFalse(store.add("shopname"))
        assertTrue("shopname" in store.words())
        assertTrue(store.remove("SHOPNAME"))
        assertTrue(store.words().isEmpty())

        store.add("seller")
        store.clear()
        assertTrue(store.words().isEmpty())
    }

    private class InMemoryPersonalDictionaryStore : PersonalDictionaryStore {
        private val values = linkedSetOf<String>()
        override fun words(): Set<String> = values.toSet()
        override fun add(word: String): Boolean = values.add(word.trim().lowercase(Locale.ROOT))
        override fun remove(word: String): Boolean = values.remove(word.trim().lowercase(Locale.ROOT))
        override fun clear() = values.clear()
    }
}
