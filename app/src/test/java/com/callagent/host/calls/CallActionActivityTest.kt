package com.callagent.host.calls

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
class CallActionActivityTest {
    private val coordinator get() = CallRuntime.coordinator

    @Before
    fun setup() {
        resetCall()
        val app = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
    }

    @After
    fun resetCall() {
        coordinator.current?.let {
            coordinator.failed(it.callId, "fixture complete")
            coordinator.clearTerminal(it.callId)
        }
    }

    @Test
    fun pendingScreenFollowsAuthorizationEvenBeforeActivityCreation() {
        assertTrue(coordinator.beginOutbound("sim-1", "+12025550101"))
        assertTrue(coordinator.outboundAuthorized("fixture-authorized", System.currentTimeMillis() + 60000, System.currentTimeMillis()))
        val intent = Intent(RuntimeEnvironment.getApplication(), CallActionActivity::class.java)
            .putExtra(CallActionActivity.EXTRA_CALL_ID, "pending")
            .putExtra(CallActionActivity.EXTRA_PENDING_SIM, "sim-1")
            .putExtra(CallActionActivity.EXTRA_PENDING_NUMBER, "+12025550101")
        val controller = Robolectric.buildActivity(CallActionActivity::class.java, intent).setup()
        try {
            val views = descendants(controller.get().findViewById(android.R.id.content)).toList()
            assertTrue(views.filterIsInstance<TextView>().any { it.text.toString() == "正在呼叫…" })
            assertTrue(views.filterIsInstance<MaterialButton>().any { it.text.toString() == "挂断" })
            controller.recreate()
            assertEquals(CallPhase.REGISTERING, coordinator.current?.phase)
        } finally { controller.pause().stop().destroy() }
    }

    @Test
    fun keypadStateSurvivesRecreationAndBackDoesNotEndCall() {
        assertTrue(coordinator.beginOutbound("sim-1", "+12025550102"))
        assertTrue(coordinator.outboundAuthorized("fixture-active", System.currentTimeMillis() + 60000, System.currentTimeMillis()))
        assertTrue(coordinator.registrationReady("fixture-active"))
        assertTrue(coordinator.connected("fixture-active"))
        val intent = Intent(RuntimeEnvironment.getApplication(), CallActionActivity::class.java)
            .putExtra(CallActionActivity.EXTRA_CALL_ID, "fixture-active")
        val controller = Robolectric.buildActivity(CallActionActivity::class.java, intent).setup()
        try {
            val activity = controller.get()
            descendants(activity.findViewById(android.R.id.content)).filterIsInstance<MaterialButton>()
                .first { it.text.toString() == "键盘" }.performClick()
            descendants(activity.findViewById(android.R.id.content)).filterIsInstance<MaterialButton>()
                .first { it.contentDescription?.toString() == "发送按键 5" }.performClick()
            controller.recreate()
            val views = descendants(controller.get().findViewById(android.R.id.content)).toList()
            assertTrue(views.any { it.contentDescription?.toString() == "已发送的按键 5" })
            views.filterIsInstance<MaterialButton>().first { it.text.toString() == "返回" }.performClick()
            assertTrue(controller.get().isFinishing)
            assertEquals(CallPhase.ACTIVE, coordinator.current?.phase)
        } finally { controller.pause().stop().destroy() }
    }

    @Test
    fun openingIncomingScreenNeverAnswersOrRequestsMicrophone() {
        val now = System.currentTimeMillis()
        assertTrue(coordinator.incomingInvite(IncomingInviteIdentity("fixture-sip", "fixture-incoming"),
            CallAuthority("fixture-incoming", "incoming", "ringing", "sim-2", "+12025550103", now + 60000), now))
        val intent = Intent(RuntimeEnvironment.getApplication(), CallActionActivity::class.java)
            .putExtra(CallActionActivity.EXTRA_CALL_ID, "fixture-incoming")
        val controller = Robolectric.buildActivity(CallActionActivity::class.java, intent).setup()
        try {
            val views = descendants(controller.get().findViewById(android.R.id.content)).toList()
            assertTrue(views.filterIsInstance<MaterialButton>().any { it.text.toString() == "接听" })
            assertTrue(views.filterIsInstance<MaterialButton>().any { it.text.toString() == "拒接" })
            assertEquals(CallPhase.INCOMING_RINGING, coordinator.current?.phase)
            assertNull(Shadows.shadowOf(controller.get()).lastRequestedPermission)
        } finally { controller.pause().stop().destroy() }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
