package com.cloneapp.ca

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class GuestProcessHostRuntimeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aliceAndBobReceiveDistinctVirtualIdentityAndDeathIsBookkept() {
        clearProcessState()
        val host = GuestProcessHost(context)

        val alice = startAndAwait(host, "Alice")
        val bob = startAndAwait(host, "Bob")

        assertEquals("Alice", alice.virtualUserId)
        assertEquals("Bob", bob.virtualUserId)
        assertNotEquals(alice.virtualUserId, bob.virtualUserId)

        assertNotNull(alice.pid)
        assertNotNull(alice.realUid)
        assertTrue(
            "Stub must run in CA-owned guest process",
            alice.processName.orEmpty().endsWith(":gueststub"),
        )
        assertEquals(
            "Virtual users must not pretend to have separate Android UIDs",
            alice.realUid,
            bob.realUid,
        )
        assertEquals(
            "Alice and Bob share the same minimal stub process in this issue",
            alice.pid,
            bob.pid,
        )

        val restartedHost = GuestProcessHost(context)
        assertEquals(
            setOf("Alice", "Bob"),
            restartedHost.records()
                .filter { it.state == GuestProcessState.RUNNING }
                .map { it.virtualUserId }
                .toSet(),
        )

        val unsupported = restartedHost.requestGuestExecution("com.cloneapp.testapp", "Alice")
        assertTrue("Unsupported guest execution must fail safely", unsupported.isFailure)

        val stubPid = requireNotNull(alice.pid)
        assertNotEquals("Test process must not be the guest stub process", Process.myPid(), stubPid)
        Process.killProcess(stubPid)
        waitForPidToDisappear(stubPid)

        val reconciled = GuestProcessHost(context).reconcileLiveness()
        val aliceAfterDeath = reconciled.single {
            it.packageName == "com.cloneapp.testapp" && it.virtualUserId == "Alice"
        }
        val bobAfterDeath = reconciled.single {
            it.packageName == "com.cloneapp.testapp" && it.virtualUserId == "Bob"
        }
        assertEquals(GuestProcessState.DEAD, aliceAfterDeath.state)
        assertEquals(GuestProcessState.DEAD, bobAfterDeath.state)
    }

    private fun startAndAwait(host: GuestProcessHost, virtualUserId: String): GuestProcessRecord {
        val latch = CountDownLatch(1)
        var result: Result<GuestProcessRecord>? = null

        host.start("com.cloneapp.testapp", virtualUserId) {
            result = it
            latch.countDown()
        }

        assertTrue(
            "Timed out waiting for stub process launch for $virtualUserId",
            latch.await(10, TimeUnit.SECONDS),
        )
        return requireNotNull(result).getOrThrow()
    }

    private fun waitForPidToDisappear(pid: Int) {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val deadline = SystemClock.uptimeMillis() + 10_000L
        while (SystemClock.uptimeMillis() < deadline) {
            val running = activityManager.runningAppProcesses.orEmpty().any { it.pid == pid }
            if (!running) return
            SystemClock.sleep(100L)
        }
        error("Guest stub PID $pid did not terminate")
    }

    private fun clearProcessState() {
        context.getSharedPreferences("ca_guest_process_host", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
