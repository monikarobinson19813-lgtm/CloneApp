package com.cloneapp.testapp

import android.content.Context
import android.content.ContextWrapper
import java.io.File

/**
 * Controlled v0.1 storage router for the CA Test App.
 *
 * The same APK binary uses the CA virtual-user id supplied at launch to route private storage
 * into a deterministic per-user namespace. This proves storage semantics for the controlled
 * probe; arbitrary third-party/native IO interception remains out of scope for Issue #6.
 */
class VirtualStorageContext(
    base: Context,
    private val virtualUserId: Int,
) : ContextWrapper(base) {

    val isVirtualized: Boolean
        get() = virtualUserId >= 0

    override fun getSharedPreferences(name: String, mode: Int) =
        if (!isVirtualized) {
            super.getSharedPreferences(name, mode)
        } else {
            super.getSharedPreferences(sharedPreferencesName(name), mode)
        }

    override fun getFilesDir(): File =
        if (!isVirtualized) {
            super.getFilesDir()
        } else {
            File(super.getFilesDir(), "ca-virtual/users/$virtualUserId/files")
                .apply { mkdirs() }
        }

    override fun getCacheDir(): File =
        if (!isVirtualized) {
            super.getCacheDir()
        } else {
            File(super.getCacheDir(), "ca-virtual/users/$virtualUserId/cache")
                .apply { mkdirs() }
        }

    override fun getDatabasePath(name: String): File =
        if (!isVirtualized) {
            super.getDatabasePath(name)
        } else {
            File(
                super.getFilesDir(),
                "ca-virtual/users/$virtualUserId/databases/$name",
            ).apply {
                parentFile?.mkdirs()
            }
        }

    fun sharedPreferencesName(name: String): String =
        if (!isVirtualized) name else "ca_virtual_u${virtualUserId}__$name"
}
