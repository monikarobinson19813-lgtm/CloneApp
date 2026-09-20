package com.cloneapp.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** CA-owned metadata/instance registry; it does not represent Android package installation. */
class VirtualPackageRegistry(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun register(metadata: GuestPackageMetadata): GuestPackageMetadata {
        val state = readState()
        state.packages[metadata.packageName] = metadata
        persist(state)
        return metadata
    }

    @Synchronized
    fun packageByName(packageName: String): GuestPackageMetadata? = readState().packages[packageName]

    @Synchronized
    fun launcherComponents(packageName: String): List<GuestComponentMetadata> {
        val metadata = packageByName(packageName) ?: return emptyList()
        val launcher = metadata.launcherActivity ?: return emptyList()
        return metadata.activities.filter { it.name == launcher }
    }

    @Synchronized
    fun bindInstance(packageName: String, virtualUserId: String): VirtualPackageInstance {
        require(virtualUserId.isNotBlank()) { "virtualUserId must not be blank" }
        val state = readState()
        require(state.packages.containsKey(packageName)) { "Package is not registered: $packageName" }
        val instance = VirtualPackageInstance(packageName, virtualUserId)
        state.instances[instance.key] = instance
        persist(state)
        return instance
    }

    @Synchronized
    fun instance(packageName: String, virtualUserId: String): VirtualPackageInstance? =
        readState().instances[VirtualPackageInstance.key(packageName, virtualUserId)]

    @Synchronized
    fun instancesFor(packageName: String): List<VirtualPackageInstance> =
        readState().instances.values.filter { it.packageName == packageName }.sortedBy { it.virtualUserId }

    @Synchronized
    fun deleteInstance(packageName: String, virtualUserId: String): Boolean {
        val state = readState()
        val removed = state.instances.remove(VirtualPackageInstance.key(packageName, virtualUserId)) != null
        if (removed) persist(state)
        return removed
    }

    @Synchronized
    fun unregister(packageName: String): Boolean {
        val state = readState()
        if (state.instances.values.any { it.packageName == packageName }) return false
        val removed = state.packages.remove(packageName) != null
        if (removed) persist(state)
        return removed
    }

    private fun readState(): RegistryState {
        val raw = prefs.getString(KEY_STATE, null) ?: return RegistryState()
        return runCatching {
            val root = JSONObject(raw)
            val packages = linkedMapOf<String, GuestPackageMetadata>()
            val packageArray = root.optJSONArray("packages") ?: JSONArray()
            for (index in 0 until packageArray.length()) {
                val metadata = packageArray.getJSONObject(index).toPackageMetadata()
                packages[metadata.packageName] = metadata
            }
            val instances = linkedMapOf<String, VirtualPackageInstance>()
            val instanceArray = root.optJSONArray("instances") ?: JSONArray()
            for (index in 0 until instanceArray.length()) {
                val item = instanceArray.getJSONObject(index)
                val instance = VirtualPackageInstance(item.getString("packageName"), item.getString("virtualUserId"))
                if (packages.containsKey(instance.packageName)) instances[instance.key] = instance
            }
            RegistryState(packages, instances)
        }.getOrDefault(RegistryState())
    }

    private fun persist(state: RegistryState) {
        val root = JSONObject()
        val packages = JSONArray()
        state.packages.toSortedMap().values.forEach { packages.put(it.toJson()) }
        val instances = JSONArray()
        state.instances.toSortedMap().values.forEach { instance ->
            instances.put(JSONObject().apply {
                put("packageName", instance.packageName)
                put("virtualUserId", instance.virtualUserId)
            })
        }
        root.put("packages", packages)
        root.put("instances", instances)
        check(prefs.edit().putString(KEY_STATE, root.toString()).commit()) { "Unable to persist virtual package registry" }
    }

    private fun GuestPackageMetadata.toJson(): JSONObject = JSONObject().apply {
        put("packageName", packageName)
        put("versionCode", versionCode)
        put("versionName", versionName ?: JSONObject.NULL)
        put("launcherActivity", launcherActivity ?: JSONObject.NULL)
        put("activities", activities.toComponentJsonArray())
        put("services", services.toComponentJsonArray())
        put("providers", providers.toComponentJsonArray())
        put("receivers", receivers.toComponentJsonArray())
        put("requestedPermissions", requestedPermissions.toStringJsonArray())
        put("nativeAbis", nativeAbis.toStringJsonArray())
        put("nativeLibraries", nativeLibraries.toStringJsonArray())
    }

    private fun JSONObject.toPackageMetadata(): GuestPackageMetadata = GuestPackageMetadata(
        packageName = getString("packageName"),
        versionCode = getLong("versionCode"),
        versionName = nullableString("versionName"),
        launcherActivity = nullableString("launcherActivity"),
        activities = getJSONArray("activities").toComponents(),
        services = getJSONArray("services").toComponents(),
        providers = getJSONArray("providers").toComponents(),
        receivers = getJSONArray("receivers").toComponents(),
        requestedPermissions = getJSONArray("requestedPermissions").toStrings(),
        nativeAbis = getJSONArray("nativeAbis").toStrings(),
        nativeLibraries = getJSONArray("nativeLibraries").toStrings(),
    )

    private fun List<GuestComponentMetadata>.toComponentJsonArray(): JSONArray = JSONArray().also { array ->
        forEach { component ->
            array.put(JSONObject().apply {
                put("name", component.name)
                put("exported", component.exported)
                put("permission", component.permission ?: JSONObject.NULL)
                put("authorities", component.authorities.toStringJsonArray())
            })
        }
    }

    private fun List<String>.toStringJsonArray(): JSONArray = JSONArray().also { array -> forEach(array::put) }

    private fun JSONArray.toComponents(): List<GuestComponentMetadata> = buildList {
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            add(GuestComponentMetadata(
                name = item.getString("name"),
                exported = item.getBoolean("exported"),
                permission = item.nullableString("permission"),
                authorities = item.getJSONArray("authorities").toStrings(),
            ))
        }
    }

    private fun JSONArray.toStrings(): List<String> = buildList {
        for (index in 0 until length()) add(getString(index))
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private data class RegistryState(
        val packages: MutableMap<String, GuestPackageMetadata> = linkedMapOf(),
        val instances: MutableMap<String, VirtualPackageInstance> = linkedMapOf(),
    )

    private companion object {
        const val PREFS_NAME = "ca_virtual_package_registry"
        const val KEY_STATE = "state"
    }
}

data class VirtualPackageInstance(val packageName: String, val virtualUserId: String) {
    val key: String get() = key(packageName, virtualUserId)

    companion object {
        fun key(packageName: String, virtualUserId: String): String = "$packageName\u0000$virtualUserId"
    }
}
