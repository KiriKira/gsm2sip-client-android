package com.callagent.host.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryEnvelopeTest {
    @Test
    fun retryKeepsTheOriginalKeyAndExactRequestBody() {
        val original = OutboundTask(
            localId = "local-1",
            idempotencyKey = "e2b0e0d4-9039-45d3-b9a4-3f5be27c8b11",
            gatewayId = "gateway-1",
            simId = "sim-2",
            mappingRevision = 7,
            to = "10690000",
            text = "中文与 emoji 🙂",
            createdAt = "2026-10-03T10:00:00Z",
            status = SmsStatus.UNKNOWN
        )

        val retryOne = original.retryEnvelope()
        val retryTwo = original.retryEnvelope()

        assertEquals(original.idempotencyKey, retryOne.idempotencyKey)
        assertEquals(retryOne, retryTwo)
        assertEquals(retryOne.requestFields(), retryTwo.requestFields())
        assertEquals("sim-2", retryOne.requestFields()["sim_id"])
        assertEquals(7L, retryOne.requestFields()["mapping_revision"])
        assertEquals("中文与 emoji 🙂", retryOne.requestFields()["text"])
    }

    @Test
    fun unknownWithoutServerIdCanOnlyOfferExplicitSameKeyRetry() {
        assertEquals(RetryAction.RETRY_SAME_KEY, retryAction("stable-key", null))
        assertEquals(RetryAction.QUERY_EXISTING_MESSAGE, retryAction("stable-key", "server-message-id"))
        assertEquals(RetryAction.NONE, retryAction(null, null))
    }

    @Test
    fun newTaskKeyIsDistinctForASeparateUserSubmission() {
        assertNotEquals(newTaskKey(), newTaskKey())
        assertTrue(SmsStatus.display(SmsStatus.UNKNOWN).contains("unknown", ignoreCase = true))
        assertNotEquals(SmsStatus.DELIVERED, SmsStatus.UNKNOWN)
    }

    @Test
    fun serverEventObservedBeforePostResponseWinsWithoutLosingLocalRecipient() {
        val accepted = MessageAccepted(
            messageId = "message-1",
            commandId = "command-1",
            status = SmsStatus.QUEUED,
            expiresAt = "2026-10-03T10:05:00Z",
            partCount = null
        )
        val observed = SmsRecord(
            localId = "remote:message-1",
            serverId = "message-1",
            commandId = "command-1",
            simId = "sim-2",
            mappingRevision = 7,
            direction = "outbound",
            from = "+8613800000000",
            to = "10690000",
            text = "body",
            status = SmsStatus.DELIVERED,
            createdAt = "2026-10-03T10:00:00Z",
            expiresAt = accepted.expiresAt,
            partCount = 1,
            parts = listOf(SmsPart(0, SmsStatus.DELIVERED, 0, null)),
            taskKey = null,
            gatewayId = "gateway-1",
            idempotencyBody = null
        )

        val merged = mergeAcceptedTaskState(SmsStatus.QUEUED, accepted, observed, localRecipient = "10690000")

        assertEquals(SmsStatus.DELIVERED, merged.status)
        assertEquals(1, merged.partCount)
        assertEquals("10690000", merged.to)
        assertEquals(1, merged.parts.size)
    }

    @Test
    fun retryCanKeepTheExactPersistedPayloadAcrossAppVersions() {
        val raw = "{ \"sim_id\":\"sim-2\",\"gateway_id\":\"gateway-1\",\"mapping_revision\":7,\"to\":\"10690000\",\"text\":\"中文🙂\",\"ttl_seconds\":123 }"
        val envelope = RetryEnvelope(
            idempotencyKey = "stable-key",
            gatewayId = "gateway-1",
            simId = "sim-2",
            mappingRevision = 7,
            to = "10690000",
            text = "中文🙂",
            savedRequestBody = raw
        )

        assertEquals(raw, envelope.requestBody())
    }

    @Test
    fun databaseIdentitySeparatesServerOwnerAndClientDevice() {
        val origin = "https://api.example.test/v1"
        val original = clientDatabaseName(origin, "owner-a", "client-a")
        assertEquals(original, clientDatabaseName(origin, "owner-a", "client-a"))
        assertNotEquals(original, clientDatabaseName(origin, "owner-b", "client-a"))
        assertNotEquals(original, clientDatabaseName(origin, "owner-a", "client-b"))
        assertNotEquals(original, clientDatabaseName("https://other.example.test/v1", "owner-a", "client-a"))
    }

    @Test
    fun sessionRefreshKeepsItsInstanceButARePairGetsANewInstance() {
        val session = HostSession(
            sessionInstanceId = "family-a",
            apiBaseUrl = "https://api.example.test/v1",
            ownerId = "owner-a",
            deviceId = "client-a",
            role = "client",
            accessToken = "access-1",
            accessExpiresAt = "2026-10-03T10:15:00Z",
            refreshToken = "refresh-1",
            refreshExpiresAt = "2026-11-02T10:00:00Z",
            sipAvailable = false,
            sipReason = "sip_not_configured"
        )
        val refreshed = session.copy(accessToken = "access-2", refreshToken = "refresh-2")
        val rePaired = refreshed.copy(sessionInstanceId = "family-b")

        assertTrue(session.sameSessionInstance(refreshed))
        assertTrue(!session.sameSessionInstance(rePaired))
        assertTrue(!session.sameSessionInstance(refreshed.copy(ownerId = "owner-b")))
    }
}
