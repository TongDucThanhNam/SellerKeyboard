package com.terasumi.sellerkeyboard.seller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyPackCodecTest {
    @Test
    fun versionedRoundTrip_validatesAndDeduplicatesStableIds() {
        val pack = ReplyPack("support", "Support replies")
        val template = SellerReplyTemplate(
            "id-1",
            "Thanks",
            "Cảm ơn bạn",
            replyPackId = pack.id
        )
        val json = ReplyPackCodec.export(listOf(template, template), 123, pack)
        val result = ReplyPackCodec.validateAndDecode(json)

        assertTrue(result is ReplyPackImportResult.Valid)
        assertEquals(listOf(template), (result as ReplyPackImportResult.Valid).templates)
        assertEquals(pack, result.pack)
    }

    @Test
    fun unknownSchemaVersion_isRejected() {
        val result = ReplyPackCodec.validateAndDecode(
            "{\"schemaVersion\":999,\"exportedAtMillis\":0,\"templates\":[]}"
        )
        assertTrue(result is ReplyPackImportResult.Invalid)
    }

    @Test
    fun invalidPackMetadata_isRejectedBeforeAnyImport() {
        val result = ReplyPackCodec.validateAndDecode(
            """{"schemaVersion":1,"exportedAtMillis":0,"pack":{"id":"","name":"x"},"templates":[]}"""
        )
        assertTrue(result is ReplyPackImportResult.Invalid)
    }

    @Test
    fun missingTemplates_isRejectedInsteadOfThrowing() {
        val result = ReplyPackCodec.validateAndDecode(
            """{"schemaVersion":1,"exportedAtMillis":0}"""
        )
        assertTrue(result is ReplyPackImportResult.Invalid)
    }

    @Test
    fun legacyTemplateWithoutOptionalCollections_isNormalized() {
        val result = ReplyPackCodec.validateAndDecode(
            """{"schemaVersion":1,"exportedAtMillis":0,"templates":[{"id":"old","title":"Old","body":"Body"}]}"""
        )

        assertTrue(result is ReplyPackImportResult.Valid)
        val template = (result as ReplyPackImportResult.Valid).templates.single()
        assertEquals("General", template.category)
        assertEquals(emptyList<String>(), template.tags)
        assertEquals(emptyList<String>(), template.mediaUrls)
        assertEquals(emptyList<String>(), template.profileIds)
    }
}
