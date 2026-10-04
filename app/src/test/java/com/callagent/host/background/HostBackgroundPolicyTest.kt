package com.callagent.host.background

import android.content.Context
import com.callagent.host.data.ClientDatabase
import com.callagent.host.data.SmsPart
import com.callagent.host.data.SmsRecord
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class HostBackgroundPolicyTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val databaseName = "host-background-${UUID.randomUUID()}.db"
    private var database: ClientDatabase? = null

    @After
    fun cleanUp() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun restoreRequiresPairingAndAnExplicitOptInAndStopStaysStopped() {
        assertFalse(HostBackgroundPolicy.DEFAULT_ENABLED)
        assertFalse(HostBackgroundPolicy.shouldRestore(HostBackgroundPolicy.DEFAULT_ENABLED, pairedClient = true))
        assertTrue(HostBackgroundPolicy.shouldRestore(enabled = true, pairedClient = true))
        assertFalse(HostBackgroundPolicy.shouldRestore(enabled = true, pairedClient = false))
        assertFalse(HostBackgroundPolicy.shouldRestore(enabled = false, pairedClient = true))
        assertTrue(HostBackgroundPolicy.isHistoricalBaseline(cursorPresent = false, bootstrapped = false, baselineInProgress = false))
        assertTrue(HostBackgroundPolicy.isHistoricalBaseline(cursorPresent = true, bootstrapped = false, baselineInProgress = true))
        assertFalse(HostBackgroundPolicy.isHistoricalBaseline(cursorPresent = true, bootstrapped = true, baselineInProgress = false))
        assertTrue(HostBackgroundPolicy.shouldSuppressUserStopRestore(true, true, 10L, 7, 11L, 7))
        assertFalse(HostBackgroundPolicy.shouldSuppressUserStopRestore(true, true, 12L, 7, 11L, 7))
        assertFalse(HostBackgroundPolicy.shouldSuppressUserStopRestore(false, true, 10L, 7, 11L, 7))
    }

    @Test
    fun initialHistoryIsSilentAndLaterInboundIdsArePersistentlyDeduplicated() {
        val db = ClientDatabase(context, databaseName).also { database = it }

        assertTrue(db.recordBackgroundInboundIds(listOf("historic-1", "historic-2"), baseline = true).isEmpty())
        assertTrue(db.recordBackgroundInboundIds(listOf("historic-1", "historic-2"), baseline = false).isEmpty())
        assertEquals(listOf("new-1"), db.recordBackgroundInboundIds(listOf("new-1"), baseline = false))
        assertEquals(listOf("new-1"), db.pendingBackgroundNotificationIds())
        assertTrue(db.recordBackgroundInboundIds(listOf("new-1"), baseline = false).isEmpty())
        db.acknowledgeBackgroundNotificationIds(listOf("new-1"))
        assertTrue(db.pendingBackgroundNotificationIds().isEmpty())
    }

    @Test
    fun onlyNewInboundRecordsBecomeNotificationCandidates() {
        val inbound = remoteMessage("inbound-1", "inbound")
        val outbound = remoteMessage("outbound-1", "outbound")

        assertTrue(HostBackgroundPolicy.newInboundNotificationIds(listOf(inbound, outbound), emptySet()).isEmpty())
        assertEquals(
            listOf("inbound-1"),
            HostBackgroundPolicy.newInboundNotificationIds(listOf(inbound, outbound), setOf("inbound-1", "outbound-1"))
        )
    }

    @Test
    fun lateCallbacksCannotCrossIntoANewPairingSession() {
        val original = session("family-1", "owner-1")
        val refreshed = original.copy(accessToken = "rotated-access")
        val replacement = session("family-2", "owner-2")

        assertTrue(isCurrentBackgroundSession(original, refreshed))
        assertFalse(isCurrentBackgroundSession(original, replacement))
        assertFalse(isCurrentBackgroundSession(original, null))
    }

    @Test
    fun wakeUrlUsesSupportedOkHttpRequestSchemeAndRejectsCredentials() {
        assertEquals("wss://api.example.test/v1/ws", hostWakeWebSocketUrl("https://api.example.test"))
        assertEquals("wss://api.example.test/v1/ws", hostWakeWebSocketUrl("https://api.example.test/v1"))
        assertEquals("wss://api.example.test/edge/v1/ws", hostWakeWebSocketUrl("https://api.example.test/edge/v1/"))
        assertEquals("https", okhttp3.Request.Builder().url(hostWakeWebSocketUrl("https://api.example.test")).build().url.scheme)
        assertTrue(runCatching { hostWakeWebSocketUrl("http://api.example.test") }.isFailure)
        assertTrue(runCatching { hostWakeWebSocketUrl("https://api.example.test?token=private") }.isFailure)
        assertTrue(runCatching { hostWakeWebSocketUrl("https://user:password@api.example.test") }.isFailure)
    }

    private fun remoteMessage(id: String, direction: String) = SmsRecord(
        localId = "remote:$id",
        serverId = id,
        commandId = null,
        simId = "sim-1",
        mappingRevision = 1L,
        direction = direction,
        from = null,
        to = null,
        text = "private body excluded from notifications",
        status = "received",
        createdAt = "2026-10-03T10:00:00Z",
        expiresAt = null,
        partCount = null,
        parts = emptyList<SmsPart>(),
        taskKey = null,
        gatewayId = "gateway-1",
        idempotencyBody = null
    )

    private fun session(instanceId: String, ownerId: String) = com.callagent.host.data.HostSession(
        sessionInstanceId = instanceId,
        apiBaseUrl = "https://api.example.test/v1",
        ownerId = ownerId,
        deviceId = "client-1",
        role = "client",
        accessToken = "access",
        accessExpiresAt = "2026-10-03T10:10:00Z",
        refreshToken = "refresh",
        refreshExpiresAt = "2026-11-03T10:00:00Z",
        sipAvailable = false,
        sipReason = null
    )
}
