package com.callagent.host.mesh

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.callagent.host.background.HostBackgroundService
import com.callagent.host.calls.CallPhase
import com.callagent.host.calls.CallRuntime
import com.callagent.host.data.ApiClient
import com.callagent.host.data.HostSession
import java.util.concurrent.atomic.AtomicInteger

/**
 * Probes every node and switches the active node by [NodeSelectionPolicy].
 * Called from the foreground refresh loop and the background sync service;
 * calls are rate limited and serialized, and network work blocks the caller.
 */
object NodeMonitor {
    const val ACTION_NODE_CHANGED = "com.callagent.host.mesh.NODE_CHANGED"
    private const val PROBE_INTERVAL_MS = 25_000L
    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 6_000

    private val lock = Any()
    @Volatile private var lastProbeAt = 0L
    private val inFlightSubmissions = AtomicInteger()

    /** Mark an SMS submission in flight; no switch happens until it returns. */
    fun <T> holdingNode(block: () -> T): T {
        inFlightSubmissions.incrementAndGet()
        try { return block() } finally { inFlightSubmissions.decrementAndGet() }
    }

    /**
     * Probe and maybe switch. Returns the new active session after a switch,
     * otherwise null. [force] skips the rate limit (network changed).
     */
    fun evaluate(context: Context, force: Boolean = false): HostSession? {
        val app = context.applicationContext
        if (!HostNodes.isMultiNode(app)) return null
        synchronized(lock) {
            val now = SystemClock.elapsedRealtime()
            if (!force && now - lastProbeAt < PROBE_INTERVAL_MS) return null
            lastProbeAt = now
            HostNodes.recoverActive(app)?.let { recovered ->
                announce(app, recovered)
                return recovered
            }
            val nodes = HostNodes.nodes(app)
            val installId = HostNodes.installId(app)
            val previous = HostNodes.health(app)
            val usable = mutableListOf<String>()
            val updates = nodes.associate { node ->
                val session = HostNodes.sessionFor(app, node)
                val observation = if (session == null) {
                    NodeObservation(reachable = false, gatewayOnline = false, rttMillis = null, issue = "需要重新配对")
                } else {
                    usable += node.key
                    probe(app, node, installId)
                }
                node.key to NodeSelectionPolicy.record(previous[node.key], observation, System.currentTimeMillis())
            }
            HostNodes.recordHealth(app, updates)
            val busy = callInProgress() || inFlightSubmissions.get() > 0
            val active = HostNodes.activeNode(app)?.key
            val target = NodeSelectionPolicy.choose(nodes.map { it.key }.filter { it in usable }, active, updates, busy)
                ?: return null
            val switched = HostNodes.activate(app, target) ?: return null
            announce(app, switched)
            return switched
        }
    }

    private fun probe(context: Context, node: HostNode, installId: String?): NodeObservation {
        val store = HostNodes.storeFor(context, node)
        return try {
            val started = SystemClock.elapsedRealtime()
            val api = ApiClient(node.apiBaseUrl, store, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS)
            api.capabilities()
            val rtt = SystemClock.elapsedRealtime() - started
            val online = api.listGateways().any { it.online && installId != null && it.installId == installId }
            NodeObservation(reachable = true, gatewayOnline = online, rttMillis = rtt)
        } catch (_: Exception) {
            NodeObservation(reachable = false, gatewayOnline = false, rttMillis = null)
        }
    }

    private fun callInProgress(): Boolean =
        CallRuntime.currentSession?.phase?.let { it != CallPhase.IDLE && it != CallPhase.ENDED && it != CallPhase.FAILED } == true

    private fun announce(context: Context, session: HostSession) {
        runCatching { HostBackgroundService.onActiveNodeChanged() }
        runCatching { CallRuntime.onActiveNodeChanged(context, session) }
        context.sendBroadcast(Intent(ACTION_NODE_CHANGED).setPackage(context.packageName))
    }
}
