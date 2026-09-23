package com.terasumi.sellerkeyboard.database

import android.content.ContentValues
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.terasumi.sellerkeyboard.seller.SellerReplyTemplate
import com.terasumi.sellerkeyboard.seller.SellerSearchNormalizer
import com.terasumi.sellerkeyboard.seller.ReplySortMode

class SellerReplyTemplateDao(private val dbHelper: DatabaseHelper) {
    private val gson = Gson()

    fun upsert(template: SellerReplyTemplate): Boolean {
        val values = template.toValues()
        val rowId = dbHelper.writableDatabase.insertWithOnConflict(
            SellerReplyTemplateTable.TABLE_NAME,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
        return rowId >= 0
    }

    fun syncLegacy(id: Int, title: String, body: String, mediaUrls: List<String>): Boolean {
        val stableId = "legacy-$id"
        val values = ContentValues().apply {
            put(SellerReplyTemplateTable.COLUMN_TITLE, title)
            put(SellerReplyTemplateTable.COLUMN_BODY, body)
            put(SellerReplyTemplateTable.COLUMN_MEDIA_URLS, gson.toJson(mediaUrls))
            put(SellerReplyTemplateTable.COLUMN_UPDATED_AT, System.currentTimeMillis())
        }
        val updated = dbHelper.writableDatabase.update(
            SellerReplyTemplateTable.TABLE_NAME,
            values,
            "${SellerReplyTemplateTable.COLUMN_ID} = ?",
            arrayOf(stableId)
        )
        return updated > 0 || upsert(
            SellerReplyTemplate(
                id = stableId,
                title = title,
                body = body,
                mediaUrls = mediaUrls,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    fun listActive(
        query: String = "",
        limit: Int = 100,
        sortMode: ReplySortMode = ReplySortMode.SMART,
        profileId: String? = null,
        preferredReplyPackId: String? = null
    ): List<SellerReplyTemplate> {
        val cursor = dbHelper.readableDatabase.query(
            SellerReplyTemplateTable.TABLE_NAME,
            ALL_COLUMNS,
            "${SellerReplyTemplateTable.COLUMN_ARCHIVED} = 0",
            null,
            null,
            null,
            sortMode.orderByClause(),
            MAX_QUERY_ROWS.toString()
        )
        return cursor.use { result ->
            buildList {
                while (result.moveToNext()) add(result.toTemplate())
            }
        }.asSequence()
            .filter { template ->
                profileId == null || template.profileIds.isEmpty() || profileId in template.profileIds
            }
            .filter { SellerSearchNormalizer.matches(it, query) }
            .toList()
            .let { matching ->
                if (preferredReplyPackId.isNullOrBlank()) matching
                else matching.sortedByDescending { it.replyPackId == preferredReplyPackId }
            }
            .take(limit.coerceIn(1, MAX_QUERY_ROWS))
    }

    fun findById(id: String): SellerReplyTemplate? {
        val cursor = dbHelper.readableDatabase.query(
            SellerReplyTemplateTable.TABLE_NAME,
            ALL_COLUMNS,
            "${SellerReplyTemplateTable.COLUMN_ID} = ?",
            arrayOf(id),
            null,
            null,
            null,
            "1"
        )
        return cursor.use { result ->
            if (result.moveToFirst()) result.toTemplate() else null
        }
    }

    fun markUsed(id: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        dbHelper.writableDatabase.execSQL(
            "UPDATE ${SellerReplyTemplateTable.TABLE_NAME} SET " +
                "${SellerReplyTemplateTable.COLUMN_USAGE_COUNT} = " +
                "${SellerReplyTemplateTable.COLUMN_USAGE_COUNT} + 1, " +
                "${SellerReplyTemplateTable.COLUMN_UPDATED_AT} = ? WHERE " +
                "${SellerReplyTemplateTable.COLUMN_ID} = ?",
            arrayOf<Any?>(nowMillis, id)
        )
        return true
    }

    fun archive(id: String): Boolean = updateFlag(
        id,
        SellerReplyTemplateTable.COLUMN_ARCHIVED,
        true
    )

    fun setFavorite(id: String, favorite: Boolean): Boolean = updateFlag(
        id,
        SellerReplyTemplateTable.COLUMN_FAVORITE,
        favorite
    )

    fun delete(id: String): Boolean = dbHelper.writableDatabase.delete(
        SellerReplyTemplateTable.TABLE_NAME,
        "${SellerReplyTemplateTable.COLUMN_ID} = ?",
        arrayOf(id)
    ) > 0

    fun resetUsageSignals(): Boolean {
        val values = ContentValues().apply {
            put(SellerReplyTemplateTable.COLUMN_USAGE_COUNT, 0L)
        }
        dbHelper.writableDatabase.update(
            SellerReplyTemplateTable.TABLE_NAME,
            values,
            null,
            null
        )
        return true
    }

    private fun updateFlag(id: String, column: String, value: Boolean): Boolean {
        val values = ContentValues().apply { put(column, if (value) 1 else 0) }
        return dbHelper.writableDatabase.update(
            SellerReplyTemplateTable.TABLE_NAME,
            values,
            "${SellerReplyTemplateTable.COLUMN_ID} = ?",
            arrayOf(id)
        ) > 0
    }

    private fun SellerReplyTemplate.toValues() = ContentValues().apply {
        put(SellerReplyTemplateTable.COLUMN_ID, id)
        put(SellerReplyTemplateTable.COLUMN_TITLE, title)
        put(SellerReplyTemplateTable.COLUMN_BODY, body)
        put(SellerReplyTemplateTable.COLUMN_CATEGORY, category)
        put(SellerReplyTemplateTable.COLUMN_TAGS, gson.toJson(tags))
        put(SellerReplyTemplateTable.COLUMN_FAVORITE, if (favorite) 1 else 0)
        put(SellerReplyTemplateTable.COLUMN_USAGE_COUNT, usageCount)
        put(SellerReplyTemplateTable.COLUMN_SHORTCUT, shortcut)
        put(SellerReplyTemplateTable.COLUMN_MEDIA_URLS, gson.toJson(mediaUrls))
        put(SellerReplyTemplateTable.COLUMN_REPLY_PACK_ID, replyPackId)
        put(SellerReplyTemplateTable.COLUMN_PROFILE_IDS, gson.toJson(profileIds))
        put(SellerReplyTemplateTable.COLUMN_ARCHIVED, if (archived) 1 else 0)
        put(SellerReplyTemplateTable.COLUMN_CREATED_AT, createdAtMillis)
        put(SellerReplyTemplateTable.COLUMN_UPDATED_AT, updatedAtMillis)
    }

    private fun android.database.Cursor.toTemplate() = SellerReplyTemplate(
        id = getString(getColumnIndexOrThrow(SellerReplyTemplateTable.COLUMN_ID)),
        title = getString(getColumnIndexOrThrow(SellerReplyTemplateTable.COLUMN_TITLE)),
        body = getString(getColumnIndexOrThrow(SellerReplyTemplateTable.COLUMN_BODY)),
        category = getString(getColumnIndexOrThrow(SellerReplyTemplateTable.COLUMN_CATEGORY)),
        tags = stringList(getString(getColumnIndexOrThrow(SellerReplyTemplateTable.COLUMN_TAGS))),
        favorite = getInt(getColumnIndexOrThrow(SellerReplyTemplateTable.COLUMN_FAVORITE)) == 1,
        usageCount = getLong(getColumnIndexOrThrow(SellerReplyTemplateTable.COLUMN_USAGE_COUNT)),
        shortcut = getString(getColumnIndexOrThrow(SellerReplyTemplateTable.COLUMN_SHORTCUT)),
        mediaUrls = stringList(
            getString(getColumnIndexOrThrow(SellerReplyTemplateTable.COLUMN_MEDIA_URLS))
        ),
        replyPackId = getString(
            getColumnIndexOrThrow(SellerReplyTemplateTable.COLUMN_REPLY_PACK_ID)
        ),
        profileIds = stringList(
            getString(getColumnIndexOrThrow(SellerReplyTemplateTable.COLUMN_PROFILE_IDS))
        ),
        archived = getInt(getColumnIndexOrThrow(SellerReplyTemplateTable.COLUMN_ARCHIVED)) == 1,
        createdAtMillis = getLong(getColumnIndexOrThrow(SellerReplyTemplateTable.COLUMN_CREATED_AT)),
        updatedAtMillis = getLong(getColumnIndexOrThrow(SellerReplyTemplateTable.COLUMN_UPDATED_AT))
    )

    private fun stringList(json: String): List<String> = runCatching<List<String>> {
        gson.fromJson(json, object : TypeToken<List<String>>() {}.type)
    }.getOrDefault(emptyList())

    private companion object {
        const val MAX_QUERY_ROWS = 500

        fun ReplySortMode.orderByClause(): String = when (this) {
            ReplySortMode.SMART ->
                "${SellerReplyTemplateTable.COLUMN_FAVORITE} DESC, " +
                    "${SellerReplyTemplateTable.COLUMN_USAGE_COUNT} DESC, " +
                    "${SellerReplyTemplateTable.COLUMN_UPDATED_AT} DESC"
            ReplySortMode.RECENT ->
                "${SellerReplyTemplateTable.COLUMN_UPDATED_AT} DESC"
            ReplySortMode.ALPHABETICAL ->
                "${SellerReplyTemplateTable.COLUMN_TITLE} COLLATE NOCASE ASC"
            ReplySortMode.CATEGORY ->
                "${SellerReplyTemplateTable.COLUMN_CATEGORY} COLLATE NOCASE ASC, " +
                    "${SellerReplyTemplateTable.COLUMN_TITLE} COLLATE NOCASE ASC"
        }

        val ALL_COLUMNS = arrayOf(
            SellerReplyTemplateTable.COLUMN_ID,
            SellerReplyTemplateTable.COLUMN_TITLE,
            SellerReplyTemplateTable.COLUMN_BODY,
            SellerReplyTemplateTable.COLUMN_CATEGORY,
            SellerReplyTemplateTable.COLUMN_TAGS,
            SellerReplyTemplateTable.COLUMN_FAVORITE,
            SellerReplyTemplateTable.COLUMN_USAGE_COUNT,
            SellerReplyTemplateTable.COLUMN_SHORTCUT,
            SellerReplyTemplateTable.COLUMN_MEDIA_URLS,
            SellerReplyTemplateTable.COLUMN_REPLY_PACK_ID,
            SellerReplyTemplateTable.COLUMN_PROFILE_IDS,
            SellerReplyTemplateTable.COLUMN_ARCHIVED,
            SellerReplyTemplateTable.COLUMN_CREATED_AT,
            SellerReplyTemplateTable.COLUMN_UPDATED_AT
        )
    }
}
