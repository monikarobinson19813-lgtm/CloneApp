package com.cloneapp.core

import java.io.File

/**
 * Deterministic host-side private-storage namespace for one CA virtual user.
 *
 * This layer defines where guest private state belongs. Runtime IO redirection still has to map
 * guest SharedPreferences / SQLite / files / cache calls into these paths before Issue #6 can be
 * accepted.
 */
class InstanceStorage(private val root: File) {
    data class Layout(
        val root: File,
        val data: File,
        val sharedPreferences: File,
        val databases: File,
        val files: File,
        val cache: File,
    )

    fun layoutFor(instance: VirtualInstance): Layout {
        require(instance.virtualUserId >= 0) { "virtualUserId must be non-negative" }
        require(instance.basePackageName.matches(PACKAGE_NAME)) {
            "Invalid guest package name: ${instance.basePackageName}"
        }

        val instanceRoot = File(
            root,
            "users/${instance.virtualUserId}/${instance.basePackageName}",
        ).safeChildOf(root)

        val data = File(instanceRoot, "data").apply { mkdirs() }
        val sharedPreferences = File(data, "shared_prefs").apply { mkdirs() }
        val databases = File(data, "databases").apply { mkdirs() }
        val files = File(data, "files").apply { mkdirs() }
        val cache = File(instanceRoot, "cache").apply { mkdirs() }

        return Layout(
            root = instanceRoot,
            data = data,
            sharedPreferences = sharedPreferences,
            databases = databases,
            files = files,
            cache = cache,
        )
    }

    fun rootFor(instance: VirtualInstance): File = layoutFor(instance).root
    fun dataDir(instance: VirtualInstance): File = layoutFor(instance).data
    fun sharedPreferencesDir(instance: VirtualInstance): File = layoutFor(instance).sharedPreferences
    fun databasesDir(instance: VirtualInstance): File = layoutFor(instance).databases
    fun filesDir(instance: VirtualInstance): File = layoutFor(instance).files
    fun cacheDir(instance: VirtualInstance): File = layoutFor(instance).cache

    /**
     * Removes only this virtual user's package-private namespace.
     * Sibling virtual users are never traversed.
     */
    fun delete(instance: VirtualInstance): Boolean {
        val instanceRoot = expectedRoot(instance)
        if (!instanceRoot.exists()) return true
        return instanceRoot.deleteRecursively()
    }

    private fun expectedRoot(instance: VirtualInstance): File {
        require(instance.virtualUserId >= 0) { "virtualUserId must be non-negative" }
        require(instance.basePackageName.matches(PACKAGE_NAME)) {
            "Invalid guest package name: ${instance.basePackageName}"
        }
        return File(
            root,
            "users/${instance.virtualUserId}/${instance.basePackageName}",
        ).safeChildOf(root)
    }

    private fun File.safeChildOf(parent: File): File {
        val parentPath = parent.canonicalFile.toPath()
        val childPath = canonicalFile.toPath()
        require(childPath.startsWith(parentPath)) {
            "Instance storage path escapes configured root"
        }
        return canonicalFile
    }

    companion object {
        private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    }
}
