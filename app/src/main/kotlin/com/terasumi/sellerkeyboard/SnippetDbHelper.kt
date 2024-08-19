package com.terasumi.sellerkeyboard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SnippetDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_TABLE_SNIPPETS)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SNIPPETS")
        onCreate(db)
    }

    fun addSnippet(snippet: Snippet): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_TITLE, snippet.title)
            put(COLUMN_CONTENT, snippet.content)
            put(COLUMN_IMAGE_URL, snippet.imageUrl)
        }
        return db.insert(TABLE_SNIPPETS, null, values)
    }

    fun getSnippet(id: Int): Snippet? {
        val db = readableDatabase
        val columns = arrayOf(COLUMN_ID, COLUMN_TITLE, COLUMN_CONTENT, COLUMN_IMAGE_URL)
        val selection = "$COLUMN_ID = ?"
        val selectionArgs = arrayOf(id.toString())

        db.query(
            TABLE_SNIPPETS, columns, selection, selectionArgs,
            null, null, null
        ).use { cursor ->
            return if (cursor.moveToFirst()) {
                val title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE))
                val content = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT))
                val imageUrl = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMAGE_URL))
                Snippet(id, title, content, imageUrl)
            } else {
                null
            }
        }
    }

    val allSnippets: List<Snippet>
        get() {
            val snippets = mutableListOf<Snippet>()
            val db = readableDatabase
            val columns = arrayOf(COLUMN_ID, COLUMN_TITLE, COLUMN_CONTENT, COLUMN_IMAGE_URL)

            db.query(TABLE_SNIPPETS, columns, null, null, null, null, null).use { cursor ->                while (cursor.moveToNext()) {
                    val id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID))
                    val title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE))
                    val content = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT))
                    val imageUrl = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMAGE_URL))
                    snippets.add(Snippet(id, title, content, imageUrl))
                }
            }
            return snippets
        }

    fun updateSnippet(snippet: Snippet): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_TITLE, snippet.title)
            put(COLUMN_CONTENT, snippet.content)
            put(COLUMN_IMAGE_URL, snippet.imageUrl)
        }
        val selection = "$COLUMN_ID = ?"
        val selectionArgs = arrayOf(snippet.id.toString())

        return db.update(TABLE_SNIPPETS, values, selection, selectionArgs)
    }

    fun deleteSnippet(id: Int): Int {
        val db = writableDatabase
        val selection = "$COLUMN_ID = ?"
        val selectionArgs = arrayOf(id.toString())

        return db.delete(TABLE_SNIPPETS, selection, selectionArgs)
    }

    companion object {
        private const val DATABASE_NAME = "snippets.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_SNIPPETS = "snippets"
        private const val COLUMN_ID = "id"
        private const val COLUMN_TITLE = "title"
        private const val COLUMN_CONTENT = "content"
        private const val COLUMN_IMAGE_URL = "imageUrl"

        private const val SQL_CREATE_TABLE_SNIPPETS = """
            CREATE TABLE $TABLE_SNIPPETS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TITLE TEXT,
                $COLUMN_CONTENT TEXT,
                $COLUMN_IMAGE_URL TEXT
            )
        """
    }
}