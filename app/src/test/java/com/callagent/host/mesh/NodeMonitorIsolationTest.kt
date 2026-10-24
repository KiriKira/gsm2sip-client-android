package com.callagent.host.mesh

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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NodeMonitorIsolationTest {
    private val app = RuntimeEnvironment.getApplication()
    private var oldProvider: Provider? = null

    @Before fun setUp() {
        oldProvider = Security.getProvider("AndroidKeyStore")
        Security.removeProvider("AndroidKeyStore")
        TestAndroidKeyStoreBacking.keys.clear()
        Security.insertProviderAt(RobolectricAndroidKeyStoreProvider(), 1)
        clearStores()
    }

    @After fun tearDown() {
        clearStores()
        TestAndroidKeyStoreBacking.keys.clear()
        Security.removeProvider("AndroidKeyStore")
        oldProvider?.let { Security.insertProviderAt(it, 1) }
    }

    @Test fun blockedRefreshOnOnePairingDoesNotBlockAnotherAndStillRotatesOnlyOnce() {
        val refreshStarted = CountDownLatch(1)
        val releaseRefresh = CountDownLatch(1)
        val refreshes = AtomicInteger()
        val clients = Executors.newFixedThreadPool(3)
        StubServer { exchange ->
            if (exchange.path == "/v1/auth/refresh") {
                refreshes.incrementAndGet()
                refreshStarted.countDown()
                check(releaseRefresh.await(8, TimeUnit.SECONDS))
                exchange.reply(200, """{"access_token":"new-access","access_expires_at":"2099-01-01T00:00:00Z","refresh_token":"new-refresh","refresh_expires_at":"2099-02-01T00:00:00Z"}""")
            } else exchange.reply(200, """{"items":[]}""")
        }.use { slow ->
            StubServer { it.reply(200, """{"items":[]}""") }.use { fast ->
                try {
                    val firstStore = SessionStore(app, "monitor-test-a")
                    firstStore.replace(session(slow.baseUrl).copy(accessExpiresAt = "2000-01-01T00:00:00Z"))
                    val secondStore = SessionStore(app, "monitor-test-b")
                    secondStore.replace(session(fast.baseUrl))
                    val firstClient = ApiClient(slow.baseUrl, firstStore, 1_000, 6_000)
                    val samePairingClient = ApiClient(slow.baseUrl, SessionStore(app, "monitor-test-a"), 1_000, 6_000)
                    val first = clients.submit<List<*>> { firstClient.listGateways() }
                    assertTrue(refreshStarted.await(2, TimeUnit.SECONDS))
                    val same = clients.submit<List<*>> { samePairingClient.listGateways() }
                    val independent = clients.submit<List<*>> { ApiClient(fast.baseUrl, secondStore).listGateways() }
                    assertTrue("a healthy node must not await another pairing's HTTP refresh", independent.get(2, TimeUnit.SECONDS).isEmpty())
                    assertFalse(first.isDone)
                    assertFalse(same.isDone)
                    releaseRefresh.countDown()
                    assertTrue(first.get(2, TimeUnit.SECONDS).isEmpty())
                    assertTrue(same.get(2, TimeUnit.SECONDS).isEmpty())
                    assertEquals("clients of the same pairing still share token rotation", 1, refreshes.get())
                } finally {
                    releaseRefresh.countDown()
                    clients.shutdownNow()
                }
            }
        }
    }

    @Test fun blockedPrimaryProbeDoesNotQueueOtherCallersOrDelayBackupProbe() {
        val primaryStarted = CountDownLatch(1)
        val releasePrimary = CountDownLatch(1)
        val backupProbed = CountDownLatch(1)
        val capabilitiesRequests = AtomicInteger()
        val callers = Executors.newFixedThreadPool(2)
        StubServer { exchange ->
            primaryStarted.countDown()
            check(releasePrimary.await(8, TimeUnit.SECONDS))
            exchange.reply(503, """{"error":{"code":"UNAVAILABLE"}}""")
        }.use { primary ->
            StubServer { exchange ->
                when (exchange.path) {
                    "/v1/capabilities" -> { capabilitiesRequests.incrementAndGet(); exchange.reply(200, """{"rtc_call_control":true,"extensions":["mesh"]}""") }
                    "/v1/gateways" -> {
                        exchange.reply(200, """{"items":[{"gateway_id":"backup-gateway","online":true,"rtc_call_control_ready":true,"install_id":"install-test"}]}""")
                        backupProbed.countDown()
                    }
                    else -> exchange.reply(200, "{}")
                }
            }.use { backup ->
                try {
                    SessionStore(app).replace(session(primary.baseUrl))
                    val backupSession = session(backup.baseUrl)
                    HostNodes.inactiveStore(app, "backup-test").replace(backupSession)
                    HostNodes.register(app, "backup-test", backupSession, "install-test")
                    val evaluating = callers.submit<HostSession?> { NodeMonitor.evaluate(app, force = true) }
                    assertTrue(primaryStarted.await(2, TimeUnit.SECONDS))
                    assertTrue("backup probing must run while the primary request is blocked", backupProbed.await(2, TimeUnit.SECONDS))
                    assertNull("foreground/background callers must not queue behind network work",
                        callers.submit<HostSession?> { NodeMonitor.evaluate(app, force = true) }.get(2, TimeUnit.SECONDS))
                    releasePrimary.countDown()
                    assertEquals(backupSession.sessionInstanceId, evaluating.get(5, TimeUnit.SECONDS)?.sessionInstanceId)
                    assertEquals("backup-test", HostNodes.activeNode(app)?.key)
                    assertEquals("only one capabilities HTTP request per healthy probe", 1, capabilitiesRequests.get())
                    val health = HostNodes.health(app).getValue("backup-test")
                    assertTrue(health.healthy)
                    assertTrue(health.callReady)
                    assertTrue(health.rttMillis!! <= NodeSelectionPolicy.MAX_HEALTHY_RTT_MS)
                } finally {
                    releasePrimary.countDown()
                    callers.shutdownNow()
                }
            }
        }
    }

    private fun session(baseUrl: String) = HostSession(UUID.randomUUID().toString(), baseUrl,
        "test-owner", UUID.randomUUID().toString(), "client", "test-access", "2099-01-01T00:00:00Z",
        "test-refresh", "2099-02-01T00:00:00Z")

    private fun clearStores() {
        HostNodes.nodes(app).forEach { HostNodes.inactiveStore(app, it.key).clear() }
        app.getSharedPreferences("host-nodes", 0).edit().clear().commit()
        SessionStore(app).clear()
        SessionStore(app, "monitor-test-a").clear()
        SessionStore(app, "monitor-test-b").clear()
    }

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
