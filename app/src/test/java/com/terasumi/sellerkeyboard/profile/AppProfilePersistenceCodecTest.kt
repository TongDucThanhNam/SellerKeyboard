package com.terasumi.sellerkeyboard.profile

import com.google.gson.Gson
import com.terasumi.sellerkeyboard.settings.KeyboardLanguageMode
import com.terasumi.sellerkeyboard.settings.KeyboardPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppProfilePersistenceCodecTest {
    @Test
    fun legacyProfile_missingNewFields_isMigratedWithSafeDefaults() {
        val profiles = AppProfilePersistenceCodec.decodeProfiles(
            """[{"id":"legacy","name":"Legacy","toolbarOrder":["replies","calculator"]}]"""
        )

        assertEquals(1, profiles.size)
        assertEquals(KeyboardLanguageMode.VIETNAMESE_ENGLISH, profiles.single().languageMode)
        assertEquals(setOf("replies", "calculator"), profiles.single().enabledSellerTools)
        assertFalse(profiles.single().isPrivate)
    }

    @Test
    fun malformedProfile_isDroppedWithoutDiscardingValidNeighbors() {
        val profiles = AppProfilePersistenceCodec.decodeProfiles(
            """[
                {"id":null,"name":"Broken","toolbarOrder":["replies"]},
                {"id":"valid","name":"Valid","toolbarOrder":null,"languageMode":"UNKNOWN"}
            ]""".trimIndent()
        )

        assertEquals(listOf("valid"), profiles.map { it.id })
        assertEquals(KeyboardPreferences.DEFAULT_TOOLBAR_ORDER, profiles.single().toolbarOrder)
        assertEquals(KeyboardLanguageMode.VIETNAMESE_ENGLISH, profiles.single().languageMode)
    }

    @Test
    fun malformedBindings_areDroppedAndValuesAreNormalized() {
        val bindings = AppProfilePersistenceCodec.decodeBindings(
            """[
                {"packageName":null,"profileId":"broken"},
                {"packageName":" com.example.seller ","profileId":" commerce "}
            ]""".trimIndent()
        )

        assertEquals(listOf(AppProfileBinding("com.example.seller", "commerce")), bindings)
    }

    @Test
    fun resolver_toleratesRuntimeNullFromLegacyGsonObject() {
        val malformed = Gson().fromJson(
            """{"id":null,"name":"Broken","toolbarOrder":[]}""",
            KeyboardProfile::class.java
        )

        assertEquals(
            KeyboardProfile.Global,
            AppProfileResolver.resolve(
                packageName = "com.example.seller",
                profiles = listOf(malformed),
                bindings = listOf(AppProfileBinding("com.example.seller", "missing"))
            )
        )
    }
}
