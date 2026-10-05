package com.callagent.host.sip

import com.callagent.host.data.SipConfiguration

enum class SipRegistrationState { STOPPED, REGISTERING, REGISTERED, FAILED }

enum class SipCallState { INCOMING, RINGING, EARLY, CONFIRMED, DISCONNECTED, FAILED }

enum class SipCallDirection { INBOUND, OUTBOUND }

enum class SipAudioRoute { DEFAULT, EARPIECE, SPEAKER, WIRED_HEADSET, BLUETOOTH }

/** A snapshot from the SIP engine. `callId` is the key accepted by call controls. */
data class SipCallSnapshot(
    val callId: String,
    val direction: SipCallDirection,
    val state: SipCallState,
    val remoteUri: String,
    val requestUri: String? = null,
    val dialogCallId: String? = null,
    val serverCallIdHeader: String? = null,
    val terminalStatusCode: Int? = null,
    val terminalReason: String? = null
)

/** The only SIP signaling/media engine used by the host client. */
interface SipEngine : AutoCloseable {
    interface Listener {
        fun onRegistration(
            state: SipRegistrationState,
            statusCode: Int? = null,
            reason: String? = null
        )

        fun onCallState(snapshot: SipCallSnapshot)

        fun onEngineError(error: Throwable)
    }

    /** Initializes the TLS-only SIP endpoint. Registration is explicit. */
    fun start(config: SipConfiguration, listener: Listener)

    fun register()

    /** `serverCallId` is also the operation key for an outgoing call. */
    fun makeCall(serverCallId: String, sipUri: String)

    fun answer(callId: String)

    fun reject(callId: String, statusCode: Int = 603)

    fun hangup(callId: String)

    fun sendDtmf(callId: String, digits: String)

    fun setMute(callId: String, muted: Boolean)

    /** Authorizes opening call audio after the host's microphone foreground service is ready. */
    fun enableAudio(callId: String)

    /** Closes local media while the host verifies server authority after a network outage. */
    fun suspendAudio(callId: String)

    fun setAudioRoute(route: SipAudioRoute)

    /** Refreshes network DNS data and renews the SIP registration. */
    fun refreshNetwork()

    override fun close()
}
