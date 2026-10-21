package com.callagent.host.calls

/** Signaling readiness, independent of whether any cellular call has been answered. */
enum class EngineConnectionState { STOPPED, CONNECTING, READY, FAILED }

enum class EngineCallState { INCOMING, RINGING, EARLY, CONNECTED, DISCONNECTED, FAILED }
enum class EngineCallDirection { INBOUND, OUTBOUND }
enum class CallRejection { DECLINED, EXPIRED }
enum class CallMediaTransport(val label: String) { SIP("SIP"), WEBRTC("WebRTC") }
enum class CallControlTransport(val label: String) { SIP("SIP"), RTC("Go/RTC") }

/**
 * [callId] is the engine's operation key. [serverCallId] is correlation supplied by the
 * authenticated signaling path and must still be checked against the server's call authority.
 * [transportCallId] identifies an incoming leg, not a second business call.
 */
data class EngineCallSnapshot(
    val callId: String,
    val direction: EngineCallDirection,
    val state: EngineCallState,
    val serverCallId: String? = null,
    val transportCallId: String? = null,
    val terminalStatusCode: Int? = null,
    val terminalReason: String? = null,
    val mediaTransport: CallMediaTransport = CallMediaTransport.SIP,
    val controlTransport: CallControlTransport = CallControlTransport.SIP
)

/**
 * Call controls used by the service. Transport-specific credential bootstrap stays outside
 * this interface. Implementations must never turn ICE connectivity alone into CONNECTED:
 * that event represents an accepted call, under the transport's validated call authority.
 * Telecom owns audio routes; media opens only after the foreground-service authorization.
 */
interface CallEngine : AutoCloseable {
    interface Listener {
        fun onConnectionState(state: EngineConnectionState, statusCode: Int? = null, reason: String? = null)
        fun onCallState(snapshot: EngineCallSnapshot)
        fun onEngineError(error: Throwable)
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
