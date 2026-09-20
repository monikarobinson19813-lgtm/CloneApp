package com.cloneapp.ca

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cloneapp.core.GuestComponentMetadata
import com.cloneapp.core.GuestPackageMetadata
import com.cloneapp.core.VirtualPackageRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VirtualPackageRegistryRuntimeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aliceAndBobShareBasePackageButKeepSeparateVirtualInstances() {
        clearRegistry()
        val metadata = fixtureMetadata()

        val registry = VirtualPackageRegistry(context)
        registry.register(metadata)
        val alice = registry.bindInstance(metadata.packageName, "Alice")
        val bob = registry.bindInstance(metadata.packageName, "Bob")

        assertEquals("Alice", alice.virtualUserId)
        assertEquals("Bob", bob.virtualUserId)
        assertEquals(metadata, registry.packageByName(metadata.packageName))
        assertEquals(
            listOf("Alice", "Bob"),
            registry.instancesFor(metadata.packageName).map { it.virtualUserId },
        )

        val launcher = registry.launcherComponents(metadata.packageName)
        assertEquals(1, launcher.size)
        assertEquals(metadata.launcherActivity, launcher.single().name)

        assertTrue(registry.deleteInstance(metadata.packageName, "Alice"))
        assertNull(registry.instance(metadata.packageName, "Alice"))
        assertNotNull(registry.instance(metadata.packageName, "Bob"))
        assertNotNull(
            "Deleting Alice must not unregister Bob's base package",
            registry.packageByName(metadata.packageName),
        )
        assertFalse(
            "Package with Bob still bound must not unregister",
            registry.unregister(metadata.packageName),
        )

        val restartedRegistry = VirtualPackageRegistry(context)
        assertNull(restartedRegistry.instance(metadata.packageName, "Alice"))
        assertNotNull(restartedRegistry.instance(metadata.packageName, "Bob"))
        assertEquals(metadata, restartedRegistry.packageByName(metadata.packageName))

        assertTrue(restartedRegistry.deleteInstance(metadata.packageName, "Bob"))
        assertTrue(restartedRegistry.unregister(metadata.packageName))
        assertNull(restartedRegistry.packageByName(metadata.packageName))
    }

    private fun fixtureMetadata(): GuestPackageMetadata = GuestPackageMetadata(
        packageName = "com.cloneapp.testapp",
        versionCode = 1L,
        versionName = "0.1",
        launcherActivity = "com.cloneapp.testapp.MainActivity",
        activities = listOf(
            GuestComponentMetadata(
                name = "com.cloneapp.testapp.MainActivity",
                exported = true,
            )
        ),
        services = emptyList(),
        providers = emptyList(),
        receivers = emptyList(),
        requestedPermissions = listOf("android.permission.POST_NOTIFICATIONS"),
        nativeAbis = emptyList(),
        nativeLibraries = emptyList(),
    )

    private fun clearRegistry() {
        context.getSharedPreferences("ca_virtual_package_registry", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
