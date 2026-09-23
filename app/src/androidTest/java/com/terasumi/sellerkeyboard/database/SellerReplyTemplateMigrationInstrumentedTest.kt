package com.terasumi.sellerkeyboard.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SellerReplyTemplateMigrationInstrumentedTest {
    @Test
    fun version2To3_addsReplyPackWithoutLosingTemplateData() {
        val database = SQLiteDatabase.create(null)
        val version2Create = SellerReplyTemplateTable.SQL_CREATE_TABLE.replace(
            "${SellerReplyTemplateTable.COLUMN_REPLY_PACK_ID} TEXT,",
            ""
        )
        database.execSQL(version2Create)
        database.insertOrThrow(
            SellerReplyTemplateTable.TABLE_NAME,
            null,
            ContentValues().apply {
                put(SellerReplyTemplateTable.COLUMN_ID, "existing")
                put(SellerReplyTemplateTable.COLUMN_TITLE, "Existing reply")
                put(SellerReplyTemplateTable.COLUMN_BODY, "Preserve me")
            }
        )

        DatabaseHelper(ApplicationProvider.getApplicationContext()).onUpgrade(database, 2, 3)

        val columns = database.rawQuery(
            "PRAGMA table_info(${SellerReplyTemplateTable.TABLE_NAME})",
            null
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
        assertTrue(SellerReplyTemplateTable.COLUMN_REPLY_PACK_ID in columns)
        database.query(
            SellerReplyTemplateTable.TABLE_NAME,
            arrayOf(
                SellerReplyTemplateTable.COLUMN_TITLE,
                SellerReplyTemplateTable.COLUMN_BODY
            ),
            "${SellerReplyTemplateTable.COLUMN_ID} = ?",
            arrayOf("existing"),
            null,
            null,
            null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Existing reply", cursor.getString(0))
            assertEquals("Preserve me", cursor.getString(1))
        }
        database.close()
    }

    @Test
    fun version1To3_migratesLegacyBodyAndImagesIntoCurrentSchema() {
        val database = SQLiteDatabase.create(null)
        database.execSQL(SnippetsTable.SQL_CREATE_TABLE)
        database.insertOrThrow(
            SnippetsTable.TABLE_NAME,
            null,
            ContentValues().apply {
                put("id", 17)
                put("title", "Legacy")
                put("content", "Legacy body")
                put("imageUrls", "[\"https://example.test/image.jpg\"]")
            }
        )

        DatabaseHelper(ApplicationProvider.getApplicationContext()).onUpgrade(database, 1, 3)

        database.query(
            SellerReplyTemplateTable.TABLE_NAME,
            arrayOf(
                SellerReplyTemplateTable.COLUMN_BODY,
                SellerReplyTemplateTable.COLUMN_MEDIA_URLS,
                SellerReplyTemplateTable.COLUMN_REPLY_PACK_ID
            ),
            "${SellerReplyTemplateTable.COLUMN_ID} = ?",
            arrayOf("legacy-17"),
            null,
            null,
            null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Legacy body", cursor.getString(0))
            assertEquals("[\"https://example.test/image.jpg\"]", cursor.getString(1))
            assertTrue(cursor.isNull(2))
        }
        database.close()
    }
}
