package com.cloneapp.testapp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

class MainActivity : AppCompatActivity() {
    private lateinit var username: EditText
    private lateinit var counterText: TextView
    private lateinit var diagnostics: TextView
    private var counter = 0

    private val storageContext by lazy {
        VirtualStorageContext(
            this,
            intent.getIntExtra(EXTRA_CA_VIRTUAL_USER_ID, -1),
        )
    }
    private val store by lazy { StorageStateStore(storageContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        username = findViewById(R.id.username)
        counterText = findViewById(R.id.counter)
        diagnostics = findViewById(R.id.diagnostics)

        val initialState = store.snapshot()
        username.setText(initialState.prefsUsername)
        counter = initialState.prefsCounter
        render()

        findViewById<Button>(R.id.increment).setOnClickListener {
            counter++
            render()
        }
        findViewById<Button>(R.id.save).setOnClickListener { saveAll() }
        findViewById<Button>(R.id.notify).setOnClickListener { postNotification() }
    }

    private fun saveAll() {
        val name = username.text.toString()
        store.save(name, counter)
        render()
    }

    private fun render() {
        counterText.text = "Counter: $counter"
        diagnostics.text = buildString {
            appendLine("package=$packageName")
            appendLine("uid=${Process.myUid()}")
            appendLine("pid=${Process.myPid()}")
            appendLine("vUser=${intent.getIntExtra(EXTRA_CA_VIRTUAL_USER_ID, -1)}")
            appendLine("virtualStorage=${storageContext.isVirtualized}")
            appendLine("files=${storageContext.filesDir.absolutePath}")
            appendLine("cache=${storageContext.cacheDir.absolutePath}")
            appendLine("db=${storageContext.getDatabasePath("ca_test.db").absolutePath}")
        }
    }

    private fun postNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        val channelId = "ca_test"
        nm.createNotificationChannel(NotificationChannel(channelId, "CA Test", NotificationManager.IMPORTANCE_DEFAULT))
        val text = "${username.text}: counter=$counter"
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("CA Test App")
            .setContentText(text)
            .build()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            nm.notify(1001, notification)
        } else if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        }
    }

    companion object {
        private const val EXTRA_CA_VIRTUAL_USER_ID = "ca.virtualUserId"
    }
}
