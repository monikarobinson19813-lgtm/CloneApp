package com.cloneapp.core

data class VirtualInstance(
    val id: String,
    val basePackageName: String,
    val displayName: String,
    val virtualUserId: Int,
    val createdAtEpochMs: Long
)
