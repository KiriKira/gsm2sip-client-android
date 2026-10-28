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
}
