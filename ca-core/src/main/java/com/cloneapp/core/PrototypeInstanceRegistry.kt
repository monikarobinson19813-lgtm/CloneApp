package com.cloneapp.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Metadata-only registry used while the guest runtime is still unimplemented.
 */
class PrototypeInstanceRegistry(private val context: Context) {
    private val prefs = context.getSharedPreferences("ca_instance_registry", Context.MODE_PRIVATE)

    fun create(basePackageName: String, displayName: String): VirtualInstance {
        val all = list().toMutableList()
        val nextUserId = (all.maxOfOrNull { it.virtualUserId } ?: -1) + 1
        val item = VirtualInstance(
            id = UUID.randomUUID().toString(),
            basePackageName = basePackageName,
            displayName = displayName,
            virtualUserId = nextUserId,
            createdAtEpochMs = System.currentTimeMillis()
        )
        all += item
        save(all)
        return item
    }

    fun list(): List<VirtualInstance> {
        val raw = prefs.getString("instances", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(VirtualInstance(
                    id = o.getString("id"),
                    basePackageName = o.getString("basePackageName"),
                    displayName = o.getString("displayName"),
                    virtualUserId = o.getInt("virtualUserId"),
                    createdAtEpochMs = o.getLong("createdAtEpochMs")
                ))
            }
        }
    }

    fun delete(id: String): VirtualInstance? {
        val all = list().toMutableList()
        val found = all.firstOrNull { it.id == id } ?: return null
        all.remove(found)
        save(all)
        return found
    }

    private fun save(items: List<VirtualInstance>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("basePackageName", item.basePackageName)
                put("displayName", item.displayName)
                put("virtualUserId", item.virtualUserId)
                put("createdAtEpochMs", item.createdAtEpochMs)
            })
        }
        prefs.edit().putString("instances", arr.toString()).apply()
    }
}
