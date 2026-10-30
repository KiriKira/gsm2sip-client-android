package com.callagent.host.calls

import android.content.Context
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CallTelecomControllerTest {
    @Test fun outgoingCallMetadataIsForwardedToConnectionService() {
        val app = RuntimeEnvironment.getApplication()
        val telecom = app.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val shadowTelecom = shadowOf(telecom)
        shadowTelecom.setCallPhonePermission(true)
        val details = TelecomCallDetails("test-outgoing-call", CallDirection.OUTGOING, "123", "Remote SIM")

        CallTelecomController.placeOutgoing(app, details)

        val outgoing = shadowTelecom.onlyOutgoingCall
        assertEquals("gsm2sip", outgoing.address.scheme)
        assertEquals(details.callId, outgoing.address.schemeSpecificPart)
        @Suppress("DEPRECATION")
        val account = outgoing.extras.getParcelable<PhoneAccountHandle>(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE)
        assertEquals("gsm2sip-remote-voice", account?.id)
        // Android builds ConnectionRequest.extras from this Bundle, discarding
        // custom keys placed directly alongside EXTRA_PHONE_ACCOUNT_HANDLE.
        val forwarded = outgoing.extras.getBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS)
        assertNotNull("Telecom must receive the call metadata Bundle", forwarded)
        assertEquals(details.callId, forwarded!!.getString(CallTelecomController.EXTRA_CALL_ID))
        assertEquals(CallDirection.OUTGOING.name, forwarded.getString(CallTelecomController.EXTRA_DIRECTION))
        assertEquals(details.remoteNumber, forwarded.getString(CallTelecomController.EXTRA_NUMBER))
        assertEquals(details.simLabel, forwarded.getString(CallTelecomController.EXTRA_SIM_LABEL))
    }

    @Test fun telecomIncomingUiRequestOpensAnswerAndRejectScreenWithoutAnswering() {
        val app = RuntimeEnvironment.getApplication()
        val now = System.currentTimeMillis()
        val callId = "incoming-ui-call"
        assertTrue(CallRuntime.coordinator.incomingInvite(IncomingInviteIdentity("engine", callId),
            CallAuthority(callId, "incoming", "ringing", "sim", "+12025550101", now + 60_000), now))
        val connection = HostCallConnection(app, TelecomCallDetails(callId, CallDirection.INCOMING, "+12025550101", "SIM"))
        try {
            connection.setRinging()
            connection.onShowIncomingCallUi()
            val intent = shadowOf(app).nextStartedActivity
            assertNotNull("Telecom's self-managed UI request must open visible call controls", intent)
            assertEquals(CallActionActivity::class.java.name, intent.component?.className)
            assertEquals(CallActionActivity.ACTION_SHOW_CALL, intent.action)
            assertEquals(callId, intent.getStringExtra(CallActionActivity.EXTRA_CALL_ID))
            assertEquals("showing UI must never accept the call", CallPhase.INCOMING_RINGING, CallRuntime.currentSession?.phase)
        } finally {
            connection.destroy()
            CallRuntime.coordinator.ended(callId)
            CallRuntime.clearTerminal(callId)
        }
    }

    @Test fun aLateTelecomUiRequestCannotOpenAnEndedOrDifferentCall() {
        val app = RuntimeEnvironment.getApplication()
        val now = System.currentTimeMillis()
        val callId = "current-incoming"
        assertTrue(CallRuntime.coordinator.incomingInvite(IncomingInviteIdentity("engine", callId),
            CallAuthority(callId, "incoming", "ringing", "sim", "+12025550102", now + 60_000), now))
        val stale = HostCallConnection(app, TelecomCallDetails("stale", CallDirection.INCOMING, null, "SIM"))
        val outgoing = HostCallConnection(app, TelecomCallDetails(callId, CallDirection.OUTGOING, null, "SIM"))
        val current = HostCallConnection(app, TelecomCallDetails(callId, CallDirection.INCOMING, null, "SIM"))
        try {
            listOf(stale, outgoing, current).forEach { it.setRinging() }
            stale.onShowIncomingCallUi()
            outgoing.onShowIncomingCallUi()
            assertNull(shadowOf(app).nextStartedActivity)
            CallRuntime.coordinator.ended(callId)
            current.onShowIncomingCallUi()
            assertNull("an ended call must not reopen its UI", shadowOf(app).nextStartedActivity)
        } finally {
            listOf(stale, outgoing, current).forEach { it.destroy() }
            CallRuntime.coordinator.ended(callId)
            CallRuntime.clearTerminal(callId)
        }
    }
}
