package com.callagent.host.calls

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.callagent.host.MainActivity

internal object CallSignalingNotification {
    const val CHANNEL_ID = "call_signaling"
    const val NOTIFICATION_ID = 7402

    fun build(context: Context, message: String = "来电信令正在保持") : Notification {
        prepare(context)
        val stop = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID,
            Intent(context, CallActionReceiver::class.java).setAction(CallActionReceiver.ACTION_STOP_SIGNALING),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID + 1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("远程 SIM 来电接收已启用")
            .setContentText(message)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止接收", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun cancel(context: Context) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
    }

    fun update(context: Context, message: String) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, build(context, message))
    }

    private fun prepare(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "远程来电接收", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "用户开启时保持自建 SIP 信令连接以接收远程 SIM 来电"
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
            )
        }
    }
}
