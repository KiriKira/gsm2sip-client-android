package com.callagent.host.data

import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/**
 * Offline copy of the server's `/calls` history, stored as one JSON value in
 * the per-account sync state. Merging keeps the highest `state_revision` per
 * call so a late page never rolls a finished call back to ringing.
 */
object CallHistoryCache {
    fun parse(raw: String): Pair<List<RemoteCall>, String?> {
        val snapshot = JSONObject(raw)
        val items = snapshot.optJSONArray("items") ?: JSONArray()
        val parsed = (0 until items.length()).map { index -> items.getJSONObject(index).toRemoteCall() }
        val cursor = snapshot.optString("next_cursor").takeIf { it.isNotBlank() && it != "null" }
        return parsed to cursor
    }

    fun encode(items: List<RemoteCall>, cursor: String?): String = JSONObject().apply {
        put("next_cursor", cursor)
        put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
    }.toString()

    fun merge(existing: List<RemoteCall>, incoming: List<RemoteCall>): List<RemoteCall> {
        val merged = LinkedHashMap<String, RemoteCall>()
        (existing + incoming).forEach { call ->
            val previous = merged[call.callId]
            if (previous == null || call.stateRevision >= previous.stateRevision) merged[call.callId] = call
        }
        return merged.values.sortedByDescending { timestampMillis(it.createdAt) }
    }

    private fun timestampMillis(value: String): Long = runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)

    private fun RemoteCall.toJson(): JSONObject = JSONObject()
        .put("call_id", callId)
        .put("gateway_id", gatewayId)
        .put("sim_id", simId)
        .put("mapping_revision", mappingRevision)
        .put("direction", direction)
        .put("state", state)
        .put("state_revision", stateRevision)
        .put("from", from)
        .put("to", to)
        .put("created_at", createdAt)
        .put("expires_at", expiresAt)
        .put("answered_at", answeredAt)
        .put("ended_at", endedAt)
        .put("reason", reason)

    private fun JSONObject.toRemoteCall(): RemoteCall = RemoteCall(
        callId = getString("call_id"),
        gatewayId = getString("gateway_id"),
        clientId = null,
        simId = getString("sim_id"),
        mappingRevision = optLong("mapping_revision"),
        direction = optString("direction"),
        state = optString("state"),
        stateRevision = optLong("state_revision"),
        from = cachedString("from"),
        to = cachedString("to"),
        createdAt = optString("created_at"),
        expiresAt = cachedString("expires_at"),
        wakeNonce = null,
        answeredAt = cachedString("answered_at"),
        endedAt = cachedString("ended_at"),
        reason = cachedString("reason"),
    )

    private fun JSONObject.cachedString(key: String): String? = optString(key).takeIf { it.isNotBlank() && it != "null" }
}
