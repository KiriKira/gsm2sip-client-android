package com.callagent.host.calls

import android.content.Context
import com.callagent.rtcmedia.MediaApi
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val TEST_CALL_ID = "b86c8f0d-084d-4b89-9c0d-504577c00313"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class RtcCallEngineMediaClosureTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test fun endRequestedFreshSnapshotTurnsMediaClosureIntoDisconnectWithoutHangup() {
        val snapshot = controlSnapshot(state = "active", endRequested = true)
        val api = RecordingMediaApi(snapshot)
        val callbacks = CallStateRecorder(api.events)
        val engine = engine(api, callbacks)
        try {
            seedActiveIncoming(engine)

            invokeMediaFailure(engine, "server_authorization_closed")

            assertTerminal(callbacks, EngineCallState.DISCONNECTED)
            val events = api.events.toList()
            val getIndex = events.indexOfFirst { it.startsWith("GET:") }
            val terminalIndex = events.indexOf("CALL:DISCONNECTED")
            assertTrue("fresh control GET must precede terminal callback: $events", getIndex >= 0 && terminalIndex > getIndex)
            assertFalse("a server-requested end must not submit another hangup", events.any { it.startsWith("POST:") })
        } finally {
            engine.close()
        }
    }

    @Test fun endedFreshSnapshotTurnsMediaClosureIntoDisconnectWithoutHangup() {
        val snapshot = controlSnapshot(state = "ended", endRequested = false)
        val api = RecordingMediaApi(snapshot)
        val callbacks = CallStateRecorder(api.events)
        val engine = engine(api, callbacks)
        try {
            seedActiveIncoming(engine)

            invokeMediaFailure(engine, "server_authorization_closed")

            assertTerminal(callbacks, EngineCallState.DISCONNECTED)
            val events = api.events.toList()
            val getIndex = events.indexOfFirst { it.startsWith("GET:") }
            val terminalIndex = events.indexOf("CALL:DISCONNECTED")
            assertTrue("fresh control GET must precede terminal callback: $events", getIndex >= 0 && terminalIndex > getIndex)
            assertFalse("an already-ended call must not submit another hangup", events.any { it.startsWith("POST:") })
        } finally {
            engine.close()
        }
    }

    @Test fun stillActiveSnapshotFailsAndAttemptsControlHangup() {
        val api = RecordingMediaApi(controlSnapshot(state = "active", endRequested = false))
        val callbacks = CallStateRecorder(api.events)
        val engine = engine(api, callbacks)
        try {
            seedActiveIncoming(engine)

            invokeMediaFailure(engine, "server_authorization_closed")

            assertTerminal(callbacks, EngineCallState.FAILED)
            assertTrue("failure path must attempt a control hangup", api.hangupAttempted.await(5, TimeUnit.SECONDS))
            val events = api.events.toList()
            val freshGet = events.indexOfFirst { it.startsWith("GET:") }
            val failed = events.indexOf("CALL:FAILED")
            val hangup = events.indexOf("POST:hangup")
            assertTrue("fresh snapshot precedes failure and hangup: $events", freshGet >= 0 && failed > freshGet && hangup > failed)
        } finally {
            engine.close()
        }
    }

    @Test fun controlGetFailureFailsClosedAndStillAttemptsHangup() {
        val api = RecordingMediaApi(
            controlSnapshot(state = "active", endRequested = false),
            failFirstGet = true,
        )
        val callbacks = CallStateRecorder(api.events)
        val engine = engine(api, callbacks)
        try {
            seedActiveIncoming(engine)

            invokeMediaFailure(engine, "server_authorization_closed")

            assertTerminal(callbacks, EngineCallState.FAILED)
            assertTrue("GET failure must still attempt a control hangup", api.hangupAttempted.await(5, TimeUnit.SECONDS))
            val events = api.events.toList()
            val failedGet = events.indexOf("GET_FAILED")
            val failed = events.indexOf("CALL:FAILED")
            val hangup = events.indexOf("POST:hangup")
            assertTrue("GET error must fail closed and then try hangup: $events", failedGet >= 0 && failed > failedGet && hangup > failed)
        } finally {
            engine.close()
        }
    }

    private fun engine(api: MediaApi, callbacks: CallStateRecorder) =
        RtcCallEngine(context, api, callbacks)

    private fun seedActiveIncoming(engine: RtcCallEngine) {
        val snapshots = privateField(engine, "snapshots").get(engine) as ConcurrentHashMap<String, JSONObject>
        val delivered = privateField(engine, "delivered").get(engine) as ConcurrentHashMap<String, EngineCallState>
        snapshots[TEST_CALL_ID] = controlSnapshot(state = "active", endRequested = false)
        delivered[TEST_CALL_ID] = EngineCallState.CONNECTED
    }

    private fun invokeMediaFailure(engine: RtcCallEngine, reason: String) {
        RtcCallEngine::class.java.getDeclaredMethod(
            "handleMediaFailure", String::class.java, String::class.java,
        ).apply { isAccessible = true }.invoke(engine, TEST_CALL_ID, reason)
    }

    private fun assertTerminal(callbacks: CallStateRecorder, expected: EngineCallState) {
        assertTrue("terminal callback timed out", callbacks.terminal.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(expected), callbacks.states.toList())
    }

    private fun privateField(target: Any, name: String) =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }

    private fun controlSnapshot(state: String, endRequested: Boolean) = JSONObject()
        .put("call_id", TEST_CALL_ID)
        .put("direction", "incoming")
        .put("state", state)
        .put("state_revision", 7)
        .put("end_requested", endRequested)
        .put("reason", if (state == "ended") "remote_hangup" else "")

    private class CallStateRecorder(private val trace: MutableList<String>) : CallEngine.Listener {
        val states = Collections.synchronizedList(mutableListOf<EngineCallState>())
        val terminal = CountDownLatch(1)

        override fun onConnectionState(state: EngineConnectionState, statusCode: Int?, reason: String?) = Unit

        override fun onCallState(snapshot: EngineCallSnapshot) {
            states += snapshot.state
            trace += "CALL:${snapshot.state.name}"
            if (snapshot.state in setOf(EngineCallState.DISCONNECTED, EngineCallState.FAILED)) terminal.countDown()
        }

        override fun onEngineError(error: Throwable) = Unit
    }

    private class RecordingMediaApi(
        private val current: JSONObject,
        private val failFirstGet: Boolean = false,
    ) : MediaApi {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val hangupAttempted = CountDownLatch(1)
        private var getCount = 0

        override fun request(method: String, path: String, body: JSONObject?): JSONObject {
            if (method == "GET" && path == "/rtc/control/calls/$TEST_CALL_ID") {
                synchronized(this) {
                    getCount++
                    if (failFirstGet && getCount == 1) {
                        events += "GET_FAILED"
                        throw IllegalStateException("synthetic control read failure")
                    }
                    events += "GET:$path"
                }
                return JSONObject(current.toString())
            }
            if (method == "POST" && path == "/rtc/control/calls/$TEST_CALL_ID/operations") {
                val kind = body?.optString("kind").orEmpty()
                events += "POST:$kind"
                if (kind == "hangup") hangupAttempted.countDown()
                return JSONObject(current.toString())
            }
            events += "UNEXPECTED:$method:$path"
            throw AssertionError("unexpected RTC control request")
        }
    }

}
