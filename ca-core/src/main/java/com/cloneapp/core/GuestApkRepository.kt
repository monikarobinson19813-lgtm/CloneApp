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
    private val packageParser = GuestPackageParser(context)

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
            list().firstOrNull { it.sha256 == sha256 && File(it.storedPath).isFile }?.let { existing ->
                temp.delete()
                if (existing.packageMetadata != null) {
                    return@runCatching existing
                }
                val enriched = existing.copy(
                    packageMetadata = packageParser.parse(File(existing.storedPath))
                        .getOrElse { error ->
                            throw IllegalArgumentException(
                                "Unable to parse APK metadata: ${error.message ?: "unknown error"}",
                                error,
                            )
                        }
                )
                persist(list().filterNot { it.id == existing.id } + enriched)
                return@runCatching enriched
            }

            val storedFile = File(importDir, "$sha256.apk")
            if (storedFile.exists()) {
                temp.delete()
            } else if (!temp.renameTo(storedFile)) {
                temp.copyTo(storedFile, overwrite = false)
                temp.delete()
            }

            val metadata = packageParser.parse(storedFile)
                .getOrElse { error ->
                    throw IllegalArgumentException(
                        "Unable to parse APK metadata: ${error.message ?: "unknown error"}",
                        error,
                    )
                }

            val artifact = GuestArtifact(
                id = sha256,
                sourceDisplayName = sourceName,
                sourceUri = uri.toString(),
                storedPath = storedFile.absolutePath,
                sizeBytes = sizeBytes,
                sha256 = sha256,
                importedAtEpochMs = System.currentTimeMillis(),
                packageMetadata = metadata,
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
        put("packageMetadata", packageMetadata?.toJson() ?: JSONObject.NULL)
    }

    private fun JSONObject.toArtifact(): GuestArtifact = GuestArtifact(
        id = getString("id"),
        sourceDisplayName = getString("sourceDisplayName"),
        sourceUri = getString("sourceUri"),
        storedPath = getString("storedPath"),
        sizeBytes = getLong("sizeBytes"),
        sha256 = getString("sha256"),
        importedAtEpochMs = getLong("importedAtEpochMs"),
        packageMetadata = optJSONObject("packageMetadata")?.toPackageMetadata(),
    )

    private fun GuestPackageMetadata.toJson(): JSONObject = JSONObject().apply {
        put("packageName", packageName)
        put("versionCode", versionCode)
        put("versionName", versionName ?: JSONObject.NULL)
        put("launcherActivity", launcherActivity ?: JSONObject.NULL)
        put("activities", activities.toJsonArray())
        put("services", services.toJsonArray())
        put("providers", providers.toJsonArray())
        put("receivers", receivers.toJsonArray())
        put("requestedPermissions", requestedPermissions.toJsonArray())
        put("nativeAbis", nativeAbis.toJsonArray())
        put("nativeLibraries", nativeLibraries.toJsonArray())
    }

    private fun JSONObject.toPackageMetadata(): GuestPackageMetadata = GuestPackageMetadata(
        packageName = getString("packageName"),
        versionCode = getLong("versionCode"),
        versionName = optString("versionName").takeIf { it.isNotBlank() && it != "null" },
        launcherActivity = optString("launcherActivity").takeIf { it.isNotBlank() && it != "null" },
        activities = getJSONArray("activities").toComponents(),
        services = getJSONArray("services").toComponents(),
        providers = getJSONArray("providers").toComponents(),
        receivers = getJSONArray("receivers").toComponents(),
        requestedPermissions = getJSONArray("requestedPermissions").toStrings(),
        nativeAbis = getJSONArray("nativeAbis").toStrings(),
        nativeLibraries = getJSONArray("nativeLibraries").toStrings(),
    )

    private fun List<GuestComponentMetadata>.toJsonArray(): JSONArray = JSONArray().also { array ->
        forEach { component ->
            array.put(JSONObject().apply {
                put("name", component.name)
                put("exported", component.exported)
                put("permission", component.permission ?: JSONObject.NULL)
                put("authorities", component.authorities.toJsonArray())
            })
        }
    }

    private fun List<String>.toJsonArray(): JSONArray = JSONArray().also { array ->
        forEach(array::put)
    }

    private fun JSONArray.toComponents(): List<GuestComponentMetadata> = buildList {
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            add(
                GuestComponentMetadata(
                    name = item.getString("name"),
                    exported = item.getBoolean("exported"),
                    permission = item.optString("permission")
                        .takeIf { it.isNotBlank() && it != "null" },
                    authorities = item.getJSONArray("authorities").toStrings(),
                )
            )
        }
    }

    private fun JSONArray.toStrings(): List<String> = buildList {
        for (index in 0 until length()) {
            add(getString(index))
        }
    }

    private companion object {
        const val KEY_ARTIFACTS = "artifacts"
    }
}
