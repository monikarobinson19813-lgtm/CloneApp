package com.cloneapp.ca

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cloneapp.core.VirtualNotificationRouter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationTranslationRuntimeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun allowNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        manager.cancelAll()
        awaitNotifications { it.isEmpty() }
    }

    @After
    fun cleanup() {
        manager.cancelAll()
    }

    @Test
    fun aliceAndBobPostIndependentlyWithVisibleInstanceIdentityAndLifecycle() {
        val alice = VirtualNotificationRouter.route(
            packageName = "com.cloneapp.testapp",
            virtualUserId = 10,
            instanceName = "Alice",
            guestNotificationId = 7,
            guestChannelId = "messages",
            guestTitle = "New message",
        )
        val bob = VirtualNotificationRouter.route(
            packageName = "com.cloneapp.testapp",
            virtualUserId = 50,
            instanceName = "Bob",
            guestNotificationId = 7,
            guestChannelId = "messages",
            guestTitle = "New message",
        )

        assertNotEquals(alice.translatedNotificationId, bob.translatedNotificationId)
        assertNotEquals(alice.translatedChannelId, bob.translatedChannelId)

        post(alice)
        post(bob)

        val active = awaitNotifications { notifications ->
            val ids = notifications.map { it.id }.toSet()
            alice.translatedNotificationId in ids && bob.translatedNotificationId in ids
        }.associateBy { it.id }
        assertEquals(alice.visibleTitle, active.getValue(alice.translatedNotificationId).notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(bob.visibleTitle, active.getValue(bob.translatedNotificationId).notification.extras.getString(Notification.EXTRA_TITLE))

        manager.cancel(alice.translatedNotificationId)
        val remaining = awaitNotifications { notifications ->
            val ids = notifications.map { it.id }.toSet()
            alice.translatedNotificationId !in ids && bob.translatedNotificationId in ids
        }.map { it.id }
        assertTrue(alice.translatedNotificationId !in remaining)
        assertTrue(bob.translatedNotificationId in remaining)

        val aliceAfterRecreation = VirtualNotificationRouter.route(
            packageName = "com.cloneapp.testapp",
            virtualUserId = 10,
            instanceName = "Alice",
            guestNotificationId = 7,
            guestChannelId = "messages",
            guestTitle = "New message",
        )
        assertEquals(alice.translatedNotificationId, aliceAfterRecreation.translatedNotificationId)
        assertEquals(alice.translatedChannelId, aliceAfterRecreation.translatedChannelId)
    }

    private fun post(route: VirtualNotificationRouter.Route) {
        manager.createNotificationChannel(
            NotificationChannel(route.translatedChannelId, route.visibleTitle, NotificationManager.IMPORTANCE_DEFAULT),
        )
        val notification = Notification.Builder(context, route.translatedChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(route.visibleTitle)
            .setContentText("controlled guest notification")
            .build()
        manager.notify(route.translatedNotificationId, notification)
    }

    private fun awaitNotifications(
        timeoutMs: Long = 3_000,
        predicate: (Array<StatusBarNotification>) -> Boolean,
    ): Array<StatusBarNotification> {
        val deadline = System.currentTimeMillis() + timeoutMs
        var notifications = manager.activeNotifications
        while (!predicate(notifications) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
            notifications = manager.activeNotifications
        }
        assertTrue("notification state did not settle before timeout", predicate(notifications))
        return notifications
    }
}
