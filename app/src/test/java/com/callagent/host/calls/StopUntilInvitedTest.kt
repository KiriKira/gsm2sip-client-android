package com.callagent.host.calls

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopUntilInvitedTest {
    @Test
    fun idleCallControlStopsOnlyWhereAPushCanBringTheNextCall() {
        assertTrue(HostCallService.mayStopUntilInvited(listening = true, callWork = false, receptionV2 = true, pushReady = true))
        assertFalse("without push nothing would wake it", HostCallService.mayStopUntilInvited(true, false, receptionV2 = true, pushReady = false))
        assertFalse("an older node needs its renewals", HostCallService.mayStopUntilInvited(true, false, receptionV2 = false, pushReady = true))
    }

    @Test
    fun anyCallWorkKeepsItRunning() {
        // Ringing, answering, dialing or a wake-up not yet polled.
        assertFalse(HostCallService.mayStopUntilInvited(listening = true, callWork = true, receptionV2 = true, pushReady = true))
        assertFalse("not listening: nothing to stop", HostCallService.mayStopUntilInvited(listening = false, callWork = false, receptionV2 = true, pushReady = true))
    }
}
