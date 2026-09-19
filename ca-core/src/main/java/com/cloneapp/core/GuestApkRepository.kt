package com.cloneapp.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class GuestApkRepository(private val context: Context) {
    private val importDir = File(context.filesDir, "guest-apks").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("ca_guest_apks", Context.MODE_PRIVATE)

    fun list(): List<GuestArtifact> {
        val raw = prefs.getString(KEY_ARTIFACTS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    add(array.getJSONObject(i).toArtifact())
                }
            }.sortedBy { it.importedAtEpochMs }
        }.getOrDefault(emptyList())
    }

    fun importFrom(uri: Uri): Result<GuestArtifact> = runCatching {
        val sourceName = resolveDisplayName(uri)
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: error("Unable to determine selected file name")

        require(ApkImportSupport.isApkFileName(sourceName)) {
            "Selected file is not an APK"
        }

        val temp = File.createTempFile("guest-import-", ".part", importDir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val prefix = ByteArray(4)
            var prefixCount = 0
            var sizeBytes = 0L

            val input = context.contentResolver.openInputStream(uri)
                ?: error("Unable to open selected APK")

            input.use { source ->
                FileOutputStream(temp).use { destination ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue

                        if (prefixCount < prefix.size) {
                            val copyCount = minOf(prefix.size - prefixCount, count)
                            System.arraycopy(buffer, 0, prefix, prefixCount, copyCount)
                            prefixCount += copyCount
                        }

                        digest.update(buffer, 0, count)
                        destination.write(buffer, 0, count)
                        sizeBytes += count
                    }
                }
            }

            require(sizeBytes > 0) { "Selected APK is empty" }
            require(prefixCount == prefix.size && ApkImportSupport.hasZipMagic(prefix)) {
                "Selected file is not a valid APK archive"
            }

            val sha256 = ApkImportSupport.toHex(digest.digest())
            list().firstOrNull { it.sha256 == sha256 && File(it.storedPath).isFile }?.let {
                temp.delete()
                return@runCatching it
            }

            val storedFile = File(importDir, "$sha256.apk")
            if (storedFile.exists()) {
                temp.delete()
            } else if (!temp.renameTo(storedFile)) {
                temp.copyTo(storedFile, overwrite = false)
                temp.delete()
            }

            val artifact = GuestArtifact(
                id = sha256,
                sourceDisplayName = sourceName,
                sourceUri = uri.toString(),
                storedPath = storedFile.absolutePath,
                sizeBytes = sizeBytes,
                sha256 = sha256,
                importedAtEpochMs = System.currentTimeMillis()
            )

            persist(list() + artifact)
            artifact
        } catch (t: Throwable) {
            temp.delete()
            throw t
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index < 0) null else cursor.getString(index)
        }
    }

    private fun persist(items: List<GuestArtifact>) {
        val array = JSONArray()
        items.distinctBy { it.sha256 }.forEach { artifact ->
            array.put(artifact.toJson())
        }
        prefs.edit().putString(KEY_ARTIFACTS, array.toString()).apply()
    }

    private fun GuestArtifact.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("sourceDisplayName", sourceDisplayName)
        put("sourceUri", sourceUri)
        put("storedPath", storedPath)
        put("sizeBytes", sizeBytes)
        put("sha256", sha256)
        put("importedAtEpochMs", importedAtEpochMs)
    }

    private fun JSONObject.toArtifact(): GuestArtifact = GuestArtifact(
        id = getString("id"),
        sourceDisplayName = getString("sourceDisplayName"),
        sourceUri = getString("sourceUri"),
        storedPath = getString("storedPath"),
        sizeBytes = getLong("sizeBytes"),
        sha256 = getString("sha256"),
        importedAtEpochMs = getLong("importedAtEpochMs")
    )

    private companion object {
        const val KEY_ARTIFACTS = "artifacts"
    }
}
