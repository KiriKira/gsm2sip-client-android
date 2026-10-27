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
import com.callagent.host.data.HostSession

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], shadows = [StartupTelecomShadow::class])
class HostCallServiceStartupTest {
    @Test fun differentNodeRetiresEngine() {
        StartupTelecomShadow.release = CountDownLatch(0)
        StartupTelecomShadow.returned = CountDownLatch(1)
        val controller = Robolectric.buildService(HostCallService::class.java).create()
        val service = controller.get()
        try {
            assertTrue(StartupTelecomShadow.returned.await(5, TimeUnit.SECONDS))
            val old = HostSession("node-a", "https://a.example.invalid/v1", "owner", "client-a", "client",
                "access", "2099-01-01T00:00:00Z", "refresh", "2099-02-01T00:00:00Z")
            for (next in listOf(old.copy(apiBaseUrl="https://b.example.invalid/v1"),
                old.copy(sessionInstanceId="node-b", deviceId="client-b"))) {
                val engine = CountingEngine()
                field("callEngine").set(service,engine)
                field("engineSession").set(service,old)
                field("engineGeneration").set(service,7L)
                field("connectionState").set(service,EngineConnectionState.READY)
                val oldListener = HostCallService::class.java.getDeclaredMethod("listenerFor",Long::class.javaPrimitiveType)
                    .apply{isAccessible=true}.invoke(service,7L) as CallEngine.Listener
                service.retireMismatchedEngine(next)
                assertEquals("old authenticated node must be closed",1,engine.closed)
                assertNull(field("callEngine").get(service))
                oldListener.onConnectionState(EngineConnectionState.READY)
                val finished=CountDownLatch(1)
                (field("io").get(service) as ExecutorService).execute{finished.countDown()}
                assertTrue(finished.await(5,TimeUnit.SECONDS))
                assertFalse("late old-node READY cannot authorize the next node",service.isSignalingReady)
            }
        } finally {controller.destroy()}
    }

    @Test fun refreshedTokenKeepsSameNodeRtcEngineAndIncomingListener() {
        StartupTelecomShadow.release = CountDownLatch(0)
        StartupTelecomShadow.returned = CountDownLatch(1)
        val controller=Robolectric.buildService(HostCallService::class.java).create()
        val service=controller.get()
        try {
            assertTrue(StartupTelecomShadow.returned.await(5,TimeUnit.SECONDS))
            val session=HostSession("node-a","https://a.example.invalid/v1","owner","client-a","client",
                "access","2099-01-01T00:00:00Z","refresh","2099-02-01T00:00:00Z")
            val engine=CountingEngine()
            field("callEngine").set(service,engine)
            field("engineSession").set(service,session)
            field("connectionState").set(service,EngineConnectionState.READY)
            service.retireMismatchedEngine(session.copy(accessToken="renewed-access",refreshToken="renewed-refresh"))
            assertEquals(0,engine.closed)
            assertSame(engine,field("callEngine").get(service))
            assertTrue(service.isSignalingReady)
        } finally {controller.destroy()}
    }

    private fun field(name:String)=HostCallService::class.java.getDeclaredField(name).apply{isAccessible=true}
    @Test fun wakeHintReachesAnyEngineThroughTheInterface() {
        StartupTelecomShadow.release = CountDownLatch(0)
        StartupTelecomShadow.returned = CountDownLatch(1)
        val controller=Robolectric.buildService(HostCallService::class.java).create()
        val service=controller.get()
        try {
            val engine=CountingEngine()
            field("callEngine").set(service,engine)
            service.requestCallSyncFromWakeHint()
            assertEquals("a wake hint must reach the call engine",1,engine.woken)
        } finally {controller.destroy()}
    }

    @Test fun wakeDuringStartupIsDeliveredWhenTheEngineBecomesAvailable() {
        StartupTelecomShadow.release = CountDownLatch(0)
        StartupTelecomShadow.returned = CountDownLatch(1)
        val controller = Robolectric.buildService(HostCallService::class.java).create()
        val service = controller.get()
        try {
            field("listeningRequested").set(service, true)
            assertTrue(service.requestCallSyncFromWakeHint())
            val engine = CountingEngine()
            field("callEngine").set(service, engine)
            val drain = HostCallService::class.java.getDeclaredMethod("deliverPendingCallWake").apply { isAccessible = true }
            drain.invoke(service)
            drain.invoke(service)
            assertEquals(1, engine.woken)
        } finally { controller.destroy() }
    }

    private class CountingEngine:CallEngine {
        var closed=0
        var woken=0
        override fun wake(){woken++}
        override fun connect()=Unit
        override fun dial(serverCallId:String,destination:String)=Unit
        override fun answer(callId:String)=Unit
        override fun reject(callId:String,reason:CallRejection)=Unit
        override fun hangup(callId:String)=Unit
        override fun sendDtmf(callId:String,digits:String)=Unit
        override fun setMute(callId:String,muted:Boolean)=Unit
        override fun enableAudio(callId:String)=Unit
        override fun suspendAudio(callId:String)=Unit
        override fun refreshNetwork()=Unit
        override fun close(){closed++}
    }

    @Test fun failedOutgoingPreflightKeepsExistingIncomingListener() {
        StartupTelecomShadow.entered = CountDownLatch(1)
        StartupTelecomShadow.release = CountDownLatch(0)
        StartupTelecomShadow.returned = CountDownLatch(1)
        val controller = Robolectric.buildService(HostCallService::class.java).create()
        val service = controller.get()
        try {
            assertTrue(StartupTelecomShadow.returned.await(5, TimeUnit.SECONDS))
            for ((name, value) in mapOf("foregroundListening" to true, "listeningRequested" to true,
                "connectionState" to EngineConnectionState.READY)) {
                HostCallService::class.java.getDeclaredField(name).apply { isAccessible = true }.set(service, value)
            }
            assertTrue(CallRuntime.coordinator.beginOutbound("sim", "15550100200"))
            service.onStartCommand(Intent(service, HostCallService::class.java)
                .setAction("com.callagent.host.calls.OUTBOUND"), 0, 1)
            val completed = CountDownLatch(1)
            val field = HostCallService::class.java.getDeclaredField("io").apply { isAccessible = true }
            (field.get(service) as ExecutorService).execute { completed.countDown() }
            assertTrue(completed.await(5, TimeUnit.SECONDS))
            assertEquals(CallPhase.FAILED, CallRuntime.currentSession?.phase)
            assertTrue("Rejected outbound must retain incoming listening", service.isForegroundListening)
            assertNotNull(shadowOf(service).lastForegroundNotification)
            assertFalse("Existing listening service must remain alive", shadowOf(service).isStoppedBySelf)
        } finally {
            controller.destroy()
            CallRuntime.clearTerminal("pending")
        }
    }

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
            if (newCall) assertTrue(CallRuntime.coordinator.beginOutbound("sim", "15550100200"))
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
