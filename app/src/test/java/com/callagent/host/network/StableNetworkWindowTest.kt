package com.callagent.host.network

import org.junit.Assert.*
import org.junit.Test

class StableNetworkWindowTest {
    @Test fun quietWifiSlowsMaintenanceButVpnChangeAndFailuresResetIt() {
        val window = StableNetworkWindow()
        window.changed("wifi", 0)
        repeat(3) { window.succeeded() }
        assertFalse(window.stable(599_999))
        assertTrue(window.stable(600_000))
        assertFalse(window.changed("wifi", 600_001))
        assertTrue(window.stable(600_001))
        assertTrue(window.changed("wifi+vpn", 600_002))
        assertFalse(window.stable(600_002))
        repeat(3) { window.succeeded() }
        assertTrue(window.stable(1_200_002))
        window.failed(1_200_003)
        assertFalse(window.stable(1_200_003))
    }
}
