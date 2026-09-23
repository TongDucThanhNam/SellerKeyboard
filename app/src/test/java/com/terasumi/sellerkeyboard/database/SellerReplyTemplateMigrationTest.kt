package com.terasumi.sellerkeyboard.database

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SellerReplyTemplateMigrationTest {
    @Test
    fun migration_isNonDestructiveAndPreservesLegacyImages() {
        val sql = SellerReplyTemplateTable.SQL_MIGRATE_LEGACY_SNIPPETS
        assertTrue("INSERT OR IGNORE" in sql)
        assertTrue("imageUrls" in sql)
        assertTrue("legacy-" in sql)
        assertTrue(
            SellerReplyTemplateTable.SQL_ADD_REPLY_PACK_COLUMN.startsWith("ALTER TABLE")
        )
        assertTrue("replyPackId" in SellerReplyTemplateTable.SQL_ADD_REPLY_PACK_COLUMN)
        assertTrue(DatabaseHelper.DATABASE_VERSION >= 3)
        assertFalse(sql.contains("DROP TABLE", ignoreCase = true))
        assertFalse(DatabaseHelper::class.java.declaredMethods.any { method ->
            method.name.contains("delete", ignoreCase = true)
        })
    }
}
