package com.callagent.host.push

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PushProbeTest {
    @After fun reset() = PushProbe.resetForTest()

    @Test fun aTestWakeUpThatArrivesClearsThePendingTestAndOneThatDoesNotIsLost() {
        val now = android.os.SystemClock.elapsedRealtime()
        PushProbe.setForTest("a", PushProbe.NodeProbe(supported = true, relayOk = true, testPending = true, checkedAt = now))
        PushProbe.setForTest("b", PushProbe.NodeProbe(supported = true, relayOk = true, testPending = true, checkedAt = now))
        ShadowSystemClock.advanceBy(Duration.ofSeconds(3))
        PushProbe.pushArrived("a")
        val arrived = PushProbe.probe("a")!!
        assertFalse(arrived.testPending)
        assertTrue(arrived.reachedAt > now)
        assertTrue("still within the window", PushProbe.probe("b")!!.testPending)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(61))
        val lost = PushProbe.probe("b")!!
        assertFalse(lost.testPending)
        assertTrue("no wake-up within a minute", lost.testLost)
        assertEquals(arrived, PushProbe.probe("a"))
    }
}
