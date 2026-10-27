package com.callagent.host.calls

import android.app.AlarmManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import com.callagent.rtcmedia.MediaApi
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkInfo
import org.robolectric.shadows.ShadowPowerManager
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class RtcSleepyListeningTest {
    private val app: Context = RuntimeEnvironment.getApplication()
    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java))
    private val listener = object : CallEngine.Listener {
        override fun onConnectionState(state: EngineConnectionState, statusCode: Int?, reason: String?) = Unit
        override fun onCallState(snapshot: EngineCallSnapshot) = Unit
        override fun onEngineError(error: Throwable) = Unit
    }

    @Before fun online() {
        val network = shadowOf(app.getSystemService(ConnectivityManager::class.java))
        network.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
            ConnectivityManager.TYPE_WIFI, 0, true, true))
        network.setDefaultNetworkActive(true)
        CallRuntime.wakeChannelLive = true
    }

    @After fun reset() {
        CallRuntime.wakeChannelLive = false
        StandbyPowerPolicy.setSleepyListening(app, true)
    }

    private fun engine() = RtcCallEngine(app, MediaApi { _, path, _ ->
        if (path.endsWith("/calls")) JSONObject("{\"calls\":[]}") else JSONObject()
    }, listener, pushReady = { false })

    private fun poll(engine: RtcCallEngine) =
        RtcCallEngine::class.java.getDeclaredMethod("poll").apply { isAccessible = true }.invoke(engine)

    @Test fun idleWithAWakeChannelSleepsBetweenAlarmBeats() {
        val engine = engine()
        val lock = ShadowPowerManager.getLatestWakeLock()
        try {
            poll(engine)
            assertFalse("idle reception must not hold the CPU between beats", lock.isHeld)
            assertNotNull("the next capability renewal is an alarm that runs in deep sleep", alarms.peekNextScheduledAlarm())
        } finally { engine.close() }
        assertNull("closing the engine drops the beat", alarms.peekNextScheduledAlarm())
    }

    @Test fun withoutAWakeChannelOrWithTheSwitchOffTheCompatibilityLeaseStays() {
        CallRuntime.wakeChannelLive = false
        var engine = engine()
        var lock = ShadowPowerManager.getLatestWakeLock()
        try { poll(engine); assertTrue("no WebSocket and no push: stay awake to hear calls", lock.isHeld) }
        finally { engine.close() }

        CallRuntime.wakeChannelLive = true
        StandbyPowerPolicy.setSleepyListening(app, false)
        engine = engine()
        lock = ShadowPowerManager.getLatestWakeLock()
        try { poll(engine); assertTrue("compatibility reception was chosen", lock.isHeld) }
        finally { engine.close() }
    }

    @Test fun beatsHeldBackPastTheCapabilityWindowFallBackToTheLease() {
        val engine = engine()
        val lock = ShadowPowerManager.getLatestWakeLock()
        try {
            poll(engine)
            assertFalse(lock.isHeld)
            repeat(2) {
                ShadowSystemClock.advanceBy(Duration.ofSeconds(85))
                poll(engine)
            }
            assertTrue("two late beats in a row must not let the 90 s capability lapse", lock.isHeld)
        } finally { engine.close() }
    }
}
