package com.callagent.host.calls

import android.content.Context
import com.callagent.host.data.SipConfiguration
import com.callagent.host.sip.SipCallDirection
import com.callagent.host.sip.SipCallSnapshot
import com.callagent.host.sip.SipCallState
import com.callagent.host.sip.SipEngine
import com.callagent.host.sip.SipEngineFactory
import com.callagent.host.sip.SipRegistrationState

/** The compatibility transport; no credentials, URI validation or native audio policy is changed. */
internal class SipCallEngine(private val sip: SipEngine) : CallEngine {
    fun start(config: SipConfiguration, listener: CallEngine.Listener) {
        try {
            sip.start(config, object : SipEngine.Listener {
                override fun onRegistration(state: SipRegistrationState, statusCode: Int?, reason: String?) {
                    val connection = when (state) {
                        SipRegistrationState.STOPPED -> EngineConnectionState.STOPPED
                        SipRegistrationState.REGISTERING -> EngineConnectionState.CONNECTING
                        SipRegistrationState.REGISTERED -> EngineConnectionState.READY
                        SipRegistrationState.FAILED -> EngineConnectionState.FAILED
                    }
                    listener.onConnectionState(connection, statusCode, reason)
                }

                override fun onCallState(snapshot: SipCallSnapshot) {
                    val state = when (snapshot.state) {
                        SipCallState.INCOMING -> EngineCallState.INCOMING
                        SipCallState.RINGING -> EngineCallState.RINGING
                        SipCallState.EARLY -> EngineCallState.EARLY
                        SipCallState.CONFIRMED -> EngineCallState.CONNECTED
                        SipCallState.DISCONNECTED -> EngineCallState.DISCONNECTED
                        SipCallState.FAILED -> EngineCallState.FAILED
                    }
                    listener.onCallState(EngineCallSnapshot(
                        callId = snapshot.callId,
                        direction = when (snapshot.direction) {
                            SipCallDirection.INBOUND -> EngineCallDirection.INBOUND
                            SipCallDirection.OUTBOUND -> EngineCallDirection.OUTBOUND
                        },
                        state = state,
                        serverCallId = snapshot.serverCallIdHeader,
                        transportCallId = snapshot.dialogCallId,
                        terminalStatusCode = snapshot.terminalStatusCode,
                        terminalReason = snapshot.terminalReason
                    ))
                }

                override fun onEngineError(error: Throwable) = listener.onEngineError(error)
            })
        } catch (failure: Throwable) {
            // A failed native startup can still own worker threads or partial native resources.
            // Preserve the original failure even if cleanup also fails.
            runCatching { sip.close() }
            throw failure
        }
    }

    override fun connect() = sip.register()
    override fun dial(serverCallId: String, destination: String) = sip.makeCall(serverCallId, destination)
    override fun answer(callId: String) = sip.answer(callId)
    override fun reject(callId: String, reason: CallRejection) = sip.reject(callId, when (reason) {
        CallRejection.DECLINED -> 603
        CallRejection.EXPIRED -> 408
    })
    override fun hangup(callId: String) = sip.hangup(callId)
    override fun sendDtmf(callId: String, digits: String) = sip.sendDtmf(callId, digits)
    override fun setMute(callId: String, muted: Boolean) = sip.setMute(callId, muted)
    override fun enableAudio(callId: String) = sip.enableAudio(callId)
    override fun suspendAudio(callId: String) = sip.suspendAudio(callId)
    override fun refreshNetwork() = sip.refreshNetwork()
    override fun close() = sip.close()
}

/** Only the verified SIP transport is enabled until the WebRTC PCM/SDK probe passes. */
object CallEngineFactory {
    fun isAvailable(context: Context): Boolean = SipEngineFactory.isAvailable(context)

    fun startSip(context: Context, config: SipConfiguration, listener: CallEngine.Listener): CallEngine =
        SipCallEngine(SipEngineFactory.create(context)).also { it.start(config, listener) }
}
