package com.callagent.host.push

import android.content.Context
import android.os.SystemClock
import com.callagent.host.data.ApiClient
import com.callagent.host.data.ApiFailure
import com.callagent.host.mesh.NodeMonitor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Whether each node's wake-ups reach this phone. A node reports whether the
 * Kyubey relay accepted its last hand-off; only the phone can tell whether FCM
 * then delivered it. After the network comes back, the phone asks every node
 * with a push registration for a silent test wake-up (an sms hint answered by
 * a sync that shows nothing) and notes whether it arrives.
 *
 * Results live in memory for this process; nothing is sent anywhere else.
 */
object PushProbe {
    /** What one node's push path looked like when last checked. */
    data class NodeProbe(
        /** The node answered the status or test request; false for a node without the feature. */
        val supported: Boolean,
        /** The relay accepted the node's last hand-off. */
        val relayOk: Boolean? = null,
        val relayFailures: Int = 0,
        /** When a wake-up from this node last reached the phone (elapsed realtime). */
        val reachedAt: Long = 0L,
        /** A test is under way and its wake-up has not arrived yet. */
        val testPending: Boolean = false,
        /** The last test's wake-up never arrived within [ARRIVAL_MS]. */
        val testLost: Boolean = false,
        val checkedAt: Long = 0L
    )

    private const val ARRIVAL_MS = 60_000L
    /** Network recovery tests a node at most this often; a user check always tests. */
    private const val TEST_EVERY_MS = 5 * 60_000L
    private const val STATUS_EVERY_MS = 10 * 60_000L

    private val probes = ConcurrentHashMap<String, NodeProbe>()
    private val testedAt = ConcurrentHashMap<String, Long>()
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "push-probe").apply { isDaemon = true } }

    fun probe(sessionId: String): NodeProbe? = probes[sessionId]?.let(::settled)

    /** A wake-up from the node of [sessionId] arrived. */
    fun pushArrived(sessionId: String) {
        probes.compute(sessionId) { _, old ->
            (old ?: NodeProbe(supported = true)).copy(reachedAt = SystemClock.elapsedRealtime(), testPending = false, testLost = false)
        }
    }

    /** The network came back: test the nodes not tested in the last few minutes. */
    fun onNetworkRecovered(context: Context) = run(context, test = true, force = false)

    /** The user asked to check now: test every node. */
    fun checkNow(context: Context) = run(context, test = true, force = true)

    /** The node list is on screen: refresh the relay status if it is old, without a test. */
    fun refreshIfStale(context: Context) = run(context, test = false, force = false)

    private fun run(context: Context, test: Boolean, force: Boolean) {
        val app = context.applicationContext
        if (!PushRegistration.available(app)) return
        runCatching {
            worker.execute {
                var changed = false
                for ((session, store) in PushRegistration.pairings(app)) {
                    val id = session.sessionInstanceId
                    if (!PushRegistration.isReady(app, id)) continue
                    val now = SystemClock.elapsedRealtime()
                    val previous = probes[id]
                    val testDue = test && (force || now - (testedAt[id] ?: Long.MIN_VALUE / 2) >= TEST_EVERY_MS)
                    if (!testDue && previous != null && now - previous.checkedAt < STATUS_EVERY_MS) continue
                    if (previous?.supported == false && !force) continue
                    val api = ApiClient(session.apiBaseUrl, store, 4_000, 8_000)
                    val next = runCatching {
                        if (testDue) {
                            testedAt[id] = now
                            val sent = api.testPush()
                            if (sent) scheduleArrivalCheck(app, id, now)
                            (previous ?: NodeProbe(supported = true)).copy(supported = true, relayOk = sent,
                                relayFailures = if (sent) 0 else (previous?.relayFailures ?: 0) + 1,
                                testPending = sent, testLost = false, checkedAt = now)
                        } else {
                            val status = api.pushStatus() ?: return@runCatching null
                            (previous ?: NodeProbe(supported = true)).copy(supported = true,
                                relayOk = status.failures == 0 && status.deliveredAt != null,
                                relayFailures = status.failures, checkedAt = now)
                        }
                    }.getOrElse { failure ->
                        // An older node has neither request; do not ask it again in this process.
                        if (failure is ApiFailure && failure.httpStatus in setOf(404, 405) && failure.code != "PUSH_NOT_REGISTERED") {
                            NodeProbe(supported = false, checkedAt = now)
                        } else null
                    } ?: continue
                    probes[id] = next
                    changed = true
                }
                if (changed) NodeMonitor.announceNodeState(app)
            }
        }
    }

    private fun scheduleArrivalCheck(app: Context, sessionId: String, requestedAt: Long) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            probes.computeIfPresent(sessionId) { _, probe ->
                if (probe.testPending && probe.reachedAt < requestedAt) probe.copy(testPending = false, testLost = true) else probe
            }
            NodeMonitor.announceNodeState(app)
        }, ARRIVAL_MS)
    }

    /** A pending test past its window counts as lost even if the delayed check never ran. */
    private fun settled(probe: NodeProbe): NodeProbe =
        if (probe.testPending && SystemClock.elapsedRealtime() - probe.checkedAt > ARRIVAL_MS && probe.reachedAt < probe.checkedAt)
            probe.copy(testPending = false, testLost = true) else probe

    internal fun resetForTest() { probes.clear(); testedAt.clear() }
    internal fun setForTest(sessionId: String, probe: NodeProbe) { probes[sessionId] = probe }
}
