package com.callagent.host.calls

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkRecoveryPolicyTest {
    @Test
    fun activeCallGetsThirtySecondNetworkLossGrace() {
        val policy = NetworkRecoveryPolicy()
        val loss = policy.networkLost("call-1", now)!!

        assertFalse(policy.shouldEndCall(loss, "call-1", now + 29_999L))
        assertTrue(policy.shouldEndCall(loss, "call-1", now + 30_000L))
    }

    @Test
    fun restoredNetworkInvalidatesThePendingLossTimeout() {
        val policy = NetworkRecoveryPolicy()
        val loss = policy.networkLost("call-1", now)!!

        policy.networkRestored()

        assertFalse(policy.shouldEndCall(loss, "call-1", now + 60_000L))
    }

    @Test
    fun staleTimeoutCannotEndAReplacementCallOrANewerLossWindow() {
        val policy = NetworkRecoveryPolicy()
        val oldLoss = policy.networkLost("call-1", now)!!
        assertFalse(policy.shouldEndCall(oldLoss, "call-2", now + 30_000L))

        val newerLoss = policy.networkLost("call-2", now + 1_000L)!!
        assertFalse(policy.shouldEndCall(oldLoss, "call-2", now + 30_000L))
        assertTrue(policy.shouldEndCall(newerLoss, "call-2", now + 31_000L))
    }

    @Test
    fun anIdleNetworkLossDoesNotScheduleCallTermination() {
        val policy = NetworkRecoveryPolicy()

        assertTrue(policy.networkLost(null, now) == null)
        val laterLoss = policy.networkLost("call-1", now + 60_000L)

        assertNotNull(laterLoss)
    }

    private companion object {
        const val now = 123_000L
    }
}
