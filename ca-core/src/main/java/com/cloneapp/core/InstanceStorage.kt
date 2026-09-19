package com.cloneapp.core

import java.io.File

/**
 * Per-instance host-side storage layout for CA virtual users.
 * This is NOT sufficient guest-app isolation by itself; IO interception/redirection is still required.
 */
class InstanceStorage(private val root: File) {
    fun rootFor(instance: VirtualInstance): File =
        File(root, "users/${instance.virtualUserId}/${instance.basePackageName}").apply { mkdirs() }

    fun dataDir(instance: VirtualInstance): File = File(rootFor(instance), "data").apply { mkdirs() }
    fun cacheDir(instance: VirtualInstance): File = File(rootFor(instance), "cache").apply { mkdirs() }
    fun filesDir(instance: VirtualInstance): File = File(rootFor(instance), "files").apply { mkdirs() }

    fun delete(instance: VirtualInstance): Boolean = rootFor(instance).deleteRecursively()
}
