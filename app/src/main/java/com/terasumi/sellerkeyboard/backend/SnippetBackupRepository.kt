package com.terasumi.sellerkeyboard.backend

import com.terasumi.sellerkeyboard.database.Snippets
import dev.convex.android.ConvexClient
import kotlinx.coroutines.flow.first

class SnippetBackupRepository(
    private val client: ConvexClient,
    private val authRepository: ConvexAuthRepository
) {
    suspend fun backup(snippets: List<Snippets>): Int {
        val session = requireSession()
        val result: BackupBatchResultDto = client.mutation<BackupBatchResultDto>(
            "snippets:backupBatch",
            args = mapOf(
                "sessionToken" to session.sessionToken,
                "snippets" to snippets.map { snippet -> snippet.toBackupPayload() }
            )
        )
        return result.savedCount
    }

    suspend fun listBackup(): List<BackupSnippetDto> {
        val session = requireSession()
        return client.subscribe<List<BackupSnippetDto>>(
            "snippets:listBackup",
            args = mapOf("sessionToken" to session.sessionToken)
        ).first().getOrThrow()
    }

    suspend fun deleteBackupItem(clientId: String): Boolean {
        val session = requireSession()
        val result: DeleteBackupResultDto = client.mutation<DeleteBackupResultDto>(
            "snippets:deleteBackupItem",
            args = mapOf(
                "sessionToken" to session.sessionToken,
                "clientId" to clientId
            )
        )
        return result.deleted
    }

    private fun requireSession(): AuthSession {
        return authRepository.currentSession()
            ?: throw IllegalStateException("Login before using cloud backup.")
    }
}
