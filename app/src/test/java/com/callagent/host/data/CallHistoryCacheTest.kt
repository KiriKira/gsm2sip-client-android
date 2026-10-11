package com.callagent.host.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CallHistoryCacheTest {
    private fun call(id: String, revision: Long, state: String, at: String) = RemoteCall(
        callId = id, gatewayId = "gw", clientId = null, simId = "sim", mappingRevision = 2,
        direction = "incoming", state = state, stateRevision = revision, from = "+861", to = null,
        createdAt = at, expiresAt = null, answeredAt = null, endedAt = null, reason = null
    )

    @Test
    fun mergeKeepsNewestRevisionAndSortsNewestFirst() {
        val ringing = call("c1", 1, "ringing", "2026-10-01T10:00:00Z")
        val ended = call("c1", 3, "ended", "2026-10-01T10:00:00Z")
        val later = call("c2", 1, "ended", "2026-10-02T10:00:00Z")
        val merged = CallHistoryCache.merge(listOf(ended), listOf(ringing, later))
        assertEquals(listOf("c2", "c1"), merged.map { it.callId })
        assertEquals("ended", merged.last().state)
    }

    @Test
    fun encodeAndParseRoundTripWithoutLeakingNulls() {
        val items = listOf(call("c1", 2, "ended", "2026-10-01T10:00:00Z"))
        val (parsed, cursor) = CallHistoryCache.parse(CallHistoryCache.encode(items, "next-page"))
        assertEquals(items, parsed)
        assertEquals("next-page", cursor)
        val (_, noCursor) = CallHistoryCache.parse(CallHistoryCache.encode(items, null))
        assertNull(noCursor)
        assertNull(parsed.single().to)
    }
}
