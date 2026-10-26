package com.callagent.host.mesh

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.callagent.host.background.HostBackgroundRuntime
import com.callagent.host.background.HostBackgroundService
import com.callagent.host.calls.CallPhase
import com.callagent.host.calls.CallRuntime
import com.callagent.host.data.ApiClient
import com.callagent.host.data.HostSession
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Probes every node and switches the active node by [NodeSelectionPolicy].
 * Called from the foreground refresh loop and the background sync service;
 * rounds are rate limited and never overlap. Independent nodes probe in
 * parallel; another caller never waits for an in-progress network round.
 */
object NodeMonitor {
    const val ACTION_NODE_CHANGED = "com.callagent.host.mesh.NODE_CHANGED"
    private const val PROBE_INTERVAL_MS = 15_000L
    /** Pause before re-probing a failed active node, so one dropped request is not an outage. */
    private const val CONFIRM_DELAY_MS = 1_000L
    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 6_000

    private val evaluating = AtomicBoolean()
    private val probeWorkers = workers(4, "NodeProbe")
    private val statusWorker = workers(1, "NodeStatus")
    private val refreshingStatus = AtomicBoolean()
    @Volatile private var lastProbeAt = -120_000L
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
    fun evaluate(context: Context, force: Boolean = false, background: Boolean = false): HostSession? {
        val app = context.applicationContext
        if (!HostNodes.isMultiNode(app)) return null
        if (!evaluating.compareAndSet(false, true)) return null
        try {
            val now = SystemClock.elapsedRealtime()
            if (!force && now - lastProbeAt < (if (background) com.callagent.host.BuildConfig.STANDBY_NODE_HEALTH_MS else PROBE_INTERVAL_MS)) return null
            lastProbeAt = now
            HostNodes.recoverActive(app)?.let { recovered ->
                announce(app, recovered)
                return recovered
            }
            val nodes = HostNodes.nodes(app)
            val installId = HostNodes.installId(app)
            val previous = HostNodes.health(app)
            val usable = nodes.filter { HostNodes.sessionFor(app, it) != null }.map { it.key }.toSet()
            val active = HostNodes.activeNode(app)?.key
            val probes = nodes.associate { node ->
                node.key to probeWorkers.submit<NodeObservation> {
                    if (node.key !in usable) {
                        NodeObservation(reachable = false, gatewayOnline = false, rttMillis = null, issue = "需要重新配对")
                    } else {
                        val first = com.callagent.host.data.StandbyDiagnostics.withCause("node_probe") { probe(app, node, installId) }
                        if (node.key == active && !NodeSelectionPolicy.healthy(first)) {
                            SystemClock.sleep(CONFIRM_DELAY_MS)
                            com.callagent.host.data.StandbyDiagnostics.withCause("node_probe") { probe(app, node, installId) }
                        } else first
                    }
                }
            }
            val updates = nodes.associate { node ->
                val observation = probes.getValue(node.key).get()
                node.key to NodeSelectionPolicy.record(previous[node.key], observation, System.currentTimeMillis())
            }
            HostNodes.recordHealth(app, updates)
            val calling = callInProgress()
            val busy = calling || inFlightSubmissions.get() > 0
            val target = NodeSelectionPolicy.choose(nodes.map { it.key }.filter { it in usable }, HostNodes.activeNode(app)?.key, updates, busy, calling)
            if (target != null) {
                val switched = HostNodes.activate(app, target) ?: return null
                announce(app, switched)
                return switched
            }
            refreshHistory(app, nodes, updates)
            return null
        } finally {
            evaluating.set(false)
        }
    }

    private val maintenanceCoordinator = workers(1, "NodeMaintenance")
    private val maintenanceWorkers = workers(4, "NodeLease")
    private val maintenancePending = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val advertised = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val maintenanceClock = Any()
    private var lastMaintenance = -30_000L
    private var lastHistory = -30_000L
    private var lastFailover = -15_000L
    private val failoverWorker = workers(1, "NodeFailover")

    /** Independent of slow health probes. No worker can queue a second renewal for a pairing. */
    fun maintain(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        val now = SystemClock.elapsedRealtime()
        synchronized(maintenanceClock) {
            if (!force && now - lastMaintenance < 30_000L) return
            lastMaintenance = now
        }
        maintenanceCoordinator.execute {
            runCatching {
                if (force) advertised.entries.removeIf { !it.value }
                maintainOwned(app)
            }
        }
    }

    private fun maintainOwned(app: Context) {
        val manager = app.getSystemService(android.net.ConnectivityManager::class.java)
        if (manager.activeNetwork == null) return
        com.callagent.host.data.StandbyDiagnostics.checkpoint(app)
        val pairs = com.callagent.host.push.PushRegistration.pairings(app)
        val liveIds = pairs.map { it.first.sessionInstanceId }.toSet()
        advertised.keys.retainAll(liveIds)
        for ((session, store) in pairs) {
            if (!maintenancePending.add(session.sessionInstanceId)) continue
            maintenanceWorkers.execute {
                try {
                    val prefs = com.callagent.host.calls.CallPreferences(app)
                    // The active engine owns the capability of an outgoing call even with listening off.
                    val active = com.callagent.host.data.SessionStore(app).read()
                    if (callInProgress() && active?.sessionInstanceId == session.sessionInstanceId) return@execute
                    val enabled = prefs.enabledFor(session)
                    if (!enabled && advertised[session.sessionInstanceId] == false) return@execute
                    if (store.read()?.sessionInstanceId != session.sessionInstanceId) return@execute
                    val api = ApiClient(session.apiBaseUrl, store, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS)
                    fun send(value: Boolean) = com.callagent.host.data.StandbyDiagnostics.withCause("rtc") {
                        api.rtcRequest("POST", "/rtc/control/capability", org.json.JSONObject().put("version", 1).put("enabled", value))
                    }
                    send(enabled)
                    advertised[session.sessionInstanceId] = enabled
                    // A user stop racing an HTTP request must win over its late enable.
                    if (enabled && !prefs.enabledFor(session) && !callInProgress()) {
                        send(false)
                        advertised[session.sessionInstanceId] = false
                    }
                } catch (_: Exception) { /* Retry next maintenance round, independently per node. */ }
                finally { maintenancePending.remove(session.sessionInstanceId) }
            }
        }
        refreshHistory(app, HostNodes.nodes(app), HostNodes.health(app))
    }

    fun requestFailover(context: Context) {
        val now = SystemClock.elapsedRealtime()
        synchronized(maintenanceClock) {
            if (now - lastFailover < 15_000L) return
            lastFailover = now
        }
        failoverWorker.execute { runCatching { evaluate(context.applicationContext, force = true) } }
    }

    private fun refreshHistory(app: Context, nodes: List<HostNode>, health: Map<String, NodeHealth>) {
        synchronized(maintenanceClock) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastHistory < 30_000L || !refreshingStatus.compareAndSet(false, true)) return
            lastHistory = now
        }
        statusWorker.execute {
            try {
                if (runCatching { CrossNodeStatus.refresh(app, nodes, health) }.getOrDefault(0) > 0)
                    app.sendBroadcast(Intent(HostBackgroundRuntime.BACKGROUND_SYNCED).setPackage(app.packageName))
            } finally { refreshingStatus.set(false) }
        }
    }

    /** Bounded process-wide pools; idle workers exit instead of staying alive. */
    private fun workers(count: Int, name: String) = ThreadPoolExecutor(
        count, count, 30, TimeUnit.SECONDS, LinkedBlockingQueue(),
        { task -> Thread(task, name).apply { isDaemon = true } }
    ).apply { allowCoreThreadTimeOut(true) }

    private fun probe(context: Context, node: HostNode, installId: String?): NodeObservation {
        val store = HostNodes.storeFor(context, node)
        return try {
            val started = SystemClock.elapsedRealtime()
            val api = ApiClient(node.apiBaseUrl, store, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS)
            val capabilities = api.serverCapabilities()
            val supportsControl = capabilities.rtcCallControl
            val rtt = SystemClock.elapsedRealtime() - started
            val gateway = api.listGateways().firstOrNull { installId != null && it.installId == installId }
            NodeObservation(
                reachable = true,
                gatewayOnline = gateway?.online == true,
                rttMillis = rtt,
                gatewayId = gateway?.gatewayId,
                callReady = supportsControl && gateway?.online == true && gateway.voiceReady == true
            )
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
