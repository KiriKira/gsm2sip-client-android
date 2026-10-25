package com.callagent.host.notify

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Types a just-received verification code into the code field the user is
 * looking at, in the manner of SmsCode-style SMS helpers. Runs only while the
 * user has enabled it in Android's accessibility settings.
 *
 * A code is offered for [CODE_LIFETIME_MS] after it arrives. It is filled
 * once, into a focused, empty, editable field that looks like a code field;
 * any other field is left alone and the code stays on the clipboard. Nothing
 * the service sees is stored or logged.
 */
class OtpAutoFillService : AccessibilityService() {
    override fun onServiceConnected() {
        connected = this
        tryFill()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (pending == null) return
        when (event?.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> tryFill()
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (connected === this) connected = null
        super.onDestroy()
    }

    private fun tryFill() {
        val code = currentCode() ?: return
        val focused = runCatching { rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull() ?: return
        if (focused.packageName?.toString() == packageName) return
        val field = Field(
            editable = focused.isEditable,
            empty = focused.text.isNullOrEmpty() || focused.text?.toString() == focused.hintText?.toString(),
            inputType = focused.inputType,
            maxLength = focused.maxTextLength,
            labels = listOfNotNull(focused.hintText, focused.contentDescription, focused.viewIdResourceName,
                focused.labeledBy?.text).joinToString(" ")
        )
        if (!looksLikeCodeField(field)) return
        val filled = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, code)
        })
        if (filled) synchronized(Companion) { if (pending?.code == code) pending = null }
    }

    internal data class Field(val editable: Boolean, val empty: Boolean, val inputType: Int, val maxLength: Int, val labels: String)

    companion object {
        internal const val CODE_LIFETIME_MS = 2 * 60 * 1000L
        private data class Pending(val code: String, val receivedAt: Long)

        @Volatile private var connected: OtpAutoFillService? = null
        @Volatile private var pending: Pending? = null

        private val codeLabel = Regex(
            "验证码|校验码|动态码|短信码|确认码|code|otp|pin|passcode|verif|one.?time|sms",
            RegexOption.IGNORE_CASE
        )
        /** Codes that are not one-time codes: postal, promotion and invitation codes. */
        private val otherCodeLabel = Regex("zip|postal|post.?code|promo|coupon|invit|referr|邮编|邮政|优惠|邀请|推荐", RegexOption.IGNORE_CASE)

        /** Offers [code] to the next code field; fills the focused one now if the service runs. */
        fun offer(code: String) {
            synchronized(this) { pending = Pending(code, SystemClock.elapsedRealtime()) }
            connected?.let { service -> android.os.Handler(android.os.Looper.getMainLooper()).post { service.tryFill() } }
        }

        private fun currentCode(): String? = synchronized(this) {
            pending?.takeIf { SystemClock.elapsedRealtime() - it.receivedAt < CODE_LIFETIME_MS }?.code
                .also { if (it == null) pending = null }
        }

        /** An empty editable field labelled as a code, or a short numeric field. */
        internal fun looksLikeCodeField(field: Field): Boolean {
            if (!field.editable || !field.empty || otherCodeLabel.containsMatchIn(field.labels)) return false
            if (codeLabel.containsMatchIn(field.labels)) return true
            val numeric = field.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_NUMBER
            return numeric && field.maxLength in 4..8
        }

        /** Whether the user enabled the service in Android's accessibility settings. */
        fun enabled(context: Context): Boolean {
            val component = ComponentName(context, OtpAutoFillService::class.java).flattenToString()
            val services = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            return services?.split(':')?.any { it.equals(component, ignoreCase = true) } == true
        }
    }
}
