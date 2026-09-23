package com.terasumi.sellerkeyboard.database

object SellerReplyTemplateTable {
    const val TABLE_NAME = "seller_reply_templates"
    const val COLUMN_ID = "id"
    const val COLUMN_TITLE = "title"
    const val COLUMN_BODY = "body"
    const val COLUMN_CATEGORY = "category"
    const val COLUMN_TAGS = "tagsJson"
    const val COLUMN_FAVORITE = "favorite"
    const val COLUMN_USAGE_COUNT = "usageCount"
    const val COLUMN_SHORTCUT = "shortcut"
    const val COLUMN_MEDIA_URLS = "mediaUrlsJson"
    const val COLUMN_REPLY_PACK_ID = "replyPackId"
    const val COLUMN_PROFILE_IDS = "profileIdsJson"
    const val COLUMN_ARCHIVED = "archived"
    const val COLUMN_CREATED_AT = "createdAtMillis"
    const val COLUMN_UPDATED_AT = "updatedAtMillis"

    const val SQL_CREATE_TABLE = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            $COLUMN_ID TEXT PRIMARY KEY NOT NULL,
            $COLUMN_TITLE TEXT NOT NULL,
            $COLUMN_BODY TEXT NOT NULL,
            $COLUMN_CATEGORY TEXT NOT NULL DEFAULT 'General',
            $COLUMN_TAGS TEXT NOT NULL DEFAULT '[]',
            $COLUMN_FAVORITE INTEGER NOT NULL DEFAULT 0,
            $COLUMN_USAGE_COUNT INTEGER NOT NULL DEFAULT 0,
            $COLUMN_SHORTCUT TEXT,
            $COLUMN_MEDIA_URLS TEXT NOT NULL DEFAULT '[]',
            $COLUMN_REPLY_PACK_ID TEXT,
            $COLUMN_PROFILE_IDS TEXT NOT NULL DEFAULT '[]',
            $COLUMN_ARCHIVED INTEGER NOT NULL DEFAULT 0,
            $COLUMN_CREATED_AT INTEGER NOT NULL DEFAULT 0,
            $COLUMN_UPDATED_AT INTEGER NOT NULL DEFAULT 0
        )
    """

    const val SQL_MIGRATE_LEGACY_SNIPPETS = """
        INSERT OR IGNORE INTO $TABLE_NAME (
            $COLUMN_ID, $COLUMN_TITLE, $COLUMN_BODY, $COLUMN_MEDIA_URLS
        )
        SELECT 'legacy-' || id, COALESCE(title, ''), COALESCE(content, ''),
            COALESCE(imageUrls, '[]')
        FROM ${SnippetsTable.TABLE_NAME}
    """

    const val SQL_ADD_REPLY_PACK_COLUMN =
        "ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_REPLY_PACK_ID TEXT"
}
