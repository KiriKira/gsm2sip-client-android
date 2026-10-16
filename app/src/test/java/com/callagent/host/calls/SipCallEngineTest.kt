package com.callagent.host.calls

import com.callagent.host.data.SipConfiguration
import com.callagent.host.sip.SipAudioRoute
import com.callagent.host.sip.SipCallDirection
import com.callagent.host.sip.SipCallSnapshot
import com.callagent.host.sip.SipCallState
import com.callagent.host.sip.SipEngine
import com.callagent.host.sip.SipRegistrationState
import org.junit.Assert.*
import org.junit.Test

class SipCallEngineTest {
    @Test
    fun signalingReadinessDoesNotConnectTheCallOrOpenAudio() {
        val sip = FakeSipEngine()
        val engine = SipCallEngine(sip)
        val listener = RecordingListener()
        val coordinator = CallSessionCoordinator()
        assertTrue(coordinator.beginOutbound("sim-1", "+15551234567"))
        assertTrue(coordinator.outboundAuthorized("call-1", 30_000, 0))
        engine.start(SipConfiguration(available = true), listener)
        engine.connect()

        for (state in SipRegistrationState.entries) sip.listener.onRegistration(state, null, null)

        assertEquals(listOf(EngineConnectionState.STOPPED, EngineConnectionState.CONNECTING,
            EngineConnectionState.READY, EngineConnectionState.FAILED), listener.connections)
        assertEquals(CallPhase.REGISTERING, coordinator.current?.phase)
        assertTrue(listener.calls.isEmpty())
        assertEquals(listOf("register"), sip.operations)

        engine.enableAudio("call-1")
        engine.suspendAudio("call-1")
        assertEquals(listOf("register", "enable:call-1", "suspend:call-1"), sip.operations)
    }

    @Test
    fun incomingAuthorityCannotBeReplacedByTheTransportIdentifier() {
        val sip = FakeSipEngine()
        val engine = SipCallEngine(sip)
        val listener = RecordingListener()
        engine.start(SipConfiguration(available = true), listener)
        sip.listener.onCallState(SipCallSnapshot(
            callId = "engine-operation", direction = SipCallDirection.INBOUND,
            state = SipCallState.INCOMING, remoteUri = "sip:caller@example.test",
            dialogCallId = "transport-dialog", serverCallIdHeader = "server-call"
        ))
        val event = listener.calls.single()
        assertEquals("engine-operation", event.callId)
        assertEquals("transport-dialog", event.transportCallId)
        assertEquals(EngineCallDirection.INBOUND, event.direction)
        val coordinator = CallSessionCoordinator()
        val authority = CallAuthority("server-call", "incoming", "ringing", "sim-1", "+15551234567", 30_000)
        assertFalse(coordinator.incomingInvite(
            IncomingInviteIdentity(event.transportCallId!!, event.callId), authority, 0))
        assertTrue(coordinator.incomingInvite(
            IncomingInviteIdentity(event.transportCallId, event.serverCallId), authority, 0))
        assertEquals("server-call", coordinator.current?.callId)
        assertTrue(sip.operations.isEmpty())
    }

    @Test
    fun onlyConfirmedCallEventsCanAdvanceAnAuthorizedCallToActive() {
        val sip = FakeSipEngine()
        val coordinator = CallSessionCoordinator()
        coordinator.beginOutbound("sim-1", "+15551234567")
        coordinator.outboundAuthorized("call-1", 30_000, 0)
        coordinator.registrationReady("call-1")
        val events = RecordingListener { event ->
            when (event.state) {
                EngineCallState.RINGING, EngineCallState.EARLY -> coordinator.outboundRinging(event.callId)
                EngineCallState.CONNECTED -> coordinator.connected(event.callId)
                EngineCallState.DISCONNECTED -> coordinator.ended(event.callId)
                else -> Unit
            }
        }
        val engine = SipCallEngine(sip)
        engine.start(SipConfiguration(available = true), events)
        engine.dial("call-1", "sips:call.authorization@example.test")
        val snapshot = SipCallSnapshot("call-1", SipCallDirection.OUTBOUND, SipCallState.EARLY, "sip:remote")
        sip.listener.onCallState(snapshot)
        assertEquals(CallPhase.OUTBOUND_RINGING, coordinator.current?.phase)
        sip.listener.onCallState(snapshot.copy(state = SipCallState.CONFIRMED))
        assertEquals(CallPhase.ACTIVE, coordinator.current?.phase)
        sip.listener.onCallState(snapshot.copy(state = SipCallState.DISCONNECTED))
        assertEquals(CallPhase.ENDED, coordinator.current?.phase)
        // A delayed native confirmation cannot revive a terminal call.
        sip.listener.onCallState(snapshot.copy(state = SipCallState.CONFIRMED))
        assertEquals(CallPhase.ENDED, coordinator.current?.phase)
        assertEquals(listOf("dial:call-1:sips:call.authorization@example.test"), sip.operations)
    }

    @Test
    fun expiredAndDeclinedCallsKeepTheirExistingSipResponsesAndOperationKeys() {
        val sip = FakeSipEngine()
        val engine: CallEngine = SipCallEngine(sip)
        engine.reject("incoming-engine-id")
        engine.reject("expired-engine-id", CallRejection.EXPIRED)
        assertEquals(listOf("reject:incoming-engine-id:603", "reject:expired-engine-id:408"), sip.operations)
    }

    @Test
    fun failedStartupReleasesPartialEngineAndPreservesTheOriginalFailure() {
        val original = IllegalStateException("native startup failed")
        val sip = FakeSipEngine().apply {
            startFailure = original
            closeFailure = IllegalStateException("cleanup also failed")
        }
        try {
            SipCallEngine(sip).start(SipConfiguration(available = true), RecordingListener())
            fail("startup must fail")
        } catch (failure: IllegalStateException) {
            assertSame(original, failure)
        }
        assertEquals(listOf("close"), sip.operations)
    }

    @Test
    fun failureDetailsAndErrorsRemainAvailableToTheExistingServicePolicy() {
        val sip = FakeSipEngine()
        val listener = RecordingListener()
        SipCallEngine(sip).start(SipConfiguration(available = true), listener)
        sip.listener.onCallState(SipCallSnapshot("call-1", SipCallDirection.OUTBOUND,
            SipCallState.FAILED, "sip:remote", terminalStatusCode = 486, terminalReason = "Busy"))
        val event = listener.calls.single()
        assertEquals(EngineCallState.FAILED, event.state)
        assertEquals(EngineCallDirection.OUTBOUND, event.direction)
        assertEquals(486, event.terminalStatusCode)
        assertEquals("Busy", event.terminalReason)
        val failure = IllegalStateException("transport stopped")
        sip.listener.onEngineError(failure)
        assertSame(failure, listener.error)
    }

    private class RecordingListener(private val onCall: (EngineCallSnapshot) -> Unit = {}) : CallEngine.Listener {
        val connections = mutableListOf<EngineConnectionState>()
        val calls = mutableListOf<EngineCallSnapshot>()
        var error: Throwable? = null
        override fun onConnectionState(state: EngineConnectionState, statusCode: Int?, reason: String?) {
            connections += state
        }
        override fun onCallState(snapshot: EngineCallSnapshot) {
            calls += snapshot
            onCall(snapshot)
        }
        override fun onEngineError(error: Throwable) { this.error = error }
    }

    private class FakeSipEngine : SipEngine {
        lateinit var listener: SipEngine.Listener
        val operations = mutableListOf<String>()
        var startFailure: Throwable? = null
        var closeFailure: Throwable? = null
        override fun start(config: SipConfiguration, listener: SipEngine.Listener) {
            this.listener = listener
            startFailure?.let { throw it }
        }
        override fun register() { operations += "register" }
        override fun makeCall(serverCallId: String, sipUri: String) { operations += "dial:$serverCallId:$sipUri" }
        override fun answer(callId: String) { operations += "answer:$callId" }
        override fun reject(callId: String, statusCode: Int) { operations += "reject:$callId:$statusCode" }
        override fun hangup(callId: String) { operations += "hangup:$callId" }
        override fun sendDtmf(callId: String, digits: String) { operations += "dtmf:$callId:$digits" }
        override fun setMute(callId: String, muted: Boolean) { operations += "mute:$callId:$muted" }
        override fun enableAudio(callId: String) { operations += "enable:$callId" }
        override fun suspendAudio(callId: String) { operations += "suspend:$callId" }
        override fun setAudioRoute(route: SipAudioRoute) = Unit
        override fun refreshNetwork() { operations += "refresh" }
        override fun close() {
            operations += "close"
            closeFailure?.let { throw it }
        }
    }
}
