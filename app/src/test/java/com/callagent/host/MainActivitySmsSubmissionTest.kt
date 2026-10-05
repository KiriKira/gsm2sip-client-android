package com.callagent.host

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.callagent.host.data.ClientDatabase
import com.callagent.host.data.GatewaySnapshot
import com.callagent.host.data.HostSession
import com.callagent.host.data.MessageAccepted
import com.callagent.host.data.RetryEnvelope
import com.callagent.host.data.SessionStore
import com.callagent.host.data.SimLine
import com.callagent.host.data.SmsRecord
import com.callagent.host.data.SmsStatus
import com.callagent.host.data.SmsSubmissionClient
import com.callagent.host.data.SmsSubmissionClients
import com.callagent.host.data.clientDatabaseName
import com.callagent.host.data.toRetryEnvelope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.textfield.TextInputEditText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.Security
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivitySmsSubmissionTest {
    @Test
    fun ackDoesNotSubmitAnEmptySecondClickOrClearANewerDraft() {
        val app = RuntimeEnvironment.getApplication()
        val originalProvider = installTestKeyStore()
        val session = session("submission-one")
        val databaseName = clientDatabaseName(session.apiBaseUrl, session.ownerId, session.deviceId)
        val blockingClient = BlockingSubmissionClient()
        var controller: ActivityController<MainActivity>? = null
        val previousFactory = SmsSubmissionClients.factoryOverride
        try {
            preparePairedAccount(app, session, "Gateway One", "sim-one")
            SmsSubmissionClients.factoryOverride = { blockingClient }
            val activityController = Robolectric.buildActivity(MainActivity::class.java).setup()
            controller = activityController
            val activity = activityController.get()
            openComposer(activity)
            activity.findViewById<TextInputEditText>(R.id.sms_recipient).setText("+15551230001")
            activity.findViewById<TextInputEditText>(R.id.sms_body).setText("first message")
            val detachedSend = activity.findViewById<View>(R.id.sms_thread_send)
            detachedSend.performClick()

            assertTrue("submit should reach the fake API", blockingClient.entered.await(5, TimeUnit.SECONDS))
            assertEquals(1, blockingClient.createCount.get())

            // The old composer is detached by navigation. Neither another tap
            // on it nor a tap on the empty current composer may create a task.
            detachedSend.performClick()
            activity.findViewById<View>(R.id.sms_thread_send).performClick()
            assertEquals(1, blockingClient.createCount.get())
            val body = activity.findViewById<TextInputEditText>(R.id.sms_body)
            assertEquals("", body.text.toString())
            val submittedTask = readDatabase(app, databaseName) { db -> db.loadOutboundTasks().single() }
            assertEquals("+15551230001", submittedTask.to)
            assertEquals("first message", submittedTask.text)

            body.setText("next draft")
            blockingClient.release.countDown()
            waitUntil {
                readDatabase(app, databaseName) { db ->
                    db.loadOutboundTasks().singleOrNull()?.status == SmsStatus.ACCEPTED
                }
            }

            assertEquals("next draft", activity.findViewById<TextInputEditText>(R.id.sms_body).text.toString())
            val persistedDraft = readDatabase(app, databaseName) { db -> db.loadDraft("sim-one") }
            assertEquals("+15551230001", persistedDraft?.recipient)
            assertEquals("next draft", persistedDraft?.text)
            val task = readDatabase(app, databaseName) { db -> db.loadOutboundTasks().single() }
            assertEquals("first message", task.text)
            assertEquals("+15551230001", task.to)
            assertEquals("sim-one", task.simId)
            assertEquals("gateway-submission-one", task.gatewayId)
            assertEquals("first message", blockingClient.lastEnvelope?.text)
            assertEquals(1, blockingClient.createCount.get())
        } finally {
            blockingClient.release.countDown()
            SmsSubmissionClients.factoryOverride = previousFactory
            controller?.let { runCatching { it.pause().stop().destroy() } }
            cleanupAccount(app, session, databaseName)
            restoreKeyStore(originalProvider)
        }
    }

    @Test
    fun lateAckAfterAccountSwitchOnlyUpdatesTheOriginalAccountDatabase() {
        val app = RuntimeEnvironment.getApplication()
        val originalProvider = installTestKeyStore()
        val firstSession = session("submission-old")
        val secondSession = session("submission-new")
        val firstDatabase = clientDatabaseName(firstSession.apiBaseUrl, firstSession.ownerId, firstSession.deviceId)
        val secondDatabase = clientDatabaseName(secondSession.apiBaseUrl, secondSession.ownerId, secondSession.deviceId)
        val blockingClient = BlockingSubmissionClient()
        var oldController: ActivityController<MainActivity>? = null
        var newController: ActivityController<MainActivity>? = null
        val previousFactory = SmsSubmissionClients.factoryOverride
        try {
            preparePairedAccount(app, firstSession, "Gateway Old", "sim-old")
            SmsSubmissionClients.factoryOverride = { blockingClient }
            val firstController = Robolectric.buildActivity(MainActivity::class.java).setup()
            oldController = firstController
            val oldActivity = firstController.get()
            openComposer(oldActivity)
            oldActivity.findViewById<TextInputEditText>(R.id.sms_recipient).setText("+15551230002")
            oldActivity.findViewById<TextInputEditText>(R.id.sms_body).setText("belongs to old account")
            oldActivity.findViewById<View>(R.id.sms_thread_send).performClick()
            assertTrue("old-account request should reach the fake API", blockingClient.entered.await(5, TimeUnit.SECONDS))

            val originalTask = readDatabase(app, firstDatabase) { db -> db.loadOutboundTasks().single() }
            assertEquals("gateway-submission-old", originalTask.gatewayId)
            assertEquals("sim-old", originalTask.simId)
            assertNotNull(originalTask.taskKey)
            assertEquals(originalTask.taskKey, blockingClient.lastEnvelope?.idempotencyKey)

            app.deleteDatabase(secondDatabase)
            seedAccountDatabase(app, secondSession, "Gateway New", "sim-new")
            assertTrue(SessionStore(app).writeIfCurrent(firstSession, secondSession))
            val secondController = Robolectric.buildActivity(MainActivity::class.java).setup()
            newController = secondController
            val newActivity = secondController.get()
            newActivity.findViewById<BottomNavigationView>(R.id.main_bottom_navigation).selectedItemId = R.id.tab_settings
            assertTrue("new account UI should show its own server binding", allText(newActivity).any { it.contains(secondSession.apiBaseUrl) })
            assertTrue(readDatabase(app, secondDatabase) { db -> db.loadOutboundTasks().isEmpty() })

            blockingClient.release.countDown()
            waitUntil {
                readDatabase(app, firstDatabase) { db ->
                    db.loadTask(originalTask.localId)?.status == SmsStatus.ACCEPTED
                }
            }

            val retainedOriginal = readDatabase(app, firstDatabase) { db -> db.loadTask(originalTask.localId) }
            assertNotNull(retainedOriginal)
            assertEquals(originalTask.taskKey, retainedOriginal?.taskKey)
            assertEquals("gateway-submission-old", retainedOriginal?.gatewayId)
            assertEquals("sim-old", retainedOriginal?.simId)
            assertTrue("late ACK must not create a task in the new account", readDatabase(app, secondDatabase) { db -> db.loadOutboundTasks().isEmpty() })
            assertEquals(R.id.tab_settings, newActivity.findViewById<BottomNavigationView>(R.id.main_bottom_navigation).selectedItemId)
            assertTrue(allText(newActivity).any { it.contains(secondSession.apiBaseUrl) })
        } finally {
            blockingClient.release.countDown()
            SmsSubmissionClients.factoryOverride = previousFactory
            newController?.let { runCatching { it.pause().stop().destroy() } }
            oldController?.let { runCatching { it.pause().stop().destroy() } }
            cleanupAccount(app, firstSession, firstDatabase)
            cleanupAccount(app, secondSession, secondDatabase)
            restoreKeyStore(originalProvider)
        }
    }

    @Test
    fun savedSmsFromOldAccountCannotBeQueriedOrRetriedThroughNewAccount() {
        val app = RuntimeEnvironment.getApplication()
        val originalProvider = installTestKeyStore()
        val oldSession = session("stale-record-old")
        val newSession = session("stale-record-new")
        val oldDatabase = clientDatabaseName(oldSession.apiBaseUrl, oldSession.ownerId, oldSession.deviceId)
        val newDatabase = clientDatabaseName(newSession.apiBaseUrl, newSession.ownerId, newSession.deviceId)
        val fakeClient = BlockingSubmissionClient()
        var controller: ActivityController<MainActivity>? = null
        val previousFactory = SmsSubmissionClients.factoryOverride
        try {
            preparePairedAccount(app, oldSession, "Gateway Old", "sim-old")
            val retryRecord: SmsRecord
            val queryRecord: SmsRecord
            ClientDatabase(app, oldDatabase).use { db ->
                db.insertOutbound(com.callagent.host.data.OutboundTask(
                    localId = "old-unknown-task",
                    idempotencyKey = "old-unknown-key",
                    gatewayId = "gateway-stale-record-old",
                    simId = "sim-old",
                    mappingRevision = 42,
                    to = "+15551230003",
                    text = "old account retry body",
                    createdAt = "2026-10-05T10:00:00Z",
                    status = SmsStatus.UNKNOWN,
                ))
                retryRecord = db.loadTask("old-unknown-task")!!

                db.insertOutbound(com.callagent.host.data.OutboundTask(
                    localId = "old-query-task",
                    idempotencyKey = "old-query-key",
                    gatewayId = "gateway-stale-record-old",
                    simId = "sim-old",
                    mappingRevision = 42,
                    to = "+15551230004",
                    text = "old account query body",
                    createdAt = "2026-10-05T10:01:00Z",
                    status = SmsStatus.SUBMITTING,
                ))
                db.updateTask("old-query-task", SmsStatus.ACCEPTED, MessageAccepted(
                    messageId = "old-account-server-message",
                    commandId = "old-account-command",
                    status = SmsStatus.ACCEPTED,
                    expiresAt = "2099-01-01T00:05:00Z",
                    partCount = 1,
                ))
                queryRecord = db.loadTask("old-query-task")!!
            }

            app.deleteDatabase(newDatabase)
            seedAccountDatabase(app, newSession, "Gateway New", "sim-new")
            assertTrue(SessionStore(app).writeIfCurrent(oldSession, newSession))
            SmsSubmissionClients.factoryOverride = { fakeClient }
            val activityController = Robolectric.buildActivity(MainActivity::class.java).setup()
            controller = activityController
            val activity = activityController.get()
            assertTrue(readDatabase(app, newDatabase) { db -> db.loadOutboundTasks().isEmpty() })
            fakeClient.messageToReturn = queryRecord.copy(status = SmsStatus.DELIVERED)

            invokePrivate(activity, "retryOrCheck", queryRecord)
            settleMainThread(700L)
            assertEquals("old query records must not use the new account API", 0, fakeClient.getCount.get())
            assertTrue("querying an old account record must not import it into the new account DB",
                readDatabase(app, newDatabase) { db ->
                    db.loadMessages("sim-new").isEmpty() && db.loadMessages("sim-old").isEmpty() &&
                        db.loadTask("old-query-task") == null && db.loadTask("old-unknown-task") == null && db.loadOutboundTasks().isEmpty()
                })

            invokePrivate(activity, "submitExistingTask", retryRecord, retryRecord.toRetryEnvelope())
            settleMainThread(700L)
            assertEquals("old retry records must not use the new account API", 0, fakeClient.createCount.get())
            assertTrue("retrying an old account task must leave the new account DB empty",
                readDatabase(app, newDatabase) { db ->
                    db.loadMessages("sim-new").isEmpty() && db.loadMessages("sim-old").isEmpty() &&
                        db.loadTask("old-query-task") == null && db.loadTask("old-unknown-task") == null && db.loadOutboundTasks().isEmpty()
                })
        } finally {
            fakeClient.release.countDown()
            SmsSubmissionClients.factoryOverride = previousFactory
            controller?.let { runCatching { it.pause().stop().destroy() } }
            cleanupAccount(app, oldSession, oldDatabase)
            cleanupAccount(app, newSession, newDatabase)
            restoreKeyStore(originalProvider)
        }
    }

    private fun openComposer(activity: MainActivity) {
        activity.findViewById<BottomNavigationView>(R.id.main_bottom_navigation).selectedItemId = R.id.tab_messages
        activity.findViewById<View>(R.id.sms_compose_fab).performClick()
        assertNotNull(activity.findViewById<View>(R.id.sms_thread_send))
    }

    private fun waitUntil(condition: () -> Boolean) {
        val mainLooper = Shadows.shadowOf(Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + 5_000L
        while (System.currentTimeMillis() < deadline) {
            mainLooper.idle()
            if (condition()) return
            Thread.sleep(10)
        }
        mainLooper.idle()
        assertTrue("expected asynchronous SMS submission state was not reached", condition())
    }

    private fun settleMainThread(durationMillis: Long) {
        val mainLooper = Shadows.shadowOf(Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + durationMillis
        while (System.currentTimeMillis() < deadline) {
            mainLooper.idle()
            Thread.sleep(10)
        }
        mainLooper.idle()
    }

    private fun invokePrivate(activity: MainActivity, name: String, vararg arguments: Any) {
        val parameterTypes = arguments.map { it.javaClass }.toTypedArray()
        val method = MainActivity::class.java.getDeclaredMethod(name, *parameterTypes)
        method.isAccessible = true
        method.invoke(activity, *arguments)
    }

    private fun preparePairedAccount(
        app: android.app.Application,
        session: HostSession,
        gatewayName: String,
        simId: String,
    ) {
        app.getSharedPreferences("host-session", 0).edit().clear().commit()
        app.getSharedPreferences("host-settings", 0).edit().clear().commit()
        Shadows.shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        val databaseName = clientDatabaseName(session.apiBaseUrl, session.ownerId, session.deviceId)
        app.deleteDatabase(databaseName)
        assertTrue(SessionStore(app).writeIfCurrent(null, session))
        seedAccountDatabase(app, session, gatewayName, simId)
    }

    private fun seedAccountDatabase(app: android.app.Application, session: HostSession, gatewayName: String, simId: String) {
        val databaseName = clientDatabaseName(session.apiBaseUrl, session.ownerId, session.deviceId)
        ClientDatabase(app, databaseName).apply {
            saveGateway(GatewaySnapshot(
                gatewayId = "gateway-${session.sessionInstanceId}",
                deviceName = gatewayName,
                online = true,
                lastSeenAt = "2026-10-05T10:00:00Z",
                mappingRevision = 42,
                root = false,
                sipRegistered = false,
                batteryPercent = 80,
                charging = false,
            ))
            replaceSims(listOf(SimLine(
                simId = simId,
                slotIndex = 0,
                label = "Test SIM",
                carrierName = "Test carrier",
                phoneNumber = "+15550000000",
                state = "active",
                mappingRevision = 42,
                identityVerified = true,
                serviceState = "in_service",
            )))
            close()
        }
    }

    private fun session(id: String) = HostSession(
        sessionInstanceId = id,
        apiBaseUrl = "https://127.0.0.1:1/$id/v1",
        ownerId = "owner-$id",
        deviceId = "device-$id",
        role = "client",
        accessToken = "access-$id",
        accessExpiresAt = "2099-01-01T00:00:00Z",
        refreshToken = "refresh-$id",
        refreshExpiresAt = "2099-01-02T00:00:00Z",
        sipAvailable = false,
        sipReason = "SMS submission test",
    )

    private fun <T> readDatabase(app: android.app.Application, name: String, block: (ClientDatabase) -> T): T {
        val db = ClientDatabase(app, name)
        return try { block(db) } finally { db.close() }
    }

    private fun cleanupAccount(app: android.app.Application, session: HostSession, databaseName: String) {
        runCatching { SessionStore(app).clearIfCurrent(session) }
        app.deleteDatabase(databaseName)
    }

    private fun installTestKeyStore(): Provider? {
        val original = Security.getProvider("AndroidKeyStore")
        Security.removeProvider("AndroidKeyStore")
        TestAndroidKeyStoreBacking.keys.clear()
        Security.insertProviderAt(RobolectricAndroidKeyStoreProvider(), 1)
        return original
    }

    private fun restoreKeyStore(original: Provider?) {
        TestAndroidKeyStoreBacking.keys.clear()
        Security.removeProvider("AndroidKeyStore")
        original?.let { Security.insertProviderAt(it, 1) }
    }

    private fun allText(activity: MainActivity): List<String> = descendants(activity.findViewById(android.R.id.content))
        .filterIsInstance<android.widget.TextView>()
        .map { it.text?.toString().orEmpty() }
        .toList()

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }

    private class BlockingSubmissionClient : SmsSubmissionClient {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val createCount = AtomicInteger()
        val getCount = AtomicInteger()
        @Volatile var messageToReturn: SmsRecord? = null
        @Volatile var lastEnvelope: RetryEnvelope? = null

        override fun createMessage(envelope: RetryEnvelope): MessageAccepted {
            createCount.incrementAndGet()
            lastEnvelope = envelope
            entered.countDown()
            if (!release.await(10, TimeUnit.SECONDS)) throw java.io.IOException("Test did not release the fake ACK")
            return MessageAccepted(
                messageId = "server-accepted-test-message",
                commandId = "command-test-message",
                status = SmsStatus.ACCEPTED,
                expiresAt = "2099-01-01T00:05:00Z",
                partCount = 1,
            )
        }

        override fun getMessage(messageId: String): SmsRecord? {
            getCount.incrementAndGet()
            return messageToReturn
        }
    }
}
