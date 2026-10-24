package com.callagent.host.data

import com.callagent.host.RobolectricAndroidKeyStoreProvider
import com.callagent.host.TestAndroidKeyStoreBacking
import com.callagent.host.data.ApiClient
import com.callagent.host.data.HostSession
import com.callagent.host.data.SessionStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.Provider
import java.security.Security
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import android.os.SystemClock
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StandbySyncIntegrationTest {
    private val app = RuntimeEnvironment.getApplication()
    private var oldProvider: Provider? = null
    private val dbNames = mutableSetOf<String>()
    @Before fun setup() {
        oldProvider = Security.getProvider("AndroidKeyStore")
        Security.removeProvider("AndroidKeyStore")
        TestAndroidKeyStoreBacking.keys.clear()
        Security.insertProviderAt(RobolectricAndroidKeyStoreProvider(), 1)
        SessionStore(app).clear()
    }
    @After fun cleanup() {
        SessionStore(app).clear()
        dbNames.forEach { app.deleteDatabase(it) }
        Security.removeProvider("AndroidKeyStore")
        oldProvider?.let { Security.insertProviderAt(it, 1) }
    }
    @Test fun idleSyncRetriesFailedAckThenOnlyFetchesEventsAcrossEngineRestart() {
        val ack = AtomicInteger()
        val metadata = AtomicInteger()
        val events = AtomicInteger()
        StubServer { request ->
            when {
                request.path == "/v1/events/ack" -> request.reply(if (ack.incrementAndGet() == 1) 503 else 200, "{}")
                request.path.startsWith("/v1/events?") -> { events.incrementAndGet(); request.reply(200, """{"items":[],"next_cursor":null}""") }
                request.path == "/v1/gateways" -> { metadata.incrementAndGet(); request.reply(200, """{"items":[{"gateway_id":"gateway-a","online":true}]}""") }
                request.path == "/v1/gateways/gateway-a/sims" -> { metadata.incrementAndGet(); request.reply(200, """{"sims":[]}""") }
                else -> request.reply(404, "{}")
            }
        }.use { server ->
            val session = session(server.baseUrl)
            SessionStore(app).replace(session)
            dbNames += session.databaseName()
            ClientDatabase(app, session.databaseName()).use { it.saveEventCursor("MQ") }
            repeat(3) {
                HostSyncEngine(app, session, { true }).use { it.sync(SystemClock.elapsedRealtime() + 60_000) }
            }
            assertEquals(3, events.get())
            assertEquals("failed ACK remains pending, successful ACK survives engine recreation", 2, ack.get())
            assertEquals("metadata is not read on every hint", 2, metadata.get())
            ClientDatabase(app, session.databaseName()).use { db ->
                assertEquals("MQ", db.syncCheckpoint(session.sessionInstanceId, "acked"))
                assertNull(db.syncCheckpoint("different-pairing", "acked"))
            }
        }
    }
    @Test fun metadataFailureCannotPreventEventFetchAndCursorAck() {
        val ack = AtomicInteger()
        StubServer { request ->
            when {
                request.path == "/v1/events/ack" -> { ack.incrementAndGet(); request.reply(200, "{}") }
                request.path.startsWith("/v1/events?") -> request.reply(200, """{"items":[],"next_cursor":null}""")
                else -> request.reply(503, "{}")
            }
        }.use { server ->
            val session = session(server.baseUrl)
            SessionStore(app).replace(session)
            dbNames += session.databaseName()
            ClientDatabase(app, session.databaseName()).use { it.saveEventCursor("MQ") }
            HostSyncEngine(app, session, { true }).use { it.sync(SystemClock.elapsedRealtime() + 60_000) }
            assertEquals(1, ack.get())
        }
    }
    @Test fun capabilityRenewalSharesOnlySuccessfulResultsAndNeverSwallowsDisable() {
        val requests = AtomicInteger()
        StubServer { request ->
            val count = requests.incrementAndGet()
            request.reply(if (count == 1) 503 else 200, "{}")
        }.use { server ->
            val session = session(server.baseUrl)
            SessionStore(app).replace(session)
            val api = ApiClient(server.baseUrl, SessionStore(app))
            val enabled = org.json.JSONObject().put("version", 1).put("enabled", true)
            runCatching { api.rtcRequest("POST", "/rtc/control/capability", enabled) }
            api.rtcRequest("POST", "/rtc/control/capability", enabled)
            api.rtcRequest("POST", "/rtc/control/capability", enabled)
            assertEquals("failed renewal is retried; duplicate success coalesces", 2, requests.get())
            api.rtcRequest("POST", "/rtc/control/capability", org.json.JSONObject().put("version", 1).put("enabled", false))
            api.rtcRequest("POST", "/rtc/control/capability", enabled)
            assertEquals("disable is immediate and invalidates the successful renewal", 4, requests.get())
        }
    }

    @Test fun checkpointsSurviveReopenButAreIsolatedByNodeAndPairing() {
        val name = "checkpoints-${UUID.randomUUID()}.db"
        dbNames += name
        ClientDatabase(app, name, "node-a").use { it.saveSyncCheckpoint("pairing-a", "acked", "opaque-cursor") }
        ClientDatabase(app, name, "node-a").use {
            assertEquals("opaque-cursor", it.syncCheckpoint("pairing-a", "acked"))
            assertNull(it.syncCheckpoint("pairing-b", "acked"))
        }
        ClientDatabase(app, name, "node-b").use { assertNull(it.syncCheckpoint("pairing-a", "acked")) }
    }

    private fun session(baseUrl: String) = HostSession(UUID.randomUUID().toString(), baseUrl,
        "test-owner", UUID.randomUUID().toString(), "client", "access", "2099-01-01T00:00:00Z",
        "refresh", "2099-02-01T00:00:00Z")
    private class StubServer(handler: (Request) -> Unit) : AutoCloseable {
        private val executor = Executors.newCachedThreadPool { Thread(it, "node-test-http").apply { isDaemon = true } }
        private val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        @Volatile private var closed = false
        private val acceptor = Thread({
            while (!closed) {
                val socket = try { server.accept() } catch (_: java.net.SocketException) { break }
                executor.execute {
                    socket.use {
                        socket.soTimeout = 2_000
                        val reader = socket.getInputStream().bufferedReader()
                        val path = reader.readLine().split(' ')[1]
                        var length = 0
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            if (line.startsWith("Content-Length:", ignoreCase = true)) length = line.substringAfter(':').trim().toInt()
                        }
                        repeat(length) { check(reader.read() >= 0) }
                        handler(Request(path, socket))
                    }
                }
            }
        }, "node-test-accept").apply { isDaemon = true; start() }
        val baseUrl = "http://127.0.0.1:${server.localPort}/v1"
        override fun close() {
            closed = true
            server.close()
            executor.shutdownNow()
            acceptor.join(1_000)
        }
    }

    private class Request(val path: String, private val socket: Socket) {
        fun reply(status: Int, body: String) {
            val bytes = body.toByteArray(Charsets.UTF_8)
            socket.getOutputStream().apply {
                write("HTTP/1.1 $status Response\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                write(bytes)
                flush()
            }
        }
    }
}
