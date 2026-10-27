package com.callagent.host.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PairedHostFilterTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 100 * day

    @Test fun anEarlierPairingOfThisPhoneIsNotListedTwice() {
        val hosts = parsePairedHosts(JSONObject("""{"items":[
            {"id":"old","name":"SM-F971Q","platform":"android","state":"active","is_self":false,"live":false},
            {"id":"self","name":"SM-F971Q","platform":"android","state":"active","is_self":true,"live":true,"last_active_at":"2026-10-27T10:00:00Z"},
            {"id":"tablet","name":"Tab","platform":"android","state":"active","is_self":false,"live":true,"last_active_at":"2026-10-27T09:00:00Z"}
        ]}"""))
        val tabletActive = hosts.single { it.id == "tablet" }.lastActiveAt!!
        assertEquals(listOf("self", "tablet"), hosts.filter { it.inUse(tabletActive + day) }.map { it.id })
        assertFalse("unused for over a week", hosts.single { it.id == "tablet" }.inUse(tabletActive + 8 * day))
    }

    @Test fun aNodeThatDoesNotSayKeepsEveryActivePairing() {
        val old = PairedHost("x", "phone", "android", "active", isSelf = false)
        assertTrue(old.inUse(now))
        assertFalse(old.copy(state = "revoked").inUse(now))
        assertTrue("this phone is always listed", old.copy(isSelf = true, live = false).inUse(now))
    }
}
