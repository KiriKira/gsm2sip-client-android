package com.callagent.host.data

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
