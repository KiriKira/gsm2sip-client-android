package com.callagent.host.data

import com.callagent.host.RobolectricAndroidKeyStoreProvider
import com.callagent.host.TestAndroidKeyStoreBacking
import com.callagent.host.calls.RtcSleep
import org.json.JSONObject
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
import java.security.Provider
import java.security.Security
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RtcSleepRequestTest {
    private val app = RuntimeEnvironment.getApplication()
    private var oldProvider: Provider? = null
    private val bodies = mutableListOf<JSONObject>()
    private var reply: (JSONObject) -> Pair<Int, String> = { 200 to "{}" }
    private val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    private val acceptor = Thread({
        while (true) {
            val socket = try { server.accept() } catch (_: java.io.IOException) { break }
            socket.use {
                val input = socket.getInputStream().bufferedReader()
                input.readLine()
                var length = 0
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", ignoreCase = true)) length = line.substringAfter(':').trim().toInt()
                }
                val body = JSONObject(CharArray(length).also { var read = 0; while (read < length) read += input.read(it, read, length - read) }.concatToString())
                val (status, text) = synchronized(bodies) { bodies += body; reply(body) }
                val bytes = text.toByteArray()
                socket.getOutputStream().apply {
                    write("HTTP/1.1 $status X\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(bytes)
                    flush()
                }
            }
        }
    }, "sleep-test-http").apply { isDaemon = true; start() }
    private val base = "http://127.0.0.1:${server.localPort}/v1"
    private val session = HostSession(UUID.randomUUID().toString(), base, "owner", UUID.randomUUID().toString(),
        "client", "access", "2099-01-01T00:00:00Z", "refresh", "2099-02-01T00:00:00Z")

    @Before fun setUp() {
        oldProvider = Security.getProvider("AndroidKeyStore")
        Security.removeProvider("AndroidKeyStore")
        TestAndroidKeyStoreBacking.keys.clear()
        Security.insertProviderAt(RobolectricAndroidKeyStoreProvider(), 1)
        SessionStore(app).replace(session)
        RtcSleep.resetForTest()
        RtcSleep.wantedForTest = { _, _ -> true }
    }

    @After fun tearDown() {
        server.close()
        RtcSleep.resetForTest()
        SessionStore(app).clear()
        TestAndroidKeyStoreBacking.keys.clear()
        Security.removeProvider("AndroidKeyStore")
        oldProvider?.let { Security.insertProviderAt(it, 1) }
    }

    private fun renew(enabled: Boolean = true) = ApiClient(base, SessionStore(app))
        .rtcRequest("POST", "/rtc/control/capability", JSONObject().put("version", 1).put("enabled", enabled))

    @Test fun aRenewalAsksToSleepAndRecordsTheGrant() {
        val until = java.time.Instant.now().plusSeconds(1800).toString()
        reply = { 200 to JSONObject().put("version", 1).put("sleep_until", until).toString() }
        renew()
        assertEquals(RtcSleep.REQUEST_SECONDS, bodies.single().getInt("sleep_seconds"))
        RtcSleep.pushArrived(session.sessionInstanceId)
        assertTrue(RtcSleep.trusted(session.sessionInstanceId))
    }

    @Test fun aNodeThatRejectsTheFieldStillGetsThePlainRenewal() {
        RtcSleep.observed(normalizeApiBaseUrl(base), true)
        reply = { body ->
            if (body.has("sleep_seconds")) 400 to """{"error":{"code":"INVALID_JSON","retryable":false}}""" else 200 to "{}"
        }
        renew()
        assertEquals("sleep, then the plain renewal", listOf(true, false), bodies.map { it.has("sleep_seconds") })
        assertFalse("the node is no longer asked to grant sleep", RtcSleep.advertises(normalizeApiBaseUrl(base)))
    }
}
