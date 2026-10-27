package com.callagent.host.push

import org.junit.Assert.assertEquals
import org.junit.Test

class PushWakeRouteTest {
    @Test
    fun aWireOneHintMayCoverBothSoItFetchesBoth() {
        for (kind in listOf("call", "sms", null)) {
            assertEquals(PushWake.Route(call = true, sms = true), PushWake.route(1, kind))
        }
    }

    @Test
    fun aWireTwoHintFetchesOnlyItsDomain() {
        assertEquals(PushWake.Route(call = true, sms = false), PushWake.route(2, "call"))
        assertEquals("an SMS hint never starts call control", PushWake.Route(call = false, sms = true), PushWake.route(2, "sms"))
        assertEquals(PushWake.Route(call = false, sms = false), PushWake.route(2, "check"))
    }
}
