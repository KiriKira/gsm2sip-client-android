package com.callagent.host.calls

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Local ringback for an outgoing call until the far end answers. The server
 * relays no early media from the GSM network, so without it the caller hears
 * silence while the remote SIM dials.
 */
internal class OutboundRingback {
    private var tone: ToneGenerator? = null
    private var callId: String? = null

    @Synchronized
    fun start(callId: String) {
        if (this.callId == callId) return
        stop()
        val generator = runCatching { ToneGenerator(AudioManager.STREAM_VOICE_CALL, VOLUME) }.getOrNull() ?: return
        if (runCatching { generator.startTone(ToneGenerator.TONE_SUP_RINGTONE) }.getOrDefault(false)) {
            tone = generator
            this.callId = callId
        } else {
            generator.release()
        }
    }

    /** Stops the tone; with [callId], only if it belongs to that call. */
    @Synchronized
    fun stop(callId: String? = null) {
        if (callId != null && callId != this.callId) return
        tone?.let { generator ->
            runCatching { generator.stopTone() }
            generator.release()
        }
        tone = null
        this.callId = null
    }

    private companion object {
        const val VOLUME = 80
    }
}
