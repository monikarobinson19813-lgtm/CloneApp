package com.cloneapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class InstanceStorageTest {
    @Test
    fun aliceAndBobReceiveSeparatePrivateStorageNamespaces() {
        val root = Files.createTempDirectory("ca-instance-storage").toFile()
        try {
            val storage = InstanceStorage(root)
            val alice = instance("alice", 0, "Alice")
            val bob = instance("bob", 1, "Bob")

            val aliceLayout = storage.layoutFor(alice)
            val bobLayout = storage.layoutFor(bob)

            assertNotEquals(aliceLayout.root.canonicalPath, bobLayout.root.canonicalPath)
            assertEquals(
                "users/0/com.cloneapp.testapp/data/shared_prefs",
                aliceLayout.sharedPreferences.relativeTo(root).invariantSeparatorsPath,
            )
            assertEquals(
                "users/1/com.cloneapp.testapp/data/databases",
                bobLayout.databases.relativeTo(root).invariantSeparatorsPath,
            )

            aliceLayout.sharedPreferences.resolve("test_state.xml").writeText("Alice|10")
            aliceLayout.databases.resolve("ca_test.db").writeText("alice-db")
            aliceLayout.files.resolve("state.txt").writeText("Alice|10")
            aliceLayout.cache.resolve("cache-marker.txt").writeText("Alice|10")

            bobLayout.sharedPreferences.resolve("test_state.xml").writeText("Bob|50")
            bobLayout.databases.resolve("ca_test.db").writeText("bob-db")
            bobLayout.files.resolve("state.txt").writeText("Bob|50")
            bobLayout.cache.resolve("cache-marker.txt").writeText("Bob|50")

            assertEquals("Alice|10", aliceLayout.files.resolve("state.txt").readText())
            assertEquals("Bob|50", bobLayout.files.resolve("state.txt").readText())
            assertEquals("alice-db", aliceLayout.databases.resolve("ca_test.db").readText())
            assertEquals("bob-db", bobLayout.databases.resolve("ca_test.db").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun deletingAlicePrivateDataLeavesBobUntouched() {
        val root = Files.createTempDirectory("ca-instance-storage-delete").toFile()
        try {
            val storage = InstanceStorage(root)
            val alice = instance("alice", 7, "Alice")
            val bob = instance("bob", 8, "Bob")
            val aliceLayout = storage.layoutFor(alice)
            val bobLayout = storage.layoutFor(bob)

            aliceLayout.files.resolve("state.txt").writeText("Alice|10")
            bobLayout.files.resolve("state.txt").writeText("Bob|50")

            assertTrue(storage.delete(alice))
            assertFalse(aliceLayout.root.exists())
            assertTrue(bobLayout.root.exists())
            assertEquals("Bob|50", bobLayout.files.resolve("state.txt").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun invalidPackageNameCannotEscapeStorageRoot() {
        val root = Files.createTempDirectory("ca-instance-storage-safe").toFile()
        try {
            val storage = InstanceStorage(root)
            val invalid = VirtualInstance(
                id = "invalid",
                basePackageName = "../escape",
                displayName = "Invalid",
                virtualUserId = 1,
                createdAtEpochMs = 1L,
            )

            val failure = runCatching { storage.layoutFor(invalid) }
            assertTrue(failure.isFailure)
            assertFalse(root.parentFile.resolve("escape").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun instance(id: String, virtualUserId: Int, name: String) = VirtualInstance(
        id = id,
        basePackageName = "com.cloneapp.testapp",
        displayName = name,
        virtualUserId = virtualUserId,
        createdAtEpochMs = 1L,
    )
}
