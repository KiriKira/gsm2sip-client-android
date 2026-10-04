package com.callagent.host.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallSessionCoordinatorTest {
    @Test
    fun incomingInviteNeedsMatchingUnexpiredServerAuthority() {
        val coordinator = CallSessionCoordinator()
        val call = authority()

        assertFalse(coordinator.incomingInvite(invite(header = "other-call"), call, now))
        assertFalse(coordinator.incomingInvite(invite(), call.copy(expiresAtEpochMillis = now), now))
        assertFalse(coordinator.incomingInvite(invite(), call.copy(direction = "outgoing"), now))
        assertNull(coordinator.current)

        assertTrue(coordinator.incomingInvite(invite(), call, now))
        assertEquals(CallPhase.INCOMING_RINGING, coordinator.current?.phase)
    }

    @Test
    fun duplicateAnswerAndTerminalCallbacksAreIdempotent() {
        val coordinator = CallSessionCoordinator()
        assertTrue(coordinator.incomingInvite(invite(), authority(), now))

        assertTrue(coordinator.answer("call-1", now))
        assertFalse(coordinator.answer("call-1", now))
        assertTrue(coordinator.connected("call-1"))
        assertFalse(coordinator.connected("call-1"))
        assertTrue(coordinator.disconnect("call-1"))
        assertFalse(coordinator.disconnect("call-1"))
        assertTrue(coordinator.ended("call-1"))
        assertFalse(coordinator.ended("call-1"))
    }

    @Test
    fun remoteTerminalNoticeIsKeptForTheEndedCall() {
        val coordinator = CallSessionCoordinator()
        assertTrue(coordinator.incomingInvite(invite(), authority(), now))

        assertTrue(coordinator.ended("call-1", "Another host answered this call."))
        assertEquals("Another host answered this call.", coordinator.current?.endNotice)
        assertEquals(CallPhase.ENDED, coordinator.current?.phase)
        assertFalse(coordinator.ended("call-1", "A later snapshot must not replace the winner state."))
        assertEquals("Another host answered this call.", coordinator.current?.endNotice)
    }

    @Test
    fun lateTerminalNoticeCannotOverwriteANewerCall() {
        val coordinator = CallSessionCoordinator()
        assertTrue(coordinator.incomingInvite(invite(), authority(), now))
        assertTrue(coordinator.ended("call-1"))
        assertTrue(coordinator.clearTerminal("call-1"))
        assertTrue(coordinator.beginOutbound("sim-1", "+15551234567"))

        assertFalse(coordinator.updateEndedNotice("call-1", "Another host answered this call."))
        assertEquals("pending", coordinator.current?.callId)
        assertNull(coordinator.current?.endNotice)
    }

    @Test
    fun canceledCallCannotBeRecreatedByLateInvite() {
        val coordinator = CallSessionCoordinator()
        assertTrue(coordinator.incomingInvite(invite(), authority(), now))
        assertTrue(coordinator.failed("call-1", "server canceled"))
        assertTrue(coordinator.clearTerminal("call-1"))

        assertFalse(coordinator.incomingInvite(invite(), authority(), now))
        assertNull(coordinator.current)
    }

    @Test
    fun expiredIncomingCallCannotBeAnswered() {
        val coordinator = CallSessionCoordinator()
        assertTrue(coordinator.incomingInvite(invite(), authority(), now))

        assertFalse(coordinator.answer("call-1", now + 25_000))
        assertEquals(CallPhase.INCOMING_RINGING, coordinator.current?.phase)
    }

    @Test
    fun outboundCanOnlyProgressOnceForItsServerCallId() {
        val coordinator = CallSessionCoordinator()
        assertTrue(coordinator.beginOutbound("sim-1", "+15551234567"))
        assertFalse(coordinator.beginOutbound("sim-2", "+15557654321"))
        assertTrue(coordinator.outboundAuthorized("out-1", now + 30_000, now))
        assertFalse(coordinator.outboundAuthorized("out-2", now + 30_000, now))
        assertTrue(coordinator.registrationReady("out-1"))
        assertFalse(coordinator.registrationReady("out-1"))
        assertTrue(coordinator.outboundRinging("out-1"))
        assertTrue(coordinator.connected("out-1"))
        assertEquals(CallPhase.ACTIVE, coordinator.current?.phase)
    }

    private fun authority() = CallAuthority(
        callId = "call-1",
        direction = "incoming",
        state = "ringing",
        simId = "sim-1",
        remoteNumber = "+15550000000",
        expiresAtEpochMillis = now + 25_000
    )

    private fun invite(header: String? = "call-1") = IncomingInviteIdentity(
        sipCallId = "sip-dialog-abc",
        serverCallIdHeader = header
    )

    private companion object {
        const val now = 1_800_000_000_000L
    }
}
