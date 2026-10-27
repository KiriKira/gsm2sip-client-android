package com.callagent.host.calls

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.SystemClock
import com.callagent.host.data.SessionStore
import com.callagent.host.push.PushWakeHandoff

/**
 * Recovery while the failure-backoff experiment has released the idle lock.
 * The engine's own retry is a plain timer, which stops in deep sleep, and a
 * weak link that comes back without any network callback (4G in a tunnel)
 * never wakes it. So a failing streak keeps an inexact allow-while-idle alarm
 * armed with a growing delay, and asks Android to re-check the network once:
 * a link that really broke then turns unvalidated and, when it recovers,
 * validated again, which the engine's network callback answers at once.
 */
internal object RtcFailureRecovery {
    private const val REQUEST = 7411
    private const val MIN_DELAY_MS = 60_000L
    private const val MAX_DELAY_MS = 15 * 60_000L

    /** 1, 2, 4, 8, then every 15 minutes from the third failure. */
    fun delayFor(failures: Int): Long =
        (MIN_DELAY_MS shl (failures - 3).coerceIn(0, 4)).coerceAtMost(MAX_DELAY_MS)

    fun schedule(context: Context, failures: Int) {
        val app = context.applicationContext
        runCatching {
            app.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delayFor(failures), intent(app))
        }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        runCatching { app.getSystemService(AlarmManager::class.java).cancel(intent(app)) }
    }

    /**
     * Asks Android to re-validate the networks the engine cannot reach through:
     * under a VPN the default network is the tunnel, and only re-checking the
     * Wi-Fi or mobile network under it notices that link failing.
     */
    fun reportUnreachable(context: Context) {
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
        (com.callagent.host.network.NetworkEnvironment.underlying() + listOfNotNull(manager.activeNetwork)).distinct()
            .forEach { runCatching { manager.reportNetworkConnectivity(it, false) } }
    }

    private fun intent(app: Context) = PendingIntent.getBroadcast(app, REQUEST,
        Intent(app, RtcRetryReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

/** The recovery alarm: hand the wake to the call engine before the receiver returns. */
class RtcRetryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val session = runCatching { SessionStore(app).read() }.getOrNull() ?: return
        // The engine's next poll consumes this bounded bridge once it owns its own lock.
        val ticket = runCatching { PushWakeHandoff.begin(app, session.sessionInstanceId, PushWakeHandoff.Channel.CALL) }.getOrNull()
        if (!CallRuntime.requestCallSyncFromWakeHint() && ticket != null) PushWakeHandoff.rejected(ticket)
    }
}
