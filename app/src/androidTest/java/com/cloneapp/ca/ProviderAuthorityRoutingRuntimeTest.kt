package com.cloneapp.ca

import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderAuthorityRoutingRuntimeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dispatcher = VirtualProviderDispatcher(context.contentResolver)

    @Test
    fun aliceAndBobUseDistinctVirtualAuthoritiesAndRouteToIsolatedPhysicalProviderState() {
        val alice = dispatcher.virtualUri(
            packageName = PACKAGE_NAME,
            virtualUserId = ALICE_USER_ID,
            physicalAuthority = PHYSICAL_AUTHORITY,
            "state",
        )
        val bob = dispatcher.virtualUri(
            packageName = PACKAGE_NAME,
            virtualUserId = BOB_USER_ID,
            physicalAuthority = PHYSICAL_AUTHORITY,
            "state",
        )

        assertNotEquals(alice.authority, bob.authority)
        assertTrue(requireNotNull(alice.authority).startsWith("ca.virtual."))
        assertTrue(requireNotNull(bob.authority).startsWith("ca.virtual."))

        assertEquals(
            Uri.parse("content://$PHYSICAL_AUTHORITY/vusers/$ALICE_USER_ID/state"),
            dispatcher.resolveForDispatch(alice),
        )
        assertEquals(
            Uri.parse("content://$PHYSICAL_AUTHORITY/vusers/$BOB_USER_ID/state"),
            dispatcher.resolveForDispatch(bob),
        )

        dispatcher.delete(alice)
        dispatcher.delete(bob)

        dispatcher.insert(
            alice,
            ContentValues().apply {
                put(KEY_USERNAME, ALICE_NAME)
                put(KEY_COUNTER, ALICE_COUNTER)
            },
        )
        dispatcher.insert(
            bob,
            ContentValues().apply {
                put(KEY_USERNAME, BOB_NAME)
                put(KEY_COUNTER, BOB_COUNTER)
            },
        )

        assertProviderState(read(alice), ALICE_NAME, ALICE_COUNTER)
        assertProviderState(read(bob), BOB_NAME, BOB_COUNTER)

        assertEquals(1, dispatcher.delete(alice))

        val deletedAlice = read(alice)
        assertEquals("", deletedAlice.prefsUsername)
        assertEquals(0, deletedAlice.prefsCounter)
        assertNull(deletedAlice.databaseUsername)
        assertNull(deletedAlice.databaseCounter)
        assertNull(deletedAlice.fileValue)
        assertNull(deletedAlice.cacheValue)

        assertProviderState(read(bob), BOB_NAME, BOB_COUNTER)
    }

    private fun read(uri: Uri): ProviderSnapshot =
        requireNotNull(dispatcher.query(uri)).use { cursor ->
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
        private const val PACKAGE_NAME = "com.cloneapp.testapp"
        private const val PHYSICAL_AUTHORITY = "com.cloneapp.testapp.state"
        private const val KEY_USERNAME = "username"
        private const val KEY_COUNTER = "counter"

        private const val ALICE_USER_ID = 0
        private const val BOB_USER_ID = 1
        private const val ALICE_NAME = "alice-ca-route"
        private const val BOB_NAME = "bob-ca-route"
        private const val ALICE_COUNTER = 17
        private const val BOB_COUNTER = 57
    }
}
