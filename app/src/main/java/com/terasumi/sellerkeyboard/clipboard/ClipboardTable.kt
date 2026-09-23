package com.terasumi.sellerkeyboard.clipboard

internal object ClipboardTable {
    const val TABLE_NAME = "clipboard_history"
    const val COLUMN_ID = "id"
    const val COLUMN_TEXT = "text"
    const val COLUMN_TYPE = "type"
    const val COLUMN_SOURCE = "source"
    const val COLUMN_CREATED_AT = "createdAtMillis"
    const val COLUMN_UPDATED_AT = "updatedAtMillis"
    const val COLUMN_PINNED = "pinned"
    const val COLUMN_SENSITIVE = "sensitive"

    const val SQL_CREATE_TABLE = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COLUMN_TEXT TEXT NOT NULL,
            $COLUMN_TYPE TEXT NOT NULL,
            $COLUMN_SOURCE TEXT NOT NULL,
            $COLUMN_CREATED_AT INTEGER NOT NULL,
            $COLUMN_UPDATED_AT INTEGER NOT NULL,
            $COLUMN_PINNED INTEGER NOT NULL DEFAULT 0,
            $COLUMN_SENSITIVE INTEGER NOT NULL DEFAULT 0
        )
    """
}
