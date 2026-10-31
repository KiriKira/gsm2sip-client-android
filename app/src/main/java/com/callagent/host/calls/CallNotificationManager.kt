package com.callagent.host.calls

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person as CompatPerson
import com.callagent.host.R
import com.callagent.host.contacts.HostContactsRepository
import com.callagent.host.notify.HostNotificationChannels

/**
 * Call notifications in the style of the system dialer: a ringing heads-up
 * with Answer/Decline while a call is incoming, then a silent ongoing-call
 * notification with Hang up. Both share [NOTIFICATION_ID], so answering
 * replaces the ringing notification and stops its ringtone.
 */
internal object CallNotificationManager {
    const val NOTIFICATION_ID = 7401

    fun prepare(context: Context) {
        HostNotificationChannels.ensure(context)
    }

    fun notificationsEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.areNotificationsEnabled() &&
            HostNotificationChannels.channelAllowed(context, HostNotificationChannels.INCOMING_CALLS) &&
            (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }

    fun fullScreenAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 ||
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).canUseFullScreenIntent()

    fun incomingNotification(context: Context, call: CallSession): Notification {
        prepare(context)
        val caller = caller(context, call)
        val notificationBuilder = builder(context, call, caller, HostNotificationChannels.INCOMING_CALLS)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    person(caller),
                    declineIntent(context, call.callId),
                    answerIntent(context, call.callId)
                ).setIsVideo(false)
            )
            .setContentIntent(incomingUiIntent(context, call.callId))
        // Locked: the full-screen call screen. Unlocked: Android shows a heads-up with the two buttons.
        // Always requested: without the full-screen permission Android shows the heads-up instead,
        // while a CallStyle notification with neither a full-screen intent nor a running foreground
        // service is refused outright (IllegalArgumentException).
        notificationBuilder.setFullScreenIntent(incomingUiIntent(context, call.callId), true)
        // Ring like a phone call until it is answered, rejected or replaced.
        return notificationBuilder.build()
    }

    /**
     * Posts the ringing notification unless it is showing already: the call
     * foreground service normally posts it, and posting it again would alert
     * twice. Telecom can ask for the UI before that service has started.
     */
    fun ensureIncoming(context: Context, call: CallSession) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val showing = runCatching { manager.activeNotifications.any { it.id == NOTIFICATION_ID } }.getOrDefault(false)
        if (!showing) post(context, incomingNotification(context, call))
    }

    fun showOngoing(context: Context, call: CallSession) = post(context, ongoingNotification(context, call))

    fun ongoingNotification(context: Context, call: CallSession): Notification = ongoing(context, call)

    fun showConnecting(context: Context, call: CallSession) = post(context, connectingNotification(context, call))

    /**
     * Updates the call notification. Android can refuse a CallStyle update
     * (no foreground service yet); the call itself must go on regardless.
     */
    private fun post(context: Context, notification: Notification) {
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, notification)
        } catch (failure: RuntimeException) {
            Log.w("HostCall", "Call notification update refused: ${failure.javaClass.simpleName}")
        }
    }

    fun connectingNotification(context: Context, call: CallSession): Notification = ongoing(context, call)

    private fun ongoing(context: Context, call: CallSession): Notification {
        prepare(context)
        val caller = caller(context, call)
        val hangup = actionIntent(context, CallActionReceiver.ACTION_HANGUP, call.callId)
        return builder(context, call, caller, HostNotificationChannels.ONGOING_CALLS)
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(person(caller), hangup).setIsVideo(false))
            .setContentIntent(incomingUiIntent(context, call.callId))
            .build()
    }

    /** Shown only for the instant a foreground start that cannot be honoured is closed again. */
    fun placeholderNotification(context: Context): Notification {
        prepare(context)
        return NotificationCompat.Builder(context, HostNotificationChannels.ONGOING_CALLS)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle("远程 SIM 通话")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setSilent(true)
            .build()
    }

    fun cancel(context: Context) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
    }

    private data class Caller(val name: String?, val number: String?) {
        val title: String get() = name ?: number ?: "未知号码"
    }

    private fun caller(context: Context, call: CallSession): Caller {
        val number = call.remoteNumber?.takeIf { it.isNotBlank() }
        return Caller(HostContactsRepository.lookupName(context, number), number)
    }

    private fun builder(context: Context, call: CallSession, caller: Caller, channel: String): NotificationCompat.Builder {
        val incoming = call.phase == CallPhase.INCOMING_RINGING
        val publicVersion = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(if (call.direction == CallDirection.INCOMING && incoming) "来电" else "通话进行中")
            .setContentText("远程 SIM 通话")
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
        val detail = listOfNotNull(state, caller.name?.let { caller.number }, SimLabels.describe(context, call.simId))
            .joinToString(" · ")
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(caller.title)
            .setContentText(detail)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(if (incoming) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setOngoing(call.phase !in setOf(CallPhase.ENDED, CallPhase.FAILED))
            .setOnlyAlertOnce(!incoming)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    }

    private fun person(caller: Caller): CompatPerson = CompatPerson.Builder()
        .setName(caller.title)
        .apply { caller.number?.let { setUri("tel:$it") } }
        .setImportant(true)
        .build()

    /** Answer goes through an Activity so a missing microphone grant can still be requested. */
    private fun answerIntent(context: Context, callId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode(callId, 1),
            Intent(context, CallActionActivity::class.java)
                .setAction(CallActionActivity.ACTION_ANSWER)
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
