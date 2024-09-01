package com.terasumi.sellerkeyboard.database

// File: SnippetsTable.kt
object SnippetsTable {
    const val TABLE_NAME = "snippets"
    const val COLUMN_ID = "id"
    const val COLUMN_TITLE = "title"
    const val COLUMN_CONTENT = "content"
    const val COLUMN_IMAGE_URLS = "imageUrls"

    const val SQL_CREATE_TABLE = """
        CREATE TABLE $TABLE_NAME (
            $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COLUMN_TITLE TEXT,
            $COLUMN_CONTENT TEXT,
            $COLUMN_IMAGE_URLS TEXT
        )
    """

    const val SQL_DELETE_TABLE = "DROP TABLE IF EXISTS $TABLE_NAME"
}