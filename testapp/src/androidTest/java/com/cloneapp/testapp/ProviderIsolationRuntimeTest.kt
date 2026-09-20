package com.cloneapp.testapp

import android.content.ContentValues
import android.database.Cursor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderIsolationRuntimeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver

    @Test
    fun aliceAndBobProviderStateAreIndependentAndDeleteDoesNotCrossUsers() {
        clear(ALICE_USER_ID)
        clear(BOB_USER_ID)

        val aliceUri = ControlledStateProvider.uriFor(ALICE_USER_ID)
        val bobUri = ControlledStateProvider.uriFor(BOB_USER_ID)
        assertNotEquals(aliceUri, bobUri)

        resolver.insert(
            aliceUri,
            ContentValues().apply {
                put(ControlledStateProvider.KEY_USERNAME, ALICE_NAME)
                put(ControlledStateProvider.KEY_COUNTER, ALICE_COUNTER)
            },
        )
        resolver.insert(
            bobUri,
            ContentValues().apply {
                put(ControlledStateProvider.KEY_USERNAME, BOB_NAME)
                put(ControlledStateProvider.KEY_COUNTER, BOB_COUNTER)
            },
        )

        assertProviderState(read(aliceUri), ALICE_NAME, ALICE_COUNTER)
        assertProviderState(read(bobUri), BOB_NAME, BOB_COUNTER)

        assertEquals(1, resolver.delete(aliceUri, null, null))

        val deletedAlice = read(aliceUri)
        assertEquals("", deletedAlice.prefsUsername)
        assertEquals(0, deletedAlice.prefsCounter)
        assertNull(deletedAlice.databaseUsername)
        assertNull(deletedAlice.databaseCounter)
        assertNull(deletedAlice.fileValue)
        assertNull(deletedAlice.cacheValue)

        assertProviderState(read(bobUri), BOB_NAME, BOB_COUNTER)
    }

    private fun read(uri: android.net.Uri): ProviderSnapshot =
        requireNotNull(resolver.query(uri, null, null, null, null)).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.snapshot()
        }

    private fun Cursor.snapshot() = ProviderSnapshot(
        prefsUsername = getString(getColumnIndexOrThrow("prefs_username")),
        prefsCounter = getInt(getColumnIndexOrThrow("prefs_counter")),
        databaseUsername = nullableString("database_username"),
        databaseCounter = nullableInt("database_counter"),
        fileValue = nullableString("file_value"),
        cacheValue = nullableString("cache_value"),
    )

    private fun Cursor.nullableString(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun Cursor.nullableInt(column: String): Int? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getInt(index)
    }

    private fun clear(virtualUserId: Int) {
        val store = StorageStateStore(VirtualStorageContext(context, virtualUserId))
        try {
            store.deleteAll()
        } finally {
            store.close()
        }
    }

    private fun assertProviderState(
        snapshot: ProviderSnapshot,
        username: String,
        counter: Int,
    ) {
        assertEquals(username, snapshot.prefsUsername)
        assertEquals(counter, snapshot.prefsCounter)
        assertEquals(username, snapshot.databaseUsername)
        assertEquals(counter, snapshot.databaseCounter)
        assertEquals("$username|$counter", snapshot.fileValue)
        assertEquals("$username|$counter", snapshot.cacheValue)
    }

    private data class ProviderSnapshot(
        val prefsUsername: String,
        val prefsCounter: Int,
        val databaseUsername: String?,
        val databaseCounter: Int?,
        val fileValue: String?,
        val cacheValue: String?,
    )

    companion object {
        private const val ALICE_USER_ID = 0
        private const val BOB_USER_ID = 1
        private const val ALICE_NAME = "alice-provider"
        private const val BOB_NAME = "bob-provider"
        private const val ALICE_COUNTER = 11
        private const val BOB_COUNTER = 51
    }
}
