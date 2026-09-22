package com.cloneapp.testapp

import android.app.Service
import android.content.Intent
import android.os.IBinder

class ControlledStateService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
