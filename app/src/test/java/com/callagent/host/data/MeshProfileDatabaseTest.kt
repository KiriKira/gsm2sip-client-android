package com.callagent.host.data

import android.content.Context
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.UUID

/** One local database shared by several server nodes of a profile. */
@RunWith(RobolectricTestRunner::class)
class MeshProfileDatabaseTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val name = "host-mesh-test-${UUID.randomUUID()}.db"
    private val open = mutableListOf<ClientDatabase>()

    @After
    fun cleanUp() {
        open.forEach { it.close() }
        context.deleteDatabase(name)
    }

    @Test
    fun eachNodeKeepsItsOwnCursorAndBaseline() {
        val home = db("")
        val vps = db("node-vps")
        home.applyRemoteEventPage(emptyList(), expectedCursor = null, cursorValue = "home-7")
        assertEquals("home-7", home.eventCursor())
        assertNull("another node's cursor is independent", vps.eventCursor())
        home.finishHistoricalBaseline()
        assertFalse(home.isHistoricalBaseline())
        assertTrue("a new node starts its own silent history pass", vps.isHistoricalBaseline())
    }

    @Test
    fun theSameSmsThroughTwoNodesIsStoredAndAlertedOnce() {
        val home = db("")
        val vps = db("node-vps")
        home.finishHistoricalBaseline()
        vps.finishHistoricalBaseline()

        val first = home.applyRemoteEventPage(listOf(inbound("msg-1")), expectedCursor = null, cursorValue = "h1")
        assertEquals(listOf("msg-1"), first.newInboundNotificationIds)

        // The VPS delivers the gateway's copy of the same SMS (same global ID).
        val copy = vps.applyRemoteEventPage(listOf(inbound("msg-1", gateway = "gateway-vps")),
            expectedCursor = null, cursorValue = "v1")
        assertTrue("no second alert for the same SMS", copy.newInboundNotificationIds.isEmpty())
        assertEquals(1, home.loadMessages("sim-1").size)

        // An SMS only the VPS saw (home node was down) still alerts once.
        val missed = vps.applyRemoteEventPage(listOf(inbound("msg-2", gateway = "gateway-vps")),
            expectedCursor = "v1", cursorValue = "v2")
        assertEquals(listOf("msg-2"), missed.newInboundNotificationIds)
        assertEquals(listOf("msg-1", "msg-2"), home.pendingBackgroundNotificationIds())
    }

    @Test
    fun meshTaskMovesToAnotherNodeKeepingItsGlobalMessageId() {
        val home = db("")
        val messageId = UUID.randomUUID().toString()
        val task = task("local-1", messageId)
        home.insertOutbound(task)
        val saved = home.loadTask("local-1")!!.toRetryEnvelope()
        assertEquals(messageId, saved.messageId)
        assertEquals(messageId, JSONObject(saved.requestBody()).getString("message_id"))

        val moved = saved.forNode("gateway-vps", mappingRevision = 3)
        assertTrue(home.moveTask("local-1", saved.idempotencyKey, moved))
        val reloaded = home.loadTask("local-1")!!.toRetryEnvelope()
        assertEquals("gateway-vps", reloaded.gatewayId)
        assertEquals(3L, reloaded.mappingRevision)
        assertEquals(messageId, reloaded.messageId)
        assertNotEquals(saved.idempotencyKey, reloaded.idempotencyKey)
        assertEquals("sim-1", reloaded.simId)

        assertFalse("a stale key cannot move the task again", home.moveTask("local-1", saved.idempotencyKey, moved))
        home.updateTask("local-1", SmsStatus.QUEUED, MessageAccepted(messageId, "cmd", SmsStatus.QUEUED, "", null))
        assertFalse("a confirmed task never moves",
            home.moveTask("local-1", reloaded.idempotencyKey, reloaded.forNode("gateway-x", 1)))
    }

    @Test
    fun gatewayAndSimStateIsOnlyReadByTheNodeThatWroteIt() {
        val home = db("")
        val vps = db("node-vps")
        home.saveGateway(GatewaySnapshot("gateway-home", "Old phone", true, null, 9, true, true, 80, false))
        home.replaceSims(listOf(SimLine("sim-1", 0, "SIM 1", null, null, "active", 9, true, "in_service")))
        assertEquals("gateway-home", home.loadGateway()?.gatewayId)
        assertNull("the next node never reuses the previous node's gateway ID", vps.loadGateway())
        assertTrue(vps.loadSims().isEmpty())

        vps.saveGateway(GatewaySnapshot("gateway-vps", "Old phone", true, null, 2, true, false, 80, false))
        vps.replaceSims(listOf(SimLine("sim-1", 0, "SIM 1", null, null, "active", 2, true, "in_service")))
        assertEquals(2L, vps.loadSims().single().mappingRevision)
        assertNull(home.loadGateway())
    }

    @Test
    fun standaloneTasksKeepTheOriginalBody() {
        val legacy = task("local-2", messageId = null).retryEnvelope()
        assertFalse(JSONObject(legacy.requestBody()).has("message_id"))
        assertNull(legacy.messageId)
    }

    private fun db(scope: String) = ClientDatabase(context, name, scope).also { open += it }

    private fun task(localId: String, messageId: String?) = OutboundTask(
        localId = localId,
        idempotencyKey = UUID.randomUUID().toString(),
        gatewayId = "gateway-home",
        simId = "sim-1",
        mappingRevision = 9,
        to = "+8613800000000",
        text = "hello",
        createdAt = "2026-10-12T10:00:00Z",
        status = SmsStatus.UNKNOWN,
        messageId = messageId
    )

    private fun inbound(id: String, gateway: String = "gateway-home") = SmsRecord(
        localId = "remote:$id", serverId = id, commandId = null, simId = "sim-1", mappingRevision = 1L,
        direction = "inbound", from = "+8610086", to = null, text = "text $id", status = SmsStatus.RECEIVED,
        createdAt = "2026-10-12T10:00:00Z", expiresAt = null, partCount = null, parts = emptyList(),
        taskKey = null, gatewayId = gateway, idempotencyBody = null
    )
}
