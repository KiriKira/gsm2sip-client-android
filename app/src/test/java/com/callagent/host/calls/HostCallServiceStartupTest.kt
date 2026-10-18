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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], shadows = [StartupTelecomShadow::class])
class HostCallServiceStartupTest {
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
