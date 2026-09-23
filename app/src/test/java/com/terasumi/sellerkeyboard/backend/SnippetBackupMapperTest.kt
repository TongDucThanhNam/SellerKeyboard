package com.terasumi.sellerkeyboard.backend

import com.terasumi.sellerkeyboard.database.Snippets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetBackupMapperTest {
    @Test
    fun toBackupPayload_usesLocalIdAndDoesNotUploadImagePaths() {
        val snippet = Snippets(
            id = 42,
            title = "Shipping",
            content = "Your order ships today.",
            imageUrls = listOf("/cache/private.jpg")
        )

        val payload = snippet.toBackupPayload(updatedAtMillis = 1234L)

        assertEquals("local-42", payload["clientId"])
        assertEquals("Shipping", payload["title"])
        assertEquals("Your order ships today.", payload["content"])
        assertEquals(emptyList<String>(), payload["imageUrls"])
        assertEquals(false, payload["deleted"])
        assertEquals(1234.0, payload["updatedAt"])
        assertEquals(BACKUP_SCHEMA_VERSION.toDouble(), payload["schemaVersion"])
    }

    @Test
    fun planSnippetRestore_skipsExactLocalMatches() {
        val local = listOf(
            Snippets(
                id = 1,
                title = "Existing",
                content = "Already here",
                imageUrls = emptyList()
            )
        )
        val remote = listOf(
            backupSnippet(title = "Existing", content = "Already here"),
            backupSnippet(title = "Remote only", content = "Insert this")
        )

        val plan = planSnippetRestore(remoteSnippets = remote, localSnippets = local)

        assertEquals(1, plan.skippedExactMatches)
        assertEquals(1, plan.snippetsToInsert.size)
        assertEquals("Remote only", plan.snippetsToInsert.single().title)
        assertEquals("Insert this", plan.snippetsToInsert.single().content)
    }

    @Test
    fun planSnippetRestore_preparesLocalInsertWithoutImages() {
        val remote = listOf(
            backupSnippet(
                title = "Cloud",
                content = "Restore me",
                imageUrls = listOf("/remote/should-not-be-used.jpg")
            )
        )

        val plan = planSnippetRestore(remoteSnippets = remote, localSnippets = emptyList())

        assertEquals(0, plan.skippedExactMatches)
        assertEquals(1, plan.snippetsToInsert.size)
        assertEquals(0, plan.snippetsToInsert.single().id)
        assertTrue(plan.snippetsToInsert.single().imageUrls.isEmpty())
    }

    private fun backupSnippet(
        title: String,
        content: String,
        imageUrls: List<String> = emptyList()
    ): BackupSnippetDto {
        return BackupSnippetDto(
            clientId = "remote-$title",
            title = title,
            content = content,
            imageUrls = imageUrls,
            updatedAt = 1234.0,
            schemaVersion = BACKUP_SCHEMA_VERSION.toDouble()
        )
    }
}

