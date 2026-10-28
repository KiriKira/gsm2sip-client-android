package com.callagent.host.calls

/** Signaling readiness, independent of whether any cellular call has been answered. */
enum class EngineConnectionState { STOPPED, CONNECTING, READY, FAILED }

enum class EngineCallState { INCOMING, RINGING, EARLY, CONNECTED, DISCONNECTED, FAILED }
enum class EngineCallDirection { INBOUND, OUTBOUND }
/** DECLINED_BY_USER is the user's own decline, which may end the call on every phone. */
enum class CallRejection { DECLINED, EXPIRED, DECLINED_BY_USER }

/**
 * [callId] is the engine's operation key. [serverCallId] is correlation supplied by the
 * authenticated control path and must still be checked against the server's call authority.
 */
data class EngineCallSnapshot(
    val callId: String,
    val direction: EngineCallDirection,
    val state: EngineCallState,
    val serverCallId: String? = null,
    val terminalReason: String? = null
)

/**
 * Call controls used by the service. Implementations must never turn ICE connectivity alone
 * into CONNECTED: that event represents an accepted call, under the server's call authority.
 * Telecom owns audio routes; media opens only after the foreground-service authorization.
 */
interface CallEngine : AutoCloseable {
    interface Listener {
        fun onConnectionState(state: EngineConnectionState, statusCode: Int? = null, reason: String? = null)
        fun onCallState(snapshot: EngineCallSnapshot)
        fun onEngineError(error: Throwable)
        /** Call control answered and nothing is ringing or live. */
        fun onIdle() {}
    }

    fun connect()
    fun dial(serverCallId: String, destination: String)
    fun answer(callId: String)
    fun reject(callId: String, reason: CallRejection = CallRejection.DECLINED)
    fun hangup(callId: String)
    fun sendDtmf(callId: String, digits: String)
    fun setMute(callId: String, muted: Boolean)
    fun enableAudio(callId: String)
    fun suspendAudio(callId: String)
    fun refreshNetwork()
    /** The server's wake channel says this device has a call event: look now. */
    fun wake() = Unit
    override fun close()
}

object CallEngineFactory {
    /** Only an instrumentation process can inject hardware in memory. Ordinary
     * app builds have no synthetic PCM implementation or external control. */
    internal var pcmForInstrumentation: ((String) -> com.callagent.rtcmedia.PcmBridge?)? = null
    fun isAvailable(context: android.content.Context): Boolean = com.callagent.rtcmedia.RtcAudioSession.isAvailable(context)
}
