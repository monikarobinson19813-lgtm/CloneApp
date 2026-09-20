package com.cloneapp.core

data class GuestComponentMetadata(
    val name: String,
    val exported: Boolean,
    val permission: String? = null,
    val authorities: List<String> = emptyList(),
)

data class GuestPackageMetadata(
    val packageName: String,
    val versionCode: Long,
    val versionName: String?,
    val launcherActivity: String?,
    val activities: List<GuestComponentMetadata>,
    val services: List<GuestComponentMetadata>,
    val providers: List<GuestComponentMetadata>,
    val receivers: List<GuestComponentMetadata>,
    val requestedPermissions: List<String>,
    val nativeAbis: List<String>,
    val nativeLibraries: List<String>,
)
