package com.terasumi.sellerkeyboard.seller

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlashShortcutPolicyTest {
    @Test
    fun onlyExplicitSlashSequencesTriggerLookup() {
        assertTrue(SlashShortcutPolicy.shouldQuery("/ship", isSensitive = false))
        assertFalse(SlashShortcutPolicy.shouldQuery("ship", isSensitive = false))
        assertFalse(SlashShortcutPolicy.shouldQuery("/ship", isSensitive = true))
        assertFalse(SlashShortcutPolicy.shouldQuery("incoming message /ship", isSensitive = false))
    }
}
