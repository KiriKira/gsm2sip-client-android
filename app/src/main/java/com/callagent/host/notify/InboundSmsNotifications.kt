package com.callagent.host.notify

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.callagent.host.MainActivity
import com.callagent.host.R
import com.callagent.host.contacts.HostContactsRepository
import com.callagent.host.data.SmsRecord
import java.time.Instant

/**
 * Inbound SMS notifications that show the message itself, one per
 * conversation, like a messaging app. On the lock screen only "新短信" shows.
 * When a message carries a verification code, the notification offers a copy
 * button and, if enabled in Settings, the code is copied right away.
 */
object InboundSmsNotifications {
    const val ACTION_COPY_CODE = "com.callagent.host.notify.COPY_CODE"
    const val ACTION_MARK_READ = "com.callagent.host.notify.MARK_READ"
    const val EXTRA_CODE = "com.callagent.host.notify.CODE"
    const val EXTRA_TAG = "com.callagent.host.notify.TAG"
    const val EXTRA_ID = "com.callagent.host.notify.ID"
    const val EXTRA_SMS_SIM = "com.callagent.host.notify.SMS_SIM"
    const val EXTRA_SMS_PEER = "com.callagent.host.notify.SMS_PEER"

    private const val PREFERENCES = "host-sms-notifications"
    private const val KEY_AUTO_COPY = "auto_copy_codes"
    private const val MAX_MESSAGES_PER_THREAD = 8

    data class Post(val id: Int, val notification: Notification)

    fun autoCopyCodes(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_COPY, true)

    fun setAutoCopyCodes(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_COPY, enabled).apply()
    }

    /** Stable per-conversation notification ID; [summaryId] is reserved for the group summary. */
    fun threadId(simId: String?, peer: String, summaryId: Int): Int {
        val id = "${simId.orEmpty()}|$peer".hashCode() and 0x3FFFFFFF
        return if (id == summaryId) id + 1 else id
    }

    /**
     * Builds the notifications for [records]: one per conversation plus a group
     * summary at [summaryId]. Messages still showing for the same conversation
     * are kept, so a second SMS extends the conversation instead of replacing it.
     */
    fun compose(
        context: Context,
        tag: String,
        summaryId: Int,
        records: List<SmsRecord>,
        simLabel: (String?) -> String,
    ): List<Post> {
        if (records.isEmpty()) return emptyList()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val active = runCatching { manager.activeNotifications.filter { it.tag == tag } }.getOrDefault(emptyList())
        val group = "${HostNotificationChannels.GROUP_INBOUND_SMS}.$tag"
        val me = Person.Builder().setName("我").build()
        val names = HashMap<String, String?>()
        val threads = records
            .sortedBy { epochMillis(it.createdAt) }
            .groupBy { it.simId to it.from.orEmpty().trim() }
        val posts = threads.map { (key, messages) ->
            val (simId, peer) = key
            val id = threadId(simId, peer, summaryId)
            val name = names.getOrPut(peer) { HostContactsRepository.lookupName(context, peer) }
            val sender = Person.Builder()
                .setName(name ?: peer.ifBlank { "未知号码" })
                .apply { if (peer.isNotBlank()) setUri("tel:$peer") }
                .build()
            val previous = active.firstOrNull { it.id == id }?.notification
                ?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
            val style = NotificationCompat.MessagingStyle(me)
            previous?.messages?.forEach(style::addMessage)
            val seen = previous?.messages?.map { it.timestamp to it.text?.toString() }.orEmpty().toSet()
            messages.forEach { message ->
                val time = epochMillis(message.createdAt)
                if ((time to message.text) !in seen) style.addMessage(message.text, time, sender)
            }
            while (style.messages.size > MAX_MESSAGES_PER_THREAD) style.messages.removeAt(0)
            val latest = messages.last()
            val code = VerificationCode.extract(latest.text)
            val sim = simLabel(simId)
            val builder = NotificationCompat.Builder(context, HostNotificationChannels.INBOUND_SMS)
                .setSmallIcon(R.drawable.ic_stat_sms)
                .setContentTitle(sender.name)
                .setContentText(latest.text)
                .setSubText(sim.takeIf { it.isNotBlank() })
                .setStyle(style)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setWhen(epochMillis(latest.createdAt).takeIf { it > 0L } ?: System.currentTimeMillis())
                .setShowWhen(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion(context))
                .setGroup(group)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setAutoCancel(true)
                .setContentIntent(openConversation(context, simId, peer, id))
            if (code != null) {
                builder.addAction(R.drawable.ic_action_copy, "复制验证码 $code", copyIntent(context, code, tag, id))
            }
            builder.addAction(0, "标为已读", markReadIntent(context, tag, id))
            Post(id, builder.build())
        }
        val total = posts.size + active.count { it.id != summaryId && posts.none { post -> post.id == it.id } }
        val summary = NotificationCompat.Builder(context, HostNotificationChannels.INBOUND_SMS)
            .setSmallIcon(R.drawable.ic_stat_sms)
            .setContentTitle("新短信")
            .setContentText(if (total <= 1) "1 个对话" else "$total 个对话")
            .setStyle(NotificationCompat.InboxStyle().also { inbox ->
                threads.values.forEach { messages ->
                    val latest = messages.last()
                    val peer = latest.from.orEmpty().trim()
                    inbox.addLine("${names[peer] ?: peer}：${latest.text}")
                }
            })
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(context))
            .setGroup(group)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .setContentIntent(openConversation(context, null, null, summaryId))
            .build()
        return posts + Post(summaryId, summary)
    }

    /** Copies the newest code in [records] when the user enabled automatic copying. */
    fun autoCopy(context: Context, records: List<SmsRecord>) {
        if (!autoCopyCodes(context)) return
        val code = records.maxByOrNull { epochMillis(it.createdAt) }?.let { VerificationCode.extract(it.text) } ?: return
        copy(context, code)
    }

    fun copy(context: Context, code: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText("验证码", code)
        if (Build.VERSION.SDK_INT >= 24) {
            clip.description.extras = PersistableBundle().apply {
                // Android 13+ hides sensitive clips in its clipboard preview.
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        runCatching { clipboard.setPrimaryClip(clip) }.onSuccess {
            // Android 13+ confirms copies itself; older releases get a toast.
            if (Build.VERSION.SDK_INT < 33) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context.applicationContext, "已复制验证码 $code", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Removes one conversation's notification, and the group summary once no
     * conversation is left, so an empty "新短信" summary never lingers.
     */
    fun cancelConversation(context: Context, tag: String, id: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        manager.cancel(tag, id)
        runCatching {
            val remaining = manager.activeNotifications.filter { it.tag == tag && it.id != id }
            if (remaining.all { it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 }) {
                remaining.forEach { manager.cancel(tag, it.id) }
            }
        }
    }

    /** Opening the messages screen from a notification clears every SMS notification, as Google Messages does. */
    fun cancelAllInbound(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching {
            manager.activeNotifications
                .filter { Build.VERSION.SDK_INT >= 26 && it.notification.channelId == HostNotificationChannels.INBOUND_SMS }
                .forEach { manager.cancel(it.tag, it.id) }
        }
    }

    /** Removes every SMS notification posted under [tag]. */
    fun cancelAll(context: Context, tag: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching { manager.activeNotifications.filter { it.tag == tag }.forEach { manager.cancel(tag, it.id) } }
    }

    private fun publicVersion(context: Context): Notification =
        NotificationCompat.Builder(context, HostNotificationChannels.INBOUND_SMS)
            .setSmallIcon(R.drawable.ic_stat_sms)
            .setContentTitle("新短信")
            .setContentText("解锁后查看")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

    private fun openConversation(context: Context, simId: String?, peer: String?, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_SHOW_SMS)
                .apply {
                    if (!peer.isNullOrBlank()) {
                        putExtra(EXTRA_SMS_PEER, peer)
                        putExtra(EXTRA_SMS_SIM, simId)
                    }
                }
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun copyIntent(context: Context, code: String, tag: String, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, SmsNotificationActionReceiver::class.java)
                .setAction(ACTION_COPY_CODE)
                .putExtra(EXTRA_CODE, code)
                .putExtra(EXTRA_TAG, tag)
                .putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun markReadIntent(context: Context, tag: String, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id xor 0x40000000,
            Intent(context, SmsNotificationActionReceiver::class.java)
                .setAction(ACTION_MARK_READ)
                .putExtra(EXTRA_TAG, tag)
                .putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun epochMillis(value: String): Long = runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
}
