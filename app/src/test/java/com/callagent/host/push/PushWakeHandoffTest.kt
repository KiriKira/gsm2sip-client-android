package com.callagent.host.push

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PushWakeHandoffTest {
    @Test fun consumersAreSessionScopedAndOldRejectionCannotReleaseANewerPush() {
        val app = RuntimeEnvironment.getApplication()
        val first = PushWakeHandoff.begin(app, "handoff-a", PushWakeHandoff.Channel.CALL)
        val lock = ShadowPowerManager.getLatestWakeLock()
        val duplicate = PushWakeHandoff.begin(app, "handoff-a", PushWakeHandoff.Channel.CALL)
        assertEquals(first, duplicate)
        PushWakeHandoff.begin(app, "handoff-b", PushWakeHandoff.Channel.SYNC)
        try {
            PushWakeHandoff.consumed("handoff-a", PushWakeHandoff.Channel.CALL)
            assertTrue("another node still needs the bridge", lock.isHeld)
            PushWakeHandoff.consumed("handoff-b", PushWakeHandoff.Channel.SYNC)
            assertFalse(lock.isHeld)
            val next = PushWakeHandoff.begin(app, "handoff-a", PushWakeHandoff.Channel.CALL)
            val nextLock = ShadowPowerManager.getLatestWakeLock()
            PushWakeHandoff.rejected(first)
            assertTrue("a rejected old start must not cancel the new hint", nextLock.isHeld)
            ShadowSystemClock.advanceBy(Duration.ofSeconds(16))
            assertFalse("a consumer that never starts cannot hold power indefinitely", nextLock.isHeld)
            PushWakeHandoff.rejected(next)
        } finally {
            PushWakeHandoff.retire("handoff-a")
            PushWakeHandoff.retire("handoff-b")
        }
    }
}
