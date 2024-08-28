// File: SnippetsDao.kt
import android.content.ContentValues
import android.database.Cursor
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.terasumi.sellerkeyboard.DatabaseHelper
import com.terasumi.sellerkeyboard.Snippets
import com.terasumi.sellerkeyboard.SnippetsTable

class SnippetsDao(private val dbHelper: DatabaseHelper) {

    private val gson = Gson()

    //Insert
    fun insertSnippet(snippet: Snippets): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(SnippetsTable.COLUMN_TITLE, snippet.title)
            put(SnippetsTable.COLUMN_CONTENT, snippet.content)
            put(SnippetsTable.COLUMN_IMAGE_URLS, gson.toJson(snippet.imageUrls))
        }
        return db.insert(SnippetsTable.TABLE_NAME, null, values)
    }

    // Fetch snippet by ID
    fun fetchSnippetById(id: Int): Snippets? {
        val db = dbHelper.readableDatabase
        val projection = arrayOf(
            SnippetsTable.COLUMN_ID,
            SnippetsTable.COLUMN_TITLE,
            SnippetsTable.COLUMN_CONTENT,
            SnippetsTable.COLUMN_IMAGE_URLS
        )
        val selection = "${SnippetsTable.COLUMN_ID} = ?"
        val selectionArgs = arrayOf(id.toString())
        val cursor: Cursor = db.query(
            SnippetsTable.TABLE_NAME,
            projection,
            selection,
            selectionArgs,
            null,
            null,
            null
        )

        var snippet: Snippets? = null
        with(cursor) {
            if (moveToFirst()) {
                val title = getString(getColumnIndexOrThrow(SnippetsTable.COLUMN_TITLE))
                val content = getString(getColumnIndexOrThrow(SnippetsTable.COLUMN_CONTENT))
                val imageUrlsJson =
                    getString(getColumnIndexOrThrow(SnippetsTable.COLUMN_IMAGE_URLS))
                val imageUrls: List<String> =
                    gson.fromJson(imageUrlsJson, object : TypeToken<List<String>>() {}.type)
                snippet = Snippets(id, title, content, imageUrls)
            }
        }
        cursor.close()
        return snippet
    }


    //Get all
    fun fetchSnippets(): List<Snippets> {
        val db = dbHelper.readableDatabase
        val projection = arrayOf(
            SnippetsTable.COLUMN_ID,
            SnippetsTable.COLUMN_TITLE,
            SnippetsTable.COLUMN_CONTENT,
            SnippetsTable.COLUMN_IMAGE_URLS
        )
        val cursor: Cursor = db.query(
            SnippetsTable.TABLE_NAME,
            projection,
            null,
            null,
            null,
            null,
            null
        )

        val snippetsList = mutableListOf<Snippets>()
        with(cursor) {
            while (moveToNext()) {
                val id = getInt(getColumnIndexOrThrow(SnippetsTable.COLUMN_ID))
                val title = getString(getColumnIndexOrThrow(SnippetsTable.COLUMN_TITLE))
                val content = getString(getColumnIndexOrThrow(SnippetsTable.COLUMN_CONTENT))
                val imageUrlsJson =
                    getString(getColumnIndexOrThrow(SnippetsTable.COLUMN_IMAGE_URLS))
                val imageUrls: List<String> =
                    gson.fromJson(imageUrlsJson, object : TypeToken<List<String>>() {}.type)
                snippetsList.add(Snippets(id, title, content, imageUrls))
            }
        }
        cursor.close()
        return snippetsList
    }

    //Update by ID
    fun updateSnippetById(snippetId: Int, title: String, content: String, imageUrls: List<String>) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(SnippetsTable.COLUMN_TITLE, title)
            put(SnippetsTable.COLUMN_CONTENT, content)
            put(SnippetsTable.COLUMN_IMAGE_URLS, gson.toJson(imageUrls))
        }
        db.update(
            SnippetsTable.TABLE_NAME,
            values,
            "${SnippetsTable.COLUMN_ID} = ?",
            arrayOf(snippetId.toString())
        )
    }

    //Update by ID (no imageUrls)
    fun updateSnippetByIdNoImageUrls(snippetId: Int, title: String, content: String) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(SnippetsTable.COLUMN_TITLE, title)
            put(SnippetsTable.COLUMN_CONTENT, content)
        }
        db.update(
            SnippetsTable.TABLE_NAME,
            values,
            "${SnippetsTable.COLUMN_ID} = ?",
            arrayOf(snippetId.toString())
        )
    }

    //Delete by Id
    fun deleteSnippetById(id: Int): Int {
        val db = dbHelper.writableDatabase
        return db.delete(
            SnippetsTable.TABLE_NAME,
            "${SnippetsTable.COLUMN_ID} = ?",
            arrayOf(id.toString())
        )
    }
}