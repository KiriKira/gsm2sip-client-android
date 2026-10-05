package com.callagent.host.calls

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SipNetworkRecoveryPolicyTest {
    @Test
    fun activeCallGetsThirtySecondNetworkLossGrace() {
        val policy = SipNetworkRecoveryPolicy()
        val loss = policy.networkLost("call-1", now)!!

        assertFalse(policy.shouldEndCall(loss, "call-1", now + 29_999L))
        assertTrue(policy.shouldEndCall(loss, "call-1", now + 30_000L))
    }

    @Test
    fun restoredNetworkInvalidatesThePendingLossTimeout() {
        val policy = SipNetworkRecoveryPolicy()
        val loss = policy.networkLost("call-1", now)!!

        policy.networkRestored()

        assertFalse(policy.shouldEndCall(loss, "call-1", now + 60_000L))
    }

    @Test
    fun staleTimeoutCannotEndAReplacementCallOrANewerLossWindow() {
        val policy = SipNetworkRecoveryPolicy()
        val oldLoss = policy.networkLost("call-1", now)!!
        assertFalse(policy.shouldEndCall(oldLoss, "call-2", now + 30_000L))

        val newerLoss = policy.networkLost("call-2", now + 1_000L)!!
        assertFalse(policy.shouldEndCall(oldLoss, "call-2", now + 30_000L))
        assertTrue(policy.shouldEndCall(newerLoss, "call-2", now + 31_000L))
    }

    @Test
    fun anIdleNetworkLossDoesNotScheduleCallTermination() {
        val policy = SipNetworkRecoveryPolicy()

        assertTrue(policy.networkLost(null, now) == null)
        val laterLoss = policy.networkLost("call-1", now + 60_000L)

        assertNotNull(laterLoss)
    }

    @Test
    fun registrationTransportAndTemporaryServerErrorsRemainRetryable() {
        listOf(null, 408, 480, 500, 503, 599).forEach { statusCode ->
            assertEquals(
                SipRegistrationFailureKind.RETRYABLE,
                SipRegistrationFailurePolicy.classify(statusCode)
            )
        }
    }

    @Test
    fun credentialRejectionIsTerminalAndOtherClientErrorsDoNotRetryForever() {
        assertEquals(
            SipRegistrationFailureKind.AUTHENTICATION_REJECTED,
            SipRegistrationFailurePolicy.classify(401)
        )
        assertEquals(
            SipRegistrationFailureKind.AUTHENTICATION_REJECTED,
            SipRegistrationFailurePolicy.classify(403)
        )
        assertEquals(
            SipRegistrationFailureKind.TERMINAL,
            SipRegistrationFailurePolicy.classify(404)
        )
    }

    private companion object {
        const val now = 123_000L
    }
}
