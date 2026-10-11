package com.callagent.host.ui

import com.callagent.host.data.SmsRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class SmsThreadIndexTest {
    private fun sms(id: String, sim: String?, peer: String, inbound: Boolean, at: String, text: String = id) = SmsRecord(
        localId = id, serverId = id, commandId = null, simId = sim, mappingRevision = 1,
        direction = if (inbound) "inbound" else "outbound", from = if (inbound) peer else null,
        to = if (inbound) null else peer, text = text, status = "received", createdAt = at, expiresAt = null,
        partCount = 1, parts = emptyList(), taskKey = null, gatewayId = "gw", idempotencyBody = null
    )

    @Test
    fun threadsAreKeyedBySimAndPeerAndSortedByLatestMessage() {
        val threads = SmsThreadIndex.build(listOf(
            sms("a1", "sim-1", "+861", inbound = true, at = "2026-10-01T10:00:00Z"),
            sms("a2", "sim-1", "+861", inbound = false, at = "2026-10-03T10:00:00Z", text = "回复"),
            sms("b1", "sim-2", "+861", inbound = true, at = "2026-10-02T10:00:00Z"),
            sms("c1", null, "", inbound = true, at = "2026-09-01T10:00:00Z"),
        ))
        assertEquals(listOf("sim-1" to "+861", "sim-2" to "+861", null to SmsThreadIndex.UNKNOWN_PEER),
            threads.map { it.simId to it.peer })
        assertEquals("回复", threads.first().latest.text)
        assertEquals(listOf("a2", "a1"), threads.first().records.map { it.localId })
    }

    @Test
    fun filterMatchesPeerOrLatestTextCaseInsensitively() {
        val threads = SmsThreadIndex.build(listOf(
            sms("a", "sim-1", "+8613800000000", inbound = true, at = "2026-10-01T10:00:00Z", text = "Your CODE is 1234"),
            sms("b", "sim-1", "10086", inbound = true, at = "2026-10-02T10:00:00Z", text = "话费余额"),
        ))
        assertEquals(listOf("+8613800000000"), SmsThreadIndex.filter(threads, " code ").map { it.peer })
        assertEquals(listOf("10086"), SmsThreadIndex.filter(threads, "1008").map { it.peer })
        assertEquals(2, SmsThreadIndex.filter(threads, "  ").size)
    }
}
