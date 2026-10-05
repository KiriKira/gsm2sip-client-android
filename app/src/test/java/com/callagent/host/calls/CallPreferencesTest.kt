package com.callagent.host.calls

import android.content.Context
import com.callagent.host.data.HostSession
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CallPreferencesTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val preferences = CallPreferences(context)

    @After
    fun cleanUp() {
        preferences.clear()
    }

    @Test
    fun listeningIsExplicitAndBoundToThePairingSession() {
        val first = session("session-1")
        val second = session("session-2")

        assertFalse(preferences.enabledFor(first))
        assertFalse(preferences.enabledFor(null))
        assertTrue(preferences.setEnabled(first, true))
        assertTrue(preferences.enabledFor(first))
        assertFalse(preferences.enabledFor(second))
        assertFalse(preferences.enabledFor(null))

        assertTrue(preferences.setEnabled(first, false))
        assertFalse(preferences.enabledFor(first))
    }

    @Test
    fun automaticListeningRequiresAvailableClientSignalingAndDoesNotNeedMicrophoneState() {
        val paired = session("paired-session")
        val nonClient = paired.copy(role = "gateway")

        assertFalse(preferences.canAutoListenFor(null, signalingAvailable = true))
        assertFalse(preferences.canAutoListenFor(nonClient, signalingAvailable = true))
        assertFalse(preferences.canAutoListenFor(paired, signalingAvailable = false))
        assertTrue(preferences.canAutoListenFor(paired, signalingAvailable = true))
        assertTrue(preferences.ensureEnabledFor(paired))
        assertTrue(preferences.enabledFor(paired))
        assertTrue(preferences.ensureEnabledFor(paired))
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
        sipAvailable = true,
        sipReason = null
    )
}
