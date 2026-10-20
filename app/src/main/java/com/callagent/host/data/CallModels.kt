package com.callagent.host.data

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
    val answeredAt: String? = null,
    val endedAt: String? = null,
    val reason: String? = null
) {
    val terminal: Boolean get() = state == "ended"
    override fun toString(): String = "RemoteCall(callId=$callId, state=$state)"
}

fun RemoteCall.localTerminalNotice(): String? = when {
    state != "ended" -> null
    reason == "answered_elsewhere" -> "另一台主机已接听此来电。"
    else -> null
}
