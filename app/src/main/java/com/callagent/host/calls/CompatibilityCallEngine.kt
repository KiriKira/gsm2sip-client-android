package com.callagent.host.calls

import java.util.concurrent.ConcurrentHashMap

/** RTC owns outgoing calls. An explicitly configured SIP adapter may receive
 * legacy incoming forks. The first authenticated call event freezes its owner;
 * an operation or failure never retries that call through the other adapter. */
internal class CompatibilityCallEngine(
    listener: CallEngine.Listener,
    rtcFactory: (CallEngine.Listener) -> CallEngine,
    sipFactory: (CallEngine.Listener) -> CallEngine
) : CallEngine {
    private val sipCalls = ConcurrentHashMap.newKeySet<String>()
    private val rtc = rtcFactory(listener)
    private val sip = try { sipFactory(object : CallEngine.Listener {
        override fun onConnectionState(state: EngineConnectionState, statusCode: Int?, reason: String?) = Unit
        override fun onEngineError(error: Throwable) = Unit // Optional legacy listener cannot stop RTC.
        override fun onCallState(snapshot: EngineCallSnapshot) {
            sipCalls.add(snapshot.callId)
            listener.onCallState(snapshot)
        }
    }) } catch (error: Throwable) { rtc.close(); throw error }
    private fun owner(id: String) = if (id in sipCalls) sip else rtc
    override fun connect() { rtc.connect(); runCatching { sip.connect() } }
    override fun dial(serverCallId: String, destination: String) = rtc.dial(serverCallId, destination)
    override fun answer(callId: String) = owner(callId).answer(callId)
    override fun reject(callId: String, reason: CallRejection) = owner(callId).reject(callId, reason)
    override fun hangup(callId: String) = owner(callId).hangup(callId)
    override fun sendDtmf(callId: String, digits: String) = owner(callId).sendDtmf(callId, digits)
    override fun setMute(callId: String, muted: Boolean) = owner(callId).setMute(callId, muted)
    override fun enableAudio(callId: String) = owner(callId).enableAudio(callId)
    override fun suspendAudio(callId: String) = owner(callId).suspendAudio(callId)
    override fun refreshNetwork() { rtc.refreshNetwork(); runCatching { sip.refreshNetwork() } }
    override fun close() { try { rtc.close() } finally { sip.close(); sipCalls.clear() } }
}
