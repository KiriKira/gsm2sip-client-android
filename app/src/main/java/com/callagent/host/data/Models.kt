package com.callagent.host.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class HostSession @JvmOverloads constructor(
    val sessionInstanceId: String,
    val apiBaseUrl: String,
    val ownerId: String,
    val deviceId: String,
    val role: String,
    val accessToken: String,
    val accessExpiresAt: String,
    val refreshToken: String,
    val refreshExpiresAt: String,
    val pendingRefreshKey: String? = null,
    /**
     * Set when this pairing is one node of a multi-node (mesh) profile. Every
     * node of a profile shares one local database and background binding; it
     * is the first node's session instance ID, so converting a single pairing
     * into a profile changes neither.
     */
    val profileId: String? = null,
    /** The profile's shared database file; null for a standalone pairing. */
    val profileDatabase: String? = null,
    /** Separates this node's server cursors inside the shared database; "" for the first node. */
    val nodeScope: String = ""
)

/** Identity of the local account data: the mesh profile, or the pairing itself. */
val HostSession.profileKey: String get() = profileId ?: sessionInstanceId

fun HostSession.databaseName(): String = profileDatabase ?: clientDatabaseName(apiBaseUrl, ownerId, deviceId)

/** True when both sessions share local data, including two nodes of one mesh profile. */
fun HostSession.sameProfile(other: HostSession): Boolean =
    profileKey == other.profileKey && databaseName() == other.databaseName()

fun HostSession.sameSessionInstance(other: HostSession): Boolean =
    sessionInstanceId == other.sessionInstanceId &&
        apiBaseUrl == other.apiBaseUrl &&
        ownerId == other.ownerId &&
        deviceId == other.deviceId &&
        role == other.role

data class GatewaySnapshot @JvmOverloads constructor(
    val gatewayId: String,
    val deviceName: String,
    val online: Boolean,
    val lastSeenAt: String?,
    val mappingRevision: Long,
    val root: Boolean?,
    /** The old phone currently advertises call control, so calls can go through it. */
    val voiceReady: Boolean?,
    val batteryPercent: Int?,
    val charging: Boolean?,
    /** The old phone's stable identity across server nodes (mesh extension). */
    val installId: String? = null
)

/** A host paired to the same owner account as the current client session. */
data class PairedHost(
    val id: String,
    val name: String,
    val platform: String,
    val state: String,
    val isSelf: Boolean
)

fun PairedHost.pairingStateLabel(): String = when (state) {
    "active" -> "已配对"
    "revoked" -> "已撤销"
    else -> state.replace('_', ' ').ifBlank { "状态未知" }
}

data class SimLine @JvmOverloads constructor(
    val simId: String,
    val slotIndex: Int,
    val label: String,
    val carrierName: String?,
    val phoneNumber: String?,
    val state: String,
    val mappingRevision: Long,
    val identityVerified: Boolean,
    val serviceState: String?,
    /** Latest radio observation the gateway reported for this SIM, if the server relays it. */
    val radio: SimRadio? = null
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

/**
 * Display-only radio telemetry for one gateway SIM. Values are whatever the old
 * phone's Android reported; any member may be unknown. It never affects which
 * SIM a task uses.
 */
data class SimRadio(
    val signalLevel: Int?,
    val networkType: String?,
    val networkOperator: String?,
    val roaming: Boolean?,
    val updatedAt: String?,
    /**
     * ISO 3166-1 alpha-2 country of the SIM, used to normalize SMS peers. Older
     * servers and gateways never send it.
     */
    val countryIso: String? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("signal_level", signalLevel ?: JSONObject.NULL)
        .put("network_type", networkType ?: JSONObject.NULL)
        .put("network_operator", networkOperator ?: JSONObject.NULL)
        .put("roaming", roaming ?: JSONObject.NULL)
        .put("updated_at", updatedAt ?: JSONObject.NULL)
        .put("country_iso", countryIso ?: JSONObject.NULL)

    companion object {
        private val COUNTRY_ISO = Regex("^[A-Z]{2}$")

        /** Parses the server's `radio` object; unknown or malformed members become null. */
        fun fromJson(json: JSONObject?): SimRadio? {
            if (json == null) return null
            fun text(name: String, max: Int) = if (json.isNull(name)) null else json.optString(name)
                .trim().takeIf { it.isNotEmpty() && it.length <= max && it.none(Char::isISOControl) }
            return SimRadio(
                signalLevel = if (json.isNull("signal_level")) null else json.optInt("signal_level", -1).takeIf { it in 0..4 },
                networkType = text("network_type", 16),
                networkOperator = text("network_operator", 64),
                roaming = if (json.isNull("roaming") || !json.has("roaming")) null else json.optBoolean("roaming"),
                updatedAt = text("updated_at", 64),
                // Exactly two uppercase letters; anything else is treated as unknown.
                countryIso = if (json.isNull("country_iso")) null else json.optString("country_iso").takeIf { COUNTRY_ISO.matches(it) }
            )
        }
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
    val status: String = "submitting",
    /**
     * Global message ID minted by this phone when every server node supports
     * the mesh extension. The old phone executes one message ID at most once,
     * whichever node delivers it.
     */
    val messageId: String? = null
) {
    fun retryEnvelope(): RetryEnvelope = RetryEnvelope(
        idempotencyKey = idempotencyKey,
        gatewayId = gatewayId,
        simId = simId,
        mappingRevision = mappingRevision,
        to = to,
        text = text,
        messageId = messageId
    )
}

data class RetryEnvelope(
    val idempotencyKey: String,
    val gatewayId: String,
    val simId: String,
    val mappingRevision: Long,
    val to: String,
    val text: String,
    val savedRequestBody: String? = null,
    val messageId: String? = null
) {
    fun requestFields(): Map<String, Any> = buildMap {
        put("gateway_id", gatewayId)
        put("sim_id", simId)
        put("mapping_revision", mappingRevision)
        put("to", to)
        put("text", text)
        put("ttl_seconds", 300)
        messageId?.let { put("message_id", it) }
    }

    /**
     * The same message for another server node: that node's gateway ID and SIM
     * revision under a fresh idempotency key, keeping the global message ID so
     * the old phone still sends it at most once.
     */
    fun forNode(gatewayId: String, mappingRevision: Long, idempotencyKey: String = newTaskKey()): RetryEnvelope {
        requireNotNull(messageId) { "only mesh tasks can move between nodes" }
        return copy(idempotencyKey = idempotencyKey, gatewayId = gatewayId,
            mappingRevision = mappingRevision, savedRequestBody = null)
    }

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
        savedRequestBody = saved,
        messageId = json.optString("message_id").takeIf { it.isNotBlank() && it != "null" }
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
