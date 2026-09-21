package com.cloneapp.core

/**
 * Deterministic CA-level notification translation for a guest virtual user.
 *
 * Guest notification identifiers and channel identifiers are namespaced by package and
 * virtual user so sibling instances cannot overwrite one another. The visible title is
 * also labelled with the instance name so the user can identify its source.
 */
object VirtualNotificationRouter {
    data class Route(
        val packageName: String,
        val virtualUserId: Int,
        val guestNotificationId: Int,
        val guestChannelId: String,
        val translatedNotificationId: Int,
        val translatedChannelId: String,
        val visibleTitle: String,
    )

    fun route(
        packageName: String,
        virtualUserId: Int,
        instanceName: String,
        guestNotificationId: Int,
        guestChannelId: String,
        guestTitle: String,
    ): Route {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(virtualUserId >= 0) { "virtualUserId must be non-negative" }
        require(instanceName.isNotBlank()) { "instanceName must not be blank" }
        require(guestChannelId.isNotBlank()) { "guestChannelId must not be blank" }

        val namespace = "$packageName|$virtualUserId"
        val translatedId = stableId("$namespace|notification|$guestNotificationId")
        val translatedChannel = "ca.v$virtualUserId.$packageName.$guestChannelId"
        val title = if (guestTitle.isBlank()) instanceName else "$instanceName — $guestTitle"

        return Route(
            packageName = packageName,
            virtualUserId = virtualUserId,
            guestNotificationId = guestNotificationId,
            guestChannelId = guestChannelId,
            translatedNotificationId = translatedId,
            translatedChannelId = translatedChannel,
            visibleTitle = title,
        )
    }

    private fun stableId(value: String): Int {
        var hash = 0x811c9dc5u
        value.encodeToByteArray().forEach { byte ->
            hash = (hash xor byte.toUByte().toUInt()) * 0x01000193u
        }
        return (hash and 0x7fffffffu).toInt()
    }
}
