package com.terasumi.sellerkeyboard.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardSettingsRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: KeyboardSettingsRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = KeyboardSettingsRepository(context)
        // Clear prefs before test
        context.getSharedPreferences(KeyboardSettingsRepository.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun teardown() {
        context.getSharedPreferences(KeyboardSettingsRepository.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testHapticEnabled_defaultTrue() {
        assertTrue(repository.hapticEnabled)
    }

    @Test
    fun testHapticEnabled_setAndGet() {
        repository.hapticEnabled = false
        assertFalse(repository.hapticEnabled)

        repository.hapticEnabled = true
        assertTrue(repository.hapticEnabled)
    }

    @Test
    fun testVietnameseInputEnabled_defaultTrue() {
        assertTrue(repository.vietnameseInputEnabled)
    }

    @Test
    fun testTelexVariant_defaultIsDefault() {
        assertEquals("default", repository.telexVariant)
        repository.telexVariant = "vni"
        assertEquals("vni", repository.telexVariant)
    }

    @Test
    fun testVoiceTypingEnabled_defaultTrue() {
        assertTrue(repository.voiceTypingEnabled)
    }

    @Test
    fun testClipboardHistoryEnabled_defaultTrue() {
        assertTrue(repository.clipboardHistoryEnabled)
    }

    @Test
    fun testClipboardAutoDeleteDays_default30() {
        assertEquals(30, repository.clipboardAutoDeleteDays)
        repository.clipboardAutoDeleteDays = 7
        assertEquals(7, repository.clipboardAutoDeleteDays)
    }

    @Test
    fun testInlineAutofillEnabled_defaultTrue() {
        assertTrue(repository.inlineAutofillEnabled)
    }
}
