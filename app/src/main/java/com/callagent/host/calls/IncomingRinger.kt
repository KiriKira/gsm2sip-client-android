package com.callagent.host.calls

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

/**
 * Rings and vibrates for an incoming call the way the system phone app does.
 * A self-managed Telecom call gets no ringtone from the system, and a
 * notification channel sound plays at notification volume (often muted) and
 * is dropped whenever Android decides not to alert for that notification.
 * The ringtone plays on the ring stream instead, at ring volume, and follows
 * the ringer mode and Do Not Disturb.
 */
internal object IncomingRinger {
    private var callId: String? = null
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    /** Rings while [call] is an incoming call still ringing, stops otherwise. */
    @Synchronized
    fun sync(context: Context, call: CallSession?) {
        val ringing = call?.takeIf { it.direction == CallDirection.INCOMING && it.phase == CallPhase.INCOMING_RINGING }
        if (ringing == null) stop() else start(context.applicationContext, ringing.callId)
    }

    @Synchronized
    fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        callId = null
    }

    private fun start(context: Context, id: String) {
        if (callId == id) return
        stop()
        callId = id
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        val mode = ringerMode(audio.ringerMode, interruptionFilter(context))
        if (mode.sound) player = playRingtone(context)
        if (mode.vibrate) vibrator = vibrate(context)
    }

    internal data class Mode(val sound: Boolean, val vibrate: Boolean)

    /** Silent mode and Do Not Disturb without call exceptions keep the phone quiet. */
    internal fun ringerMode(ringerMode: Int, interruptionFilter: Int): Mode = when {
        interruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE ||
            interruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS -> Mode(sound = false, vibrate = false)
        ringerMode == AudioManager.RINGER_MODE_SILENT -> Mode(sound = false, vibrate = false)
        ringerMode == AudioManager.RINGER_MODE_VIBRATE -> Mode(sound = false, vibrate = true)
        else -> Mode(sound = true, vibrate = true)
    }

    private fun interruptionFilter(context: Context): Int =
        context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
            ?: NotificationManager.INTERRUPTION_FILTER_ALL

    private val ringtoneAttributes: AudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    private fun playRingtone(context: Context): MediaPlayer? {
        val uris = listOfNotNull(
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE),
            Settings.System.DEFAULT_RINGTONE_URI
        )
        for (uri in uris) {
            val player = MediaPlayer()
            val started = runCatching {
                player.setAudioAttributes(ringtoneAttributes)
                player.setDataSource(context, uri)
                player.isLooping = true
                player.prepare()
                player.start()
            }.isSuccess
            if (started) return player
            player.release()
        }
        return null
    }

    private fun vibrate(context: Context): Vibrator? {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (vibrator == null || !vibrator.hasVibrator()) return null
        val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN, 0)
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_RINGTONE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, ringtoneAttributes)
            }
        }.isSuccess
        return vibrator.takeIf { started }
    }

    private val VIBRATION_PATTERN = longArrayOf(0L, 1000L, 1000L)
}
