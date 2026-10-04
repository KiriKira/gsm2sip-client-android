package com.callagent.host.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class HostSession(
    val sessionInstanceId: String,
    val apiBaseUrl: String,
    val ownerId: String,
    val deviceId: String,
    val role: String,
    val accessToken: String,
    val accessExpiresAt: String,
    val refreshToken: String,
    val refreshExpiresAt: String,
    val sipAvailable: Boolean,
    val sipReason: String?,
    val pendingRefreshKey: String? = null
)

fun HostSession.sameSessionInstance(other: HostSession): Boolean =
    sessionInstanceId == other.sessionInstanceId &&
        apiBaseUrl == other.apiBaseUrl &&
        ownerId == other.ownerId &&
        deviceId == other.deviceId &&
        role == other.role

data class GatewaySnapshot(
    val gatewayId: String,
    val deviceName: String,
    val online: Boolean,
    val lastSeenAt: String?,
    val mappingRevision: Long,
    val root: Boolean?,
    val sipRegistered: Boolean?,
    val batteryPercent: Int?,
    val charging: Boolean?
)

data class SimLine(
    val simId: String,
    val slotIndex: Int,
    val label: String,
    val carrierName: String?,
    val phoneNumber: String?,
    val state: String,
    val mappingRevision: Long,
    val identityVerified: Boolean,
    val serviceState: String?
) {
    val canSend: Boolean get() = state == "active" && identityVerified

    override fun toString(): String {
        val number = phoneNumber?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        val warning = if (canSend) "" else " · ${stateLabel()}"
        return "$label$number$warning"
    }

    fun stateLabel(): String = when {
        !identityVerified -> "identity unverified"
        state == "pending_local_confirmation" -> "confirmation pending"
        state == "removed" -> "removed"
        state == "unverified" -> "unverified"
        else -> state
    }
}

data class SmsPart(val index: Int, val state: String, val resultCode: Int?, val error: String?)

data class SmsRecord(
    val localId: String,
    val serverId: String?,
    val commandId: String?,
    val simId: String?,
    val mappingRevision: Long?,
    val direction: String,
    val from: String?,
    val to: String?,
    val text: String,
    val status: String,
    val createdAt: String,
    val expiresAt: String?,
    val partCount: Int?,
    val parts: List<SmsPart>,
    val taskKey: String?,
    val gatewayId: String?,
    val idempotencyBody: String?
) {
    fun peerAddress(): String = if (direction == "inbound") from.orEmpty() else to.orEmpty()
}

data class SmsDraft(val simId: String, val recipient: String, val text: String)

data class OutboundTask(
    val localId: String,
    val idempotencyKey: String,
    val gatewayId: String,
    val simId: String,
    val mappingRevision: Long,
    val to: String,
    val text: String,
    val createdAt: String,
    val status: String = "submitting"
) {
    fun retryEnvelope(): RetryEnvelope = RetryEnvelope(
        idempotencyKey = idempotencyKey,
        gatewayId = gatewayId,
        simId = simId,
        mappingRevision = mappingRevision,
        to = to,
        text = text
    )
}

data class RetryEnvelope(
    val idempotencyKey: String,
    val gatewayId: String,
    val simId: String,
    val mappingRevision: Long,
    val to: String,
    val text: String,
    val savedRequestBody: String? = null
) {
    fun requestFields(): Map<String, Any> = mapOf(
        "gateway_id" to gatewayId,
        "sim_id" to simId,
        "mapping_revision" to mappingRevision,
        "to" to to,
        "text" to text,
        "ttl_seconds" to 300
    )

    fun requestBody(): String = savedRequestBody ?: JSONObject(requestFields()).toString()

    fun requestJson(): JSONObject = JSONObject(requestBody())
}

fun SmsRecord.toRetryEnvelope(): RetryEnvelope {
    val saved = idempotencyBody ?: error("missing saved request body")
    val json = JSONObject(saved)
    val envelope = RetryEnvelope(
        idempotencyKey = taskKey ?: error("missing task key"),
        gatewayId = gatewayId ?: error("missing gateway id"),
        simId = simId ?: error("missing SIM id"),
        mappingRevision = mappingRevision ?: error("missing mapping revision"),
        to = to ?: error("missing recipient"),
        text = text,
        savedRequestBody = saved
    )
    require(json.optString("gateway_id") == envelope.gatewayId)
    require(json.optString("sim_id") == envelope.simId)
    require(json.optLong("mapping_revision", -1L) == envelope.mappingRevision)
    require(json.optString("to") == envelope.to)
    require(json.optString("text") == envelope.text)
    return envelope
}

object SmsStatus {
    const val SUBMITTING = "submitting"
    const val QUEUED = "queued"
    const val ACCEPTED = "accepted_by_gateway"
    const val DISPATCHING = "dispatching"
    const val SUBMITTED = "submitted"
    const val DELIVERED = "delivered"
    const val FAILED = "failed"
    const val EXPIRED = "expired"
    const val UNKNOWN = "unknown"
    const val RECEIVED = "received"

    fun display(status: String): String = when (status) {
        SUBMITTING -> "Submitting…"
        QUEUED -> "Committed by server · queued for gateway"
        ACCEPTED -> "Accepted by gateway"
        DISPATCHING -> "Sending through mobile network"
        SUBMITTED -> "Submitted to mobile network"
        DELIVERED -> "Delivered (carrier receipt)"
        FAILED -> "Failed"
        EXPIRED -> "Expired before sending"
        UNKNOWN -> "Result unknown · check this saved submission before trying again"
        RECEIVED -> "Received"
        else -> status.replace('_', ' ')
    }
}

fun newTaskKey(): String = UUID.randomUUID().toString()

fun parseSmsParts(array: JSONArray?): List<SmsPart> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val part = array.optJSONObject(index) ?: return@mapNotNull null
        SmsPart(
            index = part.optInt("part_index", index),
            state = part.optString("state", "unknown"),
            resultCode = part.takeIf { it.has("result_code") && !it.isNull("result_code") }
                ?.optInt("result_code"),
            error = part.optString("error").takeIf { it.isNotBlank() && it != "null" }
        )
    }
}

fun partsAsJson(parts: List<SmsPart>): String {
    val array = JSONArray()
    parts.forEach { part ->
        array.put(JSONObject()
            .put("part_index", part.index)
            .put("state", part.state)
            .put("result_code", part.resultCode)
            .put("error", part.error))
    }
    return array.toString()
}
