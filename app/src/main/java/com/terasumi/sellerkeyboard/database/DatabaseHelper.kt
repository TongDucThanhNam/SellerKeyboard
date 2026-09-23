package com.terasumi.sellerkeyboard.database


import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SnippetsTable.SQL_CREATE_TABLE)
        db.execSQL(SellerReplyTemplateTable.SQL_CREATE_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.beginTransaction()
        try {
            if (oldVersion < 2) {
                db.execSQL(SellerReplyTemplateTable.SQL_CREATE_TABLE)
                db.execSQL(SellerReplyTemplateTable.SQL_MIGRATE_LEGACY_SNIPPETS)
            }
            if (oldVersion in 2 until 3) {
                db.execSQL(SellerReplyTemplateTable.SQL_ADD_REPLY_PACK_COLUMN)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        db.execSQL(SellerReplyTemplateTable.SQL_CREATE_TABLE)
        runCatching { db.execSQL(SellerReplyTemplateTable.SQL_ADD_REPLY_PACK_COLUMN) }
        db.execSQL(SellerReplyTemplateTable.SQL_MIGRATE_LEGACY_SNIPPETS)
    }

    companion object {
        const val DATABASE_VERSION = 3
        const val DATABASE_NAME = "SellerKeyboard.db"
    }
}
