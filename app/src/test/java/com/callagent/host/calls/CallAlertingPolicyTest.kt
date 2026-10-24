package com.callagent.host.calls

import android.app.NotificationManager
import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallAlertingPolicyTest {
    @Test
    fun ringsAndVibratesLikeThePhoneAppForEachRingerMode() {
        val all = NotificationManager.INTERRUPTION_FILTER_ALL
        assertEquals(IncomingRinger.Mode(sound = true, vibrate = true),
            IncomingRinger.ringerMode(AudioManager.RINGER_MODE_NORMAL, all))
        assertEquals(IncomingRinger.Mode(sound = false, vibrate = true),
            IncomingRinger.ringerMode(AudioManager.RINGER_MODE_VIBRATE, all))
        assertEquals(IncomingRinger.Mode(sound = false, vibrate = false),
            IncomingRinger.ringerMode(AudioManager.RINGER_MODE_SILENT, all))
        for (quiet in listOf(NotificationManager.INTERRUPTION_FILTER_NONE, NotificationManager.INTERRUPTION_FILTER_ALARMS)) {
            assertEquals("Do Not Disturb keeps the phone quiet", IncomingRinger.Mode(sound = false, vibrate = false),
                IncomingRinger.ringerMode(AudioManager.RINGER_MODE_NORMAL, quiet))
        }
    }

    @Test
    fun theScreenTurnsOffAtTheEarOnlyWhileAudioIsOnTheEarpiece() {
        assertTrue(ProximityScreenLock.wanted(CallPhase.ACTIVE, CallAudioEndpoint.EARPIECE, speaker = false))
        assertTrue(ProximityScreenLock.wanted(CallPhase.DIALING, null, speaker = false))
        assertFalse(ProximityScreenLock.wanted(CallPhase.ACTIVE, CallAudioEndpoint.SPEAKER, speaker = true))
        assertFalse(ProximityScreenLock.wanted(CallPhase.ACTIVE, CallAudioEndpoint.BLUETOOTH, speaker = false))
        assertFalse(ProximityScreenLock.wanted(CallPhase.ACTIVE, null, speaker = true))
        assertFalse("a ringing call is answered by looking at the screen",
            ProximityScreenLock.wanted(CallPhase.INCOMING_RINGING, CallAudioEndpoint.EARPIECE, speaker = false))
        assertFalse(ProximityScreenLock.wanted(CallPhase.ENDED, CallAudioEndpoint.EARPIECE, speaker = false))
    }
}
