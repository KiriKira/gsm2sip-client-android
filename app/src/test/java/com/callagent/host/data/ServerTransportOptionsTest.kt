package com.callagent.host.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServerTransportOptionsTest {
    @Test
    fun sipConfigurationWithoutTransportFieldsDefaultsToTlsAndSdes() {
        val config = parseSipConfiguration(JSONObject()
            .put("available", true)
            .put("aor", "sips:dev_x@sip.example.test")
            .put("registrar_uri", "sips:sip.example.test:5061")
            .put("outbound_proxy_uri", "sips:sip.example.test:5061;transport=tls;lr")
            .put("server_name", "sip.example.test"))
        assertEquals(SipConfiguration.TRANSPORT_TLS, config.transport)
        assertEquals(SipConfiguration.MEDIA_ENCRYPTION_SDES, config.mediaEncryption)
    }

    @Test
    fun sipConfigurationReadsUdpTransportAndDisabledMediaEncryption() {
        val config = parseSipConfiguration(JSONObject()
            .put("available", true)
            .put("aor", "sip:dev_x@192.168.1.10:5060")
            .put("registrar_uri", "sip:192.168.1.10:5060")
            .put("outbound_proxy_uri", "sip:192.168.1.10:5060;transport=udp;lr")
            .put("transport", "udp")
            .put("media_encryption", "none"))
        assertEquals(SipConfiguration.TRANSPORT_UDP, config.transport)
        assertEquals(SipConfiguration.MEDIA_ENCRYPTION_NONE, config.mediaEncryption)
        assertEquals("sip:192.168.1.10:5060", config.registrarUri)
        assertEquals(null, config.caPem)
    }

    @Test
    fun apiBaseUrlAcceptsHttpAndHttpsButNoCredentialsQueryOrOtherSchemes() {
        assertEquals("https://api.example.test/v1", normalizeApiBaseUrl("https://api.example.test"))
        assertEquals("https://api.example.test/edge/v1", normalizeApiBaseUrl(" https://api.example.test/edge/v1/ "))
        assertEquals("http://192.168.1.10:8080/v1", normalizeApiBaseUrl("http://192.168.1.10:8080"))
        assertEquals("http://node.tailnet.ts.net/v1", normalizeApiBaseUrl("HTTP://node.tailnet.ts.net/v1"))
        listOf(
            "ftp://api.example.test",
            "ws://api.example.test",
            "http://user:pass@api.example.test",
            "http://api.example.test?token=x",
            "https://api.example.test#frag",
            "api.example.test"
        ).forEach { value ->
            assertTrue(value, runCatching { normalizeApiBaseUrl(value) }.isFailure)
        }
    }
}
