package com.example.sellerkeyboard;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class SnippetDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "snippets.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_SNIPPETS = "snippets";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_TITLE = "title";
    private static final String COLUMN_CONTENT = "content";
    private static final String COLUMN_IMAGE_URL = "imageUrl";

    private static final String SQL_CREATE_TABLE_SNIPPETS = "CREATE TABLE " + TABLE_SNIPPETS + " (" +
            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            COLUMN_TITLE + " TEXT," +
            COLUMN_CONTENT + " TEXT," +
            COLUMN_IMAGE_URL + " TEXT" +
            ")";

    public SnippetDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_TABLE_SNIPPETS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Handle database upgrade
    }

    // Add methods to interact with the database here
    // addSnippet, getSnippet, getAllSnippets, updateSnippet, deleteSnippet
    public long addSnippet(Snippet snippet) {
        // Add a new snippet to the database
        SQLiteDatabase db = getWritableDatabase();
        // Add the snippet to the database
        ContentValues values = new ContentValues();
        values.put(COLUMN_TITLE, snippet.getTitle());
        values.put(COLUMN_CONTENT, snippet.getContent());
        values.put(COLUMN_IMAGE_URL, snippet.getImageUrl());
        return db.insert(TABLE_SNIPPETS, null, values);
    }

    public Snippet getSnippet(int id) {
        // Get a snippet from the database by id
        return null;
    }

    public List<Snippet> getAllSnippets() {
        List<Snippet> snippets = new ArrayList<>();
        // Get all snippets from the database
        SQLiteDatabase db = getReadableDatabase();
        String[] columns = {COLUMN_ID, COLUMN_TITLE, COLUMN_CONTENT, COLUMN_IMAGE_URL};
        Cursor cursor = db.query(
                TABLE_SNIPPETS,
                columns, // columns to return
                null, // columns for the WHERE clause
                null, // values for the WHERE clause
                null, // group by
                null,// having
                null // order by
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE));
                String content = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT));
                String imageUrl = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMAGE_URL));

                Snippet snippet = new Snippet(id, title, content, imageUrl);
                snippets.add(snippet);
            }
            cursor.close();
        }
        return snippets;
    }

    public void updateSnippet(Snippet snippet) {
        // Update a snippet in the database
    }

    public void deleteSnippet(int id) {
        // Delete a snippet from the database by id
    }

    // Add other methods as needed
}
