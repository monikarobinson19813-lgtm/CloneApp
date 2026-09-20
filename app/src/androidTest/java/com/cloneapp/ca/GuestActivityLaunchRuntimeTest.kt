package com.cloneapp.ca

import android.content.Context
import android.os.SystemClock
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.cloneapp.core.GuestApkRepository
import com.cloneapp.core.PrototypeInstanceRegistry
import com.cloneapp.core.VirtualInstance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuestActivityLaunchRuntimeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun aliceAndBobLaunchSameImportedGuestWithDistinctVirtualIdentity() {
        clearLaunchState()
        ensureImportedGuest()
        val registry = PrototypeInstanceRegistry(context)
        registry.list().forEach { registry.delete(it.id) }

        startCloneApp()
        val coordinator = GuestLaunchCoordinator(context)

        tapLaunchButton(R.id.launchAlice)
        waitForForegroundPackage("com.cloneapp.testapp")
        val alice = registry.list().single { it.displayName == "Alice" }
        val aliceLaunch = waitForPersistedLaunch(coordinator, "Alice")
        assertEquals("Alice", aliceLaunch.instanceName)
        assertEquals(alice.virtualUserId, aliceLaunch.virtualUserId)

        device.pressBack()
        waitForForegroundPackage("com.cloneapp.ca")

        tapLaunchButton(R.id.launchBob)
        waitForForegroundPackage("com.cloneapp.testapp")
        val bob = registry.list().single { it.displayName == "Bob" }
        val bobLaunch = waitForPersistedLaunch(coordinator, "Bob")
        assertEquals("Bob", bobLaunch.instanceName)
        assertEquals(bob.virtualUserId, bobLaunch.virtualUserId)

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
        var unsupportedResult: Result<GuestLaunchResult>? = null
        coordinator.launch(unsupported) { unsupportedResult = it }
        val failure = requireNotNull(unsupportedResult)
        assertTrue("Unsupported launch path must fail explicitly", failure.isFailure)
        assertTrue(
            failure.exceptionOrNull()?.message.orEmpty()
                .contains("No imported APK metadata"),
        )

        device.pressBack()
    }

    private fun ensureImportedGuest() {
        val repository = GuestApkRepository(context)
        if (repository.list().any { it.packageMetadata?.packageName == "com.cloneapp.testapp" }) {
            return
        }

        val result = repository.importFrom(DebugFixtureApkProvider.validApkUri())
        assertTrue(
            "Controlled CA Test App fixture must import before guest launch acceptance: " +
                result.exceptionOrNull()?.message.orEmpty(),
            result.isSuccess,
        )
        assertEquals(
            "com.cloneapp.testapp",
            result.getOrThrow().packageMetadata?.packageName,
        )
    }

    private fun startCloneApp() {
        device.executeShellCommand("am start -W -n com.cloneapp.ca/.MainActivity")
        waitForForegroundPackage("com.cloneapp.ca")
    }

    private fun tapLaunchButton(resourceId: Int) {
        onView(withId(resourceId)).perform(scrollTo(), click())
    }

    private fun waitForPersistedLaunch(
        coordinator: GuestLaunchCoordinator,
        instanceName: String,
    ): GuestLaunchResult {
        val deadline = SystemClock.uptimeMillis() + 8_000L
        while (SystemClock.uptimeMillis() < deadline) {
            coordinator.lastLaunch(instanceName)?.let { return it }
            SystemClock.sleep(100L)
        }
        error("No persisted launch diagnostics for $instanceName")
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
