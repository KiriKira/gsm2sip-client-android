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
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class RtcCallEngineOfflineTest {
    @Test fun noFcmOfflineListenerReleasesPowerAndRecoveryFetchesImmediately() {
        val app: Context = RuntimeEnvironment.getApplication()
        val network = shadowOf(app.getSystemService(ConnectivityManager::class.java))
        network.setDefaultNetworkActive(false)
        val requests = AtomicInteger()
        val ready = java.util.concurrent.atomic.AtomicBoolean()
        val engine = RtcCallEngine(app, MediaApi { _, path, _ ->
            requests.incrementAndGet()
            if (path.endsWith("/calls")) JSONObject("{\"calls\":[]}") else JSONObject()
        }, object : CallEngine.Listener {
            override fun onConnectionState(state: EngineConnectionState, statusCode: Int?, reason: String?) { ready.set(state == EngineConnectionState.READY) }
            override fun onCallState(snapshot: EngineCallSnapshot) = Unit
            override fun onEngineError(error: Throwable) = Unit
        }, pushReady = { false })
        val lock = ShadowPowerManager.getLatestWakeLock()
        fun drain() {
            val worker = RtcCallEngine::class.java.getDeclaredField("worker").apply { isAccessible = true }.get(engine) as ScheduledExecutorService
            worker.submit {}.get(3, TimeUnit.SECONDS)
        }
        try {
            engine.connect()
            drain()
            assertFalse(lock.isHeld)
            assertEquals(0, requests.get())
            network.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
                ConnectivityManager.TYPE_WIFI, 0, true, true))
            network.setDefaultNetworkActive(true)
            engine.refreshNetwork()
            drain()
            assertTrue(lock.isHeld)
            assertTrue(requests.get() >= 2)
            assertTrue("restored connection must parse the real call-list response", ready.get())
        } finally { engine.close() }
        assertFalse(lock.isHeld)
    }
    @Test fun optedInFailureBackoffReleasesIdlePowerAndSuccessRestoresReception() {
        val app: Context = RuntimeEnvironment.getApplication()
        val network = shadowOf(app.getSystemService(ConnectivityManager::class.java))
        network.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
            ConnectivityManager.TYPE_WIFI, 0, true, true))
        network.setDefaultNetworkActive(true)
        StandbyPowerPolicy.setReleaseDuringFailure(app, true)
        var failing = true
        val engine = RtcCallEngine(app, MediaApi { _, path, _ ->
            if (failing) throw java.io.IOException("node unreachable")
            if (path.endsWith("/calls")) JSONObject("{\"calls\":[]}") else JSONObject()
        }, object : CallEngine.Listener {
            override fun onConnectionState(state: EngineConnectionState, statusCode: Int?, reason: String?) = Unit
            override fun onCallState(snapshot: EngineCallSnapshot) = Unit
            override fun onEngineError(error: Throwable) = Unit
        }, pushReady = { false })
        val lock = ShadowPowerManager.getLatestWakeLock()
        val poll = RtcCallEngine::class.java.getDeclaredMethod("poll").apply { isAccessible = true }
        val alarms = shadowOf(app.getSystemService(android.app.AlarmManager::class.java))
        try {
            repeat(3) { poll.invoke(engine) }
            assertFalse("failed idle attempts must release work and compatibility owners", lock.isHeld)
            assertNotNull("a released streak keeps a retry that runs in deep sleep", alarms.peekNextScheduledAlarm())
            failing = false
            poll.invoke(engine)
            assertTrue("a successful fetch restores compatibility listening", lock.isHeld)
            assertNull("success cancels the sleep-proof retry", alarms.peekNextScheduledAlarm())
        } finally {
            engine.close()
            StandbyPowerPolicy.setReleaseDuringFailure(app, false)
        }
        assertFalse(lock.isHeld)
    }

    @Test fun recoveryRetriesBackOffToFifteenMinutes() {
        assertEquals(listOf(1L, 2L, 4L, 8L, 15L, 15L), (3..8).map { RtcFailureRecovery.delayFor(it) / 60_000L })
    }

    @Test fun theSameNetworkValidatingAgainWakesAReleasedEngine() {
        val app: Context = RuntimeEnvironment.getApplication()
        val network = shadowOf(app.getSystemService(ConnectivityManager::class.java))
        network.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
            ConnectivityManager.TYPE_MOBILE, 0, true, true))
        network.setDefaultNetworkActive(true)
        val requests = AtomicInteger()
        val engine = RtcCallEngine(app, MediaApi { _, path, _ ->
            requests.incrementAndGet()
            if (path.endsWith("/calls")) JSONObject("{\"calls\":[]}") else JSONObject()
        }, object : CallEngine.Listener {
            override fun onConnectionState(state: EngineConnectionState, statusCode: Int?, reason: String?) = Unit
            override fun onCallState(snapshot: EngineCallSnapshot) = Unit
            override fun onEngineError(error: Throwable) = Unit
        }, pushReady = { false })
        val callback = RtcCallEngine::class.java.getDeclaredField("networkCallback").apply { isAccessible = true }
            .get(engine) as ConnectivityManager.NetworkCallback
        val worker = RtcCallEngine::class.java.getDeclaredField("worker").apply { isAccessible = true }.get(engine) as ScheduledExecutorService
        val mobile = org.robolectric.shadows.ShadowNetwork.newInstance(201)
        fun caps(validated: Boolean) = org.robolectric.shadows.ShadowNetworkCapabilities.newInstance().apply {
            shadowOf(this).addTransportType(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
            if (validated) shadowOf(this).addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        try {
            callback.onCapabilitiesChanged(mobile, caps(validated = true))
            callback.onCapabilitiesChanged(mobile, caps(validated = false))
            worker.submit {}.get(3, TimeUnit.SECONDS)
            val before = requests.get()
            callback.onCapabilitiesChanged(mobile, caps(validated = false))
            worker.submit {}.get(3, TimeUnit.SECONDS)
            assertEquals("an unchanged unvalidated link does not poll", before, requests.get())
            callback.onCapabilitiesChanged(mobile, caps(validated = true))
            worker.submit {}.get(3, TimeUnit.SECONDS)
            assertTrue("validation returning on the same network polls at once", requests.get() > before)
        } finally { engine.close() }
    }
}
