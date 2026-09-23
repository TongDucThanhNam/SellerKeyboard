package com.terasumi.sellerkeyboard.profile

import android.content.Context
import com.google.gson.Gson

interface AppProfileStore {
    fun profiles(): List<KeyboardProfile>
    fun bindings(): List<AppProfileBinding>
    fun saveProfile(profile: KeyboardProfile)
    fun deleteProfile(profileId: String)
    fun bind(packageName: String, profileId: String)
    fun removeBinding(packageName: String)
}

class SharedPreferencesAppProfileStore(context: Context) : AppProfileStore {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    override fun profiles(): List<KeyboardProfile> =
        AppProfilePersistenceCodec.decodeProfiles(preferences.getString(KEY_PROFILES, null))

    override fun bindings(): List<AppProfileBinding> =
        AppProfilePersistenceCodec.decodeBindings(preferences.getString(KEY_BINDINGS, null))

    override fun saveProfile(profile: KeyboardProfile) {
        require(profile.id.isNotBlank() && profile.name.isNotBlank())
        val updated = (profiles().filterNot { it.id == profile.id } + profile)
        preferences.edit().putString(KEY_PROFILES, gson.toJson(updated)).apply()
    }

    override fun deleteProfile(profileId: String) {
        require(profileId.isNotBlank())
        preferences.edit().putString(
            KEY_PROFILES,
            gson.toJson(profiles().filterNot { it.id == profileId })
        ).apply()
    }

    override fun bind(packageName: String, profileId: String) {
        require(packageName.isNotBlank() && profileId.isNotBlank())
        val normalizedPackage = packageName.trim()
        val updated = bindings().filterNot { it.packageName == normalizedPackage } +
            AppProfileBinding(normalizedPackage, profileId)
        preferences.edit().putString(KEY_BINDINGS, gson.toJson(updated)).apply()
    }

    override fun removeBinding(packageName: String) {
        preferences.edit().putString(
            KEY_BINDINGS,
            gson.toJson(bindings().filterNot { it.packageName == packageName })
        ).apply()
    }

    companion object {
        private const val PREFS_NAME = "app_profiles"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_BINDINGS = "bindings"
    }
}

object AppProfileResolver {
    fun resolve(
        packageName: String,
        profiles: List<KeyboardProfile>,
        bindings: List<AppProfileBinding>
    ): KeyboardProfile {
        val profileId = bindings.firstOrNull { packageName == it.packageName }?.profileId
        return profileId?.let { expectedId ->
            // Keep the known-non-null value on the left. Persisted data from older
            // builds can violate Kotlin's non-null declarations after Gson creates
            // the object, and R8 may otherwise optimize `profile.id == profileId`
            // into a null receiver call during IME startup.
            profiles.firstOrNull { expectedId == it.id }
        } ?: KeyboardProfile.Global
    }
}
