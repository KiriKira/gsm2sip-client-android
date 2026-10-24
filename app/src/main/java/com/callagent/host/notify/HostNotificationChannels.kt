package com.callagent.host.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

/**
 * Every notification channel the host app posts to.
 *
 * Android freezes a channel's importance and sound once it exists, so a
 * behaviour change needs a new channel ID; the retired IDs are deleted here.
 * Persistent service notifications use IMPORTANCE_MIN, which keeps them in the
 * notification shade without a status-bar icon.
 */
object HostNotificationChannels {
    const val BACKGROUND_SERVICE = "host-background-service-min"
    const val CALL_SIGNALING = "call-signaling-min"
    const val INBOUND_SMS = "host-inbound-sms-content"
    const val INCOMING_CALLS = "incoming-calls-ringing"
    const val ONGOING_CALLS = "ongoing-calls"
    const val CALL_ALERTS = "call-alerts"
    const val GROUP_INBOUND_SMS = "com.callagent.host.INBOUND_SMS"

    private val RETIRED = listOf("host-background-service", "call_signaling", "host-inbound-sms", "remote_calls")

    /** The foreground-service deadline must not wait for unrelated SMS/call
     * channel maintenance or the accompanying System UI work. */
    fun ensureCallSignaling(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CALL_SIGNALING) == null) {
            manager.createNotificationChannel(callSignalingChannel())
        }
    }

    private fun callSignalingChannel() = NotificationChannel(CALL_SIGNALING, "来电接收待命", NotificationManager.IMPORTANCE_MIN).apply {
        description = "保持远程 SIM 来电信令连接时的常驻通知。只出现在通知栏中，不在状态栏显示图标。"
        lockscreenVisibility = Notification.VISIBILITY_SECRET
        setShowBadge(false)
    }

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        RETIRED.forEach { id -> if (manager.getNotificationChannel(id) != null) manager.deleteNotificationChannel(id) }
        val channels = listOf(
            NotificationChannel(BACKGROUND_SERVICE, "后台同步", NotificationManager.IMPORTANCE_MIN).apply {
                description = "短信同步在后台运行时的常驻通知。只出现在通知栏中，不在状态栏显示图标。"
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                setShowBadge(false)
            },
            callSignalingChannel(),
            NotificationChannel(INBOUND_SMS, "短信", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "收到短信时弹出提醒并显示内容；验证码短信可直接复制。"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                enableVibration(true)
                setShowBadge(true)
            },
            NotificationChannel(INCOMING_CALLS, "来电", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "远程 SIM 来电时响铃，并弹出接听和拒接按钮。"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 1000L, 1000L)
                setShowBadge(false)
            },
            NotificationChannel(CALL_ALERTS, "来电提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "有来电但暂时连不上服务器、无法接听时提醒。"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            },
            NotificationChannel(ONGOING_CALLS, "通话中", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "正在进行的远程 SIM 通话和挂断按钮。"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannels(channels)
    }

    /** True when the user has not blocked [channelId] (an absent channel counts as allowed). */
    fun channelAllowed(context: Context, channelId: String): Boolean {
        if (Build.VERSION.SDK_INT < 26) return true
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.getNotificationChannel(channelId)?.importance != NotificationManager.IMPORTANCE_NONE
    }
}
