package com.callagent.host.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PairedHostProtocolTest {
    @Test
    fun parsesCurrentAndFuturePlatformsWithoutTreatingPairingAsPresence() {
        val hosts = parsePairedHosts(JSONObject().put("items", JSONArray()
            .put(JSONObject()
                .put("id", "client-android")
                .put("name", "Pixel")
                .put("platform", "android")
                .put("state", "active")
                .put("is_self", true)
                .put("future_field", "ignored"))
            .put(JSONObject()
                .put("id", "client-windows")
                .put("name", "Desktop")
                .put("platform", "windows")
                .put("state", "revoked")
                .put("is_self", false))))

        assertEquals(2, hosts.size)
        assertEquals("windows", hosts[1].platform)
        assertTrue(hosts[0].isSelf)
        assertEquals("已配对", hosts[0].pairingStateLabel())
        assertEquals("已撤销", hosts[1].pairingStateLabel())
        assertFalse(hosts[0].pairingStateLabel().contains("在线"))
    }

    @Test
    fun missingOptionalFieldsAreToleratedAndRowsNeedAnId() {
        val hosts = parsePairedHosts(JSONObject().put("items", JSONArray()
            .put(JSONObject())
            .put(JSONObject().put("id", "client-future"))))

        assertEquals(1, hosts.size)
        assertEquals("client-f", hosts.single().name)
        assertEquals("unknown", hosts.single().platform)
        assertEquals("unknown", hosts.single().state)
        assertFalse(hosts.single().isSelf)
    }
}
