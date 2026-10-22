package com.callagent.host.push

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.callagent.host.MainActivity
import com.callagent.host.R
import com.callagent.host.background.HostBackgroundRuntime
import com.callagent.host.calls.CallRuntime
import com.callagent.host.calls.RtcCallEngine
import com.callagent.host.data.SessionStore
import com.callagent.host.mesh.NodeMonitor
import com.callagent.host.notify.HostNotificationChannels

/**
 * Acts on an FCM wake-up through the same paths as the WebSocket wake hint:
 * SMS sync and call control fetch everything over HTTPS. When a call wake-up
 * cannot be followed because no node is reachable, the user is told instead
 * of the call vanishing; nothing retries beyond the services' own backoff.
 */
internal object PushWake {
    private const val CALL_CHECK_MS = 12_000L
    private const val UNREACHABLE_NOTICE_ID = 0x4d48

    fun handle(context: Context, kind: String?, deviceId: String?) {
        val app = context.applicationContext
        val pairings = PushRegistration.pairings(app)
        // Only wake-ups from a pairing this phone still holds are acted on.
        val from = pairings.firstOrNull { it.first.deviceId == deviceId }?.first ?: return
        HostBackgroundRuntime.wakeFromPush(app)
        CallRuntime.wakeFromPush(app)
        val active = runCatching { SessionStore(app).read() }.getOrNull()
        if (pairings.size > 1 && active?.sessionInstanceId != from.sessionInstanceId) {
            // The event reached another node: re-check which one is usable now.
            Thread({ runCatching { NodeMonitor.evaluate(app, force = true) } }, "push-node-check").start()
        }
        // Without background call reception the user chose not to get calls here.
        if (kind == "call" && CallRuntime.isListeningRequested) {
            val wokenAt = SystemClock.elapsedRealtime()
            Handler(Looper.getMainLooper()).postDelayed({
                if (callUnreachable(CallRuntime.currentSession != null, RtcCallEngine.lastPollSucceededAt, wokenAt)) {
                    postUnreachableNotice(app)
                }
            }, CALL_CHECK_MS)
        }
    }

    /** No call is showing and call control has not answered since the wake-up. */
    internal fun callUnreachable(callShowing: Boolean, lastPollSucceededAt: Long, wokenAt: Long): Boolean =
        !callShowing && lastPollSucceededAt < wokenAt

    private fun postUnreachableNotice(app: Context) {
        HostNotificationChannels.ensure(app)
        if (!HostNotificationChannels.channelAllowed(app, HostNotificationChannels.CALL_ALERTS)) return
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val open = PendingIntent.getActivity(app, UNREACHABLE_NOTICE_ID,
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(app, HostNotificationChannels.CALL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle("有来电，但连不上服务器")
            .setContentText("网络恢复后可在通话记录中查看这通来电。")
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(UNREACHABLE_NOTICE_ID, notification) }
    }
}
