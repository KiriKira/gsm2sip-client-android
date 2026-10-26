package com.callagent.host.background

import org.junit.Assert.*
import org.junit.Test

class CoalescedSyncRequestsTest {
    @Test fun burstDuringFetchRetainsExactlyOneFollowUpAndCanRestart() {
        val queue = CoalescedSyncRequests()
        assertTrue(queue.offer())
        repeat(100) { assertFalse(queue.offer()) }
        assertTrue(queue.complete())
        assertFalse(queue.complete())
        assertTrue(queue.offer())
        assertFalse(queue.complete())
    }

    @Test fun hintDuringFollowUpGetsAnotherPassRatherThanBeingLost() {
        val queue = CoalescedSyncRequests()
        assertTrue(queue.offer())
        assertFalse(queue.offer())
        assertTrue(queue.complete())
        assertFalse(queue.offer())
        assertTrue(queue.complete())
        assertFalse(queue.complete())
    }

    @Test fun fallbackUsesSocketHealthAndBacksOffWithoutDependingOnPush() {
        assertEquals(300_000L, HostBackgroundPolicy.syncDelay(true, 5, false))
        assertEquals(120_000L, HostBackgroundPolicy.syncDelay(true, 0, false))
        assertEquals(300_000L, HostBackgroundPolicy.syncDelay(true, 0, false, stableNetwork = true))
        assertEquals(30_000L, HostBackgroundPolicy.syncDelay(false, 0, false, stableNetwork = true))
        assertEquals(120_000L, HostBackgroundPolicy.syncDelay(true, 0, true))
        assertEquals(30_000L, HostBackgroundPolicy.syncDelay(false, 0, true))
        assertEquals(60_000L, HostBackgroundPolicy.syncDelay(false, 1, true))
        assertEquals(300_000L, HostBackgroundPolicy.syncDelay(false, 100, true))
    }
}
