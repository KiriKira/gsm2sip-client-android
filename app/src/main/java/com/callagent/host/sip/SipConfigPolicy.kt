package com.callagent.host.sip

import com.callagent.host.data.SipConfiguration
import java.net.IDN
import java.util.regex.Pattern

/** The one signalling transport an account uses. URIs of the other transport are rejected. */
internal enum class SipTransport { TLS, UDP }

/**
 * Server SIP settings checked before any native PJSUA2 object exists. A TLS account accepts only
 * `sips:`/`transport=tls` URIs; a UDP account (self-hosted LAN/VPN nodes) only `sip:` URIs with
 * no transport or `transport=udp`. The two are never mixed.
 */
internal data class ValidatedSipConfig(
    val transport: SipTransport,
    val mediaEncrypted: Boolean,
    val username: String,
    val realm: String,
    val aor: String,
    val registrarUri: String,
    val proxyUri: String,
    val serverName: String?,
    val customCaPem: String?,
    val password: String
) {
    override fun toString(): String = "ValidatedSipConfig(transport=$transport, mediaEncrypted=$mediaEncrypted)"

    companion object {
        fun from(config: SipConfiguration): ValidatedSipConfig {
            require(config.available) { "SIP is unavailable" }
            val transport = when (config.transport.lowercase()) {
                SipConfiguration.TRANSPORT_TLS -> SipTransport.TLS
                SipConfiguration.TRANSPORT_UDP -> SipTransport.UDP
                else -> throw IllegalArgumentException("Unsupported SIP transport")
            }
            val mediaEncrypted = when (config.mediaEncryption.lowercase()) {
                SipConfiguration.MEDIA_ENCRYPTION_SDES -> true
                SipConfiguration.MEDIA_ENCRYPTION_NONE -> false
                else -> throw IllegalArgumentException("Unsupported SIP media encryption")
            }
            val username = config.username.requireValue("username")
            val realm = config.realm.requireValue("realm")
            val aor = config.aor.requireValue("AOR")
            val registrar = config.registrarUri.requireValue("registrar")
            val password = config.password.requireValue("credential")
            require(username.length <= 256 && username.none { it.isISOControl() })
            require(realm.length <= 253 && realm.none { it.isISOControl() })
            return when (transport) {
                SipTransport.TLS -> fromTls(config, mediaEncrypted, username, realm, aor, registrar, password)
                SipTransport.UDP -> fromUdp(config, mediaEncrypted, username, realm, aor, registrar, password)
            }
        }

        private fun fromTls(
            config: SipConfiguration, mediaEncrypted: Boolean, username: String, realm: String,
            aor: String, registrar: String, password: String
        ): ValidatedSipConfig {
            val proxy = config.outboundProxyUri.requireValue("TLS proxy")
            val serverName = normalizeHost(config.serverName.requireValue("TLS server name"))

            val parsedAor = parseSipUri(aor)
            require(parsedAor.scheme == "sips" && parsedAor.user.isNotBlank()) { "AOR must be a SIPS identity" }
            val parsedRegistrar = parseSipUri(registrar)
            require(parsedRegistrar.scheme == "sips" && hasNoOrTlsTransport(registrar)) { "Registrar must use TLS" }
            val parsedProxy = parseSipUri(proxy)
            val secureProxy = parsedProxy.transportTls ||
                (parsedProxy.scheme == "sips" && hasNoOrTlsTransport(proxy))
            require(secureProxy && parsedProxy.user.isEmpty()) { "Proxy must use TLS" }
            require('?' !in registrar && '?' !in proxy) { "SIP configuration URIs cannot contain headers" }
            require(normalizeHost(parsedProxy.host) == serverName) {
                "TLS server name must identify the configured outbound proxy"
            }
            val ca = config.caPem?.trim()?.takeIf { it.isNotEmpty() }
            if (ca != null) require(ca.contains("-----BEGIN CERTIFICATE-----")) { "Custom CA is not PEM" }

            return ValidatedSipConfig(
                SipTransport.TLS,
                mediaEncrypted,
                username,
                realm,
                aor,
                ensureTlsParam(registrar),
                ensureLooseRoute(ensureTlsParam(proxy)),
                serverName,
                ca,
                password
            )
        }

        private fun fromUdp(
            config: SipConfiguration, mediaEncrypted: Boolean, username: String, realm: String,
            aor: String, registrar: String, password: String
        ): ValidatedSipConfig {
            val proxy = config.outboundProxyUri.requireValue("proxy")
            val parsedAor = parseSipUri(aor)
            require(parsedAor.scheme == "sip" && parsedAor.user.isNotBlank() && hasNoOrUdpTransport(aor)) {
                "AOR must be a SIP identity"
            }
            val parsedRegistrar = parseSipUri(registrar)
            require(parsedRegistrar.scheme == "sip" && hasNoOrUdpTransport(registrar)) { "Registrar must use UDP" }
            val parsedProxy = parseSipUri(proxy)
            require(parsedProxy.scheme == "sip" && hasNoOrUdpTransport(proxy) && parsedProxy.user.isEmpty()) {
                "Proxy must use UDP"
            }
            require('?' !in registrar && '?' !in proxy) { "SIP configuration URIs cannot contain headers" }

            return ValidatedSipConfig(
                SipTransport.UDP,
                mediaEncrypted,
                username,
                realm,
                aor,
                ensureUdpParam(registrar),
                ensureLooseRoute(ensureUdpParam(proxy)),
                serverName = null,
                customCaPem = null,
                password = password
            )
        }
    }
}

internal object SipUriPolicy {
    private val SIP_URI = Pattern.compile(
        "^(sips?):(?:([^@;?]+)@)?(\\[[^]]+]|[^;:?]+)(?::([0-9]{1,5}))?(?:;([^?]*))?(?:\\?.*)?$",
        Pattern.CASE_INSENSITIVE
    )
    private val TLS_CALL_INTENT_URI = Pattern.compile(
        "^sips:call\\.([A-Za-z0-9._~-]{1,180})@gsm2sip;transport=tls$",
        Pattern.CASE_INSENSITIVE
    )
    private val UDP_CALL_INTENT_URI = Pattern.compile(
        "^sip:call\\.([A-Za-z0-9._~-]{1,180})@gsm2sip;transport=udp$",
        Pattern.CASE_INSENSITIVE
    )

    /** The call-intent URI must use the same transport as the registered account. */
    fun validateCallIntentUri(uri: String, transport: SipTransport) {
        val pattern = when (transport) {
            SipTransport.TLS -> TLS_CALL_INTENT_URI
            SipTransport.UDP -> UDP_CALL_INTENT_URI
        }
        require(pattern.matcher(uri).matches()) {
            "Call intents must use the configured SIP token URI"
        }
    }

    fun parse(uri: String): SipUriParts {
        val match = SIP_URI.matcher(uri.trim())
        require(match.matches()) { "Invalid SIP URI" }
        val params = match.group(5).orEmpty().split(';').map { it.substringBefore('=').lowercase() }
        return SipUriParts(
            scheme = match.group(1).orEmpty().lowercase(),
            user = match.group(2).orEmpty(),
            host = match.group(3).orEmpty().removePrefix("[").removeSuffix("]"),
            transportTls = "transport" in params && Regex("(?:^|;)transport=tls(?:;|$)", RegexOption.IGNORE_CASE).containsMatchIn(uri)
        )
    }
}

internal data class SipUriParts(
    val scheme: String,
    val user: String,
    val host: String,
    val transportTls: Boolean
)

private fun parseSipUri(uri: String): SipUriParts = SipUriPolicy.parse(uri)

private fun ensureTlsParam(uri: String): String {
    val parsed = parseSipUri(uri)
    return if (parsed.transportTls) uri else "$uri;transport=tls"
}

private fun ensureUdpParam(uri: String): String =
    if (transportParam(uri) != null) uri else "$uri;transport=udp"

private fun transportParam(uri: String): String? {
    val parameters = uri.substringBefore('?').substringAfter(';', "").split(';')
    return parameters.firstOrNull { it.substringBefore('=').equals("transport", true) }
        ?.substringAfter('=', "")
}

private fun hasNoOrTlsTransport(uri: String): Boolean {
    val transport = transportParam(uri)
    return transport == null || transport.equals("tls", true)
}

private fun hasNoOrUdpTransport(uri: String): Boolean {
    val transport = transportParam(uri)
    return transport == null || transport.equals("udp", true)
}

private fun ensureLooseRoute(uri: String): String {
    val params = uri.substringAfter(';', "")
    if (params.split(';').any { it.equals("lr", true) }) return uri
    return "$uri;lr"
}

private fun normalizeHost(host: String): String {
    val withoutBrackets = host.removePrefix("[").removeSuffix("]")
    require(withoutBrackets.isNotBlank() && withoutBrackets.length <= 253)
    val ascii = IDN.toASCII(withoutBrackets, IDN.USE_STD3_ASCII_RULES).trimEnd('.')
    require(ascii.isNotBlank() && ascii.split('.').all { label ->
        label.isNotEmpty() && label.length <= 63 && label.firstOrNull()?.isLetterOrDigit() == true &&
            label.lastOrNull()?.isLetterOrDigit() == true
    }) { "Invalid TLS server name" }
    return ascii.lowercase()
}

private fun String?.requireValue(name: String): String = this?.trim()?.takeIf { it.isNotEmpty() }
    ?: throw IllegalArgumentException("Missing SIP $name")
