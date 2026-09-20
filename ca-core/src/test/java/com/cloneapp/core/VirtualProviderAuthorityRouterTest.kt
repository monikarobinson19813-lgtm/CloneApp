package com.cloneapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VirtualProviderAuthorityRouterTest {
    @Test
    fun virtualAuthoritiesAreDistinctPerUserAndResolveToPhysicalAuthority() {
        val alice = VirtualProviderAuthorityRouter.route(
            packageName = "com.cloneapp.testapp",
            virtualUserId = 0,
            physicalAuthority = "com.cloneapp.testapp.state",
        )
        val bob = VirtualProviderAuthorityRouter.route(
            packageName = "com.cloneapp.testapp",
            virtualUserId = 1,
            physicalAuthority = "com.cloneapp.testapp.state",
        )

        assertNotEquals(alice.virtualAuthority, bob.virtualAuthority)
        assertEquals(alice, VirtualProviderAuthorityRouter.resolve(alice.virtualAuthority))
        assertEquals(bob, VirtualProviderAuthorityRouter.resolve(bob.virtualAuthority))
        assertEquals(alice.physicalAuthority, bob.physicalAuthority)
    }

    @Test
    fun unrelatedOrMalformedAuthoritiesAreNotResolved() {
        assertNull(VirtualProviderAuthorityRouter.resolve("com.cloneapp.testapp.state"))
        assertNull(VirtualProviderAuthorityRouter.resolve("ca.virtual.com.cloneapp.testapp.vx.state"))
        assertNull(VirtualProviderAuthorityRouter.resolve("ca.virtual.com.cloneapp.testapp.v-1.state"))
    }
}
