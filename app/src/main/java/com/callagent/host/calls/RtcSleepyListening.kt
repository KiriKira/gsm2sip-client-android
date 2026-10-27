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
    /** A gap this long between beats means the alarm was held back. */
    const val LATE_MS = 80_000L
    private const val REQUEST = 7412

    fun canSchedule(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            runCatching { context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }.getOrDefault(false)

    fun schedule(context: Context) {
        val app = context.applicationContext
        runCatching {
            app.getSystemService(AlarmManager::class.java).setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + INTERVAL_MS, intent(app))
        }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        runCatching { app.getSystemService(AlarmManager::class.java).cancel(intent(app)) }
    }

    private fun intent(app: Context) = PendingIntent.getBroadcast(app, REQUEST,
        Intent(app, RtcRetryReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
