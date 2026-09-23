package com.terasumi.sellerkeyboard.backend

import com.terasumi.sellerkeyboard.database.Snippets

const val BACKUP_SCHEMA_VERSION = 1

data class RestorePlan(
    val snippetsToInsert: List<Snippets>,
    val skippedExactMatches: Int
)

fun Snippets.toBackupPayload(updatedAtMillis: Long = System.currentTimeMillis()): Map<String, Any?> {
    return mapOf(
        "clientId" to "local-$id",
        "title" to title,
        "content" to content,
        "imageUrls" to emptyList<String>(),
        "deleted" to false,
        "updatedAt" to updatedAtMillis.toDouble(),
        "schemaVersion" to BACKUP_SCHEMA_VERSION.toDouble()
    )
}

fun planSnippetRestore(
    remoteSnippets: List<BackupSnippetDto>,
    localSnippets: List<Snippets>
): RestorePlan {
    val localExactMatches = localSnippets
        .map { snippet -> snippet.title.trim() to snippet.content.trim() }
        .toSet()
    val snippetsToInsert = remoteSnippets
        .filter { remote -> (remote.title.trim() to remote.content.trim()) !in localExactMatches }
        .map { remote ->
            Snippets(
                id = 0,
                title = remote.title,
                content = remote.content,
                imageUrls = emptyList()
            )
        }

    return RestorePlan(
        snippetsToInsert = snippetsToInsert,
        skippedExactMatches = remoteSnippets.size - snippetsToInsert.size
    )
}

