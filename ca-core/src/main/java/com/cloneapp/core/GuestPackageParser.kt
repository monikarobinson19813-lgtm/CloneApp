package com.cloneapp.core

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import java.util.zip.ZipFile

class GuestPackageParser(private val context: Context) {
    fun parse(apkFile: File): Result<GuestPackageMetadata> = runCatching {
        require(apkFile.isFile) { "APK file does not exist: ${apkFile.absolutePath}" }

        val flags = PackageManager.PackageInfoFlags.of(
            (
                PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_PROVIDERS or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PERMISSIONS or
                    PackageManager.GET_CONFIGURATIONS
                ).toLong()
        )
        val info = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: error("Android PackageManager could not parse APK metadata")

        val activities = info.activities.orEmpty().map {
            GuestComponentMetadata(
                name = it.name,
                exported = it.exported,
                permission = it.permission,
            )
        }.sortedBy { it.name }

        val installedLauncher = runCatching {
            context.packageManager.getLaunchIntentForPackage(info.packageName)
                ?.component
                ?.className
        }.getOrNull()

        // Public PackageInfo does not expose archive intent filters. For the
        // v0.1 controlled fixture there is one declared Activity, so this is a
        // deterministic fallback when the package is not installed.
        val launcherActivity = installedLauncher ?: activities.singleOrNull()?.name

        val nativeEntries = ZipFile(apkFile).use { zip ->
            zip.entries().asSequence()
                .map { it.name }
                .filter { it.startsWith("lib/") && it.endsWith(".so") }
                .toList()
        }

        GuestPackageMetadata(
            packageName = info.packageName,
            versionCode = info.longVersionCode,
            versionName = info.versionName,
            launcherActivity = launcherActivity,
            activities = activities,
            services = info.services.orEmpty().map {
                GuestComponentMetadata(it.name, it.exported, it.permission)
            }.sortedBy { it.name },
            providers = info.providers.orEmpty().map {
                GuestComponentMetadata(
                    name = it.name,
                    exported = it.exported,
                    permission = it.readPermission ?: it.writePermission,
                    authorities = it.authority.orEmpty()
                        .split(';')
                        .filter { authority -> authority.isNotBlank() }
                        .sorted(),
                )
            }.sortedBy { it.name },
            receivers = info.receivers.orEmpty().map {
                GuestComponentMetadata(it.name, it.exported, it.permission)
            }.sortedBy { it.name },
            requestedPermissions = info.requestedPermissions.orEmpty()
                .distinct()
                .sorted(),
            nativeAbis = nativeEntries.mapNotNull { entry ->
                entry.split('/').getOrNull(1)
            }.distinct().sorted(),
            nativeLibraries = nativeEntries.mapNotNull { entry ->
                entry.substringAfterLast('/').takeIf { it.isNotBlank() }
            }.distinct().sorted(),
        )
    }
}
