package com.cloneapp.ca

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cloneapp.core.GuestApkRepository
import com.cloneapp.core.PrototypeInstanceRegistry
import org.junit.Assert.assertArrayEquals
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
        val registry = PrototypeInstanceRegistry(context)
        val sentinel = registry.create("com.cloneapp.registry-sentinel", "Registry Sentinel")

        try {
            val requestedMimeTypes = clickImportWithoutLeavingCloneApp(activity)
            assertPickerContract(requestedMimeTypes)

            deliverPickerResult(activity, FixtureApkProvider.validApkUri())

            val text = waitForTextContaining(activity, R.id.guestArtifactText, SOURCE_APK_NAME)
            assertTrue("Imported APK name missing from diagnostics: $text", text.contains(SOURCE_APK_NAME))
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

        val requestedMimeTypes = clickImportWithoutLeavingCloneApp(activity)
        assertPickerContract(requestedMimeTypes)

        deliverPickerResult(activity, FixtureApkProvider.invalidApkUri())

        val text = waitForTextContaining(activity, R.id.guestArtifactText, "Import failed:")
        assertTrue(
            "Invalid APK error was not visible: $text",
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
    }

    private fun clickImportWithoutLeavingCloneApp(activity: MainActivity): Array<String> {
        assertEquals(
            "CloneApp must own the Import Guest APK interaction",
            MainActivity::class.java.name,
            activity.componentName.className
        )

        var requestedMimeTypes: Array<String>? = null
        instrumentation.runOnMainSync {
            activity.apkPickerLauncher = { mimeTypes ->
                requestedMimeTypes = mimeTypes.copyOf()
            }
            val button = activity.findViewById<Button>(R.id.importApk)
                ?: error("Import Guest APK button not found")
            assertTrue("Import Guest APK button is not enabled", button.isEnabled)
            assertTrue("Import Guest APK button click was not handled", button.performClick())
        }

        assertNotNull(
            "Import Guest APK button did not request the picker contract",
            requestedMimeTypes,
        )
        return requireNotNull(requestedMimeTypes)
    }

    private fun assertPickerContract(requestedMimeTypes: Array<String>) {
        assertArrayEquals(
            "CloneApp picker MIME types drifted",
            arrayOf(
                "application/vnd.android.package-archive",
                "application/octet-stream",
                "application/zip",
            ),
            requestedMimeTypes,
        )

        val pickerIntent = ActivityResultContracts.OpenDocument()
            .createIntent(context, requestedMimeTypes)

        assertEquals(
            "Import Guest APK must use Android's document picker contract",
            Intent.ACTION_OPEN_DOCUMENT,
            pickerIntent.action,
        )
    }

    private fun deliverPickerResult(activity: MainActivity, uri: android.net.Uri) {
        // The real ACTION_OPEN_DOCUMENT flow returns a URI carrying a read grant.
        // Reproduce that security contract explicitly for the instrumentation fixture
        // instead of relying on provider export semantics that vary across Android builds.
        context.grantUriPermission(
            context.packageName,
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        instrumentation.runOnMainSync {
            activity.handleApkSelection(uri)
        }
    }

    private fun launchCloneApp(): MainActivity {
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
        return activity as MainActivity
    }

    private fun waitForTextContaining(
        activity: Activity,
        viewId: Int,
        needle: String,
    ): String {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        var latest = ""

        while (SystemClock.uptimeMillis() < deadline) {
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
        const val POLL_INTERVAL_MS = 100L
    }
}
