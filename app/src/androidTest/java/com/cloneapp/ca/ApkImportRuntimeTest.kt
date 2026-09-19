package com.cloneapp.ca

import android.content.Context
import android.content.Intent
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
        launchCloneApp()

        val importButton = device.wait(
            Until.findObject(By.res(context.packageName, "importApk")),
            TIMEOUT_MS
        )
        assertNotNull("Import Guest APK button not found", importButton)
        importButton!!.click()

        val apk = waitForSourceApk()
        assertNotNull("CA Test App APK was not visible in Android document picker", apk)
        apk!!.click()

        val artifactText = device.wait(
            Until.findObject(By.res(context.packageName, "guestArtifactText")),
            TIMEOUT_MS
        )
        assertNotNull("Guest artifact diagnostics were not shown after import", artifactText)

        val text = artifactText!!.text.orEmpty()
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
        launchCloneApp()

        val artifactText = device.wait(
            Until.findObject(By.res(context.packageName, "guestArtifactText")),
            TIMEOUT_MS
        )
        assertNotNull("Guest artifact diagnostics missing after relaunch", artifactText)

        val text = artifactText!!.text.orEmpty()
        assertTrue("Imported APK record did not survive relaunch", text.contains(SOURCE_APK_NAME))
        assertTrue("Persisted SHA-256 missing after relaunch", text.contains("sha256="))

        val artifacts = GuestApkRepository(context).list()
        assertEquals("Persisted artifact record missing", 1, artifacts.size)
        assertTrue("Persisted private APK copy missing", File(artifacts.single().storedPath).isFile)
    }

    private fun launchCloneApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: error("CloneApp launch intent unavailable")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)

        val ready = device.wait(
            Until.hasObject(By.res(context.packageName, "importApk")),
            TIMEOUT_MS
        )
        assertTrue("CloneApp did not reach main dashboard", ready)
    }

    private fun waitForSourceApk(): UiObject2? {
        device.waitForIdle()

        device.wait(
            Until.findObject(By.text(SOURCE_APK_NAME)),
            SHORT_TIMEOUT_MS
        )?.let { return it }

        val drawer = device.findObject(By.descContains("Show roots"))
            ?: device.findObject(By.descContains("Open navigation drawer"))
            ?: device.findObject(By.descContains("Navigate up"))

        drawer?.click()

        val downloads = device.wait(
            Until.findObject(By.text("Downloads")),
            SHORT_TIMEOUT_MS
        )
        downloads?.click()

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
        const val SOURCE_APK_NAME = "CA-Test-App-debug.apk"
        const val TIMEOUT_MS = 15_000L
        const val SHORT_TIMEOUT_MS = 5_000L
    }
}
