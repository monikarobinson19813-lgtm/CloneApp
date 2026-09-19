package com.cloneapp.ca

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cloneapp.core.InstanceStorage
import com.cloneapp.core.PrototypeInstanceRegistry

class MainActivity : AppCompatActivity() {
    private lateinit var registry: PrototypeInstanceRegistry
    private lateinit var storage: InstanceStorage
    private lateinit var instancesText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        registry = PrototypeInstanceRegistry(this)
        storage = InstanceStorage(filesDir.resolve("virtual"))
        instancesText = findViewById(R.id.instancesText)

        findViewById<Button>(R.id.createAlice).setOnClickListener {
            createIfMissing("Alice")
        }
        findViewById<Button>(R.id.createBob).setOnClickListener {
            createIfMissing("Bob")
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
        val items = registry.list()
        instancesText.text = if (items.isEmpty()) {
            "No instances yet.\n\nNOTE: v0.1 scaffold does not yet launch guest APKs."
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
}
