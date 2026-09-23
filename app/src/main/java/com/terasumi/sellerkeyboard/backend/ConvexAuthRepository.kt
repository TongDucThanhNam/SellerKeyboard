package com.terasumi.sellerkeyboard.backend

import android.content.Context
import dev.convex.android.ConvexClient
import kotlinx.coroutines.flow.first

class ConvexAuthRepository(
    context: Context,
    private val client: ConvexClient
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun currentSession(): AuthSession? {
        val email = preferences.getString(KEY_EMAIL, null)
        val sessionToken = preferences.getString(KEY_SESSION_TOKEN, null)
        if (email.isNullOrBlank() || sessionToken.isNullOrBlank()) {
            return null
        }
        return AuthSession(email = email, sessionToken = sessionToken)
    }

    suspend fun register(email: String, password: String): AuthSession {
        val result: AuthResultDto = client.action<AuthResultDto>(
            "auth:register",
            args = mapOf(
                "email" to email.trim(),
                "password" to password
            )
        )
        return saveSession(result)
    }

    suspend fun login(email: String, password: String): AuthSession {
        val result: AuthResultDto = client.action<AuthResultDto>(
            "auth:login",
            args = mapOf(
                "email" to email.trim(),
                "password" to password
            )
        )
        return saveSession(result)
    }

    suspend fun logout() {
        currentSession()?.let { session ->
            runCatching {
                client.action<LogoutResultDto>(
                    "auth:logout",
                    args = mapOf("sessionToken" to session.sessionToken)
                )
            }
        }
        clearSession()
    }

    suspend fun whoAmI(): WhoAmIResultDto? {
        val session = currentSession() ?: return null
        val result = client.subscribe<WhoAmIResultDto>(
            "auth:whoAmI",
            args = mapOf("sessionToken" to session.sessionToken)
        ).first().getOrThrow()
        preferences.edit()
            .putString(KEY_EMAIL, result.email)
            .apply()
        return result
    }

    fun clearSession() {
        preferences.edit()
            .remove(KEY_EMAIL)
            .remove(KEY_SESSION_TOKEN)
            .apply()
    }

    private fun saveSession(result: AuthResultDto): AuthSession {
        preferences.edit()
            .putString(KEY_EMAIL, result.email)
            .putString(KEY_SESSION_TOKEN, result.sessionToken)
            .apply()
        return AuthSession(
            email = result.email,
            sessionToken = result.sessionToken
        )
    }

    private companion object {
        const val PREFS_NAME = "convex_auth"
        const val KEY_EMAIL = "email"
        const val KEY_SESSION_TOKEN = "session_token"
    }
}
