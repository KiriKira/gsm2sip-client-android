package com.callagent.host.calls

import android.content.Context
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
@Config(sdk = [28])
class RtcSignalingWakeLeaseTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test fun stoppingListeningReleasesLockAndLatePollCannotReacquireIt() {
        val lease = RtcSignalingWakeLease(context)
        val lock = ShadowPowerManager.getLatestWakeLock()
        assertFalse(lock.isHeld)
        lease.renew()
        lease.renew()
        assertTrue(lock.isHeld)
        lease.close()
        lease.close()
        lease.renew()
        assertFalse(lock.isHeld)
    }

    @Test fun losingNetworkPausesLeaseAndRecoveryCanRenewUntilClosed() {
        val lease = RtcSignalingWakeLease(context)
        val lock = ShadowPowerManager.getLatestWakeLock()
        lease.renew()
        lease.pause()
        assertFalse(lock.isHeld)
        lease.renew()
        assertTrue(lock.isHeld)
        lease.close()
        lease.renew()
        assertFalse(lock.isHeld)
    }

    @Test fun replacingNodeEngineDoesNotReleaseTheNewEngineLease() {
        val old = RtcSignalingWakeLease(context)
        val oldLock = ShadowPowerManager.getLatestWakeLock()
        old.renew()
        val replacement = RtcSignalingWakeLease(context)
        val newLock = ShadowPowerManager.getLatestWakeLock()
        replacement.renew()
        old.close()
        assertFalse(oldLock.isHeld)
        assertTrue(newLock.isHeld)
        replacement.close()
        assertFalse(newLock.isHeld)
    }

    @Test fun workerRenewsAcrossInitialDeadlineButAStalledWorkerEventuallyLosesLease() {
        val lease = RtcSignalingWakeLease(context)
        val lock = ShadowPowerManager.getLatestWakeLock()
        lease.renew()
        repeat(4) {
            ShadowSystemClock.advanceBy(Duration.ofSeconds(21))
            assertTrue(lock.isHeld)
            lease.renew()
        }
        assertTrue("polling continues after the original 60 second lease", lock.isHeld)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(61))
        assertFalse("a stalled polling worker must not hold power forever", lock.isHeld)
        lease.close()
    }
}
