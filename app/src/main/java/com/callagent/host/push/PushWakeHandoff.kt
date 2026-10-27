package com.callagent.host.push

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import com.callagent.host.data.StandbyDiagnostics

/** A bounded bridge from a valid push to the consumer's own work lock.
 * Hints coalesce by session and channel; another node cannot consume them.
 * No timer extends these leases. Android releases the physical lock at expiry. */
internal object PushWakeHandoff {
    enum class Channel { CALL, SYNC }
    private data class Key(val session: String, val channel: Channel)
    private data class Pending(val id: Long, val deadline: Long, val phase: Long)
    data class Ticket internal constructor(val session: String, val channel: Channel, internal val id: Long)
    private val pending = mutableMapOf<Key, Pending>()
    private var sequence = 0L
    private var lock: PowerManager.WakeLock? = null
    private var physicalDeadline = 0L
    private var diagnostic: Long? = null
    private const val TIMEOUT_MS = 15_000L

    @Synchronized fun begin(context: Context, session: String, channel: Channel): Ticket {
        prune()
        val key = Key(session, channel)
        val existing = pending[key]
        if (existing != null) return Ticket(session, channel, existing.id)
        // Bound memory as well as power, including bursts from many old pairings.
        if (pending.size >= 64) pending.keys.firstOrNull()?.let { remove(it) }
        if (pending.isEmpty()) reconcile()
        if (lock == null || pending.isEmpty()) lock = context.applicationContext.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "host:push-handoff").apply { setReferenceCounted(false) }
        val record = Pending(++sequence, SystemClock.elapsedRealtime() + TIMEOUT_MS,
            StandbyDiagnostics.phaseStarted("push-${channel.name.lowercase()}-handoff", TIMEOUT_MS))
        pending[key] = record
        reconcile()
        return Ticket(session, channel, record.id)
    }

    /** Called only after this exact session's consumer owns its own power. */
    @Synchronized fun consumed(session: String, channel: Channel) {
        remove(Key(session, channel))
        reconcile()
    }

    /** A rejected start must not wait for the hard timeout. */
    @Synchronized fun rejected(ticket: Ticket) {
        val key = Key(ticket.session, ticket.channel)
        if (pending[key]?.id == ticket.id) remove(key)
        reconcile()
    }

    @Synchronized fun retire(session: String) {
        Channel.values().forEach { remove(Key(session, it)) }
        reconcile()
    }

    private fun remove(key: Key) {
        pending.remove(key)?.let { StandbyDiagnostics.phaseEnded(it.phase, it.deadline) }
    }

    private fun prune() {
        val now = SystemClock.elapsedRealtime()
        pending.filterValues { it.deadline <= now }.keys.toList().forEach { remove(it) }
    }

    private fun reconcile() {
        prune()
        val power = lock ?: return
        val now = SystemClock.elapsedRealtime()
        val deadline = pending.values.maxOfOrNull { it.deadline } ?: now
        if (!power.isHeld) {
            diagnostic?.let { StandbyDiagnostics.wakeReleased(it) }
            diagnostic = null
        }
        if (deadline <= now) {
            if (power.isHeld) power.release()
            diagnostic?.let { StandbyDiagnostics.wakeReleased(it) }
            diagnostic = null
            physicalDeadline = 0L
        } else if (!power.isHeld || physicalDeadline != deadline) {
            power.acquire(deadline - now)
            physicalDeadline = deadline
            val token = diagnostic
            if (token == null) diagnostic = StandbyDiagnostics.wakeAcquired("push-handoff", deadline - now)
            else StandbyDiagnostics.wakeRenewed(token, deadline - now)
        }
    }
}
