package com.callagent.host.calls

import android.content.Intent
import android.os.Looper
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ExecutorService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], shadows = [StartupTelecomShadow::class])
class HostCallServiceStartupTest {
    @Test fun retiredNodeCleanupCannotStopNewListeningRequest() = checkRetiredNodeCleanup(false)

    @Test fun retiredNodeCleanupCannotFailNextNodesNewCall() = checkRetiredNodeCleanup(true)

    private fun checkRetiredNodeCleanup(newCall: Boolean) {
        StartupTelecomShadow.entered = CountDownLatch(1)
        StartupTelecomShadow.release = CountDownLatch(1)
        StartupTelecomShadow.returned = CountDownLatch(1)
        val controller = Robolectric.buildService(HostCallService::class.java).create()
        val service = controller.get()
        val reachedBarrier = CountDownLatch(1)
        val releaseBarrier = CountDownLatch(1)
        try {
            assertTrue(StartupTelecomShadow.entered.await(5, TimeUnit.SECONDS))
            service.onStartCommand(Intent(service, HostCallService::class.java).setAction("test.old.node"), 0, 1)
            service.stopForSessionChange()
            val field = HostCallService::class.java.getDeclaredField("io").apply { isAccessible = true }
            (field.get(service) as ExecutorService).execute {
                reachedBarrier.countDown()
                releaseBarrier.await(10, TimeUnit.SECONDS)
            }
            if (newCall) assertTrue(CallRuntime.coordinator.beginOutbound("sim", "15550100200", CallMediaTransport.WEBRTC))
            service.onStartCommand(Intent(service, HostCallService::class.java)
                .setAction("com.callagent.host.calls.LISTEN"), 0, 2)
            StartupTelecomShadow.release.countDown()
            assertTrue(reachedBarrier.await(5, TimeUnit.SECONDS))
            assertFalse("An old node must not stop the new node's service", shadowOf(service).isStoppedBySelf)
            assertTrue("The newer LISTEN remains active", service.isForegroundListening)
            if (newCall) assertEquals(CallPhase.AUTHORIZING_OUTBOUND, CallRuntime.currentSession?.phase)
        } finally {
            controller.destroy()
            StartupTelecomShadow.release.countDown()
            releaseBarrier.countDown()
            if (newCall) {
                CallRuntime.coordinator.failed("pending", "test cleanup")
                CallRuntime.clearTerminal("pending")
            }
        }
    }

    @Test fun slowTelecomRegistrationDoesNotBlockForegroundPromotion() {
        StartupTelecomShadow.entered = CountDownLatch(1)
        StartupTelecomShadow.release = CountDownLatch(1)
        StartupTelecomShadow.returned = CountDownLatch(1)
        StartupTelecomShadow.onMain = false
        val controller = Robolectric.buildService(HostCallService::class.java).create()
        val service = controller.get()
        try {
            assertTrue(StartupTelecomShadow.entered.await(5, TimeUnit.SECONDS))
            assertFalse("Telecom registration must not block service callbacks", StartupTelecomShadow.onMain)
            service.onStartCommand(Intent(service, HostCallService::class.java)
                .setAction("com.callagent.host.calls.LISTEN"), 0, 1)
            assertNotNull("Promote while Telecom Binder is still blocked", shadowOf(service).lastForegroundNotification)
            assertEquals(1L, StartupTelecomShadow.release.count)
            assertEquals("Registration must still be blocked when promotion finishes", 1L, StartupTelecomShadow.returned.count)
        } finally {
            controller.destroy()
            StartupTelecomShadow.release.countDown()
        }
    }
}

@Implements(TelecomManager::class)
class StartupTelecomShadow {
    @Implementation fun registerPhoneAccount(account: PhoneAccount) {
        onMain = Looper.myLooper() == Looper.getMainLooper()
        entered.countDown()
        try { release.await(60, TimeUnit.SECONDS) } finally { returned.countDown() }
    }
    companion object {
        @Volatile var entered = CountDownLatch(1)
        @Volatile var release = CountDownLatch(1)
        @Volatile var returned = CountDownLatch(1)
        @Volatile var onMain = false
    }
}
