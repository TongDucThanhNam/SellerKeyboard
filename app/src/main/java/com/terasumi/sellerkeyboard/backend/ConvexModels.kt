package com.terasumi.sellerkeyboard.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class AuthSession(
    val email: String,
    val sessionToken: String
)

@Serializable
data class AuthResultDto(
    val sessionToken: String,
    val email: String
)

@Serializable
data class LogoutResultDto(
    val signedOut: Boolean
)

@Serializable
data class WhoAmIResultDto(
    val email: String,
    val userId: String? = null,
    val ownerToken: String? = null
)

@Serializable
data class BackupBatchResultDto(
    @SerialName("savedCount")
    private val savedCountValue: Double
) {
    val savedCount: Int
        get() = savedCountValue.toInt()
}

@Serializable
data class BackupSnippetDto(
    val clientId: String,
    val title: String,
    val content: String,
    val imageUrls: List<String> = emptyList(),
    val updatedAt: Double,
    val schemaVersion: Double
) {
    val schemaVersionInt: Int
        get() = schemaVersion.toInt()
}

@Serializable
data class DeleteBackupResultDto(
    val deleted: Boolean
)

