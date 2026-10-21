package com.callagent.host.calls

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.telephony.PhoneNumberUtils
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ImageView
import android.graphics.drawable.GradientDrawable
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.window.java.layout.WindowInfoTrackerCallbackAdapter
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import com.callagent.host.R
import com.callagent.host.contacts.HostContactsRepository
import com.callagent.host.ui.DialPadKeyView
import com.callagent.host.ui.HostUi
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors

/** Controls observe the process-wide owner; folding or returning never ends a call. */
class CallActionActivity : AppCompatActivity() {
    private var callId: String? = null
    private var pendingNumber: String? = null
    private var pendingSim: String? = null
    private var receiverRegistered = false
    private var keypadVisible = false
    private var dtmfDigits = ""
    private var savedScrollY = 0
    private var contactName: String? = null
    private var queriedNumber: String? = null
    private var surface: ScrollView? = null
    private var fold: FoldingFeature? = null
    private val tracker by lazy { WindowInfoTrackerCallbackAdapter(WindowInfoTracker.getOrCreate(this)) }
    private val foldListener = Consumer<WindowLayoutInfo> { info ->
        fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull { it.isSeparating }
        surface?.let { ViewCompat.requestApplyInsets(it) }
    }
    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getStringExtra(CallRuntime.EXTRA_CHANGED_CALL_ID) == callId || callId == "pending") render()
        }
    }

    companion object {
        const val ACTION_SHOW_INCOMING = "com.callagent.host.calls.SHOW_INCOMING"
        /** The notification's Answer button: answer at once, asking for the microphone first if needed. */
        const val ACTION_ANSWER = "com.callagent.host.calls.ANSWER"
        const val ACTION_SHOW_CALL = "com.callagent.host.calls.SHOW_CALL"
        const val EXTRA_CALL_ID = "com.callagent.host.calls.CALL_ID"
        const val EXTRA_PENDING_SIM = "com.callagent.host.calls.PENDING_SIM"
        const val EXTRA_PENDING_NUMBER = "com.callagent.host.calls.PENDING_NUMBER"
        private const val REQUEST_MICROPHONE = 7201
        private const val ANSWER_GREEN = 0xFF188038.toInt()
        private const val DECLINE_RED = 0xFFD93025.toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyLockScreenFlags()
        callId = savedInstanceState?.getString("callId") ?: intent.getStringExtra(EXTRA_CALL_ID)
        keypadVisible = savedInstanceState?.getBoolean("keypad") ?: false
        dtmfDigits = savedInstanceState?.getString("digits").orEmpty()
        savedScrollY = savedInstanceState?.getInt("scrollY") ?: 0
        pendingNumber = savedInstanceState?.getString("pendingNumber") ?: intent.getStringExtra(EXTRA_PENDING_NUMBER)
        pendingSim = savedInstanceState?.getString("pendingSim") ?: intent.getStringExtra(EXTRA_PENDING_SIM)
        rememberPendingCall()
        if (savedInstanceState == null) answerIfRequested(intent)
        render()
    }

    private fun answerIfRequested(source: Intent?) {
        if (source?.action != ACTION_ANSWER) return
        // Handle once: a recreated Activity must not answer again.
        source.action = ACTION_SHOW_CALL
        val call = visibleCall() ?: return
        if (call.phase != CallPhase.INCOMING_RINGING) return
        answer(call.callId)
    }

    private fun answer(activeId: String) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) CallRuntime.answerFromVisibleUser(this, activeId)
        else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
    }

    private fun rememberPendingCall() {
        if (callId == "pending" && pendingSim == null) {
            CallRuntime.currentSession?.takeIf { it.direction == CallDirection.OUTGOING }?.let {
                pendingNumber = it.remoteNumber
                pendingSim = it.simId
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("callId", callId)
        outState.putBoolean("keypad", keypadVisible)
        outState.putString("digits", dtmfDigits)
        outState.putInt("scrollY", surface?.scrollY ?: savedScrollY)
        outState.putString("pendingNumber", pendingNumber)
        outState.putString("pendingSim", pendingSim)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val nextId = intent.getStringExtra(EXTRA_CALL_ID)
        if (nextId != callId) {
            keypadVisible = false
            dtmfDigits = ""
            pendingSim = null
            pendingNumber = null
            contactName = null
            queriedNumber = null
            savedScrollY = 0
            surface?.scrollTo(0, 0)
        }
        callId = nextId
        pendingSim = pendingSim ?: intent.getStringExtra(EXTRA_PENDING_SIM)
        pendingNumber = pendingNumber ?: intent.getStringExtra(EXTRA_PENDING_NUMBER)
        rememberPendingCall()
        answerIfRequested(intent)
        render()
    }

    override fun onStart() {
        super.onStart()
        tracker.addWindowLayoutInfoListener(this, ContextCompat.getMainExecutor(this), foldListener)
    }

    override fun onStop() {
        tracker.removeWindowLayoutInfoListener(foldListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(this, updateReceiver, IntentFilter(CallRuntime.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        render()
    }

    override fun onPause() {
        if (receiverRegistered) {
            runCatching { unregisterReceiver(updateReceiver) }
            receiverRegistered = false
        }
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MICROPHONE && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            callId?.let { CallRuntime.answerFromVisibleUser(this, it) }
        } else if (requestCode == REQUEST_MICROPHONE) {
            android.widget.Toast.makeText(this, "接听电话需要麦克风权限", android.widget.Toast.LENGTH_LONG).show()
        }
        render()
    }

    private fun visibleCall(): CallSession? {
        if (callId == "pending") {
            CallRuntime.currentSession?.takeIf {
                it.direction == CallDirection.OUTGOING && it.simId == pendingSim && it.remoteNumber == pendingNumber
            }?.let { callId = it.callId; return it }
        }
        return callId?.let(CallRuntime::snapshot)
    }

    private fun render() {
        val call = visibleCall()
        val activeId = call?.callId
        loadContactName(call?.remoteNumber)
        val previousScrollY = surface?.scrollY ?: savedScrollY
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(color(com.google.android.material.R.attr.colorSurfaceContainer))
        }
        surface = scroll
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(12), dp(20), dp(24))
        }
        scroll.addView(root, ViewGroup.LayoutParams(-1, -1))
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(iconButton("返回", R.drawable.call_ui_back) { finish() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        top.addView(label(simLabel(call?.simId), 14f).apply {
            setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(android.view.View(this), LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(top, LinearLayout.LayoutParams(-1, -2))

        // Caller block in the style of the Google Phone in-call screen.
        val number = call?.remoteNumber?.takeIf { it.isNotBlank() } ?: "电话"
        val compact = keypadVisible
        if (!compact) {
            root.addView(HostUi.avatar(this, contactName, number, 112).apply {
                layoutParams = LinearLayout.LayoutParams(dp(112), dp(112)).apply { topMargin = dp(32); bottomMargin = dp(20) }
            })
        }
        root.addView(label(contactName ?: number, if (compact) 26f else 34f).apply {
            setPadding(0, dp(if (compact) 8 else 0), 0, 0)
        })
        if (contactName != null) root.addView(label(number, 16f).apply {
            setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant))
        })
        root.addView(label(phaseLabel(call?.phase), 16f).apply {
            setTextColor(color(androidx.appcompat.R.attr.colorPrimary))
            setPadding(0, dp(8), 0, dp(if (compact) 8 else 16))
        })
        if (call != null) root.addView(label("语音：${call.mediaTransport.label} · 控制：${CallRuntime.controlLabel}", 14f).apply {
            setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant))
        })
        call?.endNotice?.takeIf { it.isNotBlank() }?.let { root.addView(label(it, 15f)) }
        if (call?.phase == CallPhase.FAILED) root.addView(label("连接失败，请检查网络后重试", 15f))
        if (call?.phase == CallPhase.ACTIVE && CallRuntime.currentStatus.let { it.contains("网络") || it.contains("恢复") }) {
            root.addView(label("网络连接不稳定，正在恢复…", 15f))
        }
        // Push the controls towards the bottom, where the thumb is.
        root.addView(android.view.View(this), LinearLayout.LayoutParams(1, 0, 1f))
        if (call?.phase == CallPhase.ACTIVE && activeId != null) {
            if (keypadVisible) {
                root.addView(label(dtmfDigits, 24f).apply {
                    contentDescription = "已发送的按键 $dtmfDigits"
                    setPadding(0, 0, 0, dp(8))
                })
                val pad = android.widget.GridLayout(this).apply {
                    columnCount = 3
                    useDefaultMargins = false
                }
                "123456789*0#".forEach { digit ->
                    pad.addView(DialPadKeyView(this, digit.toString()).apply {
                        contentDescription = "发送按键 $digit"
                        layoutParams = android.widget.GridLayout.LayoutParams().apply {
                            width = 0
                            height = ViewGroup.LayoutParams.WRAP_CONTENT
                            columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                            setMargins(dp(6), dp(4), dp(6), dp(4))
                        }
                        setOnClickListener {
                            if (CallRuntime.snapshot(activeId)?.phase == CallPhase.ACTIVE) {
                                dtmfDigits = (dtmfDigits + digit).takeLast(24)
                                CallRuntime.sendDtmf(this@CallActionActivity, activeId, digit.toString())
                                render()
                            }
                        }
                    })
                }
                root.addView(pad, LinearLayout.LayoutParams(dp(340).coerceAtMost(resources.displayMetrics.widthPixels - dp(40)), ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { bottomMargin = dp(12) })
            }
            val controls = LinearLayout(this).apply { gravity = Gravity.CENTER }
            controls.addView(roundControl(if (call.muted) "取消静音" else "静音", R.drawable.call_ui_mic, call.muted) {
                CallRuntime.setMuted(this, activeId, !call.muted)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            controls.addView(roundControl("键盘", R.drawable.call_ui_keypad, keypadVisible) { keypadVisible = !keypadVisible; render() },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            // One tap toggles speaker and earpiece; a picker for two choices
            // only added a step, and it could miss taps while the screen redrew.
            val speakerOn = CallRuntime.selectedAudioEndpoint(activeId)?.let { it == CallAudioEndpoint.SPEAKER } ?: call.speaker
            controls.addView(roundControl(if (speakerOn) "扬声器" else "听筒", R.drawable.call_ui_speaker, speakerOn) {
                CallRuntime.toggleSpeaker(this, activeId, !speakerOn)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            root.addView(controls, LinearLayout.LayoutParams(dp(360).coerceAtMost(resources.displayMetrics.widthPixels - dp(40)), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        when (call?.phase) {
            CallPhase.INCOMING_RINGING -> {
                // Decline on the left, answer on the right, like the system incoming-call screen.
                val choices = LinearLayout(this).apply { gravity = Gravity.CENTER }
                choices.addView(roundAction("拒接", R.drawable.call_ui_end, DECLINE_RED) { CallRuntime.rejectFromUi(this, activeId!!) },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                choices.addView(roundAction("接听", R.drawable.call_ui_phone, ANSWER_GREEN) { answer(activeId!!) },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                root.addView(choices, LinearLayout.LayoutParams(dp(360).coerceAtMost(resources.displayMetrics.widthPixels - dp(40)), ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(24); bottomMargin = dp(24) })
            }
            CallPhase.ACTIVE, CallPhase.AUTHORIZING_OUTBOUND, CallPhase.REGISTERING, CallPhase.DIALING,
            CallPhase.OUTBOUND_RINGING, CallPhase.ANSWERING -> root.addView(hangupButton("挂断") {
                CallRuntime.hangupFromUi(this, activeId!!)
            })
            CallPhase.DISCONNECTING -> root.addView(label("正在挂断…", 16f).apply { setPadding(0, dp(24), 0, dp(24)) })
            else -> root.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonTonalStyle).apply {
                text = "关闭"
                contentDescription = "关闭"
                cornerRadius = dp(28)
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(dp(160), dp(56)).apply { topMargin = dp(24); bottomMargin = dp(24) })
        }
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            var left = safe.left
            var right = safe.right
            var topInset = safe.top
            var bottom = safe.bottom
            fold?.let { hinge ->
                if (hinge.orientation == FoldingFeature.Orientation.VERTICAL) {
                    if (hinge.bounds.left >= view.width - hinge.bounds.right) right = maxOf(right, view.width - hinge.bounds.left)
                    else left = maxOf(left, hinge.bounds.right)
                } else {
                    if (hinge.bounds.top >= view.height - hinge.bounds.bottom) bottom = maxOf(bottom, view.height - hinge.bounds.top)
                    else topInset = maxOf(topInset, hinge.bounds.bottom)
                }
            }
            view.setPadding(left, topInset, right, bottom)
            val gutter = maxOf(dp(20), (view.width - left - right - dp(600)) / 2)
            root.setPadding(gutter, dp(12), gutter, dp(24))
            insets
        }
        scroll.addOnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) ViewCompat.requestApplyInsets(view)
        }
        ViewCompat.requestApplyInsets(scroll)
        scroll.post { scroll.scrollTo(0, previousScrollY) }
    }

    private fun simLabel(simId: String?): String = SimLabels.describe(this, simId)

    private fun loadContactName(number: String?) {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            contactName = null
            queriedNumber = null
            return
        }
        if (number.isNullOrBlank() || !number.any(Char::isDigit) || queriedNumber == number) return
        queriedNumber = number
        contactName = null
        val expectedSim = visibleCall()?.simId
        val normalized = PhoneNumberUtils.normalizeNumber(number)
        HostContactsRepository(this).queryAsync { result ->
            if (isDestroyed || isFinishing || queriedNumber != number || visibleCall()?.simId != expectedSim) return@queryAsync
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return@queryAsync
            val match = result.getOrDefault(emptyList()).firstOrNull {
                PhoneNumberUtils.normalizeNumber(it.normalizedNumber ?: it.number) == normalized
            }
            if (match != null && contactName != match.name) { contactName = match.name; render() }
        }
    }

    private fun iconButton(description: String, drawable: Int, click: () -> Unit) =
        MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            icon = ContextCompat.getDrawable(this@CallActionActivity, drawable)
            iconTint = ColorStateList.valueOf(color(com.google.android.material.R.attr.colorOnSurface))
            iconSize = dp(24)
            iconPadding = 0
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            contentDescription = description
            cornerRadius = dp(24)
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            setOnClickListener { click() }
        }

    /** A round in-call toggle with its label underneath; filled while [selected]. */
    private fun roundControl(text: String, drawable: Int, selected: Boolean = false, click: () -> Unit): LinearLayout {
        val fill = color(if (selected) androidx.appcompat.R.attr.colorPrimary else com.google.android.material.R.attr.colorSurfaceContainerHighest)
        val onFill = color(if (selected) com.google.android.material.R.attr.colorOnPrimary else com.google.android.material.R.attr.colorOnSurface)
        return roundButton(text, drawable, fill, onFill, 72, click).also { column ->
            (column.getChildAt(0) as MaterialButton).apply {
                isCheckable = true
                isChecked = selected
            }
        }
    }

    /** A large round answer/decline button with its label underneath. */
    private fun roundAction(text: String, drawable: Int, fill: Int, click: () -> Unit) =
        roundButton(text, drawable, fill, android.graphics.Color.WHITE, 80, click)

    private fun roundButton(text: String, drawable: Int, fill: Int, onFill: Int, sizeDp: Int, click: () -> Unit): LinearLayout {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(8), 0, dp(8))
        }
        column.addView(MaterialButton(this).apply {
            icon = ContextCompat.getDrawable(this@CallActionActivity, drawable)
            iconTint = ColorStateList.valueOf(onFill)
            iconSize = dp(if (sizeDp >= 80) 32 else 28)
            iconPadding = 0
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            backgroundTintList = ColorStateList.valueOf(fill)
            cornerRadius = dp(sizeDp / 2)
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            contentDescription = text
            setOnClickListener { click() }
        }, LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)))
        column.addView(label(text, 13f).apply {
            setPadding(0, dp(8), 0, 0)
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        return column
    }

    /** The red end-call pill, centered at the bottom. */
    private fun hangupButton(text: String, click: () -> Unit) = MaterialButton(this).apply {
        icon = ContextCompat.getDrawable(this@CallActionActivity, R.drawable.call_ui_end)
        iconSize = dp(32)
        iconPadding = 0
        iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        iconTint = ColorStateList.valueOf(android.graphics.Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(DECLINE_RED)
        cornerRadius = dp(36)
        insetTop = 0
        insetBottom = 0
        contentDescription = text
        layoutParams = LinearLayout.LayoutParams(dp(112), dp(72)).apply { topMargin = dp(24); bottomMargin = dp(24) }
        setOnClickListener { click() }
    }

    private fun label(value: String, size: Float) = TextView(this).apply {
        text = value
        textSize = size
        gravity = Gravity.CENTER
        setTextColor(color(com.google.android.material.R.attr.colorOnSurface))
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun phaseLabel(phase: CallPhase?): String = when (phase) {
        CallPhase.INCOMING_RINGING -> "来电"
        CallPhase.AUTHORIZING_OUTBOUND, CallPhase.REGISTERING, CallPhase.DIALING, CallPhase.OUTBOUND_RINGING -> "正在呼叫…"
        CallPhase.ANSWERING -> "正在接听…"
        CallPhase.ACTIVE -> "通话中"
        CallPhase.DISCONNECTING -> "正在挂断…"
        CallPhase.ENDED -> "通话已结束"
        CallPhase.FAILED -> "通话失败"
        else -> "通话已结束"
    }

    private fun applyLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun color(attribute: Int) = MaterialColors.getColor(this, attribute, 0)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
