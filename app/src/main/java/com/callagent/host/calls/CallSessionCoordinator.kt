package com.callagent.host.calls

/** User-visible phases shared by the call screen, Telecom connection and notification. */
enum class CallPhase {
    IDLE,
    AUTHORIZING_OUTBOUND,
    REGISTERING,
    DIALING,
    OUTBOUND_RINGING,
    INCOMING_MATCHING,
    INCOMING_RINGING,
    ANSWERING,
    ACTIVE,
    DISCONNECTING,
    ENDED,
    FAILED
}

enum class CallDirection { INCOMING, OUTGOING }

data class CallSession(
    val callId: String,
    val direction: CallDirection,
    val simId: String,
    val remoteNumber: String?,
    val phase: CallPhase,
    val expiresAtEpochMillis: Long,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val failure: String? = null
)

/** Values copied from the authenticated server call snapshot. */
data class CallAuthority(
    val callId: String,
    val direction: String,
    val state: String,
    val simId: String,
    val remoteNumber: String?,
    val expiresAtEpochMillis: Long
)

/** Correlation extracted from an actual SIP INVITE, not from a push or an HTTP snapshot. */
data class IncomingInviteIdentity(
    val sipCallId: String,
    val serverCallIdHeader: String?
)

/**
 * Serializes app-side transitions and makes repeated Android/SIP callbacks harmless.
 * Network and SIP operations are deliberately performed by the owner after inspecting
 * the returned transition; this class has no side effects.
 */
class CallSessionCoordinator {
    private val retiredCallIds = linkedSetOf<String>()

    @Volatile
    var current: CallSession? = null
        private set

    @Synchronized
    fun beginOutbound(simId: String, remoteNumber: String): Boolean {
        if (current?.phase?.isLive() == true || current?.phase == CallPhase.AUTHORIZING_OUTBOUND) return false
        current = CallSession(
            callId = "pending",
            direction = CallDirection.OUTGOING,
            simId = simId,
            remoteNumber = remoteNumber,
            phase = CallPhase.AUTHORIZING_OUTBOUND,
            expiresAtEpochMillis = 0L
        )
        return true
    }

    @Synchronized
    fun outboundAuthorized(callId: String, expiresAtEpochMillis: Long, nowEpochMillis: Long): Boolean {
        val state = current ?: return false
        if (state.phase != CallPhase.AUTHORIZING_OUTBOUND || callId.isBlank() || expiresAtEpochMillis <= nowEpochMillis) return false
        current = state.copy(callId = callId, expiresAtEpochMillis = expiresAtEpochMillis, phase = CallPhase.REGISTERING)
        return true
    }

    @Synchronized
    fun registrationReady(callId: String): Boolean = update(callId) { state ->
        if (state.direction != CallDirection.OUTGOING || state.phase != CallPhase.REGISTERING) null
        else state.copy(phase = CallPhase.DIALING)
    }

    @Synchronized
    fun outboundRinging(callId: String): Boolean = update(callId) { state ->
        if (state.direction != CallDirection.OUTGOING || state.phase != CallPhase.DIALING) null
        else state.copy(phase = CallPhase.OUTBOUND_RINGING)
    }

    @Synchronized
    fun incomingInvite(invite: IncomingInviteIdentity, authority: CallAuthority, nowEpochMillis: Long): Boolean {
        if (current?.phase?.isLive() == true || invite.sipCallId.isBlank()) return false
        if (invite.serverCallIdHeader.isNullOrBlank() || invite.serverCallIdHeader != authority.callId) return false
        if (authority.callId in retiredCallIds) return false
        if (authority.direction !in setOf("incoming", "inbound")) return false
        if (authority.state !in setOf("pending_wakeup", "ringing", "connecting")) return false
        if (authority.expiresAtEpochMillis <= nowEpochMillis || authority.simId.isBlank()) return false
        current = CallSession(
            callId = authority.callId,
            direction = CallDirection.INCOMING,
            simId = authority.simId,
            remoteNumber = authority.remoteNumber,
            phase = CallPhase.INCOMING_RINGING,
            expiresAtEpochMillis = authority.expiresAtEpochMillis
        )
        return true
    }

    @Synchronized
    fun answer(callId: String, nowEpochMillis: Long = System.currentTimeMillis()): Boolean = update(callId) { state ->
        if (state.direction != CallDirection.INCOMING || state.phase != CallPhase.INCOMING_RINGING ||
            state.expiresAtEpochMillis <= nowEpochMillis
        ) null
        else state.copy(phase = CallPhase.ANSWERING)
    }

    @Synchronized
    fun connected(callId: String): Boolean = update(callId) { state ->
        if (state.phase !in setOf(CallPhase.ANSWERING, CallPhase.DIALING, CallPhase.OUTBOUND_RINGING)) null
        else state.copy(phase = CallPhase.ACTIVE)
    }

    @Synchronized
    fun setMuted(callId: String, muted: Boolean): Boolean = update(callId) { state ->
        if (state.phase != CallPhase.ACTIVE || state.muted == muted) null else state.copy(muted = muted)
    }

    @Synchronized
    fun setSpeaker(callId: String, enabled: Boolean): Boolean = update(callId) { state ->
        if (state.phase != CallPhase.ACTIVE || state.speaker == enabled) null else state.copy(speaker = enabled)
    }

    @Synchronized
    fun disconnect(callId: String): Boolean = update(callId) { state ->
        if (!state.phase.isLive() || state.phase == CallPhase.DISCONNECTING) null
        else state.copy(phase = CallPhase.DISCONNECTING)
    }

    @Synchronized
    fun ended(callId: String): Boolean = update(callId) { state ->
        if (!state.phase.isLive()) null else state.copy(phase = CallPhase.ENDED).also { retiredCallIds += callId }
    }

    @Synchronized
    fun failed(callId: String, reason: String): Boolean = update(callId) { state ->
        if (!state.phase.isLive()) null else state.copy(phase = CallPhase.FAILED, failure = reason.take(180)).also { retiredCallIds += callId }
    }

    @Synchronized
    fun clearTerminal(callId: String): Boolean {
        val state = current ?: return false
        if (state.callId != callId || state.phase !in setOf(CallPhase.ENDED, CallPhase.FAILED)) return false
        retiredCallIds += callId
        current = null
        return true
    }

    @Synchronized
    fun abandonPendingOutbound(): String? {
        val state = current ?: return null
        if (state.direction != CallDirection.OUTGOING || state.phase != CallPhase.AUTHORIZING_OUTBOUND) return null
        current = state.copy(phase = CallPhase.ENDED)
        return state.callId.takeUnless { it == "pending" }
    }

    private fun update(callId: String, transform: (CallSession) -> CallSession?): Boolean {
        val state = current ?: return false
        if (state.callId != callId) return false
        val changed = transform(state) ?: return false
        current = changed
        return true
    }

    private fun CallPhase.isLive(): Boolean = this !in setOf(CallPhase.IDLE, CallPhase.ENDED, CallPhase.FAILED)
}
