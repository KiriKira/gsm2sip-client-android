package com.callagent.host.backup

import com.callagent.backup.SmsArchiveCodec
import com.callagent.host.data.ClientDatabase
import com.callagent.host.data.HostSession
import com.callagent.host.data.OutboundTask
import com.callagent.host.data.SessionChanged
import com.callagent.host.data.SessionStore
import com.callagent.host.data.SimLine
import com.callagent.host.data.SmsPart
import com.callagent.host.data.SmsRecord
import com.callagent.host.data.SmsStatus
import com.callagent.host.data.clientDatabaseName
import com.callagent.host.RobolectricAndroidKeyStoreProvider
import com.callagent.host.TestAndroidKeyStoreBacking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.security.Security
import java.io.Closeable

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HostSmsArchiveRecordProviderTest {
    @Test
    fun exportsEveryCachedMessageWithUnicodeAndOnlyRevisionMatchedSimLabels() {
        val app = RuntimeEnvironment.getApplication()
        val originalProvider = Security.getProvider("AndroidKeyStore")
        Security.removeProvider("AndroidKeyStore")
        TestAndroidKeyStoreBacking.keys.clear()
        Security.insertProviderAt(RobolectricAndroidKeyStoreProvider(), 1)
        val session = testSession("archive-snapshot-account", "https://PAIR.example.test/root/v1")
        val name = clientDatabaseName(session.apiBaseUrl, session.ownerId, session.deviceId)
        app.deleteDatabase(name)
        var liveDatabase: ClientDatabase? = null
        try {
            assertEquals(name, archiveDatabaseName(session))
            assertTrue(SessionStore(app).writeIfCurrent(null, session))
            val live = ClientDatabase(app, name)
            liveDatabase = live
            live.replaceSims(listOf(
                SimLine("sim-a", 0, "线路甲", "carrier a", "+8613800000001", "active", 8, true, "in_service"),
                SimLine("sim-b", 1, "线路乙", "carrier b", "+8613800000002", "active", 8, true, "in_service")
            ))
            live.upsertRemoteMessage(SmsRecord(
                localId = "local-inbound-a",
                serverId = "server-message-a",
                commandId = "PRIVATE-COMMAND-ID",
                simId = "sim-a",
                mappingRevision = 8,
                direction = "inbound",
                from = "+8613800000010",
                to = null,
                text = "短信正文：你好，世界 🌏\n第二行 café é",
                status = "received",
                createdAt = "2026-10-04T12:30:45.123Z",
                expiresAt = null,
                partCount = 2,
                parts = listOf(SmsPart(0, "received", null, null), SmsPart(1, "received", null, null)),
                taskKey = null,
                gatewayId = "gateway-a",
                idempotencyBody = null
            ))
            live.upsertRemoteMessage(SmsRecord(
                localId = "local-secondary",
                serverId = "server-message-b",
                commandId = null,
                simId = "sim-b",
                mappingRevision = 7,
                direction = "outbound",
                from = null,
                to = "+8613800000020",
                text = "old SIM mapping record",
                status = "submitted",
                createdAt = "2026-10-04T12:31:45Z",
                expiresAt = null,
                partCount = 1,
                parts = emptyList(),
                taskKey = null,
                gatewayId = "gateway-a",
                idempotencyBody = null
            ))
            live.insertOutbound(OutboundTask(
                localId = "local-unknown-sim-task",
                idempotencyKey = "PRIVATE-TASK-KEY",
                gatewayId = "gateway-a",
                simId = "removed-sim",
                mappingRevision = 4,
                to = "+8613800000030",
                text = "result still unknown",
                createdAt = "2026-10-04T12:32:45Z",
                status = SmsStatus.UNKNOWN
            ))
            live.saveEventCursor("live-events-cursor")

            val beforeTask = live.loadTask("local-unknown-sim-task")
            val beforeSnapshot = System.currentTimeMillis()
            val exported = HostSmsArchiveRecordProvider.records(app).toList()
            val afterSnapshot = System.currentTimeMillis()

            assertEquals(3, exported.size)
            val first = exported.single { it.messageId == "server-message-a" }
            assertEquals("短信正文：你好，世界 🌏\n第二行 café é", first.body)
            assertEquals("线路甲", first.simLabel)
            assertEquals(0, first.slotIndex)
            assertEquals("gateway-a", first.gatewayId)
            assertEquals("archive-snapshot-account", first.ownerId)
            assertEquals("https://pair.example.test/root/v1", first.source)
            assertEquals(instantMillis("2026-10-04T12:30:45.123Z"), first.createdAt)
            assertTrue(first.observedAt in beforeSnapshot..afterSnapshot)
            assertEquals(1, exported.map { it.observedAt }.distinct().size)
            assertEquals(
                SmsArchiveCodec.stableId(first.source, first.ownerId, first.gatewayId, "server-message-a"),
                first.id
            )

            val oldMapping = exported.single { it.messageId == "server-message-b" }
            assertEquals("sim-b", oldMapping.simId)
            assertNull("a stale revision must not acquire today's SIM label", oldMapping.simLabel)
            assertNull(oldMapping.slotIndex)

            val unknownTask = exported.single { it.messageId == null }
            assertEquals("unknown", unknownTask.status)
            assertEquals("removed-sim", unknownTask.simId)
            assertNull(unknownTask.simLabel)
            assertNull(unknownTask.slotIndex)
            assertEquals(
                SmsArchiveCodec.stableId(unknownTask.source, unknownTask.ownerId, unknownTask.gatewayId, "local:local-unknown-sim-task"),
                unknownTask.id
            )
            assertFalse(exported.toString().contains("PRIVATE-TASK-KEY"))
            assertFalse(exported.toString().contains("PRIVATE-COMMAND-ID"))
            assertFalse(exported.toString().contains("idempotency_body"))
            assertFalse(exported.toString().contains("MUST-NOT-EXPORT"))
            assertEquals("live-events-cursor", live.eventCursor())
            assertEquals(beforeTask, live.loadTask("local-unknown-sim-task"))
        } finally {
            liveDatabase?.close()
            runCatching { SessionStore(app).clearIfCurrent(session) }
            app.deleteDatabase(name)
            TestAndroidKeyStoreBacking.keys.clear()
            Security.removeProvider("AndroidKeyStore")
            originalProvider?.let { Security.insertProviderAt(it, 1) }
        }
    }

    @Test
    fun accountSwitchAbortsTheSnapshotAndReleasesItsReadOnlyCursor() {
        val app = RuntimeEnvironment.getApplication()
        val originalProvider = Security.getProvider("AndroidKeyStore")
        Security.removeProvider("AndroidKeyStore")
        TestAndroidKeyStoreBacking.keys.clear()
        Security.insertProviderAt(RobolectricAndroidKeyStoreProvider(), 1)
        val firstSession = testSession("archive-switch-account-a", "https://a.example.test/v1")
        val secondSession = testSession("archive-switch-account-b", "https://b.example.test/v1")
        val firstName = clientDatabaseName(firstSession.apiBaseUrl, firstSession.ownerId, firstSession.deviceId)
        val secondName = clientDatabaseName(secondSession.apiBaseUrl, secondSession.ownerId, secondSession.deviceId)
        app.deleteDatabase(firstName)
        app.deleteDatabase(secondName)
        try {
            assertNotEquals(firstName, secondName)
            assertTrue(SessionStore(app).writeIfCurrent(null, firstSession))
            val seeded = ClientDatabase(app, firstName)
            try {
                seeded.upsertRemoteMessage(record("local-a", "server-a", "first"))
                seeded.upsertRemoteMessage(record("local-b", "server-b", "second"))
            } finally {
                seeded.close()
            }

            val iterator = HostSmsArchiveRecordProvider.records(app).iterator()
            assertEquals("server-a", iterator.next().messageId)
            assertTrue(SessionStore(app).clearIfCurrent(firstSession))
            assertTrue(SessionStore(app).writeIfCurrent(null, secondSession))
            assertThrows(SessionChanged::class.java) { iterator.hasNext() }

            // A leaked read transaction would keep the journal-mode database locked.
            val database = ClientDatabase(app, firstName)
            try {
                database.saveEventCursor("write-after-cancel")
                assertEquals("write-after-cancel", database.eventCursor())
            } finally {
                database.close()
            }
        } finally {
            runCatching { SessionStore(app).clearIfCurrent(firstSession) }
            runCatching { SessionStore(app).clearIfCurrent(secondSession) }
            app.deleteDatabase(firstName)
            app.deleteDatabase(secondName)
            TestAndroidKeyStoreBacking.keys.clear()
            Security.removeProvider("AndroidKeyStore")
            originalProvider?.let { Security.insertProviderAt(it, 1) }
        }
    }

    @Test
    fun explicitCloseAfterOneRecordReleasesAnEarlyAbandonedSnapshot() {
        val app = RuntimeEnvironment.getApplication()
        val originalProvider = Security.getProvider("AndroidKeyStore")
        Security.removeProvider("AndroidKeyStore")
        TestAndroidKeyStoreBacking.keys.clear()
        Security.insertProviderAt(RobolectricAndroidKeyStoreProvider(), 1)
        val session = testSession("archive-early-close-account", "https://early.example.test/v1")
        val name = clientDatabaseName(session.apiBaseUrl, session.ownerId, session.deviceId)
        app.deleteDatabase(name)
        try {
            assertTrue(SessionStore(app).writeIfCurrent(null, session))
            val seeded = ClientDatabase(app, name)
            try {
                seeded.upsertRemoteMessage(record("local-first", "server-first", "first"))
                seeded.upsertRemoteMessage(record("local-second", "server-second", "second"))
            } finally {
                seeded.close()
            }

            val records = HostSmsArchiveRecordProvider.records(app)
            val iterator = records.iterator()
            assertEquals("server-first", iterator.next().messageId)
            (records as Closeable).close()

            val writable = ClientDatabase(app, name)
            try {
                writable.saveEventCursor("write-after-explicit-close")
                assertEquals("write-after-explicit-close", writable.eventCursor())
            } finally {
                writable.close()
            }
        } finally {
            runCatching { SessionStore(app).clearIfCurrent(session) }
            app.deleteDatabase(name)
            TestAndroidKeyStoreBacking.keys.clear()
            Security.removeProvider("AndroidKeyStore")
            originalProvider?.let { Security.insertProviderAt(it, 1) }
        }
    }

    private fun testSession(instance: String, baseUrl: String) = HostSession(
        sessionInstanceId = instance,
        apiBaseUrl = baseUrl,
        ownerId = instance,
        deviceId = "device-$instance",
        role = "client",
        accessToken = "MUST-NOT-EXPORT-access-token",
        accessExpiresAt = "2099-01-01T00:00:00Z",
        refreshToken = "MUST-NOT-EXPORT-refresh-token",
        refreshExpiresAt = "2099-01-02T00:00:00Z",
        sipAvailable = false,
        sipReason = null
    )

    private fun record(localId: String, serverId: String, body: String) = SmsRecord(
        localId, serverId, null, "sim-a", 1L, "inbound", "+100", "+200", body,
        "received", "2026-10-04T12:30:45Z", null, null, emptyList(), null, "gateway-a", null
    )

    private fun instantMillis(value: String): Long = java.time.Instant.parse(value).toEpochMilli()
}
