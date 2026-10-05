package com.callagent.host.calls

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout

/** Visible call controls. The incoming screen never answers automatically when launched full-screen. */
class CallActionActivity : AppCompatActivity() {
    private var callId: String? = null
    private var receiverRegistered = false
    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getStringExtra(CallRuntime.EXTRA_CHANGED_CALL_ID) == callId) render()
        }
    }

    companion object {
        const val ACTION_SHOW_INCOMING = "com.callagent.host.calls.SHOW_INCOMING"
        const val ACTION_SHOW_CALL = "com.callagent.host.calls.SHOW_CALL"
        const val EXTRA_CALL_ID = "com.callagent.host.calls.CALL_ID"
        private const val REQUEST_MICROPHONE = 7201
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLockScreenFlags()
        callId = intent.getStringExtra(EXTRA_CALL_ID)
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        callId = intent.getStringExtra(EXTRA_CALL_ID)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                updateReceiver,
                IntentFilter(CallRuntime.ACTION_STATE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
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
            showToast("需要麦克风权限才能接听远程通话。")
        }
        render()
    }

    private fun render() {
        val activeId = callId
        val call = activeId?.let(CallRuntime::snapshot)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
        }
        val headline = TextView(this).apply {
            textSize = 26f
            text = when (call?.phase) {
                CallPhase.INCOMING_RINGING -> "远程 SIM 来电"
                CallPhase.ANSWERING -> "正在接听…"
                CallPhase.ACTIVE -> "通话中"
                CallPhase.REGISTERING, CallPhase.DIALING, CallPhase.OUTBOUND_RINGING -> "正在呼叫…"
                CallPhase.DISCONNECTING -> "正在结束通话…"
                CallPhase.FAILED -> "通话失败"
                CallPhase.ENDED -> "通话已结束"
                else -> "远程通话"
            }
        }
        root.addView(headline)
        root.addView(TextView(this).apply {
            textSize = 20f
            text = call?.remoteNumber?.takeIf { it.isNotBlank() } ?: "远程号码"
            setPadding(0, dp(16), 0, dp(8))
        })
        root.addView(TextView(this).apply {
            textSize = 15f
            text = call?.let { "${it.simId} · ${callPhaseText(it.phase)}" } ?: "通话状态不可用"
        })
        if (call?.failure != null) {
            root.addView(TextView(this).apply { text = call.failure; setPadding(0, dp(12), 0, 0) })
        }

        when (call?.phase) {
            CallPhase.INCOMING_RINGING -> {
                addButton(root, "接听") {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        CallRuntime.answerFromVisibleUser(this, activeId!!)
                    } else {
                        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
                    }
                }
                addButton(root, "拒接") { CallRuntime.rejectFromUi(this, activeId!!) }
            }
            CallPhase.ACTIVE -> {
                val mute = addButton(root, if (call.muted) "取消静音" else "静音") {
                    CallRuntime.setMuted(this, activeId!!, !call.muted)
                }
                val routeLabel = if (call.speaker) "使用听筒/耳机" else "扬声器"
                addButton(root, routeLabel) { CallRuntime.toggleSpeaker(this, activeId!!, !call.speaker) }
                val digits = TextInputLayout(this).apply {
                    hint = "DTMF 数字"
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(12)
                    }
                    addView(EditText(this@CallActionActivity).apply {
                        inputType = android.text.InputType.TYPE_CLASS_PHONE
                        maxLines = 1
                    })
                }
                root.addView(digits)
                addButton(root, "发送 DTMF") {
                    val text = (digits.editText?.text?.toString()).orEmpty()
                    if (text.isNotBlank()) CallRuntime.sendDtmf(this, activeId!!, text)
                }
                addButton(root, "挂断") { CallRuntime.hangupFromUi(this, activeId!!) }
            }
            CallPhase.REGISTERING, CallPhase.DIALING, CallPhase.OUTBOUND_RINGING, CallPhase.ANSWERING ->
                addButton(root, "取消") { CallRuntime.hangupFromUi(this, activeId!!) }
            CallPhase.DISCONNECTING, CallPhase.ENDED, CallPhase.FAILED ->
                addButton(root, "返回") { finish() }
            else -> addButton(root, "关闭") { finish() }
        }
        setContentView(root)
    }

    private fun applyLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit): MaterialButton =
        MaterialButton(this).apply {
            text = label
            minHeight = dp(52)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            }
            setOnClickListener { action() }
            parent.addView(this)
        }

    private fun callPhaseText(phase: CallPhase): String = when (phase) {
        CallPhase.INCOMING_RINGING -> "来电"
        CallPhase.ACTIVE -> "通话中"
        CallPhase.FAILED -> "失败"
        CallPhase.ENDED -> "已结束"
        else -> "连接中"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showToast(text: String) = android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_LONG).show()
}
