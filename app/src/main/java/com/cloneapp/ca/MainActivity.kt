package com.cloneapp.ca

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.cloneapp.core.GuestApkRepository
import com.cloneapp.core.InstanceStorage
import com.cloneapp.core.PrototypeInstanceRegistry

class MainActivity : AppCompatActivity() {
    private lateinit var registry: PrototypeInstanceRegistry
    private lateinit var storage: InstanceStorage
    private lateinit var guestApks: GuestApkRepository
    private lateinit var instancesText: TextView
    private lateinit var guestArtifactText: TextView
    private var importStatus: String? = null

    private val apkPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        handleApkSelection(uri)
    }

    internal var apkPickerLauncher: (Array<String>) -> Unit = { mimeTypes ->
        apkPicker.launch(mimeTypes)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        registry = PrototypeInstanceRegistry(this)
        storage = InstanceStorage(filesDir.resolve("virtual"))
        guestApks = GuestApkRepository(this)
        instancesText = findViewById(R.id.instancesText)
        guestArtifactText = findViewById(R.id.guestArtifactText)

        findViewById<Button>(R.id.importApk).setOnClickListener {
            apkPickerLauncher(APK_MIME_TYPES.copyOf())
        }

        findViewById<Button>(R.id.createAlice).setOnClickListener {
            createIfMissing("Alice")
        }
        findViewById<Button>(R.id.createBob).setOnClickListener {
            createIfMissing("Bob")
        }
        render()
    }

    internal fun handleApkSelection(uri: android.net.Uri?) {
        if (uri == null) {
            importStatus = "APK import cancelled"
            render()
            return
        }

        guestApks.importFrom(uri)
            .onSuccess { artifact ->
                importStatus = "Imported ${artifact.sourceDisplayName}"
                Toast.makeText(this, "APK imported", Toast.LENGTH_SHORT).show()
            }
            .onFailure { error ->
                importStatus = "Import failed: ${error.message ?: "unknown error"}"
                Toast.makeText(this, importStatus, Toast.LENGTH_LONG).show()
            }

        render()
    }

    private fun createIfMissing(name: String) {
        if (registry.list().none { it.displayName == name }) {
            val instance = registry.create("com.cloneapp.testapp", name)
            storage.rootFor(instance)
        }
        render()
    }

    private fun render() {
        renderImportedApks()

        val items = registry.list()
        instancesText.text = if (items.isEmpty()) {
            "No instances yet.\n\nNOTE: v0.1 does not yet launch guest APKs."
        } else {
            buildString {
                appendLine("Prototype registry:")
                items.forEach {
                    appendLine("• ${it.displayName}")
                    appendLine("  package=${it.basePackageName}")
                    appendLine("  vUser=${it.virtualUserId}")
                    appendLine("  root=${storage.rootFor(it).absolutePath}")
                }
                appendLine()
                append("Guest runtime: NOT IMPLEMENTED")
            }
        }
    }

    internal fun configuredApkMimeTypes(): Array<String> = APK_MIME_TYPES.copyOf()

    private companion object {
        val APK_MIME_TYPES = arrayOf(
            "application/vnd.android.package-archive",
            "application/octet-stream",
            "application/zip",
        )
    }

    private fun renderImportedApks() {
        val artifacts = guestApks.list()
        guestArtifactText.text = buildString {
            importStatus?.let {
                appendLine(it)
                appendLine()
            }

            if (artifacts.isEmpty()) {
                append("No guest APK imported yet.")
            } else {
                appendLine("Imported guest APKs: ${artifacts.size}")
                artifacts.forEach { artifact ->
                    appendLine("• ${artifact.sourceDisplayName}")
                    appendLine("  size=${artifact.sizeBytes} bytes")
                    appendLine("  sha256=${artifact.sha256}")
                    appendLine("  stored=${artifact.storedPath}")
                }
            }
        }
    }
}
