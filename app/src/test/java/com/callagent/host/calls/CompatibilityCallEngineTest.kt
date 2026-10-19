package com.callagent.host.calls

import org.junit.Assert.*
import org.junit.Test

class CompatibilityCallEngineTest {
    private class Engine(val listener: CallEngine.Listener) : CallEngine {
        val effects = mutableListOf<String>()
        override fun connect() { listener.onConnectionState(EngineConnectionState.READY) }
        override fun dial(serverCallId: String, destination: String) { effects += "dial:$serverCallId" }
        override fun answer(callId: String) { effects += "answer:$callId" }
        override fun reject(callId: String, reason: CallRejection) { effects += "reject:$callId" }
        override fun hangup(callId: String) { effects += "hangup:$callId" }
        override fun sendDtmf(callId: String, digits: String) { effects += "dtmf:$callId" }
        override fun setMute(callId: String, muted: Boolean) { effects += "mute:$callId" }
        override fun enableAudio(callId: String) { effects += "audio:$callId" }
        override fun suspendAudio(callId: String) = Unit
        override fun refreshNetwork() = Unit
        override fun close() { effects += "close" }
    }
    @Test fun rtcOutgoingNeverFallsBackAndSipIncomingKeepsItsOwner() {
        val connections = mutableListOf<EngineConnectionState>()
        val calls = mutableListOf<EngineCallSnapshot>()
        val listener = object : CallEngine.Listener {
            override fun onConnectionState(state: EngineConnectionState, statusCode: Int?, reason: String?) { connections += state }
            override fun onCallState(snapshot: EngineCallSnapshot) { calls += snapshot }
            override fun onEngineError(error: Throwable) { throw AssertionError(error) }
        }
        lateinit var rtc: Engine
        lateinit var sip: Engine
        val engine = CompatibilityCallEngine(listener, { Engine(it).also { rtc=it } }, { Engine(it).also { sip=it } })
        engine.connect()
        sip.listener.onConnectionState(EngineConnectionState.FAILED, 503)
        sip.listener.onEngineError(IllegalStateException("legacy disconnected"))
        assertEquals(listOf(EngineConnectionState.READY), connections)
        engine.dial("rtc-call", "")
        rtc.listener.onCallState(EngineCallSnapshot("rtc-call", EngineCallDirection.OUTBOUND, EngineCallState.FAILED,
            controlTransport=CallControlTransport.RTC))
        engine.hangup("rtc-call")
        assertTrue(sip.effects.isEmpty())
        sip.listener.onCallState(EngineCallSnapshot("sip-leg", EngineCallDirection.INBOUND, EngineCallState.INCOMING))
        engine.answer("sip-leg")
        engine.enableAudio("sip-leg")
        engine.setMute("sip-leg", true)
        engine.sendDtmf("sip-leg", "5")
        engine.hangup("sip-leg")
        assertEquals(listOf("answer:sip-leg", "audio:sip-leg", "mute:sip-leg", "dtmf:sip-leg", "hangup:sip-leg"), sip.effects)
        assertEquals(listOf("dial:rtc-call", "hangup:rtc-call"), rtc.effects)
        assertEquals(CallControlTransport.SIP, calls.last().controlTransport)
        engine.close()
        assertEquals("close", rtc.effects.last())
        assertEquals("close", sip.effects.last())
    }
    @Test fun terminalCallRetainsActualControlProtocol() {
        val coordinator = CallSessionCoordinator()
        assertTrue(coordinator.beginOutbound("sim", "15550100200", CallMediaTransport.WEBRTC))
        assertNull(coordinator.current?.controlTransport)
        assertTrue(coordinator.outboundAuthorized("rtc-call", 30_000, 0, CallControlTransport.RTC))
        assertTrue(coordinator.ended("rtc-call"))
        assertEquals(CallControlTransport.RTC, coordinator.current?.controlTransport)
        assertTrue(coordinator.clearTerminal("rtc-call"))
        assertTrue(coordinator.incomingInvite(IncomingInviteIdentity("sip-leg", "sip-call"),
            CallAuthority("sip-call", "incoming", "ringing", "sim", "15550100200", 30_000), 0))
        assertTrue(coordinator.ended("sip-call"))
        assertEquals(CallControlTransport.SIP, coordinator.current?.controlTransport)
    }
}
