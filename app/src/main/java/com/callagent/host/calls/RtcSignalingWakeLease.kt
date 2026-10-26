package com.callagent.host.calls

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock

/** RTC invitations arrive through HTTPS polling, which cannot wake a suspended
 * CPU by itself. Own this lease only while the user-enabled call engine lives.
 * A stalled/dead worker cannot leave an unbounded power lock behind. */
internal class RtcSignalingWakeLease(context: Context) : AutoCloseable {
    private val lock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "host:rtc-signaling")
        .apply { setReferenceCounted(false) }
    private var renewAt = 0L
    private var closed = false
    private var diagnosticToken: Long? = null

    @Synchronized fun renew() {
        if (closed) return
        val now = SystemClock.elapsedRealtime()
        if (!lock.isHeld || now >= renewAt) {
            if (!lock.isHeld) {
                diagnosticToken?.let { com.callagent.host.data.StandbyDiagnostics.wakeReleased(it) }
                diagnosticToken = null
            }
            lock.acquire(LEASE_MILLIS)
            val token = diagnosticToken
            if (token == null) diagnosticToken = com.callagent.host.data.StandbyDiagnostics.wakeAcquired("rtc-signaling", LEASE_MILLIS)
            else com.callagent.host.data.StandbyDiagnostics.wakeRenewed(token, LEASE_MILLIS)
            renewAt = now + RENEW_MILLIS
        }
    }

    @Synchronized fun pause() {
        if (lock.isHeld) lock.release()
        diagnosticToken?.let { com.callagent.host.data.StandbyDiagnostics.wakeReleased(it) }
        diagnosticToken = null
        renewAt = 0L
    }

    @Synchronized override fun close() {
        closed = true
        pause()
    }

    companion object {
        private const val LEASE_MILLIS = 60_000L
        private const val RENEW_MILLIS = 20_000L
    }
}
