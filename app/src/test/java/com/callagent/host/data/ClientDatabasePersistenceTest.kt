package com.callagent.host.data

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class ClientDatabasePersistenceTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val databaseNames = mutableSetOf<String>()
    private val openDatabases = mutableListOf<ClientDatabase>()

    @After
    fun cleanUp() {
        openDatabases.forEach { it.close() }
        databaseNames.forEach(context::deleteDatabase)
    }

    @Test
    fun appRestartKeepsTaskKeyAndExactPostBody() {
        val name = testDatabaseName()
        val task = task(localId = "local-1", key = "stable-retry-key")
        val first = openDatabase(name)
        first.insertOutbound(task)
        first.updateTask(task.localId, SmsStatus.UNKNOWN)
        val saved = first.loadMessages(task.simId).single()
        val savedBody = saved.idempotencyBody
        val firstPostBody = first.loadTask(task.localId)!!.toRetryEnvelope().requestBody()
        first.close()

        val reopened = openDatabase(name)
        val restored = reopened.loadPendingTasks().single()

        assertEquals(SmsStatus.UNKNOWN, restored.status)
        assertEquals(task.idempotencyKey, restored.taskKey)
        assertEquals(task.text, restored.text)
        assertEquals(savedBody, restored.idempotencyBody)
        assertEquals(firstPostBody, savedBody)
        assertEquals(savedBody, restored.toRetryEnvelope().requestBody())
    }

    @Test
    fun lateEventAndPostResponseMergeIntoOneRowAndKeepTheLocalTask() {
        val db = openDatabase(testDatabaseName())
        val task = task(localId = "local-outbox", key = "stable-retry-key")
        db.insertOutbound(task)
        val serverObservation = SmsRecord(
            localId = "remote:message-1",
            serverId = "message-1",
            commandId = "command-1",
            simId = task.simId,
            mappingRevision = task.mappingRevision,
            direction = "outbound",
            from = "+8613800000000",
            to = task.to,
            text = task.text,
            status = SmsStatus.DELIVERED,
            createdAt = task.createdAt,
            expiresAt = "2026-10-03T10:05:00Z",
            partCount = 1,
            parts = listOf(SmsPart(0, SmsStatus.DELIVERED, 0, null)),
            taskKey = null,
            gatewayId = task.gatewayId,
            idempotencyBody = null
        )
        db.upsertRemoteMessage(serverObservation)

        db.updateTask(
            localId = task.localId,
            status = SmsStatus.QUEUED,
            accepted = MessageAccepted(
                messageId = "message-1",
                commandId = "command-1",
                status = SmsStatus.QUEUED,
                expiresAt = "2026-10-03T10:05:00Z",
                partCount = null
            )
        )

        val records = db.loadMessages(task.simId)
        assertEquals(1, records.size)
        val merged = records.single()
        assertEquals(task.localId, merged.localId)
        assertEquals("message-1", merged.serverId)
        assertEquals(task.idempotencyKey, merged.taskKey)
        assertEquals(task.text, merged.text)
        assertEquals(task.to, merged.to)
        assertEquals(SmsStatus.DELIVERED, merged.status)
        assertEquals(1, merged.parts.size)
        assertTrue(merged.idempotencyBody!!.contains("\"ttl_seconds\":300"))
    }

    @Test
    fun eventPageCommitReopensWithMessageCursorAndPendingAlertAndRejectsStaleHelper() {
        val name = testDatabaseName()
        val first = openDatabase(name)
        val historic = remoteMessage("historic-1")
        first.beginHistoricalBaseline()
        assertTrue(first.applyRemoteSnapshotPage(listOf(historic)).isEmpty())
        assertTrue(first.pendingBackgroundNotificationIds().isEmpty())
        assertTrue(first.applyRemoteEventPage(listOf(historic), null, "cursor-1").committed)
        first.finishHistoricalBaseline()

        val incoming = remoteMessage("incoming-1")
        val committed = first.applyRemoteEventPage(listOf(incoming), "cursor-1", "cursor-2")
        assertTrue(committed.committed)
        assertEquals(listOf("incoming-1"), committed.newInboundNotificationIds)

        val staleHelper = openDatabase(name)
        val stale = staleHelper.applyRemoteEventPage(
            listOf(remoteMessage("stale-1")),
            expectedCursor = "cursor-1",
            cursorValue = "cursor-stale"
        )
        assertFalse(stale.committed)
        assertEquals("cursor-2", first.eventCursor())
        assertTrue(first.loadMessages("sim-1").any { it.serverId == "incoming-1" })
        assertFalse(first.loadMessages("sim-1").any { it.serverId == "stale-1" })

        first.close()
        staleHelper.close()
        val reopened = openDatabase(name)
        assertEquals("cursor-2", reopened.eventCursor())
        assertEquals(listOf("incoming-1"), reopened.pendingBackgroundNotificationIds())

        val replay = reopened.applyRemoteEventPage(listOf(incoming), "cursor-2", "cursor-2")
        assertTrue(replay.committed)
        assertTrue(replay.newInboundNotificationIds.isEmpty())
        assertEquals(listOf("incoming-1"), reopened.pendingBackgroundNotificationIds())
    }

    @Test
    fun failedSessionFenceRollsBackMessageJournalAndCursorTogether() {
        val db = openDatabase(testDatabaseName())
        var checks = 0
        val result = runCatching {
            db.applyRemoteEventPage(
                messages = listOf(remoteMessage("rolled-back-1")),
                expectedCursor = null,
                cursorValue = "must-not-commit",
                sessionIsCurrent = { ++checks == 1 }
            )
        }

        assertTrue(result.isFailure)
        assertNull(db.eventCursor())
        assertTrue(db.loadMessages("sim-1").isEmpty())
        assertTrue(db.pendingBackgroundNotificationIds().isEmpty())
        assertTrue(db.isHistoricalBaseline())
    }

    @Test
    fun postBootstrapSnapshotJournalsNewInboundMessages() {
        val db = openDatabase(testDatabaseName())
        db.beginHistoricalBaseline()
        db.finishHistoricalBaseline()

        assertEquals(listOf("snapshot-incoming"), db.applyRemoteSnapshotPage(listOf(remoteMessage("snapshot-incoming"))))
        assertEquals(listOf("snapshot-incoming"), db.pendingBackgroundNotificationIds())
    }

    @Test
    fun firstSnapshotStaysSilentWhenAnotherHelperFinishesBaselineMidPagination() {
        val name = testDatabaseName()
        val first = openDatabase(name)
        val second = openDatabase(name)
        first.beginHistoricalBaseline()
        assertTrue(first.applyRemoteSnapshotPage(
            messages = listOf(remoteMessage("historic-page-1")),
            forceHistoricalBaseline = true
        ).isEmpty())

        assertTrue(second.applyRemoteEventPage(listOf(remoteMessage("historic-page-1")), null, "cursor-1").committed)
        second.finishHistoricalBaseline()

        assertTrue(first.applyRemoteSnapshotPage(
            messages = listOf(remoteMessage("historic-page-2")),
            forceHistoricalBaseline = true
        ).isEmpty())
        assertTrue(first.pendingBackgroundNotificationIds().isEmpty())
    }

    @Test
    fun eventJournalRetainsMoreThanOneNotificationBatchAcrossReopen() {
        val name = testDatabaseName()
        val db = openDatabase(name)
        db.beginHistoricalBaseline()
        db.finishHistoricalBaseline()
        val records = (0 until 120).map { index -> remoteMessage("incoming-$index") }

        val result = db.applyRemoteEventPage(records, expectedCursor = null, cursorValue = "cursor-120")
        assertTrue(result.committed)
        assertEquals(120, result.newInboundNotificationIds.size)
        assertEquals(120, db.pendingBackgroundNotificationIds().size)

        db.close()
        val reopened = openDatabase(name)
        assertEquals(120, reopened.pendingBackgroundNotificationIds().size)
        assertEquals("cursor-120", reopened.eventCursor())
    }

    @Test
    fun aDifferentServerOwnerOrDeviceCannotReadPreviousDraftOrTask() {
        val firstName = clientDatabaseName("https://api.example.test/v1", "owner-a", "client-a")
        val secondName = clientDatabaseName("https://api.example.test/v1", "owner-b", "client-a")
        databaseNames += firstName
        databaseNames += secondName
        val first = openDatabase(firstName)
        val task = task(localId = "owner-a-task", key = "owner-a-key")
        first.insertOutbound(task)
        first.saveDraft(SmsDraft(task.simId, task.to, task.text))

        val second = openDatabase(secondName)

        assertTrue(second.loadMessages(task.simId).isEmpty())
        assertNull(second.loadDraft(task.simId))
        assertTrue(second.loadPendingTasks().isEmpty())
    }

    private fun openDatabase(name: String): ClientDatabase = ClientDatabase(context, name).also {
        databaseNames += name
        openDatabases += it
    }

    private fun testDatabaseName(): String = "host-test-${UUID.randomUUID()}.db"

    private fun task(localId: String, key: String) = OutboundTask(
        localId = localId,
        idempotencyKey = key,
        gatewayId = "gateway-1",
        simId = "sim-2",
        mappingRevision = 7,
        to = "10690000",
        text = "中文与 emoji 🙂",
        createdAt = "2026-10-03T10:00:00Z",
        status = SmsStatus.SUBMITTING
    )

    private fun remoteMessage(id: String) = SmsRecord(
        localId = "remote:$id",
        serverId = id,
        commandId = null,
        simId = "sim-1",
        mappingRevision = 1L,
        direction = "inbound",
        from = "+8613800000000",
        to = null,
        text = "message $id",
        status = SmsStatus.RECEIVED,
        createdAt = "2026-10-04T10:00:00Z",
        expiresAt = null,
        partCount = null,
        parts = emptyList(),
        taskKey = null,
        gatewayId = "gateway-1",
        idempotencyBody = null
    )
}
