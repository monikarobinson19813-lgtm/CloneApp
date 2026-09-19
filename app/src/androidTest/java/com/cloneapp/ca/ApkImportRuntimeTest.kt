package com.cloneapp.ca

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.Button
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.cloneapp.core.GuestApkRepository
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
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun importApkThroughDocumentPicker() {
        clearImportedState()
        val activity = launchCloneApp()

        val importButton = activity.findViewById<Button>(R.id.importApk)
        assertNotNull("Import Guest APK button not found", importButton)
        instrumentation.runOnMainSync {
            importButton.performClick()
        }

        val pickerOpened = device.wait(
            Until.hasObject(By.pkg(DOCUMENTS_UI_PACKAGE)),
            PICKER_START_TIMEOUT_MS
        )
        assertTrue("Android document picker did not open", pickerOpened)

        val apk = waitForSourceApk()
        assertNotNull("CA Test App APK was not visible in Android document picker", apk)
        apk!!.click()

        val text = waitForTextContaining(activity, R.id.guestArtifactText, SOURCE_APK_NAME)
        assertTrue("Imported APK name missing from diagnostics", text.contains(SOURCE_APK_NAME))
        assertTrue("SHA-256 missing from diagnostics", text.contains("sha256="))
        assertTrue("Stored path missing from diagnostics", text.contains("stored="))

        val artifacts = GuestApkRepository(context).list()
        assertEquals("Exactly one imported artifact expected", 1, artifacts.size)
        assertEquals(SOURCE_APK_NAME, artifacts.single().sourceDisplayName)
        assertTrue("Imported APK private copy missing", File(artifacts.single().storedPath).isFile)
        assertTrue("Imported APK checksum missing", artifacts.single().sha256.length == 64)
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

    private fun waitForSourceApk(): UiObject2? {
        device.waitForIdle()

        device.wait(
            Until.findObject(By.text(SOURCE_APK_NAME)),
            SHORT_TIMEOUT_MS
        )?.let { return it }

        val drawer = device.wait(
            Until.findObject(By.descContains("Show roots")),
            TIMEOUT_MS
        ) ?: device.findObject(By.descContains("Open navigation drawer"))
            ?: device.findObject(By.descContains("Navigate up"))

        assertNotNull("Android document picker navigation control not found", drawer)
        drawer!!.click()

        val downloads = device.wait(
            Until.findObject(By.text("Downloads")),
            TIMEOUT_MS
        )
        assertNotNull("Downloads root not found in Android document picker", downloads)
        downloads!!.click()

        device.waitForIdle()
        return device.wait(
            Until.findObject(By.text(SOURCE_APK_NAME)),
            TIMEOUT_MS
        )
    }

    private fun clearImportedState() {
        context.getSharedPreferences("ca_guest_apks", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        File(context.filesDir, "guest-apks").deleteRecursively()
    }

    private companion object {
        const val DOCUMENTS_UI_PACKAGE = "com.google.android.documentsui"
        const val SOURCE_APK_NAME = "CA-Test-App-debug.apk"
        const val TIMEOUT_MS = 20_000L
        const val PICKER_START_TIMEOUT_MS = 20_000L
        const val SHORT_TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 100L
    }
}
