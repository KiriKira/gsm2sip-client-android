package com.callagent.host.calls

import android.content.Context
import com.callagent.host.data.HostSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ReceptionV2Test {
    private val context: Context = RuntimeEnvironment.getApplication()

    @After
    fun cleanUp() = ReceptionV2.resetForTest(context)

    @Test
    fun aStoredAnswerSurvivesAsWrittenAndRejectsGarbage() {
        val config = ReceptionConfig(version = 2, incoming = false, sms = true, revision = 8)
        assertEquals(config, ReceptionConfig.decode(config.encode()))
        for (bad in listOf(null, "", "2,false,true", "2,maybe,true,8", "x,false,true,8")) assertNull(ReceptionConfig.decode(bad))
    }

    @Test
    fun aNodeNeedsTheChoiceUntilItConfirmedVersionTwoWithThatChoice() {
        val session = session("node-a")
        val both = ReceptionV2.Choice(incoming = true, sms = true)
        assertTrue(ReceptionV2.needsSync(context, session, both))
        assertFalse(ReceptionV2.active(context, session))
        store(session, ReceptionConfig(2, incoming = true, sms = true, revision = 1))
        assertFalse(ReceptionV2.needsSync(context, session, both))
        assertTrue("a changed call choice is written again", ReceptionV2.needsSync(context, session, both.copy(incoming = false)))
        assertTrue("so is a changed SMS choice", ReceptionV2.needsSync(context, session, both.copy(sms = false)))
        assertTrue(ReceptionV2.active(context, session))
        store(session, ReceptionConfig(1, incoming = true, sms = true, revision = 2))
        assertFalse("a node set back to version 1 renews the old way", ReceptionV2.active(context, session))
    }

    @Test
    fun aNodeSeenWithoutReceptionV2IsRenewedTheOldWayAgain() {
        val session = session("node-b")
        store(session, ReceptionConfig(2, incoming = true, sms = true, revision = 1))
        ReceptionV2.observed("https://api.example.test/v1", offered = false)
        assertFalse(ReceptionV2.active(context, session))
        ReceptionV2.observed("https://api.example.test/v1", offered = true)
        assertTrue(ReceptionV2.active(context, session))
    }

    private fun store(session: HostSession, config: ReceptionConfig) {
        context.getSharedPreferences("host-reception-v2", Context.MODE_PRIVATE).edit()
            .putString(session.sessionInstanceId, config.encode()).commit()
    }

    private fun session(instanceId: String) = HostSession(
        sessionInstanceId = instanceId,
        apiBaseUrl = "https://api.example.test/v1",
        ownerId = "owner-1",
        deviceId = "client-1",
        role = "client",
        accessToken = "access",
        accessExpiresAt = "2026-10-03T10:10:00Z",
        refreshToken = "refresh",
        refreshExpiresAt = "2026-11-03T10:00:00Z",
    )
}
