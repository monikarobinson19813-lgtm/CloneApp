package com.cloneapp.testapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

data class StorageSnapshot(
    val prefsUsername: String,
    val prefsCounter: Int,
    val databaseUsername: String?,
    val databaseCounter: Int?,
    val fileValue: String?,
    val cacheValue: String?,
)

class StorageStateStore(
    private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val db = TestDb(context)

    fun snapshot(): StorageSnapshot {
        val databaseState = db.read()
        return StorageSnapshot(
            prefsUsername = prefs.getString(KEY_USERNAME, "").orEmpty(),
            prefsCounter = prefs.getInt(KEY_COUNTER, 0),
            databaseUsername = databaseState?.first,
            databaseCounter = databaseState?.second,
            fileValue = File(context.filesDir, STATE_FILE).takeIf { it.exists() }?.readText(),
            cacheValue = File(context.cacheDir, CACHE_FILE).takeIf { it.exists() }?.readText(),
        )
    }

    fun save(username: String, counter: Int) {
        check(
            prefs.edit()
                .putString(KEY_USERNAME, username)
                .putInt(KEY_COUNTER, counter)
                .commit()
        ) {
            "Unable to persist SharedPreferences state"
        }
        File(context.filesDir, STATE_FILE).writeText("$username|$counter")
        File(context.cacheDir, CACHE_FILE).writeText("$username|$counter")
        db.save(username, counter)
    }

    fun deleteAll(): Boolean {
        db.close()
        val prefsDeleted = context.deleteSharedPreferences(PREFS_NAME)
        val databaseDeleted = context.deleteDatabase(DB_NAME)
        val filesDeleted = !context.filesDir.exists() || context.filesDir.deleteRecursively()
        val cacheDeleted = !context.cacheDir.exists() || context.cacheDir.deleteRecursively()
        return prefsDeleted && databaseDeleted && filesDeleted && cacheDeleted
    }

    fun close() {
        db.close()
    }

    companion object {
        const val DB_NAME = "ca_test.db"
        private const val PREFS_NAME = "test_state"
        private const val KEY_USERNAME = "username"
        private const val KEY_COUNTER = "counter"
        private const val STATE_FILE = "state.txt"
        private const val CACHE_FILE = "cache-marker.txt"
    }
}

private class TestDb(context: Context) : SQLiteOpenHelper(context, StorageStateStore.DB_NAME, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE state(id INTEGER PRIMARY KEY, username TEXT, counter INTEGER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun save(username: String, counter: Int) {
        writableDatabase.delete("state", null, null)
        writableDatabase.insert("state", null, ContentValues().apply {
            put("id", 1)
            put("username", username)
            put("counter", counter)
        })
    }

    fun read(): Pair<String, Int>? =
        readableDatabase.query(
            "state",
            arrayOf("username", "counter"),
            "id = ?",
            arrayOf("1"),
            null,
            null,
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                cursor.getString(0) to cursor.getInt(1)
            }
        }
}
