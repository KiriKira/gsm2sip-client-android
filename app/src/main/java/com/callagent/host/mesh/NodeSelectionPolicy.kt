package com.callagent.host.mesh

/** What one probe of a server node observed. */
data class NodeObservation(
    val reachable: Boolean,
    /** The account's old phone is online on this node (matched by install ID). */
    val gatewayOnline: Boolean,
    val rttMillis: Long?,
    val issue: String? = null,
    /** This account's gateway on that node (the IDs differ per node). */
    val gatewayId: String? = null,
    /** The old phone is SIP-registered on that node, so calls work through it. */
    val callReady: Boolean = false
)

/** One probe result kept for the node quality view. */
data class ProbeSample(val ok: Boolean, val rttMillis: Long?, val at: Long)

/** Rolling health of one node, persisted between probes. */
data class NodeHealth(
    val consecutiveFailures: Int = 0,
    val consecutiveSuccesses: Int = 0,
    val rttMillis: Long? = null,
    val gatewayOnline: Boolean = false,
    val checkedAt: Long = 0L,
    val issue: String? = null,
    val gatewayId: String? = null,
    val callReady: Boolean = false,
    /** Most recent probes, oldest first, at most [NodeSelectionPolicy.QUALITY_SAMPLES]. */
    val recent: List<ProbeSample> = emptyList()
) {
    val healthy: Boolean get() = consecutiveSuccesses > 0

    /** Share of recent probes that were healthy, 0..100; null before any probe. */
    val availabilityPercent: Int? get() =
        if (recent.isEmpty()) null else recent.count { it.ok } * 100 / recent.size

    /** Median round trip of the recent healthy probes. */
    val medianRttMillis: Long? get() {
        val values = recent.filter { it.ok }.mapNotNull { it.rttMillis }.sorted()
        return if (values.isEmpty()) null else values[values.size / 2]
    }
}

/**
 * Pure node choice. Nodes are tried in the user's priority order:
 * - failover: the active node failed [FAILOVER_AFTER_FAILURES] probes in a row
 *   (unreachable, old phone offline there, or too slow) → the first healthy
 *   node in priority order;
 * - failback: a higher-priority node was healthy for [FAILBACK_AFTER_SUCCESSES]
 *   probes in a row → return to it, so a flapping link does not bounce;
 * - never while busy (an SMS submission or a call is in flight).
 */
object NodeSelectionPolicy {
    const val MAX_HEALTHY_RTT_MS = 3_000L
    const val FAILOVER_AFTER_FAILURES = 2
    const val FAILBACK_AFTER_SUCCESSES = 3
    const val QUALITY_SAMPLES = 20

    fun healthy(observation: NodeObservation): Boolean =
        observation.reachable && observation.gatewayOnline &&
            (observation.rttMillis ?: Long.MAX_VALUE) <= MAX_HEALTHY_RTT_MS

    fun record(previous: NodeHealth?, observation: NodeObservation, now: Long): NodeHealth {
        val before = previous ?: NodeHealth()
        val ok = healthy(observation)
        return NodeHealth(
            consecutiveFailures = if (ok) 0 else before.consecutiveFailures + 1,
            consecutiveSuccesses = if (ok) before.consecutiveSuccesses + 1 else 0,
            rttMillis = observation.rttMillis,
            gatewayOnline = observation.gatewayOnline,
            checkedAt = now,
            gatewayId = observation.gatewayId ?: before.gatewayId,
            callReady = ok && observation.callReady,
            recent = (before.recent + ProbeSample(ok, observation.rttMillis, now)).takeLast(QUALITY_SAMPLES),
            issue = observation.issue ?: when {
                !observation.reachable -> "无法连接"
                !observation.gatewayOnline -> "旧手机不在线"
                !ok -> "延迟过高"
                else -> null
            }
        )
    }

    /**
     * @param priority node keys, highest priority first; only nodes that hold a session.
     * @return the node to switch to, or null to stay on [active].
     */
    fun choose(priority: List<String>, active: String?, health: Map<String, NodeHealth>, busy: Boolean): String? {
        if (busy || priority.isEmpty()) return null
        val activeIndex = if (active == null) -1 else priority.indexOf(active)
        if (activeIndex < 0) return priority.firstOrNull { health[it]?.healthy == true }
        for (index in 0 until activeIndex) {
            val key = priority[index]
            if ((health[key]?.consecutiveSuccesses ?: 0) >= FAILBACK_AFTER_SUCCESSES) return key
        }
        val activeHealth = health[active] ?: return null
        if (activeHealth.consecutiveFailures < FAILOVER_AFTER_FAILURES) return null
        return priority.firstOrNull { it != active && health[it]?.healthy == true }
    }
}
