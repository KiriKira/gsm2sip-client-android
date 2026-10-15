package com.callagent.host.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the "复制验证码" and "标为已读" buttons on inbound SMS notifications. */
class SmsNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tag = intent.getStringExtra(InboundSmsNotifications.EXTRA_TAG)
        val id = intent.getIntExtra(InboundSmsNotifications.EXTRA_ID, 0)
        when (intent.action) {
            InboundSmsNotifications.ACTION_COPY_CODE -> {
                intent.getStringExtra(InboundSmsNotifications.EXTRA_CODE)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { InboundSmsNotifications.copy(context, it) }
            }
            InboundSmsNotifications.ACTION_MARK_READ -> {
                if (tag != null) InboundSmsNotifications.cancelConversation(context, tag, id)
            }
        }
    }
}
