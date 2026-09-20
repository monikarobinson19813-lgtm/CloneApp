package com.cloneapp.ca

import android.content.Context
import android.os.SystemClock
import android.widget.Button
import androidx.test.core.app.ActivityScenario
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

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val coordinator = GuestLaunchCoordinator(context)

            tapLaunchButton(scenario, R.id.launchAlice)
            waitForForegroundPackage("com.cloneapp.testapp")
            val alice = registry.list().single { it.displayName == "Alice" }
            val aliceLaunch = waitForPersistedLaunch(coordinator, "Alice")
            assertEquals("Alice", aliceLaunch.instanceName)
            assertEquals(alice.virtualUserId, aliceLaunch.virtualUserId)

            stopGuestAndWaitForHost()

            tapLaunchButton(scenario, R.id.launchBob)
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

            stopGuestAndWaitForHost()
        } finally {
            scenario.close()
        }
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

    private fun tapLaunchButton(
        scenario: ActivityScenario<MainActivity>,
        resourceId: Int,
    ) {
        scenario.onActivity { activity ->
            val clicked = activity.findViewById<Button>(resourceId).performClick()
            check(clicked) { "Launch button $resourceId did not handle click" }
        }
    }

    private fun stopGuestAndWaitForHost() {
        device.executeShellCommand("am force-stop com.cloneapp.testapp")
        waitForForegroundPackage("com.cloneapp.ca")
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
        val deadline = SystemClock.uptimeMillis() + PACKAGE_APPEAR_TIMEOUT_MS
        var activityState = ""
        while (SystemClock.uptimeMillis() < deadline) {
            activityState = device.executeShellCommand(
                "dumpsys activity activities | grep -E 'topResumedActivity|ResumedActivity|mFocusedApp'"
            )
            if (
                activityState.lineSequence().any { line ->
                    line.contains("topResumedActivity=") &&
                        line.contains(" $packageName/") 
                }
            ) {
                return
            }
            SystemClock.sleep(100L)
        }

        throw AssertionError(
            "Package $packageName did not become top-resumed within " +
                "$PACKAGE_APPEAR_TIMEOUT_MS ms. Activity state:\n$activityState"
        )
    }

    companion object {
        private const val PACKAGE_APPEAR_TIMEOUT_MS = 8_000L
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
