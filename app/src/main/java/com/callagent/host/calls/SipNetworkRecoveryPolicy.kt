package com.callagent.host.calls

/** Fences delayed network-loss work so a restored network or replacement call wins. */
internal class SipNetworkRecoveryPolicy(private val graceMillis: Long = DEFAULT_GRACE_MILLIS) {
    class LossWindow internal constructor(
        val generation: Long,
        val callId: String,
        val deadlineElapsedMillis: Long
    )

    private var generation = 0L
    private var pendingLoss: LossWindow? = null

    init {
        require(graceMillis > 0L)
    }

    @Synchronized
    fun networkLost(callId: String?, nowElapsedMillis: Long): LossWindow? {
        generation += 1L
        val activeCallId = callId?.takeIf { it.isNotBlank() }
        if (activeCallId == null) {
            pendingLoss = null
            return null
        }
        return LossWindow(generation, activeCallId, nowElapsedMillis + graceMillis).also {
            pendingLoss = it
        }
    }

    @Synchronized
    fun networkRestored() {
        generation += 1L
        pendingLoss = null
    }

    @Synchronized
    fun invalidate() {
        generation += 1L
        pendingLoss = null
    }

    @Synchronized
    fun shouldEndCall(window: LossWindow, activeCallId: String?, nowElapsedMillis: Long): Boolean =
        window.generation == generation && pendingLoss == window &&
            window.callId == activeCallId && nowElapsedMillis >= window.deadlineElapsedMillis

    private companion object {
        const val DEFAULT_GRACE_MILLIS = 30_000L
    }
}

internal enum class SipRegistrationFailureKind {
    RETRYABLE,
    AUTHENTICATION_REJECTED,
    TERMINAL
}

internal object SipRegistrationFailurePolicy {
    fun classify(statusCode: Int?): SipRegistrationFailureKind = when {
        statusCode == null || statusCode == 408 || statusCode == 480 || statusCode in 500..599 ->
            SipRegistrationFailureKind.RETRYABLE
        statusCode == 401 || statusCode == 403 -> SipRegistrationFailureKind.AUTHENTICATION_REJECTED
        else -> SipRegistrationFailureKind.TERMINAL
    }
}
