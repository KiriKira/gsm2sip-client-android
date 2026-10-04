package com.callagent.host.calls

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person as CompatPerson

internal object CallNotificationManager {
    const val CHANNEL_ID = "remote_calls"
    const val NOTIFICATION_ID = 7401

    fun prepare(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "远程通话", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "远程 SIM 呼入、通话状态和接听操作"
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
            )
        }
    }

    fun notificationsEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelAllowed = Build.VERSION.SDK_INT < 26 || manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
        return manager.areNotificationsEnabled() && channelAllowed &&
            (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }

    fun fullScreenAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 ||
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).canUseFullScreenIntent()

    fun showIncoming(context: Context, call: CallSession) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, incomingNotification(context, call))
    }

    fun incomingNotification(context: Context, call: CallSession): Notification {
        prepare(context)
        val notificationBuilder = builder(context, call)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    person(call),
                    declineIntent(context, call.callId),
                    answerIntent(context, call.callId)
                ).setIsVideo(false)
            )
            .setContentIntent(incomingUiIntent(context, call.callId))
        if (fullScreenAllowed(context)) {
            notificationBuilder.setFullScreenIntent(incomingUiIntent(context, call.callId), true)
        }
        return notificationBuilder.build()
    }

    fun showOngoing(context: Context, call: CallSession) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, ongoingNotification(context, call))
    }

    fun ongoingNotification(context: Context, call: CallSession): Notification {
        prepare(context)
        val hangup = actionIntent(context, CallActionReceiver.ACTION_HANGUP, call.callId)
        return builder(context, call)
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(person(call), hangup).setIsVideo(false))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "挂断", hangup)
            .setContentIntent(incomingUiIntent(context, call.callId))
            .build()
    }

    fun showConnecting(context: Context, call: CallSession) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, connectingNotification(context, call))
    }

    fun connectingNotification(context: Context, call: CallSession): Notification {
        prepare(context)
        val hangup = actionIntent(context, CallActionReceiver.ACTION_HANGUP, call.callId)
        return builder(context, call)
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(person(call), hangup).setIsVideo(false))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消", hangup)
            .setContentIntent(incomingUiIntent(context, call.callId))
            .build()
    }

    fun cancel(context: Context) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
    }

    private fun builder(context: Context, call: CallSession): NotificationCompat.Builder {
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("远程 SIM 通话")
            .setContentText(if (call.direction == CallDirection.INCOMING) "来电" else "通话进行中")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        val state = when (call.phase) {
            CallPhase.INCOMING_RINGING -> "来电"
            CallPhase.ACTIVE -> "通话中"
            CallPhase.REGISTERING, CallPhase.DIALING, CallPhase.OUTBOUND_RINGING -> "正在呼叫"
            CallPhase.ANSWERING -> "正在接听"
            CallPhase.DISCONNECTING -> "正在结束通话"
            else -> "通话"
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(call.remoteNumber?.takeIf { it.isNotBlank() } ?: "远程 SIM")
            .setContentText("$state · ${call.simId}")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setOngoing(call.phase !in setOf(CallPhase.ENDED, CallPhase.FAILED))
            .setOnlyAlertOnce(call.phase != CallPhase.INCOMING_RINGING)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    }

    private fun person(call: CallSession): CompatPerson = CompatPerson.Builder()
        .setName(call.remoteNumber?.takeIf { it.isNotBlank() } ?: "未知号码")
        .setImportant(true)
        .build()

    private fun answerIntent(context: Context, callId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode(callId, 1),
            Intent(context, CallActionActivity::class.java)
                .setAction(CallActionActivity.ACTION_SHOW_INCOMING)
                .putExtra(CallActionActivity.EXTRA_CALL_ID, callId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun incomingUiIntent(context: Context, callId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode(callId, 2),
            Intent(context, CallActionActivity::class.java)
                .setAction(CallActionActivity.ACTION_SHOW_CALL)
                .putExtra(CallActionActivity.EXTRA_CALL_ID, callId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun declineIntent(context: Context, callId: String): PendingIntent =
        actionIntent(context, CallActionReceiver.ACTION_REJECT, callId)

    private fun actionIntent(context: Context, action: String, callId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(callId, action.hashCode()),
            Intent(context, CallActionReceiver::class.java).setAction(action).putExtra(CallActionActivity.EXTRA_CALL_ID, callId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun requestCode(callId: String, suffix: Int): Int = 7000 + 31 * callId.hashCode() + suffix
}
