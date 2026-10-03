package com.callagent.host.data

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
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
}
