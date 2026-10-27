package com.callagent.host.calls

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import com.callagent.host.data.StandbyDiagnostics

/** Each asynchronous owner releases its own token. The physical lock is bounded
 * by the latest live owner deadline; closing an engine fences all late owners. */
internal class RtcSignalingWakeLease(context: Context) : AutoCloseable {
    enum class Reason(val label: String) {
        COMPAT_IDLE("compat-idle"), LIVE_CALL("live-call"), CONTROL_WORK("control-work")
    }
    private data class Owner(val reason: Reason, var deadline: Long, val diagnostic: Long)
    private val lock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "host:rtc-signaling")
        .apply { setReferenceCounted(false) }
    private val owners = mutableMapOf<Long, Owner>()
    private var sequence = 0L
    private var continuous: Long? = null
    private var closed = false
    private var diagnosticToken: Long? = null
    private var physicalDeadline = 0L

    @Synchronized fun acquire(reason: Reason, timeout: Long = LEASE_MILLIS): Long? {
        if (closed) return null
        prune()
        val token = ++sequence
        owners[token] = Owner(reason, SystemClock.elapsedRealtime() + timeout,
            StandbyDiagnostics.phaseStarted("rtc-${reason.label}", timeout))
        reconcile()
        return token
    }

    @Synchronized fun release(token: Long?) {
        token ?: return
        owners.remove(token)?.let { StandbyDiagnostics.phaseEnded(it.diagnostic, it.deadline) }
        if (continuous == token) continuous = null
        reconcile()
    }

    /** The compatibility or live-call owner is explicit, independent of work owners. */
    @Synchronized fun renew(reason: Reason = Reason.COMPAT_IDLE) {
        if (closed) return
        prune()
        val owner = continuous?.let { owners[it] }
        val now = SystemClock.elapsedRealtime()
        if (owner?.reason != reason) {
            val previous = continuous
            continuous = acquire(reason)
            release(previous)
        } else if (owner.deadline - now <= LEASE_MILLIS - RENEW_MILLIS) {
            owner.deadline = now + LEASE_MILLIS
            StandbyDiagnostics.phaseRenewed(owner.diagnostic, owner.deadline)
            reconcile()
        }
    }

    @Synchronized fun releaseContinuous() = release(continuous)

    private fun prune() {
        val now = SystemClock.elapsedRealtime()
        owners.entries.removeAll { (_, owner) ->
            (owner.deadline <= now).also { expired ->
                if (expired) StandbyDiagnostics.phaseEnded(owner.diagnostic, owner.deadline)
            }
        }
        if (continuous?.let { owners.containsKey(it) } != true) continuous = null
    }

    private fun reconcile() {
        prune()
        val now = SystemClock.elapsedRealtime()
        val deadline = owners.values.maxOfOrNull { it.deadline } ?: now
        if (!lock.isHeld) {
            diagnosticToken?.let { StandbyDiagnostics.wakeReleased(it) }
            diagnosticToken = null
        }
        if (deadline <= now) {
            if (lock.isHeld) lock.release()
            diagnosticToken?.let { StandbyDiagnostics.wakeReleased(it) }
            diagnosticToken = null
            physicalDeadline = 0L
        } else if (!lock.isHeld || physicalDeadline != deadline) {
            lock.acquire(deadline - now)
            physicalDeadline = deadline
            val token = diagnosticToken
            if (token == null) diagnosticToken = StandbyDiagnostics.wakeAcquired("rtc-signaling", deadline - now)
            else StandbyDiagnostics.wakeRenewed(token, deadline - now)
        }
    }

    @Synchronized fun pause() {
        owners.values.forEach { StandbyDiagnostics.phaseEnded(it.diagnostic, it.deadline) }
        owners.clear()
        continuous = null
        reconcile()
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
