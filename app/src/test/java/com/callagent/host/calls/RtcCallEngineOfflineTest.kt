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
}
