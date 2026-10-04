package com.callagent.host.data

/** Non-secret SIP connection settings; password appears only in credential issuance. */
data class SipConfiguration(
    val available: Boolean,
    val reason: String? = null,
    val endpointId: String? = null,
    val username: String? = null,
    val realm: String? = null,
    val aor: String? = null,
    val registrarUri: String? = null,
    val outboundProxyUri: String? = null,
    val serverName: String? = null,
    val caPem: String? = null,
    val password: String? = null
) {
    // Credentials and SIP URIs must never appear in ordinary diagnostic logs.
    override fun toString(): String = "SipConfiguration(available=$available)"
}

data class CallIntent(
    val intentId: String,
    val callId: String,
    val sipUri: String,
    val expiresAt: String
) {
    override fun toString(): String = "CallIntent(callId=$callId)"
}

data class RemoteCall(
    val callId: String,
    val gatewayId: String,
    val clientId: String?,
    val simId: String,
    val mappingRevision: Long,
    val direction: String,
    val state: String,
    val stateRevision: Long,
    val from: String?,
    val to: String?,
    val createdAt: String,
    val expiresAt: String?,
    val wakeNonce: String? = null,
    val answeredAt: String? = null,
    val endedAt: String? = null,
    val reason: String? = null
) {
    val terminal: Boolean get() = state == "ended"
    override fun toString(): String = "RemoteCall(callId=$callId, state=$state)"
}
