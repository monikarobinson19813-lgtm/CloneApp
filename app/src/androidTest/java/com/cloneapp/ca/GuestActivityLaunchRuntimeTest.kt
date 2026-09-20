package com.cloneapp.ca

import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.cloneapp.core.PrototypeInstanceRegistry
import com.cloneapp.core.VirtualInstance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class GuestActivityLaunchRuntimeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun aliceAndBobLaunchSameImportedGuestWithDistinctVirtualIdentity() {
        clearLaunchState()
        val registry = PrototypeInstanceRegistry(context)
        registry.list().forEach { registry.delete(it.id) }

        val alice = registry.create("com.cloneapp.testapp", "Alice")
        val bob = registry.create("com.cloneapp.testapp", "Bob")
        val coordinator = GuestLaunchCoordinator(context)

        val aliceLaunch = launchAndAwait(coordinator, alice)
        waitForForegroundPackage("com.cloneapp.testapp")
        assertEquals("Alice", aliceLaunch.instanceName)
        assertEquals(alice.virtualUserId, aliceLaunch.virtualUserId)
        assertNotNull(coordinator.lastLaunch("Alice"))

        device.pressBack()
        waitForForegroundPackage("com.cloneapp.ca")

        val bobLaunch = launchAndAwait(coordinator, bob)
        waitForForegroundPackage("com.cloneapp.testapp")
        assertEquals("Bob", bobLaunch.instanceName)
        assertEquals(bob.virtualUserId, bobLaunch.virtualUserId)
        assertNotNull(coordinator.lastLaunch("Bob"))

        assertNotEquals(
            "Alice and Bob must keep distinct CA virtual-user IDs",
            aliceLaunch.virtualUserId,
            bobLaunch.virtualUserId,
        )
        assertEquals(
            "Both virtual users must launch from the same imported artifact",
            aliceLaunch.sourceArtifactId,
            bobLaunch.sourceArtifactId,
        )
        assertEquals(aliceLaunch.sourceSha256, bobLaunch.sourceSha256)
        assertEquals("com.cloneapp.testapp", aliceLaunch.packageName)
        assertEquals(
            "com.cloneapp.testapp.MainActivity",
            aliceLaunch.launcherActivity,
        )

        val unsupported = VirtualInstance(
            id = "unsupported",
            basePackageName = "com.cloneapp.notimported",
            displayName = "Unsupported",
            virtualUserId = 999,
            createdAtEpochMs = System.currentTimeMillis(),
        )
        val failure = launchResult(coordinator, unsupported)
        assertTrue("Unsupported launch path must fail explicitly", failure.isFailure)
        assertTrue(
            failure.exceptionOrNull()?.message.orEmpty()
                .contains("No imported APK metadata"),
        )

        device.pressBack()
    }

    private fun launchAndAwait(
        coordinator: GuestLaunchCoordinator,
        instance: VirtualInstance,
    ): GuestLaunchResult = launchResult(coordinator, instance).getOrThrow()

    private fun launchResult(
        coordinator: GuestLaunchCoordinator,
        instance: VirtualInstance,
    ): Result<GuestLaunchResult> {
        val latch = CountDownLatch(1)
        var result: Result<GuestLaunchResult>? = null
        coordinator.launch(instance) {
            result = it
            latch.countDown()
        }
        assertTrue(
            "Timed out waiting for guest launch for ${instance.displayName}",
            latch.await(10, TimeUnit.SECONDS),
        )
        return requireNotNull(result)
    }

    private fun waitForForegroundPackage(packageName: String) {
        val deadline = SystemClock.uptimeMillis() + 8_000L
        while (SystemClock.uptimeMillis() < deadline) {
            if (device.currentPackageName == packageName) return
            SystemClock.sleep(100L)
        }
        assertEquals(
            "Unexpected foreground package",
            packageName,
            device.currentPackageName,
        )
    }

    private fun clearLaunchState() {
        listOf(
            "ca_guest_launch_diagnostics",
            "ca_guest_process_host",
            "ca_virtual_package_registry",
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
    }
}
