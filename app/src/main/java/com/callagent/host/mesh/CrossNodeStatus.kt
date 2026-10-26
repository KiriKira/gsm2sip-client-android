package com.callagent.host.mesh

import android.content.Context
import com.callagent.host.data.ApiClient
import com.callagent.host.data.ClientDatabase
import com.callagent.host.data.SessionStore
import com.callagent.host.data.SmsStatus
import com.callagent.host.data.databaseName

/**
 * An SMS keeps its later states (dispatching, delivered, failed) only on the
 * node it was submitted to. While another node is active, ask the submitting
 * node directly — every node has its own stored session — so the shared inbox
 * still shows how the message ended.
 */
internal object CrossNodeStatus {
    private val FINAL = setOf(SmsStatus.DELIVERED, SmsStatus.FAILED, SmsStatus.EXPIRED)
    private const val MAX_PER_ROUND = 30
    private val retry = mutableMapOf<String, Pair<Int, Long>>()

    /** Returns how many local records changed. Blocking. */
    fun refresh(context: Context, nodes: List<HostNode>, health: Map<String, NodeHealth>): Int {
        val active = SessionStore(context).read() ?: return 0
        val nodeByGateway = nodes
            .filter { it.sessionInstanceId != active.sessionInstanceId }
            .mapNotNull { node ->
                val state = health[node.key]?.takeIf { it.healthy } ?: return@mapNotNull null
                state.gatewayId?.let { it to node }
            }.toMap()
        if (nodeByGateway.isEmpty()) return 0
        val database = ClientDatabase(context, active.databaseName(), active.nodeScope)
        try {
            val pending = database.loadOutboundTasks()
                .filter { it.serverId != null && it.status !in FINAL && it.gatewayId in nodeByGateway }
                .takeLast(MAX_PER_ROUND)
            val liveIds = pending.map { it.serverId!! }.toSet()
            retry.keys.retainAll(liveIds)
            var changed = 0
            pending.groupBy { it.gatewayId!! }.forEach { (gatewayId, records) ->
                val node = nodeByGateway.getValue(gatewayId)
                val api = ApiClient(node.apiBaseUrl, HostNodes.storeFor(context, node), 4_000, 6_000)
                for (record in records) {
                    val id = record.serverId!!
                    val now = android.os.SystemClock.elapsedRealtime()
                    val previous = retry[id]
                    if (previous != null && now < previous.second) continue
                    val remote = runCatching { api.getMessage(id) }.getOrNull()
                    val attempts = ((previous?.first ?: 0) + 1).coerceAtMost(4)
                    retry[id] = attempts to (now + (30_000L shl (attempts - 1)).coerceAtMost(300_000L))
                    if (remote == null) continue
                    if (remote.status != record.status || remote.parts != record.parts || remote.partCount != record.partCount) {
                        database.upsertRemoteMessage(remote)
                        retry.remove(id)
                        changed++
                    }
                }
            }
            return changed
        } finally {
            database.close()
        }
    }
}
