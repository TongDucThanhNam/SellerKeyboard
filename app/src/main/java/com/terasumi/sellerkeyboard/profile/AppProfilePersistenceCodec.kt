package com.terasumi.sellerkeyboard.profile

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.terasumi.sellerkeyboard.settings.KeyboardHeightPreset
import com.terasumi.sellerkeyboard.settings.KeyboardLanguageMode
import com.terasumi.sellerkeyboard.settings.KeyboardPreferences
import com.terasumi.sellerkeyboard.settings.KeyboardThemeMode

/**
 * Treats shared-preference JSON as untrusted, versioned storage data.
 *
 * Gson can instantiate Kotlin classes with null values in properties declared non-null. That is
 * especially dangerous in an IME because a single malformed profile can crash every editor
 * session before the keyboard is visible. Nullable storage records make the migration boundary
 * explicit and ensure only valid domain objects leave this codec.
 */
internal object AppProfilePersistenceCodec {
    private val gson = Gson()

    fun decodeProfiles(json: String?): List<KeyboardProfile> = decodeElements(json) { element ->
        gson.fromJson(element, StoredKeyboardProfile::class.java).toDomainOrNull()
    }

    fun decodeBindings(json: String?): List<AppProfileBinding> = decodeElements(json) { element ->
        gson.fromJson(element, StoredAppProfileBinding::class.java).toDomainOrNull()
    }

    private fun <T : Any> decodeElements(
        json: String?,
        decode: (com.google.gson.JsonElement) -> T?
    ): List<T> {
        if (json.isNullOrBlank()) return emptyList()
        val array = runCatching { JsonParser.parseString(json).asJsonArray }.getOrNull()
            ?: return emptyList()
        return array.mapNotNull { element -> runCatching { decode(element) }.getOrNull() }
    }

    private data class StoredKeyboardProfile(
        val id: String? = null,
        val name: String? = null,
        val toolbarOrder: List<String?>? = null,
        val preferredReplyPackId: String? = null,
        val languageMode: String? = null,
        val numberRow: Boolean? = null,
        val themeOverride: String? = null,
        val heightOverride: String? = null,
        val enabledSellerTools: Set<String?>? = null,
        val isPrivate: Boolean? = null
    ) {
        fun toDomainOrNull(): KeyboardProfile? {
            val validId = id?.trim()?.takeIf(String::isNotEmpty) ?: return null
            val validName = name?.trim()?.takeIf(String::isNotEmpty) ?: return null
            val tools = toolbarOrder.cleanTools().ifEmpty {
                KeyboardPreferences.DEFAULT_TOOLBAR_ORDER
            }
            val enabledTools = enabledSellerTools.cleanTools().ifEmpty { tools }.toSet()
            return KeyboardProfile(
                id = validId,
                name = validName,
                toolbarOrder = tools,
                preferredReplyPackId = preferredReplyPackId?.trim()?.takeIf(String::isNotEmpty),
                languageMode = languageMode.toEnumOrNull<KeyboardLanguageMode>()
                    ?: KeyboardLanguageMode.VIETNAMESE_ENGLISH,
                numberRow = numberRow,
                themeOverride = themeOverride.toEnumOrNull<KeyboardThemeMode>(),
                heightOverride = heightOverride.toEnumOrNull<KeyboardHeightPreset>(),
                enabledSellerTools = enabledTools,
                isPrivate = isPrivate ?: false
            )
        }
    }

    private data class StoredAppProfileBinding(
        val packageName: String? = null,
        val profileId: String? = null
    ) {
        fun toDomainOrNull(): AppProfileBinding? {
            val validPackage = packageName?.trim()?.takeIf(String::isNotEmpty) ?: return null
            val validProfileId = profileId?.trim()?.takeIf(String::isNotEmpty) ?: return null
            return AppProfileBinding(validPackage, validProfileId)
        }
    }

    private fun Collection<String?>?.cleanTools(): List<String> = orEmpty()
        .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .distinct()

    private inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? =
        this?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } }
}
