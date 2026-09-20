package com.cloneapp.testapp

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageIsolationRuntimeTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aliceAndBobPrivateStorageAreIndependent() {
        val aliceContext = VirtualStorageContext(context, ALICE_USER_ID)
        val bobContext = VirtualStorageContext(context, BOB_USER_ID)
        val alice = StorageStateStore(aliceContext)
        val bob = StorageStateStore(bobContext)

        try {
            alice.deleteAll()
            bob.deleteAll()

            alice.save(ALICE_NAME, ALICE_COUNTER)
            bob.save(BOB_NAME, BOB_COUNTER)

            assertStoredState(alice.snapshot(), ALICE_NAME, ALICE_COUNTER)
            assertStoredState(bob.snapshot(), BOB_NAME, BOB_COUNTER)

            assertNotEquals(aliceContext.filesDir.absolutePath, bobContext.filesDir.absolutePath)
            assertNotEquals(aliceContext.cacheDir.absolutePath, bobContext.cacheDir.absolutePath)
            assertNotEquals(
                aliceContext.getDatabasePath(StorageStateStore.DB_NAME).absolutePath,
                bobContext.getDatabasePath(StorageStateStore.DB_NAME).absolutePath,
            )
            assertNotEquals(
                aliceContext.sharedPreferencesName("test_state"),
                bobContext.sharedPreferencesName("test_state"),
            )
        } finally {
            alice.close()
            bob.close()
        }
    }

    @Test
    fun stateSurvivesRestartAndDeletingAliceLeavesBobIntact() {
        var alice = StorageStateStore(VirtualStorageContext(context, ALICE_USER_ID))
        var bob = StorageStateStore(VirtualStorageContext(context, BOB_USER_ID))

        try {
            assertStoredState(alice.snapshot(), ALICE_NAME, ALICE_COUNTER)
            assertStoredState(bob.snapshot(), BOB_NAME, BOB_COUNTER)

            assertTrue("Alice virtual storage should delete cleanly", alice.deleteAll())
        } finally {
            alice.close()
            bob.close()
        }

        alice = StorageStateStore(VirtualStorageContext(context, ALICE_USER_ID))
        bob = StorageStateStore(VirtualStorageContext(context, BOB_USER_ID))

        try {
            val deletedAlice = alice.snapshot()
            assertEquals("", deletedAlice.prefsUsername)
            assertEquals(0, deletedAlice.prefsCounter)
            assertNull(deletedAlice.databaseUsername)
            assertNull(deletedAlice.databaseCounter)
            assertNull(deletedAlice.fileValue)
            assertNull(deletedAlice.cacheValue)

            assertStoredState(bob.snapshot(), BOB_NAME, BOB_COUNTER)
        } finally {
            alice.close()
            bob.close()
        }
    }

    private fun assertStoredState(
        snapshot: StorageSnapshot,
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

    companion object {
        private const val ALICE_USER_ID = 0
        private const val BOB_USER_ID = 1
        private const val ALICE_NAME = "alice"
        private const val BOB_NAME = "bob"
        private const val ALICE_COUNTER = 10
        private const val BOB_COUNTER = 50
    }
}
