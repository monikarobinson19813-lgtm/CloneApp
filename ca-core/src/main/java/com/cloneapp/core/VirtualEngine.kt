package com.cloneapp.core

/**
 * CA v0.1 contract. This intentionally describes capabilities before implementation.
 * A capability must not be reported as supported until a concrete engine passes its tests.
 */
interface VirtualEngine {
    fun createInstance(basePackageName: String, displayName: String): Result<VirtualInstance>
    fun listInstances(): List<VirtualInstance>
    fun deleteInstance(instanceId: String): Result<Unit>
    fun launchInstance(instanceId: String): Result<Unit>
    fun stopInstance(instanceId: String): Result<Unit>
    fun capabilityReport(): CapabilityReport
}

data class CapabilityReport(
    val guestApkLoading: Boolean = false,
    val packageManagerVirtualization: Boolean = false,
    val activityVirtualization: Boolean = false,
    val serviceVirtualization: Boolean = false,
    val providerVirtualization: Boolean = false,
    val filesystemIsolation: Boolean = false,
    val notificationTranslation: Boolean = false,
    val nativeLibraryLoading: Boolean = false,
    val rebootPersistence: Boolean = false
)
