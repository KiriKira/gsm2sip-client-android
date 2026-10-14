package com.callagent.host.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JoinLinkTest {
    private val entries = listOf(
        JoinEntry("https://home.example.lan:8443/v1", "AB12-CD34"),
        JoinEntry("https://vps.example.com", "ZXCV-9876")
    )

    @Test
    fun roundTripsSeveralNodesInPriorityOrder() {
        val link = JoinLink.build("client", entries)
        assertTrue(JoinLink.isJoinLink(link))
        assertEquals(entries, JoinLink.parse(link, "client"))
    }

    @Test
    fun refusesTheOtherRoleAndUnsafeOrMalformedEntries() {
        assertNull("a gateway link is not a client link", JoinLink.parse(JoinLink.build("gateway", entries), "client"))
        assertNull(JoinLink.parse("gsm2sip://join?v=2&role=client&n=https%3A%2F%2Fa%23C", "client"))
        assertNull("other schemes are refused", JoinLink.parse(JoinLink.build("client", listOf(JoinEntry("ftp://a.example", "C"))), "client"))
        assertNull("a query is refused", JoinLink.parse(JoinLink.build("client", listOf(JoinEntry("http://a.example?x=1", "C"))), "client"))
        assertNull("a code is required", JoinLink.parse("gsm2sip://join?v=1&role=client&n=https%3A%2F%2Fa.example", "client"))
        assertNull("credentials in the URL are refused",
            JoinLink.parse(JoinLink.build("client", listOf(JoinEntry("https://u:p@a.example", "C"))), "client"))
        assertNull("duplicate servers are refused", JoinLink.parse(JoinLink.build("client", entries + entries.first()), "client"))
        assertNull(JoinLink.parse("gsm2sip://other?v=1&role=client", "client"))
        assertFalse(JoinLink.isJoinLink("https://example.com"))
    }

    @Test
    fun acceptsPlainHttpNodesForLanAndVpnServers() {
        val mixed = listOf(
            JoinEntry("http://192.168.1.10:8080/v1", "LAN1-CODE"),
            JoinEntry("https://vps.example.com", "ZXCV-9876")
        )
        assertEquals(mixed, JoinLink.parse(JoinLink.build("client", mixed), "client"))
        val link = "gsm2sip://join?v=1&role=client&n=http%3A%2F%2Fnode.tailnet.ts.net%3A8080%23AB12-CD34"
        assertEquals(listOf(JoinEntry("http://node.tailnet.ts.net:8080", "AB12-CD34")), JoinLink.parse(link, "client"))
    }

    @Test
    fun parsesTheServerScriptOutput() {
        // Produced by gsm2sip-server scripts/mesh-join-link.py.
        val link = "gsm2sip://join?v=1&role=client&n=https%3A%2F%2Fhome.example.lan%3A8443%2Fv1%23AB12-CD34" +
            "&n=https%3A%2F%2Fvps.example.com%23ZXCV-9876"
        assertEquals(entries, JoinLink.parse(link, "client"))
    }
}
