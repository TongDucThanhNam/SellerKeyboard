package com.terasumi.sellerkeyboard.seller

import androidx.annotation.Keep
import com.google.gson.Gson

@Keep
data class ReplyPackExport(
    val schemaVersion: Int,
    val exportedAtMillis: Long,
    val pack: ReplyPack? = null,
    val templates: List<SellerReplyTemplate>
)

@Keep
data class ReplyPack(
    val id: String,
    val name: String,
    val description: String = ""
)

sealed interface ReplyPackImportResult {
    data class Valid(
        val templates: List<SellerReplyTemplate>,
        val pack: ReplyPack? = null
    ) : ReplyPackImportResult
    data class Invalid(val reason: String) : ReplyPackImportResult
}

object ReplyPackCodec {
    const val SCHEMA_VERSION = 1
    private val gson = Gson()

    fun export(
        templates: List<SellerReplyTemplate>,
        nowMillis: Long,
        pack: ReplyPack? = null
    ): String = gson.toJson(
        ReplyPackExport(SCHEMA_VERSION, nowMillis, pack, templates)
    )

    fun validateAndDecode(json: String): ReplyPackImportResult {
        val envelope = runCatching { gson.fromJson(json, ReplyPackExport::class.java) }
            .getOrNull() ?: return ReplyPackImportResult.Invalid("Malformed JSON")
        if (envelope.schemaVersion != SCHEMA_VERSION) {
            return ReplyPackImportResult.Invalid("Unsupported schema version")
        }
        val templates = envelope.templates
            ?: return ReplyPackImportResult.Invalid("Missing templates")
        if (templates.size > MAX_TEMPLATE_COUNT) {
            return ReplyPackImportResult.Invalid("Too many templates")
        }
        val pack = envelope.pack?.let { raw ->
            val id = raw.id.orEmpty().trim()
            val name = raw.name.orEmpty().trim()
            if (id.isBlank() || name.isBlank() || id.length > MAX_ID_LENGTH ||
                name.length > MAX_TITLE_LENGTH
            ) {
                return ReplyPackImportResult.Invalid("Invalid reply pack metadata")
            }
            ReplyPack(id = id, name = name, description = raw.description.orEmpty())
        }
        val invalid = templates.any { template ->
            template.id.isNullOrBlank() || template.id.length > MAX_ID_LENGTH ||
                template.title.isNullOrBlank() || template.title.length > MAX_TITLE_LENGTH ||
                template.body.orEmpty().length > MAX_BODY_LENGTH ||
                template.tags.orEmpty().size > MAX_LIST_ITEMS ||
                template.mediaUrls.orEmpty().size > MAX_LIST_ITEMS ||
                template.profileIds.orEmpty().size > MAX_LIST_ITEMS
        }
        if (invalid) return ReplyPackImportResult.Invalid("Invalid template data")
        val normalized = templates.map { template ->
            template.copy(
                category = template.category.orEmpty().ifBlank { "General" },
                tags = template.tags.orEmpty().filter(String::isNotBlank).distinct(),
                mediaUrls = template.mediaUrls.orEmpty().filter(String::isNotBlank).distinct(),
                replyPackId = template.replyPackId?.takeIf(String::isNotBlank),
                profileIds = template.profileIds.orEmpty().filter(String::isNotBlank).distinct()
            )
        }
        return ReplyPackImportResult.Valid(
            templates = normalized.distinctBy(SellerReplyTemplate::id),
            pack = pack
        )
    }

    private const val MAX_TEMPLATE_COUNT = 1_000
    private const val MAX_LIST_ITEMS = 100
    private const val MAX_ID_LENGTH = 200
    private const val MAX_TITLE_LENGTH = 500
    private const val MAX_BODY_LENGTH = 20_000
}
