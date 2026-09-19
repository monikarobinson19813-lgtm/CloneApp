package com.cloneapp.ca

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.SystemClock
import android.widget.Button
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cloneapp.core.GuestApkRepository
import com.cloneapp.core.PrototypeInstanceRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ApkImportRuntimeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun importApkThroughDocumentPickerContract() {
        clearImportedState()
        val activity = launchCloneApp()
        val monitor = interceptOpenDocument(FixtureApkProvider.validApkUri())
        val registry = PrototypeInstanceRegistry(context)
        val sentinel = registry.create("com.cloneapp.registry-sentinel", "Registry Sentinel")

        try {
            clickImport(activity)
            assertTrue(
                "Import button did not launch ACTION_OPEN_DOCUMENT",
                waitForMonitorHit(monitor)
            )

            val text = waitForTextContaining(activity, R.id.guestArtifactText, SOURCE_APK_NAME)
            assertTrue("Imported APK name missing from diagnostics", text.contains(SOURCE_APK_NAME))
            assertTrue("SHA-256 missing from diagnostics", text.contains("sha256="))
            assertTrue("Stored path missing from diagnostics", text.contains("stored="))

            val artifacts = GuestApkRepository(context).list()
            assertEquals("Exactly one imported artifact expected", 1, artifacts.size)
            val artifact = artifacts.single()
            assertEquals(SOURCE_APK_NAME, artifact.sourceDisplayName)
            assertTrue("Imported APK private copy missing", File(artifact.storedPath).isFile)
            assertEquals("Imported APK checksum must be SHA-256", 64, artifact.sha256.length)
            assertEquals(
                "Stored APK must be named by its deterministic checksum",
                "${artifact.sha256}.apk",
                File(artifact.storedPath).name
            )
            assertTrue(
                "Guest import must not disturb the existing instance registry",
                registry.list().any { it.id == sentinel.id }
            )
        } finally {
            registry.delete(sentinel.id)
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun importedApkRecordSurvivesRelaunch() {
        val activity = launchCloneApp()

        val text = waitForTextContaining(activity, R.id.guestArtifactText, SOURCE_APK_NAME)
        assertTrue("Imported APK record did not survive relaunch", text.contains(SOURCE_APK_NAME))
        assertTrue("Persisted SHA-256 missing after relaunch", text.contains("sha256="))

        val artifacts = GuestApkRepository(context).list()
        assertEquals("Persisted artifact record missing", 1, artifacts.size)
        assertTrue("Persisted private APK copy missing", File(artifacts.single().storedPath).isFile)
    }

    @Test
    fun invalidApkShowsVisibleErrorWithoutCrash() {
        clearImportedState()
        val activity = launchCloneApp()
        val monitor = interceptOpenDocument(FixtureApkProvider.invalidApkUri())

        try {
            clickImport(activity)
            assertTrue(
                "Import button did not launch ACTION_OPEN_DOCUMENT",
                waitForMonitorHit(monitor)
            )

            val text = waitForTextContaining(activity, R.id.guestArtifactText, "Import failed:")
            assertTrue(
                "Invalid APK error was not visible",
                text.contains("Selected file is not a valid APK archive")
            )
            assertTrue(
                "Invalid APK must not create a guest artifact",
                GuestApkRepository(context).list().isEmpty()
            )
            assertEquals(
                "CloneApp should remain alive after invalid import",
                MainActivity::class.java.name,
                activity.componentName.className
            )
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun interceptOpenDocument(uri: Uri): Instrumentation.ActivityMonitor {
        val resultIntent = Intent().apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val filter = IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            addDataType("*/*")
        }
        return instrumentation.addMonitor(
            filter,
            Instrumentation.ActivityResult(Activity.RESULT_OK, resultIntent),
            true
        )
    }

    private fun clickImport(activity: Activity) {
        val importButton = activity.findViewById<Button>(R.id.importApk)
        assertNotNull("Import Guest APK button not found", importButton)
        instrumentation.runOnMainSync {
            importButton.performClick()
        }
    }

    private fun waitForMonitorHit(monitor: Instrumentation.ActivityMonitor): Boolean {
        val deadline = SystemClock.uptimeMillis() + CONTRACT_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            if (monitor.hits > 0) {
                return true
            }
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        return monitor.hits > 0
    }

    private fun launchCloneApp(): Activity {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: error("CloneApp launch intent unavailable")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        val activity = instrumentation.startActivitySync(intent)
        instrumentation.waitForIdleSync()
        assertEquals(
            "CloneApp did not launch MainActivity",
            MainActivity::class.java.name,
            activity.componentName.className
        )
        return activity
    }

    private fun waitForTextContaining(
        activity: Activity,
        viewId: Int,
        needle: String,
    ): String {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        var latest = ""

        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                latest = activity.findViewById<TextView>(viewId)?.text?.toString().orEmpty()
            }
            if (latest.contains(needle)) {
                return latest
            }
            SystemClock.sleep(POLL_INTERVAL_MS)
        }

        return latest
    }

    private fun clearImportedState() {
        context.getSharedPreferences("ca_guest_apks", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        File(context.filesDir, "guest-apks").deleteRecursively()
    }

    private companion object {
        const val SOURCE_APK_NAME = "CA-Test-App-debug.apk"
        const val TIMEOUT_MS = 20_000L
        const val CONTRACT_TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 100L
    }
}
