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

    @Synchronized fun renew() {
        if (closed) return
        val now = SystemClock.elapsedRealtime()
        if (!lock.isHeld || now >= renewAt) {
            lock.acquire(LEASE_MILLIS)
            renewAt = now + RENEW_MILLIS
        }
    }

    @Synchronized override fun close() {
        closed = true
        if (lock.isHeld) lock.release()
    }

    companion object {
        private const val LEASE_MILLIS = 60_000L
        private const val RENEW_MILLIS = 20_000L
    }
}
