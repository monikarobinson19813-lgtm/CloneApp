package com.cloneapp.ca

import android.app.Application
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.os.ResultReceiver

class GuestStubProcessService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        val virtualUserId = intent.getStringExtra(EXTRA_VIRTUAL_USER_ID).orEmpty()

        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val receiver = intent.resultReceiver()
        if (action != ACTION_START || packageName.isBlank() || virtualUserId.isBlank()) {
            receiver?.send(
                RESULT_ERROR,
                Bundle().apply {
                    putString(EXTRA_DETAIL, "Invalid stub process launch request")
                }
            )
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        receiver?.send(
            RESULT_STARTED,
            Bundle().apply {
                putString(EXTRA_PACKAGE_NAME, packageName)
                putString(EXTRA_VIRTUAL_USER_ID, virtualUserId)
                putInt(EXTRA_PID, Process.myPid())
                putInt(EXTRA_REAL_UID, Process.myUid())
                putString(EXTRA_PROCESS_NAME, Application.getProcessName())
                putString(
                    EXTRA_DETAIL,
                    "CA virtual user identity is logical; Android real UID is shared by design",
                )
            }
        )

        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun Intent.resultReceiver(): ResultReceiver? =
        if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(EXTRA_RESULT_RECEIVER, ResultReceiver::class.java)
        } else {
            getParcelableExtra(EXTRA_RESULT_RECEIVER)
        }

    companion object {
        const val ACTION_START = "com.cloneapp.ca.action.START_GUEST_STUB"
        const val ACTION_STOP = "com.cloneapp.ca.action.STOP_GUEST_STUB"

        const val EXTRA_PACKAGE_NAME = "packageName"
        const val EXTRA_VIRTUAL_USER_ID = "virtualUserId"
        const val EXTRA_RESULT_RECEIVER = "resultReceiver"
        const val EXTRA_PID = "pid"
        const val EXTRA_REAL_UID = "realUid"
        const val EXTRA_PROCESS_NAME = "processName"
        const val EXTRA_DETAIL = "detail"

        const val RESULT_STARTED = 1
        const val RESULT_ERROR = 2
    }
}
