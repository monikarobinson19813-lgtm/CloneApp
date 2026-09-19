package com.cloneapp.core

data class GuestArtifact(
    val id: String,
    val sourceDisplayName: String,
    val sourceUri: String,
    val storedPath: String,
    val sizeBytes: Long,
    val sha256: String,
    val importedAtEpochMs: Long
)
