package com.cloneapp.ca

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import org.json.JSONArray
import org.json.JSONObject

enum class GuestProcessState {
    STARTING,
    RUNNING,
    STOPPED,
    DEAD,
    FAILED,
}

data class GuestProcessRecord(
    val packageName: String,
    val virtualUserId: String,
    val pid: Int?,
    val realUid: Int?,
    val processName: String?,
    val state: GuestProcessState,
    val updatedAtEpochMs: Long,
    val detail: String? = null,
)

class GuestProcessHost(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun start(
        packageName: String,
        virtualUserId: String,
        callback: (Result<GuestProcessRecord>) -> Unit,
    ) {
        if (packageName.isBlank() || virtualUserId.isBlank()) {
            callback(Result.failure(IllegalArgumentException("packageName and virtualUserId are required")))
            return
        }

        persist(
            GuestProcessRecord(
                packageName = packageName,
                virtualUserId = virtualUserId,
                pid = null,
                realUid = null,
                processName = null,
                state = GuestProcessState.STARTING,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        )

        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                val record = resultData?.toRecord()
                if (resultCode == GuestStubProcessService.RESULT_STARTED && record != null) {
                    persist(record)
                    callback(Result.success(record))
                    return
                }

                val message = resultData?.getString(GuestStubProcessService.EXTRA_DETAIL)
                    ?: "Stub process launch failed"
                val failed = GuestProcessRecord(
                    packageName = packageName,
                    virtualUserId = virtualUserId,
                    pid = null,
                    realUid = null,
                    processName = null,
                    state = GuestProcessState.FAILED,
                    updatedAtEpochMs = System.currentTimeMillis(),
                    detail = message,
                )
                persist(failed)
                callback(Result.failure(IllegalStateException(message)))
            }
        }

        val intent = Intent(appContext, GuestStubProcessService::class.java).apply {
            action = GuestStubProcessService.ACTION_START
            putExtra(GuestStubProcessService.EXTRA_PACKAGE_NAME, packageName)
            putExtra(GuestStubProcessService.EXTRA_VIRTUAL_USER_ID, virtualUserId)
            putExtra(GuestStubProcessService.EXTRA_RESULT_RECEIVER, receiver)
        }

        runCatching { appContext.startService(intent) }
            .onFailure { error ->
                val failed = GuestProcessRecord(
                    packageName = packageName,
                    virtualUserId = virtualUserId,
                    pid = null,
                    realUid = null,
                    processName = null,
                    state = GuestProcessState.FAILED,
                    updatedAtEpochMs = System.currentTimeMillis(),
                    detail = error.message,
                )
                persist(failed)
                callback(Result.failure(error))
            }
    }

    fun stop(packageName: String, virtualUserId: String) {
        appContext.startService(
            Intent(appContext, GuestStubProcessService::class.java).apply {
                action = GuestStubProcessService.ACTION_STOP
                putExtra(GuestStubProcessService.EXTRA_PACKAGE_NAME, packageName)
                putExtra(GuestStubProcessService.EXTRA_VIRTUAL_USER_ID, virtualUserId)
            }
        )
        records()
            .firstOrNull { it.packageName == packageName && it.virtualUserId == virtualUserId }
            ?.let {
                persist(
                    it.copy(
                        state = GuestProcessState.STOPPED,
                        updatedAtEpochMs = System.currentTimeMillis(),
                    )
                )
            }
    }

    fun records(): List<GuestProcessRecord> {
        val raw = prefs.getString(KEY_RECORDS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toRecord())
                }
            }.sortedWith(compareBy(GuestProcessRecord::packageName, GuestProcessRecord::virtualUserId))
        }.getOrDefault(emptyList())
    }

    fun reconcileLiveness(): List<GuestProcessRecord> {
        val livePids = (
            appContext.getSystemService(ActivityManager::class.java)
                .runningAppProcesses
                .orEmpty()
                .map { it.pid }
                .toSet()
            )

        records().forEach { record ->
            if (
                record.state == GuestProcessState.RUNNING &&
                record.pid != null &&
                record.pid !in livePids
            ) {
                persist(
                    record.copy(
                        state = GuestProcessState.DEAD,
                        updatedAtEpochMs = System.currentTimeMillis(),
                        detail = "Stub process PID is no longer running",
                    )
                )
            }
        }
        return records()
    }

    fun requestGuestExecution(
        packageName: String,
        virtualUserId: String,
    ): Result<Unit> = Result.failure(
        UnsupportedOperationException(
            "Guest execution is not implemented for $packageName / $virtualUserId"
        )
    )

    private fun persist(record: GuestProcessRecord) {
        val map = records()
            .associateBy { key(it.packageName, it.virtualUserId) }
            .toMutableMap()
        map[key(record.packageName, record.virtualUserId)] = record

        val array = JSONArray()
        map.toSortedMap().values.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("packageName", item.packageName)
                    put("virtualUserId", item.virtualUserId)
                    put("pid", item.pid ?: JSONObject.NULL)
                    put("realUid", item.realUid ?: JSONObject.NULL)
                    put("processName", item.processName ?: JSONObject.NULL)
                    put("state", item.state.name)
                    put("updatedAtEpochMs", item.updatedAtEpochMs)
                    put("detail", item.detail ?: JSONObject.NULL)
                }
            )
        }
        check(prefs.edit().putString(KEY_RECORDS, array.toString()).commit()) {
            "Unable to persist guest process lifecycle"
        }
    }

    private fun Bundle.toRecord(): GuestProcessRecord? {
        val packageName = getString(GuestStubProcessService.EXTRA_PACKAGE_NAME) ?: return null
        val virtualUserId = getString(GuestStubProcessService.EXTRA_VIRTUAL_USER_ID) ?: return null
        return GuestProcessRecord(
            packageName = packageName,
            virtualUserId = virtualUserId,
            pid = getInt(GuestStubProcessService.EXTRA_PID),
            realUid = getInt(GuestStubProcessService.EXTRA_REAL_UID),
            processName = getString(GuestStubProcessService.EXTRA_PROCESS_NAME),
            state = GuestProcessState.RUNNING,
            updatedAtEpochMs = System.currentTimeMillis(),
            detail = getString(GuestStubProcessService.EXTRA_DETAIL),
        )
    }

    private fun JSONObject.toRecord(): GuestProcessRecord = GuestProcessRecord(
        packageName = getString("packageName"),
        virtualUserId = getString("virtualUserId"),
        pid = if (isNull("pid")) null else getInt("pid"),
        realUid = if (isNull("realUid")) null else getInt("realUid"),
        processName = if (isNull("processName")) null else getString("processName"),
        state = GuestProcessState.valueOf(getString("state")),
        updatedAtEpochMs = getLong("updatedAtEpochMs"),
        detail = if (isNull("detail")) null else getString("detail"),
    )

    private fun key(packageName: String, virtualUserId: String): String =
        "$packageName\u0000$virtualUserId"

    private companion object {
        const val PREFS_NAME = "ca_guest_process_host"
        const val KEY_RECORDS = "records"
    }
}
