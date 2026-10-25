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
    fun aCallLeftInProgressOnAnotherNodeIsReadFromTheNodeThatKnowsIt() {
        val now = java.time.Instant.parse("2026-10-25T02:00:00Z").toEpochMilli()
        val stuck = call("node-a-call", 4, "connecting", "2026-10-25T01:18:00Z")
        val recent = call("just-now", 1, "ringing", "2026-10-25T01:59:30Z")
        val fresh = call("node-b-call", 2, "ended", "2026-10-25T01:30:00Z")
        val cached = CallHistoryCache.merge(listOf(stuck, recent), listOf(fresh))

        val stale = CallHistoryCache.staleLiveCalls(cached, setOf("node-b-call"), now)
        assertEquals("a call still settling is left alone", listOf("node-a-call"), stale.map { it.callId })

        val nodeB: (String) -> RemoteCall? = { throw java.io.IOException("404") }
        val nodeA: (String) -> RemoteCall? = { id -> if (id == "node-a-call") call(id, 6, "ended", stuck.createdAt) else null }
        val refreshed = CallHistoryCache.refreshStale(stale, listOf(nodeB, nodeA))
        val merged = CallHistoryCache.merge(cached, refreshed)
        assertEquals("ended", merged.first { it.callId == "node-a-call" }.state)
        assertEquals("unreachable nodes leave the cache as it was",
            emptyList<RemoteCall>(), CallHistoryCache.refreshStale(stale, listOf(nodeB)))
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
