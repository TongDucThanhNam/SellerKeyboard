package com.terasumi.sellerkeyboard.clipboard

import android.content.ContentValues
import android.database.Cursor
import com.terasumi.sellerkeyboard.database.DatabaseHelper

class SQLiteClipboardStore(
    private val dbHelper: DatabaseHelper
) : ClipboardStore {
    @Volatile
    private var ready = false

    override fun ensureReady() {
        if (ready) return
        synchronized(this) {
            if (ready) return
            val database = dbHelper.writableDatabase
            database.execSQL(ClipboardTable.SQL_CREATE_TABLE)
            runCatching {
                database.execSQL(
                    "ALTER TABLE ${ClipboardTable.TABLE_NAME} ADD COLUMN " +
                        "${ClipboardTable.COLUMN_SENSITIVE} INTEGER NOT NULL DEFAULT 0"
                )
            }
            ready = true
        }
    }

    override fun listRecent(limit: Int): List<ClipboardItem> {
        ensureReady()
        val cursor = dbHelper.readableDatabase.query(
            ClipboardTable.TABLE_NAME,
            allColumns,
            null,
            null,
            null,
            null,
            "${ClipboardTable.COLUMN_PINNED} DESC, ${ClipboardTable.COLUMN_UPDATED_AT} DESC",
            limit.coerceAtLeast(1).toString()
        )

        return cursor.useItems()
    }

    override fun latestByCreatedAt(): ClipboardItem? {
        ensureReady()
        val cursor = dbHelper.readableDatabase.query(
            ClipboardTable.TABLE_NAME,
            allColumns,
            null,
            null,
            null,
            null,
            "${ClipboardTable.COLUMN_CREATED_AT} DESC",
            "1"
        )

        return cursor.useItems().firstOrNull()
    }

    override fun insert(item: ClipboardItem): ClipboardItem {
        ensureReady()
        val values = ContentValues().apply {
            put(ClipboardTable.COLUMN_TEXT, item.text)
            put(ClipboardTable.COLUMN_TYPE, item.type.name)
            put(ClipboardTable.COLUMN_SOURCE, item.source.name)
            put(ClipboardTable.COLUMN_CREATED_AT, item.createdAtMillis)
            put(ClipboardTable.COLUMN_UPDATED_AT, item.updatedAtMillis)
            put(ClipboardTable.COLUMN_PINNED, if (item.pinned) 1 else 0)
            put(ClipboardTable.COLUMN_SENSITIVE, if (item.sensitive) 1 else 0)
        }
        val id = dbHelper.writableDatabase.insert(ClipboardTable.TABLE_NAME, null, values)
        return item.copy(id = id)
    }

    override fun setPinned(id: Long, pinned: Boolean, updatedAtMillis: Long): Boolean {
        ensureReady()
        val values = ContentValues().apply {
            put(ClipboardTable.COLUMN_PINNED, if (pinned) 1 else 0)
            put(ClipboardTable.COLUMN_UPDATED_AT, updatedAtMillis)
        }
        return dbHelper.writableDatabase.update(
            ClipboardTable.TABLE_NAME,
            values,
            "${ClipboardTable.COLUMN_ID} = ?",
            arrayOf(id.toString())
        ) > 0
    }

    override fun markUsed(id: Long, updatedAtMillis: Long): Boolean {
        ensureReady()
        val values = ContentValues().apply {
            put(ClipboardTable.COLUMN_UPDATED_AT, updatedAtMillis)
        }
        return dbHelper.writableDatabase.update(
            ClipboardTable.TABLE_NAME,
            values,
            "${ClipboardTable.COLUMN_ID} = ?",
            arrayOf(id.toString())
        ) > 0
    }

    override fun delete(id: Long): Boolean {
        ensureReady()
        return dbHelper.writableDatabase.delete(
            ClipboardTable.TABLE_NAME,
            "${ClipboardTable.COLUMN_ID} = ?",
            arrayOf(id.toString())
        ) > 0
    }

    override fun clear(): Boolean {
        ensureReady()
        return dbHelper.writableDatabase.delete(ClipboardTable.TABLE_NAME, null, null) >= 0
    }

    override fun deleteOlderThan(cutoffMillis: Long): Int {
        ensureReady()
        return dbHelper.writableDatabase.delete(
            ClipboardTable.TABLE_NAME,
            "${ClipboardTable.COLUMN_PINNED} = 0 AND ${ClipboardTable.COLUMN_CREATED_AT} < ?",
            arrayOf(cutoffMillis.toString())
        )
    }

    override fun pruneUnpinned(maxItems: Int): Int {
        ensureReady()
        val boundedLimit = maxItems.coerceAtLeast(0)
        return dbHelper.writableDatabase.delete(
            ClipboardTable.TABLE_NAME,
            "${ClipboardTable.COLUMN_PINNED} = 0 AND ${ClipboardTable.COLUMN_ID} NOT IN (" +
                "SELECT ${ClipboardTable.COLUMN_ID} FROM ${ClipboardTable.TABLE_NAME} " +
                "WHERE ${ClipboardTable.COLUMN_PINNED} = 0 ORDER BY " +
                "${ClipboardTable.COLUMN_UPDATED_AT} DESC LIMIT ?)",
            arrayOf(boundedLimit.toString())
        )
    }

    private fun Cursor.useItems(): List<ClipboardItem> {
        return use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ClipboardItem(
                            id = cursor.getLong(cursor.getColumnIndexOrThrow(ClipboardTable.COLUMN_ID)),
                            text = cursor.getString(cursor.getColumnIndexOrThrow(ClipboardTable.COLUMN_TEXT)),
                            type = ClipboardItemType.valueOf(
                                cursor.getString(cursor.getColumnIndexOrThrow(ClipboardTable.COLUMN_TYPE))
                            ),
                            source = ClipboardSource.valueOf(
                                cursor.getString(cursor.getColumnIndexOrThrow(ClipboardTable.COLUMN_SOURCE))
                            ),
                            createdAtMillis = cursor.getLong(
                                cursor.getColumnIndexOrThrow(ClipboardTable.COLUMN_CREATED_AT)
                            ),
                            updatedAtMillis = cursor.getLong(
                                cursor.getColumnIndexOrThrow(ClipboardTable.COLUMN_UPDATED_AT)
                            ),
                            pinned = cursor.getInt(cursor.getColumnIndexOrThrow(ClipboardTable.COLUMN_PINNED)) == 1,
                            sensitive = cursor.getInt(
                                cursor.getColumnIndexOrThrow(ClipboardTable.COLUMN_SENSITIVE)
                            ) == 1
                        )
                    )
                }
            }
        }
    }

    private companion object {
        private val allColumns = arrayOf(
            ClipboardTable.COLUMN_ID,
            ClipboardTable.COLUMN_TEXT,
            ClipboardTable.COLUMN_TYPE,
            ClipboardTable.COLUMN_SOURCE,
            ClipboardTable.COLUMN_CREATED_AT,
            ClipboardTable.COLUMN_UPDATED_AT,
            ClipboardTable.COLUMN_PINNED,
            ClipboardTable.COLUMN_SENSITIVE
        )
    }
}
