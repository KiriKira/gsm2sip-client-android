package com.callagent.host.data

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

data class EventPage(
    val messages: List<SmsRecord>,
    val nextCursor: String?,
    val lastItemCursor: String?,
    val resyncRequired: Boolean
)

class ApiFailure(
    val httpStatus: Int,
    val code: String,
    val retryable: Boolean
) : IOException(if (code.isBlank()) "HTTP $httpStatus" else "$httpStatus $code")

class CallingNotReady : IOException("CALLING_NOT_READY")
class SessionNeedsPairing : IOException("SESSION_REVOKED")
class SessionChanged : IOException("SESSION_CHANGED")

class ApiClient(
    private val configuredBaseUrl: String,
    private val sessionStore: SessionStore,
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 20_000
) {
    private val apiRoot: String = normalizeBaseUrl(configuredBaseUrl)
    private val boundSession: HostSession? = sessionStore.read()

    fun pair(pairingCode: String, deviceName: String): HostSession {
        val expectedSession = sessionStore.read()
        if ((boundSession == null) != (expectedSession == null) ||
            (boundSession != null && expectedSession != null && !boundSession.sameSessionInstance(expectedSession))) {
            throw SessionChanged()
        }
        val response = publicRequest(
            "POST", "/pairings/claim",
            JSONObject().put("pairing_code", pairingCode).put("device_name", deviceName)
        )
        val result = sessionFromPairing(response)
        if (result.role != "client") throw IOException("Pairing code is not for a host client")
        if (!sessionStore.writeIfCurrent(expectedSession, result)) throw SessionChanged()
        return result
    }

    fun revoke() = synchronized(refreshLock) {
        val session = sessionStore.read() ?: return@synchronized
        val bound = boundSession ?: throw SessionChanged()
        if (!bound.sameSessionInstance(session) || session.apiBaseUrl != apiRoot) throw SessionChanged()
        publicRequest("POST", "/auth/revoke", JSONObject().put("refresh_token", session.refreshToken), expectBody = false)
        if (!sessionStore.clearIfCurrent(session)) throw SessionChanged()
    }

    fun listGateways(): List<GatewaySnapshot> {
        val json = authenticatedRequest("GET", "/gateways")
        val items = json.optJSONArray("items") ?: JSONArray()
        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optNullableString("gateway_id") ?: return@mapNotNull null
            GatewaySnapshot(
                gatewayId = id,
                deviceName = item.optNullableString("device_name") ?: "Gateway",
                online = item.optBoolean("online", false),
                lastSeenAt = item.optNullableString("last_seen_at"),
                mappingRevision = item.optLong("mapping_revision", 0L),
                root = item.optNullableBoolean("root"),
                sipRegistered = item.optNullableBoolean("sip_registered"),
                batteryPercent = item.optNullableInt("battery_percent"),
                charging = item.optNullableBoolean("charging")
            )
        }
    }

    fun listSims(gatewayId: String): Pair<Long, List<SimLine>> {
        val json = authenticatedRequest("GET", "/gateways/${pathSegment(gatewayId)}/sims")
        val revision = json.optLong("mapping_revision", 0L)
        val items = json.optJSONArray("sims") ?: JSONArray()
        val lines = (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val simId = item.optNullableString("sim_id") ?: return@mapNotNull null
            SimLine(
                simId = simId,
                slotIndex = item.optInt("slot_index", index),
                label = item.optNullableString("label") ?: "SIM ${index + 1}",
                carrierName = item.optNullableString("carrier_name"),
                phoneNumber = item.optNullableString("phone_number"),
                state = item.optNullableString("state") ?: "unverified",
                mappingRevision = item.optLong("mapping_revision", revision),
                identityVerified = item.optBoolean("identity_verified", false),
                serviceState = item.optNullableString("service_state")
            )
        }
        return revision to lines
    }

    fun listMessages(cursor: String? = null, simId: String? = null, limit: Int = 100): MessagePage {
        val query = mutableListOf("limit=$limit")
        cursor?.let { query += "cursor=${Uri.encode(it)}" }
        simId?.let { query += "sim_id=${Uri.encode(it)}" }
        val json = authenticatedRequest("GET", "/messages?${query.joinToString("&")}")
        return MessagePage(
            items = parseMessageArray(MessageWireValidation.messageItems(json)),
            nextCursor = json.optNullableString("next_cursor"),
            resyncRequired = false
        )
    }

    fun listEvents(cursor: String?): EventPage {
        val suffix = cursor?.let { "?cursor=${Uri.encode(it)}&limit=100" } ?: "?limit=100"
        val json = authenticatedRequest("GET", "/events$suffix")
        val items = MessageWireValidation.eventItems(json, cursor)
        val messages = buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                val message = item.getJSONObject("message")
                add(parseMessage(message) ?: throw IOException("Invalid durable message"))
            }
        }
        return EventPage(
            messages = messages,
            nextCursor = json.optNullableString("next_cursor"),
            lastItemCursor = items.optJSONObject(items.length() - 1)?.optNullableString("cursor"),
            resyncRequired = json.optBoolean("resync_required", false)
        )
    }

    fun getMessage(messageId: String): SmsRecord? {
        val json = authenticatedRequest("GET", "/messages/${pathSegment(messageId)}")
        return parseMessage(json)
    }

    /** Call only with ClientDatabase.eventCursor() after its transaction commits. */
    fun acknowledgeEventCursor(durableCursor: String) {
        authenticatedRequest("POST", "/events/ack", JSONObject().put("durable_cursor", durableCursor))
    }

    fun getSipConfiguration(): SipConfiguration = parseSipConfiguration(
        authenticatedRequest("GET", "/devices/self/sip-config")
    )

    fun rotateSipCredentials(idempotencyKey: String): SipConfiguration = parseSipConfiguration(
        authenticatedRequest("POST", "/devices/self/sip-credentials/rotate", JSONObject(), idempotencyKey)
    )

    fun listCalls(): List<RemoteCall> {
        val items = authenticatedRequest("GET", "/calls").optJSONArray("items") ?: JSONArray()
        return (0 until items.length()).map { parseCall(items.getJSONObject(it)) }
    }

    fun getCall(callId: String): RemoteCall = parseCall(authenticatedRequest("GET", "/calls/${pathSegment(callId)}"))

    fun createCallIntent(gatewayId: String, simId: String, mappingRevision: Long, destination: String, idempotencyKey: String): CallIntent {
        val json = authenticatedRequest("POST", "/call-intents", JSONObject()
            .put("gateway_id", gatewayId).put("sim_id", simId)
            .put("mapping_revision", mappingRevision).put("to", destination), idempotencyKey)
        return CallIntent(json.getString("intent_id"), json.getString("call_id"), json.getString("sip_uri"), json.getString("expires_at"))
    }

    fun cancelCallIntent(intentId: String) {
        authenticatedRequest("DELETE", "/call-intents/${pathSegment(intentId)}")
    }

    fun ready(callId: String, wakeNonce: String): RemoteCall = parseCall(authenticatedRequest(
        "POST", "/clients/${pathSegment(currentBoundSession().deviceId)}/ready",
        JSONObject().put("call_id", callId).put("wake_nonce", wakeNonce)
    ))

    private fun parseSipConfiguration(json: JSONObject) = SipConfiguration(
        available = json.optBoolean("available", false), reason = json.optNullableString("reason"),
        endpointId = json.optNullableString("endpoint_id"), username = json.optNullableString("auth_username"),
        realm = json.optNullableString("auth_realm"), aor = json.optNullableString("aor"),
        registrarUri = json.optNullableString("registrar_uri"), outboundProxyUri = json.optNullableString("outbound_proxy_uri"),
        serverName = json.optNullableString("server_name"), caPem = json.optNullableString("ca_pem"),
        password = json.optNullableString("password")
    )

    private fun parseCall(json: JSONObject) = RemoteCall(
        callId = json.getString("call_id"), gatewayId = json.getString("gateway_id"),
        clientId = json.optNullableString("client_id"), simId = json.getString("sim_id"),
        mappingRevision = json.getLong("mapping_revision"), direction = json.getString("direction"),
        state = json.getString("state"), stateRevision = json.getLong("state_revision"),
        from = json.optNullableString("from"), to = json.optNullableString("to"),
        createdAt = json.getString("created_at"), expiresAt = json.optNullableString("expires_at"),
        wakeNonce = json.optNullableString("wake_nonce"), answeredAt = json.optNullableString("answered_at"),
        endedAt = json.optNullableString("ended_at"), reason = json.optNullableString("reason")
    )

    fun createMessage(envelope: RetryEnvelope): MessageAccepted {
        val json = authenticatedRequest(
            "POST", "/messages", idempotencyKey = envelope.idempotencyKey,
            rawBody = envelope.requestBody()
        )
        return MessageAccepted(
            messageId = json.getString("message_id"),
            commandId = json.getString("command_id"),
            status = json.optString("status", SmsStatus.QUEUED),
            expiresAt = json.optString("expires_at", ""),
            partCount = json.optNullableInt("part_count")
        )
    }

    private fun authenticatedRequest(
        method: String,
        path: String,
        body: JSONObject? = null,
        idempotencyKey: String? = null,
        rawBody: String? = null
    ): JSONObject {
        refreshIfExpiring()
        val first = currentBoundSession()
        try {
            return request(method, path, body, first.accessToken, idempotencyKey, rawBody = rawBody)
        } catch (failure: ApiFailure) {
            if (failure.httpStatus != 401) throw failure
            refreshSession(expected = first)
            val second = currentBoundSession()
            return request(method, path, body, second.accessToken, idempotencyKey, rawBody = rawBody)
        }
    }

    private fun refreshIfExpiring() {
        synchronized(refreshLock) {
            val current = currentBoundSession()
            val expires = parseInstant(current.accessExpiresAt)
            if (expires != null && expires.isAfter(Instant.now().plusSeconds(45))) return
            refreshSessionLocked(current)
        }
    }

    private fun refreshSession(expected: HostSession) {
        synchronized(refreshLock) {
            val current = currentBoundSession()
            if (!current.sameSessionInstance(expected)) throw SessionChanged()
            if (current.accessToken != expected.accessToken) return
            refreshSessionLocked(current)
        }
    }

    private fun refreshSessionLocked(current: HostSession) {
        val pending = sessionStore.beginRefresh(current) ?: throw SessionChanged()
        val key = pending.pendingRefreshKey ?: throw IOException("Could not persist refresh retry key")
        val json = try {
            publicRequest("POST", "/auth/refresh", JSONObject().put("refresh_token", pending.refreshToken), idempotencyKey = key)
        } catch (failure: ApiFailure) {
            if (failure.httpStatus != 401) throw failure
            if (sessionStore.clearIfCurrent(pending)) throw SessionNeedsPairing()
            throw SessionChanged()
        }
        val updated = sessionStore.updateTokens(
                expected = pending,
                access = json.getString("access_token"),
                accessExpiry = json.getString("access_expires_at"),
                refresh = json.getString("refresh_token"),
                refreshExpiry = json.getString("refresh_expires_at")
            ) ?: throw SessionChanged()
        if (parseInstant(updated.refreshExpiresAt)?.isBefore(Instant.now()) == true) {
            if (sessionStore.clearIfCurrent(updated)) throw SessionNeedsPairing()
            throw SessionChanged()
        }
    }

    private fun publicRequest(
        method: String,
        path: String,
        body: JSONObject,
        expectBody: Boolean = true,
        idempotencyKey: String? = null
    ): JSONObject = request(method, path, body, token = null, idempotencyKey = idempotencyKey, expectBody = expectBody)

    private fun request(
        method: String,
        path: String,
        body: JSONObject?,
        token: String?,
        idempotencyKey: String?,
        expectBody: Boolean = true,
        rawBody: String? = null
    ): JSONObject {
        val connection = (URL(apiRoot + path).openConnection() as HttpsURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", "application/json")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
            idempotencyKey?.let { setRequestProperty("Idempotency-Key", it) }
            if (body != null || rawBody != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null || rawBody != null) connection.outputStream.use { output ->
                output.write((rawBody ?: body!!.toString()).toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            if (status in 300..399) throw IOException("HTTPS endpoint redirected; check the configured server URL")
            if (status !in 200..299) {
                val errorBody = runCatching { connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } }.getOrNull()
                val code = runCatching {
                    JSONObject(errorBody ?: "{}").optJSONObject("error")?.optString("code").orEmpty()
                }.getOrDefault("")
                val retryable = runCatching {
                    JSONObject(errorBody ?: "{}").optJSONObject("error")?.optBoolean("retryable", false) ?: false
                }.getOrDefault(false)
                throw ApiFailure(status, code, retryable)
            }
            if (!expectBody || status == HttpsURLConnection.HTTP_NO_CONTENT) return JSONObject()
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun sessionFromPairing(json: JSONObject): HostSession {
        val sip = json.optJSONObject("sip") ?: JSONObject()
        return HostSession(
            sessionInstanceId = UUID.randomUUID().toString(),
            apiBaseUrl = apiRoot,
            ownerId = json.getString("owner_id"),
            deviceId = json.getString("device_id"),
            role = json.getString("role"),
            accessToken = json.getString("access_token"),
            accessExpiresAt = json.getString("access_expires_at"),
            refreshToken = json.getString("refresh_token"),
            refreshExpiresAt = json.getString("refresh_expires_at"),
            sipAvailable = sip.optBoolean("available", false),
            sipReason = sip.optNullableString("reason")
        )
    }

    private fun parseMessageArray(array: JSONArray?): List<SmsRecord> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { parseMessage(array.optJSONObject(it)) }
    }

    private fun parseMessage(json: JSONObject?): SmsRecord? {
        if (json == null) return null
        val serverId = json.optNullableString("message_id") ?: return null
        val parts = parseSmsParts(json.optJSONArray("parts"))
        return SmsRecord(
            localId = "remote:$serverId",
            serverId = serverId,
            commandId = json.optNullableString("command_id"),
            simId = json.optNullableString("sim_id"),
            mappingRevision = json.optNullableLong("mapping_revision"),
            direction = json.optString("direction", "inbound"),
            from = json.optNullableString("from"),
            to = json.optNullableString("to"),
            text = json.optString("text", ""),
            status = json.optString("status", SmsStatus.UNKNOWN),
            createdAt = json.optNullableString("created_at") ?: Instant.now().toString(),
            expiresAt = json.optNullableString("expires_at"),
            partCount = json.optNullableInt("part_count"),
            parts = parts,
            taskKey = null,
            gatewayId = json.optNullableString("gateway_id"),
            idempotencyBody = null
        )
    }

    private fun normalizeBaseUrl(value: String): String {
        val uri = runCatching { java.net.URI(value.trim()) }.getOrElse { throw IOException("Enter a valid HTTPS server URL") }
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null) {
            throw IOException("Server URL must use HTTPS and contain no credentials or query")
        }
        val path = uri.rawPath.orEmpty().trimEnd('/')
        val originAndPath = "https://${uri.rawAuthority}$path"
        return if (path.endsWith("/v1")) originAndPath else "$originAndPath/v1"
    }

    private fun pathSegment(value: String): String = Uri.encode(value)

    private fun parseInstant(value: String): Instant? = runCatching {
        runCatching { Instant.parse(value) }.getOrElse { OffsetDateTime.parse(value).toInstant() }
    }.getOrNull()

    private fun currentBoundSession(): HostSession {
        val current = sessionStore.read() ?: throw SessionNeedsPairing()
        val bound = boundSession ?: throw SessionChanged()
        if (!bound.sameSessionInstance(current)) throw SessionChanged()
        if (current.apiBaseUrl != apiRoot) throw SessionChanged()
        return current
    }

    companion object {
        private val refreshLock = Any()
    }
}

private fun JSONObject.optNullableString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return optString(name).takeIf { it.isNotBlank() && it != "null" }
}

private fun JSONObject.optNullableLong(name: String): Long? =
    if (!has(name) || isNull(name)) null else optLong(name)

private fun JSONObject.optNullableInt(name: String): Int? =
    if (!has(name) || isNull(name)) null else optInt(name)

private fun JSONObject.optNullableBoolean(name: String): Boolean? =
    if (!has(name) || isNull(name)) null else optBoolean(name)

data class MessagePage(val items: List<SmsRecord>, val nextCursor: String?, val resyncRequired: Boolean)
