package com.cloneapp.testapp

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * Controlled v0.1 provider probe.
 *
 * Android owns one physical provider authority for the Test App. CloneApp's provider router
 * will supply a virtual-user id and translate virtual authorities before dispatching here.
 */
class ControlledStateProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val store = storeFor(uri)
        return try {
            val state = store.snapshot()
            MatrixCursor(COLUMNS).apply {
                addRow(
                    arrayOf(
                        state.prefsUsername,
                        state.prefsCounter,
                        state.databaseUsername,
                        state.databaseCounter,
                        state.fileValue,
                        state.cacheValue,
                    )
                )
            }
        } finally {
            store.close()
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        val username = requireNotNull(values?.getAsString(KEY_USERNAME)) {
            "Provider write requires username"
        }
        val counter = requireNotNull(values.getAsInteger(KEY_COUNTER)) {
            "Provider write requires counter"
        }

        val store = storeFor(uri)
        try {
            store.save(username, counter)
        } finally {
            store.close()
        }
        return uri
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        val store = storeFor(uri)
        return try {
            if (store.deleteAll()) 1 else 0
        } finally {
            store.close()
        }
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        insert(uri, values)
        return 1
    }

    override fun getType(uri: Uri): String {
        virtualUserId(uri)
        return MIME_TYPE
    }

    private fun storeFor(uri: Uri): StorageStateStore {
        val appContext = requireNotNull(context) { "Provider context is unavailable" }
        return StorageStateStore(
            VirtualStorageContext(appContext, virtualUserId(uri))
        )
    }

    private fun virtualUserId(uri: Uri): Int {
        require(uri.authority == AUTHORITY) {
            "Unexpected provider authority: ${uri.authority}"
        }
        val parts = uri.pathSegments
        require(parts.size == 3 && parts[0] == "vusers" && parts[2] == "state") {
            "Expected provider URI /vusers/{id}/state: $uri"
        }
        return parts[1].toIntOrNull()?.takeIf { it >= 0 }
            ?: throw IllegalArgumentException("Invalid virtual-user id in provider URI: $uri")
    }

    companion object {
        const val AUTHORITY = "com.cloneapp.testapp.state"
        const val KEY_USERNAME = "username"
        const val KEY_COUNTER = "counter"

        val COLUMNS = arrayOf(
            "prefs_username",
            "prefs_counter",
            "database_username",
            "database_counter",
            "file_value",
            "cache_value",
        )

        private const val MIME_TYPE =
            "vnd.android.cursor.item/vnd.com.cloneapp.testapp.state"

        fun uriFor(virtualUserId: Int): Uri {
            require(virtualUserId >= 0) { "virtualUserId must be non-negative" }
            return Uri.parse("content://$AUTHORITY/vusers/$virtualUserId/state")
        }
    }
}
