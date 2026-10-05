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
import com.callagent.host.data.ClientDatabase
import com.callagent.host.data.SessionStore
import com.callagent.host.data.clientDatabaseName
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        const val ACTION_SHOW_CALL = "com.callagent.host.calls.SHOW_CALL"
        const val EXTRA_CALL_ID = "com.callagent.host.calls.CALL_ID"
        const val EXTRA_PENDING_SIM = "com.callagent.host.calls.PENDING_SIM"
        const val EXTRA_PENDING_NUMBER = "com.callagent.host.calls.PENDING_NUMBER"
        private const val REQUEST_MICROPHONE = 7201
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
        render()
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
            setBackgroundColor(color(com.google.android.material.R.attr.colorSurface))
        }
        surface = scroll
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(12), dp(20), dp(24))
        }
        scroll.addView(root, ViewGroup.LayoutParams(-1, -1))
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(control("返回", R.drawable.call_ui_back) { finish() }, LinearLayout.LayoutParams(dp(64), dp(64)))
        top.addView(label("电话", 16f), LinearLayout.LayoutParams(0, dp(56), 1f))
        root.addView(top, LinearLayout.LayoutParams(-1, -2))
        if (!keypadVisible) root.addView(ImageView(this).apply {
            setImageResource(R.drawable.call_ui_phone)
            imageTintList = ColorStateList.valueOf(color(androidx.appcompat.R.attr.colorPrimary))
            setPadding(dp(24), dp(24), dp(24), dp(24))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color(com.google.android.material.R.attr.colorPrimaryContainer))
            }
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(88), dp(88)).apply { topMargin = dp(24); bottomMargin = dp(24) })
        val number = call?.remoteNumber?.takeIf { it.isNotBlank() } ?: "电话"
        root.addView(label(contactName ?: number, if (keypadVisible) 24f else 30f))
        if (contactName != null) root.addView(label(number, 16f))
        root.addView(label(simLabel(call?.simId), 15f).apply { setPadding(0, dp(8), 0, dp(8)) })
        root.addView(label(phaseLabel(call?.phase), 18f).apply {
            setTextColor(color(androidx.appcompat.R.attr.colorPrimary))
            setPadding(0, 0, 0, dp(if (keypadVisible) 12 else 24))
        })
        call?.endNotice?.takeIf { it.isNotBlank() }?.let { root.addView(label(it, 15f)) }
        if (call?.phase == CallPhase.FAILED) root.addView(label("连接失败，请检查网络后重试", 15f))
        if (call?.phase == CallPhase.ACTIVE && CallRuntime.currentStatus.let { it.contains("网络") || it.contains("恢复") }) {
            root.addView(label("网络连接不稳定，正在恢复…", 15f))
        }
        if (call?.phase == CallPhase.ACTIVE && activeId != null) {
            val controls = LinearLayout(this).apply { gravity = Gravity.CENTER }
            controls.addView(control(if (call.muted) "取消静音" else "静音", R.drawable.call_ui_mic, call.muted) {
                CallRuntime.setMuted(this, activeId, !call.muted)
            }, LinearLayout.LayoutParams(0, dp(84), 1f))
            controls.addView(control("键盘", R.drawable.call_ui_keypad, keypadVisible) { keypadVisible = !keypadVisible; render() }, LinearLayout.LayoutParams(0, dp(84), 1f))
            controls.addView(control("音频", R.drawable.call_ui_speaker, call.speaker) {
                val endpoints = CallRuntime.audioEndpoints(activeId)
                if (endpoints.isEmpty()) CallRuntime.toggleSpeaker(this, activeId, !call.speaker)
                else MaterialAlertDialogBuilder(this).setTitle("音频输出")
                    .setItems(endpoints.map { it.displayName() }.toTypedArray()) { _, which ->
                        CallRuntime.selectAudioEndpoint(this, activeId, endpoints[which])
                    }.show()
            }, LinearLayout.LayoutParams(0, dp(84), 1f))
            root.addView(controls, LinearLayout.LayoutParams(-1, -2))
            if (keypadVisible) {
                root.addView(label(dtmfDigits, 22f).apply { contentDescription = "已发送的按键 $dtmfDigits" })
                listOf("123", "456", "789", "*0#").forEach { digits ->
                    val row = LinearLayout(this)
                    digits.forEach { digit ->
                        row.addView(MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
                            text = digit.toString()
                            textSize = 27f
                            contentDescription = "发送按键 $digit"
                            setOnClickListener {
                                if (CallRuntime.snapshot(activeId)?.phase == CallPhase.ACTIVE) {
                                    dtmfDigits = (dtmfDigits + digit).takeLast(24)
                                    CallRuntime.sendDtmf(this@CallActionActivity, activeId, digit.toString())
                                    render()
                                }
                            }
                        }, LinearLayout.LayoutParams(0, dp(64), 1f))
                    }
                    root.addView(row, LinearLayout.LayoutParams(-1, -2))
                }
            }
        }
        when (call?.phase) {
            CallPhase.INCOMING_RINGING -> {
                root.addView(action("接听", 0xFF188038.toInt(), R.drawable.call_ui_phone) {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) CallRuntime.answerFromVisibleUser(this, activeId!!)
                    else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
                })
                root.addView(action("拒接", 0xFFBA1A1A.toInt(), R.drawable.call_ui_end) { CallRuntime.rejectFromUi(this, activeId!!) })
            }
            CallPhase.ACTIVE, CallPhase.AUTHORIZING_OUTBOUND, CallPhase.REGISTERING, CallPhase.DIALING,
            CallPhase.OUTBOUND_RINGING, CallPhase.ANSWERING -> root.addView(action("挂断", 0xFFBA1A1A.toInt(), R.drawable.call_ui_end) {
                CallRuntime.hangupFromUi(this, activeId!!)
            })
            CallPhase.DISCONNECTING -> root.addView(label("正在挂断…", 16f))
            else -> root.addView(action("返回", color(androidx.appcompat.R.attr.colorPrimary), R.drawable.call_ui_back) { finish() })
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

    private fun simLabel(simId: String?): String {
        if (simId == null) return ""
        return runCatching {
            val session = SessionStore(this).read() ?: return@runCatching "远程 SIM"
            ClientDatabase(this, clientDatabaseName(session.apiBaseUrl, session.ownerId, session.deviceId)).use { db ->
                db.loadSims().firstOrNull { it.simId == simId }?.let { "SIM${it.slotIndex + 1}${it.phoneNumber?.let { number -> " · $number" }.orEmpty()}" } ?: "远程 SIM"
            }
        }.getOrDefault("远程 SIM")
    }

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

    private fun control(text: String, drawable: Int, selected: Boolean = false, click: () -> Unit) =
        MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            this.text = text
            textSize = 12f
            icon = ContextCompat.getDrawable(this@CallActionActivity, drawable)
            iconGravity = MaterialButton.ICON_GRAVITY_TOP
            iconSize = dp(26)
            contentDescription = text
            isCheckable = true
            isChecked = selected
            backgroundTintList = ColorStateList.valueOf(
                if (selected) color(com.google.android.material.R.attr.colorPrimaryContainer) else android.graphics.Color.TRANSPARENT
            )
            setOnClickListener { click() }
        }

    private fun action(text: String, background: Int, drawable: Int, click: () -> Unit) = MaterialButton(this).apply {
        this.text = text
        icon = ContextCompat.getDrawable(this@CallActionActivity, drawable)
        iconSize = dp(26)
        iconTint = ColorStateList.valueOf(android.graphics.Color.WHITE)
        setTextColor(android.graphics.Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(background)
        cornerRadius = dp(36)
        minHeight = dp(64)
        layoutParams = LinearLayout.LayoutParams(dp(168), dp(64)).apply { topMargin = dp(24) }
        contentDescription = text
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
