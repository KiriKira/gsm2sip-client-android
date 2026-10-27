package com.callagent.host.calls

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

/**
 * The beat of idle reception without a continuous wake lock. The server offers
 * a call only to a phone that renewed its capability within 90 s, and a plain
 * timer stops in deep sleep, so an exact allow-while-idle alarm wakes the
 * engine every [INTERVAL_MS] to renew it and read the call list, then the CPU
 * sleeps again. An invitation in between arrives as a WebSocket packet or an
 * FCM push, either of which wakes the phone at once.
 *
 * Android 12+ allows an app 72 allow-while-idle alarms an hour in Doze; one a
 * minute stays inside that. If they still come late, the engine falls back to
 * the compatibility lease rather than let the capability lapse.
 */
internal object RtcSleepyListening {
    const val INTERVAL_MS = 60_000L
    /** With push sleep granted by every node (see [RtcSleep]): only grants and SMS catch-up remain. */
    const val TRUSTED_INTERVAL_MS = 10 * 60_000L
    private const val REQUEST = 7412

    fun intervalFor(trusted: Boolean) = if (trusted) TRUSTED_INTERVAL_MS else INTERVAL_MS

    /** A gap this long between beats means the alarm was held back: 20 s past a
     * minute beat, before the 90 s capability lapses; 10 minutes past a trusted
     * beat, well inside a 30-minute grant. */
    fun lateAfter(intervalMs: Long) = intervalMs + if (intervalMs > INTERVAL_MS) 10 * 60_000L else 20_000L

    fun canSchedule(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            runCatching { context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }.getOrDefault(false)

    fun schedule(context: Context, intervalMs: Long = INTERVAL_MS) {
        val app = context.applicationContext
        runCatching {
            app.getSystemService(AlarmManager::class.java).setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + intervalMs, intent(app))
        }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        runCatching { app.getSystemService(AlarmManager::class.java).cancel(intent(app)) }
    }

    private fun intent(app: Context) = PendingIntent.getBroadcast(app, REQUEST,
        Intent(app, RtcRetryReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
