package com.callagent.host.calls

import android.content.Context
import android.os.PowerManager

/**
 * Turns the screen off while the phone is held to the ear during a call, as
 * the system phone app does, so a cheek cannot hang up or mute. Held only
 * while the call is dialing or connected and its audio is on the earpiece.
 */
internal object ProximityScreenLock {
    private var lock: PowerManager.WakeLock? = null

    @Synchronized
    fun sync(context: Context, call: CallSession?) {
        val wanted = call != null && wanted(call.phase,
            call.callId.let(CallTelecomController::currentAudioEndpoint), call.speaker)
        if (wanted) acquire(context.applicationContext) else release()
    }

    @Synchronized
    fun release() {
        // Wait until nothing is near the sensor, or the screen would flash on at the ear.
        lock?.takeIf { it.isHeld }?.let { runCatching { it.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY) } }
        lock = null
    }

    /** An unknown route is the earpiece unless the call asked for the speaker. */
    internal fun wanted(phase: CallPhase, endpoint: CallAudioEndpoint?, speaker: Boolean): Boolean =
        phase in EAR_PHASES && (endpoint == CallAudioEndpoint.EARPIECE || (endpoint == null && !speaker))

    private fun acquire(context: Context) {
        if (lock?.isHeld == true) return
        val power = context.getSystemService(PowerManager::class.java) ?: return
        if (!power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
        lock = power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "host:call-proximity").apply {
            setReferenceCounted(false)
            // Released when the call ends; the timeout only bounds a lost release.
            acquire(MAX_HOLD_MS)
        }
    }

    private val EAR_PHASES = setOf(CallPhase.DIALING, CallPhase.OUTBOUND_RINGING, CallPhase.ANSWERING, CallPhase.ACTIVE)
    private const val MAX_HOLD_MS = 4 * 60 * 60 * 1000L
}
