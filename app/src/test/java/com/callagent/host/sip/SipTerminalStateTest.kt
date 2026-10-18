package com.callagent.host.sip

import org.junit.Assert.assertEquals
import org.junit.Test

class SipTerminalStateTest {
    @Test fun cancelledRingingCallEndsNormally() {
        assertEquals(SipCallState.DISCONNECTED, terminalSipCallState(487))
    }
    @Test fun busyTimeoutAndServerFailuresRemainFailures() {
        for (status in listOf(408, 480, 486, 500, 503, 603)) {
            assertEquals("SIP $status", SipCallState.FAILED, terminalSipCallState(status))
        }
    }
    @Test fun completedDialogEndsNormally() {
        assertEquals(SipCallState.DISCONNECTED, terminalSipCallState(200))
    }
}
