package com.cloneapp.ca

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.cloneapp.core.GuestApkRepository
import com.cloneapp.core.VirtualInstance
import com.cloneapp.core.VirtualPackageRegistry
import org.json.JSONObject

data class GuestLaunchResult(
    val instanceName: String,
    val virtualUserId: Int,
    val packageName: String,
    val launcherActivity: String,
    val sourceArtifactId: String,
    val sourceSha256: String,
    val stubPid: Int,
    val realUid: Int,
)

class GuestLaunchCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val artifacts = GuestApkRepository(appContext)
    private val virtualPackages = VirtualPackageRegistry(appContext)
    private val processHost = GuestProcessHost(appContext)
    private val diagnostics = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun launch(
        instance: VirtualInstance,
        callback: (Result<GuestLaunchResult>) -> Unit,
    ) {
        val artifact = artifacts.list()
            .firstOrNull { it.packageMetadata?.packageName == instance.basePackageName }

        if (artifact == null) {
            callback(
                Result.failure(
                    IllegalStateException(
                        "No imported APK metadata found for ${instance.basePackageName}"
                    )
                )
            )
            return
        }

        val metadata = artifact.packageMetadata
        if (metadata == null) {
            callback(Result.failure(IllegalStateException("Imported APK metadata is missing")))
            return
        }

        virtualPackages.register(metadata)
        val virtualUserKey = instance.virtualUserId.toString()
        if (virtualPackages.instance(metadata.packageName, virtualUserKey) == null) {
            virtualPackages.bindInstance(metadata.packageName, virtualUserKey)
        }

        val launcher = virtualPackages.launcherComponents(metadata.packageName).singleOrNull()
        if (launcher == null) {
            callback(
                Result.failure(
                    UnsupportedOperationException(
                        "No deterministic guest launcher component is available for ${metadata.packageName}"
                    )
                )
            )
            return
        }

        val component = ComponentName(metadata.packageName, launcher.name)
        val componentAvailable = runCatching {
            appContext.packageManager.getActivityInfo(
                component,
                PackageManager.ComponentInfoFlags.of(0),
            )
        }.isSuccess

        if (!componentAvailable) {
            callback(
                Result.failure(
                    UnsupportedOperationException(
                        "Guest launcher ${component.flattenToShortString()} is not executable by the current " +
                            "v0.1 milestone path. Full uninstalled-APK activity virtualization is not implemented yet."
                    )
                )
            )
            return
        }

        processHost.start(metadata.packageName, virtualUserKey) { processResult ->
            processResult
                .onFailure { callback(Result.failure(it)) }
                .onSuccess { process ->
                    val pid = process.pid
                    val uid = process.realUid
                    if (pid == null || uid == null) {
                        callback(Result.failure(IllegalStateException("Guest stub diagnostics are incomplete")))
                        return@onSuccess
                    }

                    val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                        this.component = component
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra(EXTRA_CA_INSTANCE_NAME, instance.displayName)
                        putExtra(EXTRA_CA_VIRTUAL_USER_ID, instance.virtualUserId)
                        putExtra(EXTRA_CA_SOURCE_ARTIFACT_ID, artifact.id)
                    }

                    runCatching {
                        appContext.startActivity(launchIntent)
                        GuestLaunchResult(
                            instanceName = instance.displayName,
                            virtualUserId = instance.virtualUserId,
                            packageName = metadata.packageName,
                            launcherActivity = launcher.name,
                            sourceArtifactId = artifact.id,
                            sourceSha256 = artifact.sha256,
                            stubPid = pid,
                            realUid = uid,
                        )
                    }.onSuccess { result ->
                        persist(result)
                        callback(Result.success(result))
                    }.onFailure { error ->
                        callback(Result.failure(error))
                    }
                }
        }
    }

    fun lastLaunch(instanceName: String): GuestLaunchResult? {
        val raw = diagnostics.getString(instanceName, null) ?: return null
        return runCatching {
            val item = JSONObject(raw)
            GuestLaunchResult(
                instanceName = item.getString("instanceName"),
                virtualUserId = item.getInt("virtualUserId"),
                packageName = item.getString("packageName"),
                launcherActivity = item.getString("launcherActivity"),
                sourceArtifactId = item.getString("sourceArtifactId"),
                sourceSha256 = item.getString("sourceSha256"),
                stubPid = item.getInt("stubPid"),
                realUid = item.getInt("realUid"),
            )
        }.getOrNull()
    }

    private fun persist(result: GuestLaunchResult) {
        val json = JSONObject().apply {
            put("instanceName", result.instanceName)
            put("virtualUserId", result.virtualUserId)
            put("packageName", result.packageName)
            put("launcherActivity", result.launcherActivity)
            put("sourceArtifactId", result.sourceArtifactId)
            put("sourceSha256", result.sourceSha256)
            put("stubPid", result.stubPid)
            put("realUid", result.realUid)
        }
        check(diagnostics.edit().putString(result.instanceName, json.toString()).commit()) {
            "Unable to persist guest launch diagnostics"
        }
    }

    companion object {
        const val EXTRA_CA_INSTANCE_NAME = "ca.instanceName"
        const val EXTRA_CA_VIRTUAL_USER_ID = "ca.virtualUserId"
        const val EXTRA_CA_SOURCE_ARTIFACT_ID = "ca.sourceArtifactId"

        private const val PREFS_NAME = "ca_guest_launch_diagnostics"
    }
}
