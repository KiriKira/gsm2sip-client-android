package com.callagent.host.calls

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import com.callagent.rtcmedia.MediaApi
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkInfo
import org.robolectric.shadows.ShadowPowerManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class RtcCallEngineStandbyTest {
    private val app: Context = RuntimeEnvironment.getApplication()
    private val states = mutableListOf<EngineCallState>()
    private val listener = object : CallEngine.Listener {
        override fun onConnectionState(state: EngineConnectionState, statusCode: Int?, reason: String?) = Unit
        override fun onCallState(snapshot: EngineCallSnapshot) { states += snapshot.state }
        override fun onEngineError(error: Throwable) = Unit
    }

    @Test fun missingLiveCallIsConfirmedIndividuallyAndStoppedListeningReleasesIdlePower() {
        val network = shadowOf(app.getSystemService(ConnectivityManager::class.java))
        network.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
            ConnectivityManager.TYPE_WIFI, 0, true, true))
        network.setDefaultNetworkActive(true)
        val requests = mutableListOf<String>()
        val snapshot = JSONObject().put("call_id", "standby-call").put("direction", "incoming")
            .put("state", "ringing").put("state_revision", 1L)
        val engine = RtcCallEngine(app, MediaApi { _, path, _ ->
            requests += path
            when {
                path == "/rtc/control/calls" -> JSONObject().put("calls", org.json.JSONArray())
                path.endsWith("/standby-call") -> JSONObject(snapshot.toString()).put("state", "ended").put("state_revision", 2L)
                else -> JSONObject()
            }
        }, listener, listeningEnabled = { false })
        val lock = ShadowPowerManager.getLatestWakeLock()
        try {
            method("store", String::class.java, JSONObject::class.java).invoke(engine, "standby-call", snapshot)
            method("report", String::class.java).invoke(engine, "standby-call")
            method("poll").invoke(engine)
            assertEquals(listOf(EngineCallState.INCOMING, EngineCallState.DISCONNECTED), states)
            assertTrue(requests.contains("/rtc/control/calls/standby-call"))
            assertFalse("no live call and reception off must release all RTC owners", lock.isHeld)
            val previous = requests.size
            method("poll").invoke(engine)
            assertEquals("stopped listening must not advertise or fetch again", previous, requests.size)
        } finally { engine.close() }
    }

    @Test fun anIdlePollWithReceptionOffKeepsADialInFlight() {
        val engine = RtcCallEngine(app, MediaApi { _, _, _ -> JSONObject() }, listener, listeningEnabled = { false })
        val lock = ShadowPowerManager.getLatestWakeLock()
        try {
            val lease = RtcCallEngine::class.java.getDeclaredField("wakeLease").apply { isAccessible = true }
                .get(engine) as RtcSignalingWakeLease
            // An outgoing dial on another thread owns bounded control work.
            val dial = lease.acquire(RtcSignalingWakeLease.Reason.CONTROL_WORK)
            method("poll").invoke(engine)
            assertTrue("the idle branch must not drop another thread's work owner", lock.isHeld)
            lease.release(dial)
            assertFalse(lock.isHeld)
        } finally { engine.close() }
    }

    @Test fun retiredSessionCannotAdvertiseCapabilityOrFetchCalls() {
        var requests = 0
        val engine = RtcCallEngine(app, MediaApi { _, _, _ -> requests++; JSONObject() }, listener,
            sessionIsCurrent = { false })
        try { method("poll").invoke(engine); assertEquals(0, requests) } finally { engine.close() }
    }

    private fun method(name: String, vararg parameters: Class<*>) =
        RtcCallEngine::class.java.getDeclaredMethod(name, *parameters).apply { isAccessible = true }
}
