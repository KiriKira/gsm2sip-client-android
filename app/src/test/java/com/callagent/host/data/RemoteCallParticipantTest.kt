package com.callagent.host.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteCallParticipantTest {
    @Test
    fun answeredElsewhereIsOnlyShownForThisParticipantAfterItEnded() {
        assertEquals("另一台主机已接听此来电。", call("ended", "answered_elsewhere").localTerminalNotice())
        assertNull(call("active", "answered_elsewhere").localTerminalNotice())
        assertNull(call("ended", "busy").localTerminalNotice())
    }

    private fun call(state: String, reason: String) = RemoteCall(
        callId = "call-1",
        gatewayId = "gateway-1",
        clientId = "client-self",
        simId = "sim-1",
        mappingRevision = 3L,
        direction = "incoming",
        state = state,
        stateRevision = 5L,
        from = "+15550000000",
        to = null,
        createdAt = "2026-01-01T00:00:00Z",
        expiresAt = null,
        reason = reason
    )
}
