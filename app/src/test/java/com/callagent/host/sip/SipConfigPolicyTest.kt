package com.callagent.host.sip

import com.callagent.host.data.SipConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SipConfigPolicyTest {
    private val tls = SipConfiguration(
        available = true,
        username = "dev_x",
        realm = "gsm2sip",
        aor = "sips:dev_x@sip.example.test",
        registrarUri = "sips:sip.example.test:5061",
        outboundProxyUri = "sips:sip.example.test:5061;transport=tls;lr",
        serverName = "sip.example.test",
        password = "secret"
    )

    private val udp = SipConfiguration(
        available = true,
        username = "dev_x",
        realm = "gsm2sip",
        aor = "sip:dev_x@192.168.1.10:5060",
        registrarUri = "sip:192.168.1.10:5060",
        outboundProxyUri = "sip:192.168.1.10:5060;transport=udp;lr",
        password = "secret",
        transport = SipConfiguration.TRANSPORT_UDP,
        mediaEncryption = SipConfiguration.MEDIA_ENCRYPTION_NONE
    )

    @Test
    fun tlsConfigurationKeepsTheExistingSipsContract() {
        val checked = ValidatedSipConfig.from(tls)
        assertEquals(SipTransport.TLS, checked.transport)
        assertTrue(checked.mediaEncrypted)
        assertEquals("sips:sip.example.test:5061;transport=tls", checked.registrarUri)
        assertEquals("sips:sip.example.test:5061;transport=tls;lr", checked.proxyUri)
        assertEquals("sip.example.test", checked.serverName)
    }

    @Test
    fun tlsConfigurationRejectsPlainSipAndUdpUris() {
        assertInvalid(tls.copy(aor = "sip:dev_x@sip.example.test"))
        assertInvalid(tls.copy(registrarUri = "sip:sip.example.test:5060"))
        assertInvalid(tls.copy(registrarUri = "sips:sip.example.test;transport=udp"))
        assertInvalid(tls.copy(outboundProxyUri = "sip:sip.example.test:5060;transport=udp;lr"))
        assertInvalid(tls.copy(outboundProxyUri = "sips:sip.example.test;transport=udp"))
        assertInvalid(tls.copy(serverName = null))
        assertInvalid(tls.copy(serverName = "other.example.test"))
    }

    @Test
    fun udpConfigurationUsesPlainSipUrisAndOptionalMediaEncryption() {
        val checked = ValidatedSipConfig.from(udp)
        assertEquals(SipTransport.UDP, checked.transport)
        assertFalse(checked.mediaEncrypted)
        assertEquals("sip:dev_x@192.168.1.10:5060", checked.aor)
        assertEquals("sip:192.168.1.10:5060;transport=udp", checked.registrarUri)
        assertEquals("sip:192.168.1.10:5060;transport=udp;lr", checked.proxyUri)
        assertNull(checked.serverName)
        assertNull(checked.customCaPem)

        val bareProxy = ValidatedSipConfig.from(udp.copy(outboundProxyUri = "sip:node.tailnet.ts.net:5060"))
        assertEquals("sip:node.tailnet.ts.net:5060;transport=udp;lr", bareProxy.proxyUri)
        assertTrue(ValidatedSipConfig.from(udp.copy(mediaEncryption = SipConfiguration.MEDIA_ENCRYPTION_SDES)).mediaEncrypted)
    }

    @Test
    fun udpConfigurationRejectsSipsAndOtherTransports() {
        assertInvalid(udp.copy(aor = "sips:dev_x@192.168.1.10"))
        assertInvalid(udp.copy(aor = "sip:dev_x@192.168.1.10;transport=tls"))
        assertInvalid(udp.copy(registrarUri = "sips:192.168.1.10:5061"))
        assertInvalid(udp.copy(registrarUri = "sip:192.168.1.10:5060;transport=tcp"))
        assertInvalid(udp.copy(outboundProxyUri = "sip:192.168.1.10:5061;transport=tls;lr"))
        assertInvalid(udp.copy(outboundProxyUri = "sips:192.168.1.10:5061;lr"))
        assertInvalid(udp.copy(outboundProxyUri = "sip:user@192.168.1.10:5060"))
        assertInvalid(udp.copy(registrarUri = "sip:192.168.1.10:5060?X-Header=1"))
    }

    @Test
    fun unknownTransportOrMediaEncryptionIsRejected() {
        assertInvalid(udp.copy(transport = "tcp"))
        assertInvalid(udp.copy(mediaEncryption = "zrtp"))
        assertInvalid(udp.copy(available = false))
        assertInvalid(udp.copy(password = null))
    }

    @Test
    fun callIntentUriMustMatchTheAccountTransport() {
        val tlsUri = "sips:call.AbC-123_x.y~z@gsm2sip;transport=tls"
        val udpUri = "sip:call.AbC-123_x.y~z@gsm2sip;transport=udp"
        SipUriPolicy.validateCallIntentUri(tlsUri, SipTransport.TLS)
        SipUriPolicy.validateCallIntentUri(udpUri, SipTransport.UDP)

        assertCallUriInvalid(udpUri, SipTransport.TLS)
        assertCallUriInvalid(tlsUri, SipTransport.UDP)
        assertCallUriInvalid("sip:call.token@gsm2sip;transport=tls", SipTransport.TLS)
        assertCallUriInvalid("sips:call.token@gsm2sip;transport=udp", SipTransport.UDP)
        assertCallUriInvalid("sip:call.token@gsm2sip", SipTransport.UDP)
        assertCallUriInvalid("sip:call.token@evil.example;transport=udp", SipTransport.UDP)
        assertCallUriInvalid("sip:+15551234567@gsm2sip;transport=udp", SipTransport.UDP)
        assertCallUriInvalid("sip:call.token@gsm2sip;transport=udp;maddr=10.0.0.1", SipTransport.UDP)
    }

    private fun assertInvalid(config: SipConfiguration) {
        assertTrue(runCatching { ValidatedSipConfig.from(config) }.exceptionOrNull() is IllegalArgumentException)
    }

    private fun assertCallUriInvalid(uri: String, transport: SipTransport) {
        assertTrue(uri, runCatching { SipUriPolicy.validateCallIntentUri(uri, transport) }.isFailure)
    }
}
