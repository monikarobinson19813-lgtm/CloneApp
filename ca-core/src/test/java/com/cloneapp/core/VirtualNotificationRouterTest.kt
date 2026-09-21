package com.cloneapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VirtualNotificationRouterTest {
    @Test
    fun siblingInstancesReceiveDistinctStableNotificationNamespaces() {
        val alice = VirtualNotificationRouter.route(
            packageName = "com.cloneapp.testapp",
            virtualUserId = 1,
            instanceName = "Alice",
            guestNotificationId = 7,
            guestChannelId = "messages",
            guestTitle = "New message",
        )
        val bob = VirtualNotificationRouter.route(
            packageName = "com.cloneapp.testapp",
            virtualUserId = 2,
            instanceName = "Bob",
            guestNotificationId = 7,
            guestChannelId = "messages",
            guestTitle = "New message",
        )

        assertNotEquals(alice.translatedNotificationId, bob.translatedNotificationId)
        assertNotEquals(alice.translatedChannelId, bob.translatedChannelId)
        assertEquals("Alice — New message", alice.visibleTitle)
        assertEquals("Bob — New message", bob.visibleTitle)

        assertEquals(
            alice,
            VirtualNotificationRouter.route(
                packageName = "com.cloneapp.testapp",
                virtualUserId = 1,
                instanceName = "Alice",
                guestNotificationId = 7,
                guestChannelId = "messages",
                guestTitle = "New message",
            ),
        )
    }

    @Test
    fun blankGuestTitleStillIdentifiesInstance() {
        val route = VirtualNotificationRouter.route(
            packageName = "com.cloneapp.testapp",
            virtualUserId = 1,
            instanceName = "Alice",
            guestNotificationId = 1,
            guestChannelId = "default",
            guestTitle = "",
        )

        assertEquals("Alice", route.visibleTitle)
    }
}
