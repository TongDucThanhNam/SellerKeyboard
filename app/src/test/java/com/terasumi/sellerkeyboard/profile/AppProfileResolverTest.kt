package com.terasumi.sellerkeyboard.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class AppProfileResolverTest {
    private val commerce = KeyboardProfile(
        id = "commerce",
        name = "Commerce",
        toolbarOrder = listOf("replies", "products", "calculator")
    )

    @Test
    fun exactActivePackage_resolvesLocalProfile() {
        val result = AppProfileResolver.resolve(
            packageName = "com.example.seller",
            profiles = listOf(commerce),
            bindings = listOf(AppProfileBinding("com.example.seller", "commerce"))
        )
        assertEquals(commerce, result)
    }

    @Test
    fun unknownPackageOrStaleBinding_usesGlobalFallback() {
        assertEquals(
            KeyboardProfile.Global,
            AppProfileResolver.resolve("other.app", listOf(commerce), emptyList())
        )
        assertEquals(
            KeyboardProfile.Global,
            AppProfileResolver.resolve(
                "com.example.seller",
                emptyList(),
                listOf(AppProfileBinding("com.example.seller", "missing"))
            )
        )
    }
}
