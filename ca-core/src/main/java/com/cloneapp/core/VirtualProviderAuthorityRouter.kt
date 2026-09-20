package com.cloneapp.core

/**
 * Deterministic CA-level provider authority routing for a guest virtual user.
 *
 * Android continues to own the guest's physical authority. CA uses a distinct virtual
 * authority per package/user pair and resolves it back to the physical guest authority
 * before controlled dispatch. This prevents Alice/Bob authority collisions without
 * rewriting the guest package.
 */
object VirtualProviderAuthorityRouter {
    private const val PREFIX = "ca.virtual"

    data class Route(
        val packageName: String,
        val virtualUserId: Int,
        val physicalAuthority: String,
        val virtualAuthority: String,
    )

    fun route(packageName: String, virtualUserId: Int, physicalAuthority: String): Route {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(virtualUserId >= 0) { "virtualUserId must be non-negative" }
        require(physicalAuthority.isNotBlank()) { "physicalAuthority must not be blank" }

        return Route(
            packageName = packageName,
            virtualUserId = virtualUserId,
            physicalAuthority = physicalAuthority,
            virtualAuthority = "$PREFIX.$packageName.v$virtualUserId.$physicalAuthority",
        )
    }

    fun resolve(virtualAuthority: String): Route? {
        if (!virtualAuthority.startsWith("$PREFIX.")) return null
        val marker = ".v"
        val markerIndex = virtualAuthority.indexOf(marker, PREFIX.length + 1)
        if (markerIndex < 0) return null

        val packageName = virtualAuthority.substring(PREFIX.length + 1, markerIndex)
        val remainder = virtualAuthority.substring(markerIndex + marker.length)
        val separator = remainder.indexOf('.')
        if (separator <= 0 || separator == remainder.lastIndex) return null

        val virtualUserId = remainder.substring(0, separator).toIntOrNull() ?: return null
        if (virtualUserId < 0) return null
        val physicalAuthority = remainder.substring(separator + 1)
        if (packageName.isBlank() || physicalAuthority.isBlank()) return null

        return Route(packageName, virtualUserId, physicalAuthority, virtualAuthority)
    }
}