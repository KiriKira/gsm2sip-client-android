package com.callagent.host

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.window.java.layout.WindowInfoTrackerCallbackAdapter
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import androidx.window.layout.WindowMetricsCalculator
import com.callagent.host.background.HostBackgroundRuntime
import com.callagent.host.background.BackgroundStatus
import com.callagent.host.calls.CallAudioEndpoint
import com.callagent.host.calls.CallNotificationManager
import com.callagent.host.calls.CallPhase
import com.callagent.host.calls.CallRuntime
import com.callagent.host.calls.startCallUi
import com.callagent.host.data.ApiClient
import com.callagent.host.data.ApiFailure
import com.callagent.host.data.ClientDatabase
import com.callagent.host.data.ClientPreferences
import com.callagent.host.data.GatewaySnapshot
import com.callagent.host.data.HostSession
import com.callagent.host.data.OutboundTask
import com.callagent.host.data.RetryEnvelope
import com.callagent.host.data.RetryAction
import com.callagent.host.data.SessionNeedsPairing
import com.callagent.host.data.SessionChanged
import com.callagent.host.data.SessionStore
import com.callagent.host.data.SimLine
import com.callagent.host.data.SmsDraft
import com.callagent.host.data.SmsRecord
import com.callagent.host.data.SmsStatus
import com.callagent.host.data.toRetryEnvelope
import com.callagent.host.data.clientDatabaseName
import com.callagent.host.data.newTaskKey
import com.callagent.host.data.retryAction
import com.callagent.host.data.sameSessionInstance
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.callagent.host.ui.AdaptiveLayoutDecision
import com.callagent.host.ui.AdaptiveWindowLayoutPolicy
import com.callagent.host.ui.FoldGeometry
import com.callagent.host.ui.HorizontalPaneSelection
import com.callagent.host.ui.PairingOperationState
import com.callagent.host.ui.PairingFormViewModel
import androidx.core.util.Consumer
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var sessionStore: SessionStore
    private lateinit var preferences: ClientPreferences
    private lateinit var pairingForm: PairingFormViewModel
    private lateinit var database: ClientDatabase
    private var session: HostSession? = null
    private var gateway: GatewaySnapshot? = null
    private var sims: List<SimLine> = emptyList()
    private var selectedSimId: String? = null
    private var statusMessage: String = ""
    private var syncing = false
    private var resumed = false
    private var pairingButton: MaterialButton? = null
    private var searchQuery = ""
    private var smsList: LinearLayout? = null
    private var composeRecipient: EditText? = null
    private var composeBody: EditText? = null
    private var bindingDraft = false
    private var previousMappingRevision: Long? = null
    private var databaseName: String = ""
    private var backgroundStatus: BackgroundStatus? = null
    private var backgroundSwitch: MaterialSwitch? = null
    private var backgroundSummary: TextView? = null
    private var backgroundRestartButton: MaterialButton? = null
    private var lastRenderedBackgroundSyncAt = 0L
    private var backgroundReceiverRegistered = false
    private var callPanel: LinearLayout? = null
    private var callDestination: String = ""
    private var callDestinationEdit: EditText? = null
    private var searchEdit: EditText? = null
    private var callServerAvailable: Boolean? = null
    private var callServerReason: String? = null
    private var callAvailabilityLoading = false
    private var callAvailabilityCheckedAt = 0L
    private var pendingDialAfterMicGrant: (() -> Unit)? = null
    private var selectedRestoreSimId: String? = null
    private var savedScrollY = 0
    private var savedTopScrollY = 0
    private var savedBottomScrollY = 0
    private var restoreImeVisible = false
    private var restoreFocusedInput: String? = null
    private var safeWindowInsets = Insets.NONE
    private var imeVisible = false
    private var foldGeometry: FoldGeometry? = null
    private var windowLayoutListening = false
    private var adaptiveHost: FrameLayout? = null
    private var mainScroll: ScrollView? = null
    private var currentContent: LinearLayout? = null
    private var currentPanels: LinearLayout? = null
    private var primaryPanel: LinearLayout? = null
    private var secondaryPanel: LinearLayout? = null
    private var panelSpacer: View? = null
    private var headerViews: List<View> = emptyList()
    private var horizontalTopScroll: ScrollView? = null
    private var horizontalBottomScroll: ScrollView? = null
    private var horizontalTopContent: LinearLayout? = null
    private var horizontalBottomContent: LinearLayout? = null
    private val pairingOperationObserver = Observer<PairingOperationState> { state ->
        pairingButton?.isEnabled = !pairingForm.hasPendingOrRunningPairing
        state.outcome?.let(::handlePairingOutcome)
    }
    private val windowInfoTracker by lazy { WindowInfoTrackerCallbackAdapter(WindowInfoTracker.getOrCreate(this)) }
    private val windowLayoutConsumer = Consumer<WindowLayoutInfo> { info ->
        if (!windowLayoutListening || isDestroyed || isFinishing) return@Consumer
        val feature = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull { it.isSeparating }
        foldGeometry = feature?.let {
            FoldGeometry(
                separating = true,
                axis = if (it.orientation == FoldingFeature.Orientation.VERTICAL) FoldGeometry.Axis.VERTICAL else FoldGeometry.Axis.HORIZONTAL,
                startPx = if (it.orientation == FoldingFeature.Orientation.VERTICAL) it.bounds.left else it.bounds.top,
                endPx = if (it.orientation == FoldingFeature.Orientation.VERTICAL) it.bounds.right else it.bounds.bottom,
            )
        }
        adaptiveHost?.post {
            if (windowLayoutListening && !isDestroyed && !isFinishing) updateAdaptiveLayout()
        }
    }
    private val backgroundReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_BACKGROUND_SYNCED -> refreshVisibleCache()
                ACTION_BACKGROUND_STATUS_CHANGED -> refreshBackgroundStatus()
                CallRuntime.ACTION_STATE_CHANGED -> renderCallPanel()
            }
        }
    }

    companion object {
        private const val ACTION_BACKGROUND_SYNCED = "com.callagent.host.BACKGROUND_SYNCED"
        private const val ACTION_BACKGROUND_STATUS_CHANGED = "com.callagent.host.background.STATUS_CHANGED"
        private const val REQUEST_CALL_MICROPHONE = 7311
        private const val REQUEST_CALL_NOTIFICATIONS = 7312
        private const val STATE_SELECTED_SIM = "adaptive.selectedSim"
        private const val STATE_SEARCH_QUERY = "adaptive.searchQuery"
        private const val STATE_CALL_DESTINATION = "adaptive.callDestination"
        private const val STATE_SCROLL_Y = "adaptive.scrollY"
        private const val STATE_TOP_SCROLL_Y = "adaptive.topScrollY"
        private const val STATE_BOTTOM_SCROLL_Y = "adaptive.bottomScrollY"
        private const val STATE_IME_VISIBLE = "adaptive.imeVisible"
        private const val STATE_FOCUSED_INPUT = "adaptive.focusedInput"
        private const val STATE_PAIRING_SERVER = "pairing.server"
        private const val STATE_PAIRING_DEVICE_NAME = "pairing.deviceName"
    }

    private val periodicSync = object : Runnable {
        override fun run() {
            if (!resumed || session == null) return
            syncFromServer(showProgress = false)
            mainHandler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedRestoreSimId = savedInstanceState?.getString(STATE_SELECTED_SIM)
        searchQuery = savedInstanceState?.getString(STATE_SEARCH_QUERY).orEmpty()
        callDestination = savedInstanceState?.getString(STATE_CALL_DESTINATION).orEmpty()
        savedScrollY = savedInstanceState?.getInt(STATE_SCROLL_Y) ?: 0
        savedTopScrollY = savedInstanceState?.getInt(STATE_TOP_SCROLL_Y) ?: 0
        savedBottomScrollY = savedInstanceState?.getInt(STATE_BOTTOM_SCROLL_Y) ?: 0
        restoreImeVisible = savedInstanceState?.getBoolean(STATE_IME_VISIBLE) ?: false
        restoreFocusedInput = savedInstanceState?.getString(STATE_FOCUSED_INPUT)
        sessionStore = SessionStore(this)
        preferences = ClientPreferences(this)
        pairingForm = ViewModelProvider(this)[PairingFormViewModel::class.java]
        pairingForm.initialize(
            defaultServerUrl = preferences.apiBaseUrl,
            defaultDeviceName = Build.MODEL.orEmpty().take(100),
            restoredServerUrl = savedInstanceState?.getString(STATE_PAIRING_SERVER),
            restoredDeviceName = savedInstanceState?.getString(STATE_PAIRING_DEVICE_NAME),
        )
        session = sessionStore.read()
        CallRuntime.restoreForegroundPreference(this, session)
        switchDatabase(session)
        selectedSimId = selectedRestoreSimId
        setUpWindow()
        if (session == null) showPairing() else {
            showDashboard()
            syncFromServer(showProgress = false)
        }
    }

    override fun onStart() {
        super.onStart()
        pairingForm.pairingOperation.observe(this, pairingOperationObserver)
        if (!windowLayoutListening) {
            windowLayoutListening = true
            windowInfoTracker.addWindowLayoutInfoListener(this, ContextCompat.getMainExecutor(this), windowLayoutConsumer)
        }
    }

    override fun onStop() {
        if (windowLayoutListening) {
            windowInfoTracker.removeWindowLayoutInfoListener(windowLayoutConsumer)
            windowLayoutListening = false
        }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        saveVisibleDraft()
        selectedSimId?.let { outState.putString(STATE_SELECTED_SIM, it) }
        outState.putString(STATE_SEARCH_QUERY, searchEdit?.text?.toString() ?: searchQuery)
        outState.putString(STATE_CALL_DESTINATION, callDestinationEdit?.text?.toString() ?: callDestination)
        val topScrollY = horizontalTopScroll?.scrollY ?: savedTopScrollY
        val bottomScrollY = horizontalBottomScroll?.scrollY ?: savedBottomScrollY
        val focusedInput = focusedInputKey()
        val mergedScrollY = if (horizontalTopScroll != null) {
            when (focusedInput) {
                "composeRecipient", "composeBody", "search" -> bottomScrollY
                "callDestination" -> topScrollY
                else -> maxOf(topScrollY, bottomScrollY)
            }
        } else {
            mainScroll?.scrollY ?: savedScrollY
        }
        outState.putInt(STATE_SCROLL_Y, mergedScrollY)
        outState.putInt(STATE_TOP_SCROLL_Y, topScrollY)
        outState.putInt(STATE_BOTTOM_SCROLL_Y, bottomScrollY)
        val rootInsets = adaptiveHost?.let { ViewCompat.getRootWindowInsets(it) }
        outState.putBoolean(STATE_IME_VISIBLE, rootInsets?.isVisible(WindowInsetsCompat.Type.ime()) == true)
        outState.putString(STATE_FOCUSED_INPUT, focusedInput)
        if (session == null) {
            outState.putString(STATE_PAIRING_SERVER, pairingForm.serverUrl)
            outState.putString(STATE_PAIRING_DEVICE_NAME, pairingForm.deviceName)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        registerBackgroundReceiver()
        refreshBackgroundStatus()
        if (session != null && (backgroundStatus?.lastSyncAt ?: 0L) > lastRenderedBackgroundSyncAt) {
            refreshVisibleCache()
        }
        if (session != null) {
            mainHandler.removeCallbacks(periodicSync)
            mainHandler.post(periodicSync)
        }
        CallRuntime.setAppVisible(this, true)
        if (session != null) refreshCallAvailability()
    }

    override fun onPause() {
        resumed = false
        mainHandler.removeCallbacks(periodicSync)
        CallRuntime.setAppVisible(this, false)
        unregisterBackgroundReceiver()
        super.onPause()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        database.close()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CALL_MICROPHONE) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            if (granted) {
                pendingDialAfterMicGrant?.invoke()
            } else {
                pendingDialAfterMicGrant = null
                showToast("麦克风权限未允许，无法拨出或接听远程通话。")
            }
            pendingDialAfterMicGrant = null
            renderCallPanel()
        } else if (requestCode == REQUEST_CALL_NOTIFICATIONS) {
            renderCallPanel()
        }
    }

    private fun setUpWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun showPairing(message: String? = null) {
        pairingForm.initialize(preferences.apiBaseUrl, Build.MODEL.orEmpty().take(100))
        session = null
        selectedSimId = null
        composeRecipient = null
        composeBody = null
        callDestinationEdit = null
        searchEdit = null
        backgroundSwitch = null
        backgroundSummary = null
        backgroundRestartButton = null
        pairingButton = null
        val content = verticalRoot()
        content.addView(title("GSM2SIP 主机"))
        content.addView(body("将这台手机与服务器配对，即可查看网关中的远程 SIM 卡并收发短信。"))
        message?.let { content.addView(messageCard(it, error = true)) }

        val serverField = inputField("服务器地址", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val serverInput = serverField.editText as TextInputEditText
        serverInput.id = R.id.pairing_server
        serverInput.setText(pairingForm.serverUrl)
        serverInput.setSingleLine(true)
        serverInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                pairingForm.updateServerUrl(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        content.addView(serverField)

        val codeField = inputField("一次性配对码", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val pairingCode = codeField.editText as TextInputEditText
        pairingCode.id = R.id.pairing_code
        pairingCode.isSaveEnabled = false
        pairingCode.isSaveFromParentEnabled = false
        pairingCode.setText(this.pairingForm.pairingCode)
        pairingCode.maxLines = 1
        pairingCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                this@MainActivity.pairingForm.updatePairingCode(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        content.addView(codeField)

        val nameField = inputField("设备名称", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME)
        val deviceName = nameField.editText as TextInputEditText
        deviceName.id = R.id.pairing_device_name
        deviceName.setText(pairingForm.deviceName)
        deviceName.setSingleLine(true)
        deviceName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                pairingForm.updateDeviceName(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        content.addView(nameField)

        val pairButton = button("配对此手机")
        pairingButton = pairButton
        pairButton.isEnabled = !pairingForm.hasPendingOrRunningPairing
        pairButton.setOnClickListener {
            val base = serverInput.text.toString().trim()
            val code = pairingCode.text.toString().trim()
            val name = deviceName.text.toString().trim().ifBlank { "Android host" }.take(100)
            if (base.isBlank() || code.isBlank()) {
                showToast("请输入 HTTPS 服务器地址和一次性配对码。")
                if (base.isBlank()) serverInput.requestFocus() else pairingCode.requestFocus()
                return@setOnClickListener
            }
            if (!pairingForm.startPairing(applicationContext, base, code, name)) return@setOnClickListener
            pairButton.isEnabled = false
        }
        content.addView(pairButton)
        content.addView(messageCard("配对后可检查服务器通话配置与远程 SIM 状态。"))
        installContent(content)
    }

    private fun showDashboard() {
        val currentSession = session ?: return showPairing()
        pairingButton = null
        gateway = database.loadGateway() ?: gateway
        if (sims.isEmpty()) sims = database.loadSims()
        composeRecipient = null
        composeBody = null
        callDestinationEdit = null
        searchEdit = null
        val content = verticalRoot()

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val headingText = title("GSM2SIP 主机")
        heading.addView(headingText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val syncButton = smallButton("刷新")
        syncButton.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        syncButton.setOnClickListener { syncFromServer(showProgress = true) }
        heading.addView(syncButton)
        content.addView(heading)

        content.addView(body("账号 ${currentSession.role} · 设备 ${shortId(currentSession.deviceId)}"))
        content.addView(body("远程 SIM 卡仍由网关手机管理；此应用通过服务器连接网关。"))
        if (statusMessage.isNotBlank()) {
            val isError = statusMessage.startsWith("Offline") || statusMessage.startsWith("Session") || statusMessage.startsWith("Could not")
            content.addView(messageCard(statusMessage, error = isError))
        }

        val panelRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = fullWidthParams()
            id = R.id.dashboard_panel_row
        }
        val overviewPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            id = R.id.dashboard_overview_panel
        }
        val messagesPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            id = R.id.dashboard_messages_panel
        }
        val columnSpacer = View(this)
        panelRow.addView(overviewPanel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        panelRow.addView(columnSpacer, LinearLayout.LayoutParams(0, 0))
        panelRow.addView(messagesPanel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(panelRow)

        overviewPanel.addView(sectionTitle("网关"))
        val currentGateway = gateway
        if (currentGateway == null) {
            overviewPanel.addView(messageCard("尚无网关缓存。点击刷新以载入已配对的网关。"))
        } else {
            val connection = if (currentGateway.online) "在线" else "离线"
            val heartbeat = currentGateway.lastSeenAt?.let { " · 最近心跳 $it" } ?: " · 暂无心跳时间"
            overviewPanel.addView(messageCard("${currentGateway.deviceName}: $connection$heartbeat\nSIM 映射版本 ${currentGateway.mappingRevision}"))
            val rootState = currentGateway.root?.let { if (it) "Root 可用" else "Root 不可用" } ?: "Root 状态未知"
            val sipState = currentGateway.sipRegistered?.let { if (it) "SIP 已注册" else "SIP 未注册" } ?: "SIP 状态未知"
            val power = currentGateway.batteryPercent?.let { " · 电量 $it%" }.orEmpty()
            overviewPanel.addView(body("$rootState · $sipState$power"))
        }

        overviewPanel.addView(sectionTitle("远程 SIM 卡"))
        if (sims.isEmpty()) {
            overviewPanel.addView(messageCard("当前没有可用的 SIM 绑定。请先由网关确认 SIM 卡映射。"))
        } else {
            if (selectedSimId == null) selectedSimId = selectedRestoreSimId
            val selectedStillPresent = sims.any { it.simId == selectedSimId }
            if (!selectedStillPresent) selectedSimId = null
            selectedRestoreSimId = null
            val simChips = ChipGroup(this).apply {
                isSingleSelection = true
                isSelectionRequired = false
                id = R.id.remote_sim_selector
                chipSpacingHorizontal = dp(8)
                chipSpacingVertical = dp(8)
                layoutParams = fullWidthParams(top = 4, bottom = 8)
            }
            sims.sortedBy { it.slotIndex }.forEach { line ->
                val status = if (line.canSend) "可发送" else line.stateLabel()
                val details = listOfNotNull(line.carrierName, line.phoneNumber).joinToString(" · ")
                val chip = Chip(this).apply {
                    text = "SIM ${line.slotIndex + 1} · ${line.label}" + if (details.isNotBlank()) "\n$details · $status" else " · $status"
                    isCheckable = true
                    isSingleLine = false
                    maxLines = 2
                    isChecked = line.simId == selectedSimId
                    minHeight = dp(48)
                    contentDescription = "${line.label}, $details, $status"
                }
                simChips.addView(chip)
                chip.setOnClickListener {
                    if (selectedSimId != line.simId) {
                        saveVisibleDraft()
                        selectedSimId = line.simId
                        showDashboard()
                    }
                }
            }
            overviewPanel.addView(simChips)
            sims.sortedBy { it.slotIndex }.forEach { line ->
                val carrier = line.carrierName?.let { " · $it" }.orEmpty()
                val number = line.phoneNumber?.let { " · $it" }.orEmpty()
                val status = if (line.canSend) "已确认" else line.stateLabel()
                overviewPanel.addView(body("卡槽 ${line.slotIndex + 1}: ${line.label}$carrier$number · $status · 版本 ${line.mappingRevision}"))
            }
        }

        addBackgroundCard(overviewPanel)

        overviewPanel.addView(sectionTitle("通话"))
        callPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        overviewPanel.addView(callPanel)
        renderCallPanel()

        messagesPanel.addView(sectionTitle("短信收件箱"))
        val searchField = inputField("搜索短信内容或号码", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        val search = searchField.editText as TextInputEditText
        search.id = R.id.sms_search
        search.setText(searchQuery)
        searchEdit = search
        messagesPanel.addView(searchField)
        smsList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        messagesPanel.addView(smsList)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty()
                renderMessages()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        renderMessages()

        messagesPanel.addView(sectionTitle("撰写短信"))
        val selected = sims.firstOrNull { it.simId == selectedSimId }
        if (selected == null) {
            messagesPanel.addView(body("请选择上方远程 SIM 卡后再撰写短信。若映射已变化，请刷新并重新选择。"))
        } else {
            val readiness = if (selected.canSend) "发送线路：${selected.label}${selected.phoneNumber?.let { " · $it" }.orEmpty()}" else "暂不能发送：${selected.label} 当前${selected.stateLabel()}。请刷新 SIM 映射。"
            messagesPanel.addView(messageCard(readiness, error = !selected.canSend))
            val recipientField = inputField("收件人号码或短码", InputType.TYPE_CLASS_PHONE)
            val recipient = recipientField.editText as TextInputEditText
            recipient.id = R.id.sms_recipient
            recipient.setSingleLine(true)
            val bodyField = inputField("短信内容", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
            val body = bodyField.editText as TextInputEditText
            body.id = R.id.sms_body
            body.minLines = 3
            body.maxLines = 7
            body.gravity = Gravity.TOP or Gravity.START
            composeRecipient = recipient
            composeBody = body
            val savedDraft = database.loadDraft(selected.simId)
            bindingDraft = true
            recipient.setText(savedDraft?.recipient.orEmpty())
            body.setText(savedDraft?.text.orEmpty())
            bindingDraft = false
            val draftWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!bindingDraft) saveVisibleDraft()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            }
            recipient.addTextChangedListener(draftWatcher)
            body.addTextChangedListener(draftWatcher)
            messagesPanel.addView(recipientField)
            messagesPanel.addView(bodyField)
            val send = button("发送短信")
            send.isEnabled = selected.canSend
            send.setOnClickListener { confirmSend(selected, recipient.text.toString(), body.text.toString()) }
            messagesPanel.addView(send)
        }

        val pending = database.loadPendingTasks()
        if (pending.isNotEmpty()) {
            messagesPanel.addView(sectionTitle("结果待确认的提交"))
            messagesPanel.addView(body("这些提交已保存，但服务器结果未知。可以查询状态或继续提交同一条已保存任务。"))
            pending.forEach { record ->
                val label = if (retryAction(record.taskKey, record.serverId) == RetryAction.RETRY_SAME_KEY) "重试同一任务" else "查询状态"
                val retry = smallButton("$label · ${record.to.orEmpty()}")
                retry.setOnClickListener { retryOrCheck(record) }
                messagesPanel.addView(retry)
            }
        }

        val unpair = smallButton("解除此手机配对")
        unpair.setOnClickListener { confirmUnpair() }
        messagesPanel.addView(unpair)
        installContent(content, panelRow, overviewPanel, messagesPanel, columnSpacer)
    }

    private fun addBackgroundCard(parent: LinearLayout) {
        parent.addView(sectionTitle("后台接收"))
        val card = MaterialCardView(this).apply {
            radius = dp(24).toFloat()
            cardElevation = dp(1).toFloat()
            strokeWidth = dp(1)
            strokeColor = materialColor(com.google.android.material.R.attr.colorOutlineVariant)
            setCardBackgroundColor(materialColor(com.google.android.material.R.attr.colorSurfaceVariant))
            layoutParams = fullWidthParams(bottom = 8)
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
        }
        val optInRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
        }
        val switchLabel = MaterialTextView(this).apply {
            text = "启用后台接收"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
        }
        optInRow.addView(switchLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val toggle = MaterialSwitch(this).apply {
            contentDescription = "启用后台接收"
            isChecked = backgroundStatus?.enabled == true
            minimumWidth = dp(48)
            minimumHeight = dp(48)
        }
        toggle.setOnCheckedChangeListener { _, checked ->
            val accepted = runCatching { HostBackgroundRuntime.setEnabled(this, checked) }.getOrDefault(false)
            if (!accepted) showToast(if (checked) "后台服务无法启动，请检查通知权限。" else "无法停止后台接收。")
            refreshBackgroundStatus()
        }
        backgroundSwitch = toggle
        optInRow.addView(toggle)
        inner.addView(optInRow)
        val summary = body("")
        backgroundSummary = summary
        inner.addView(summary)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val notifications = smallButton("通知权限")
        notifications.setOnClickListener {
            val latest = runCatching { HostBackgroundRuntime.snapshot(this) }.getOrNull()
            if (latest?.notificationsEnabled == true) HostBackgroundRuntime.openNotificationSettings(this)
            else HostBackgroundRuntime.requestNotificationPermission(this)
        }
        actions.addView(notifications, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val battery = smallButton("电池设置")
        battery.setOnClickListener { HostBackgroundRuntime.openBatterySettings(this) }
        actions.addView(battery, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        inner.addView(actions)
        val restart = smallButton("重新启动后台接收")
        restart.setOnClickListener {
            val accepted = runCatching { HostBackgroundRuntime.setEnabled(this, true) }.getOrDefault(false)
            if (!accepted) showToast("无法重新启动后台服务，请检查通知权限。")
            refreshBackgroundStatus()
        }
        backgroundRestartButton = restart
        inner.addView(restart)
        val sync = smallButton("立即同步")
        sync.setOnClickListener {
            val latest = runCatching { HostBackgroundRuntime.snapshot(this) }.getOrNull()
            if (latest != null && latest.enabled && latest.running) HostBackgroundRuntime.requestSync(this)
            else syncFromServer(showProgress = true)
        }
        inner.addView(sync)
        card.addView(inner)
        parent.addView(card)
        updateBackgroundCard(backgroundStatus ?: runCatching { HostBackgroundRuntime.snapshot(this) }.getOrNull())
    }

    private fun refreshCallAvailability(force: Boolean = false) {
        if (session == null || callAvailabilityLoading) return
        if (!force && System.currentTimeMillis() - callAvailabilityCheckedAt < 60_000L) return
        callAvailabilityLoading = true
        renderCallPanel()
        val expected = session ?: return
        executor.execute {
            val result = runCatching { client().getSipConfiguration() }
            mainHandler.post {
                callAvailabilityLoading = false
                callAvailabilityCheckedAt = System.currentTimeMillis()
                if (session?.sameSessionInstance(expected) != true) return@post
                result.onSuccess {
                    callServerAvailable = it.available
                    callServerReason = it.reason
                }.onFailure { failure ->
                    callServerAvailable = false
                    callServerReason = when (failure) {
                        is SessionNeedsPairing -> "配对会话已失效，请重新配对。"
                        is SessionChanged -> "配对账户已变化，请刷新。"
                        is ApiFailure -> "服务器暂不可用（${failure.code}）。"
                        else -> "无法读取服务器 SIP 可用状态。"
                    }
                }
                renderCallPanel()
            }
        }
    }

    private fun renderCallPanel() {
        val panel = callPanel ?: return
        panel.removeAllViews()
        val call = CallRuntime.currentSession
        if (call != null && isLiveCallPhase(call.phase)) {
            panel.addView(messageCard(
                "${call.remoteNumber?.takeIf { it.isNotBlank() } ?: "远程号码"} · ${call.simId}\n${callPhaseLabel(call.phase)}${CallRuntime.currentStatus.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty()}"
            ))
            when (call.phase) {
                CallPhase.INCOMING_RINGING -> {
                    val answer = button("接听")
                    answer.setOnClickListener {
                        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            CallRuntime.answerFromVisibleUser(this, call.callId)
                        } else {
                            pendingDialAfterMicGrant = { CallRuntime.answerFromVisibleUser(this, call.callId) }
                            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CALL_MICROPHONE)
                        }
                    }
                    panel.addView(answer)
                    val reject = smallButton("拒接")
                    reject.setOnClickListener { CallRuntime.rejectFromUi(this, call.callId) }
                    panel.addView(reject)
                }
                CallPhase.ACTIVE -> {
                    val mute = smallButton(if (call.muted) "取消静音" else "静音")
                    mute.setOnClickListener { CallRuntime.setMuted(this, call.callId, !call.muted) }
                    panel.addView(mute)
                    val endpoints = CallRuntime.audioEndpoints(call.callId)
                    if (endpoints.isNotEmpty()) {
                        val selected = CallRuntime.selectedAudioEndpoint(call.callId)
                        val route = smallButton("音频：${selected?.displayName() ?: "自动"}")
                        route.setOnClickListener { chooseAudioEndpoint(call.callId, endpoints) }
                        panel.addView(route)
                    }
                    val controls = smallButton("打开通话界面 / DTMF")
                    controls.setOnClickListener { startCallUi(call.callId) }
                    panel.addView(controls)
                    val hangup = button("挂断")
                    hangup.setOnClickListener { CallRuntime.hangupFromUi(this, call.callId) }
                    panel.addView(hangup)
                }
                CallPhase.REGISTERING, CallPhase.DIALING, CallPhase.OUTBOUND_RINGING, CallPhase.ANSWERING -> {
                    val controls = smallButton("打开通话界面")
                    controls.setOnClickListener { startCallUi(call.callId) }
                    panel.addView(controls)
                    val cancel = button("取消")
                    cancel.setOnClickListener { CallRuntime.hangupFromUi(this, call.callId) }
                    panel.addView(cancel)
                }
                else -> Unit
            }
            return
        }
        if (call != null && call.phase in setOf(CallPhase.ENDED, CallPhase.FAILED)) {
            panel.addView(messageCard(
                "${if (call.phase == CallPhase.FAILED) "通话失败" else "通话已结束"}${call.failure?.let { "：$it" }.orEmpty()}"
            ))
            val dismiss = smallButton("关闭通话记录")
            dismiss.setOnClickListener { CallRuntime.clearTerminal(call.callId); renderCallPanel() }
            panel.addView(dismiss)
        }

        val line = sims.firstOrNull { it.simId == selectedSimId }
        val currentGateway = gateway
        val gatewayReady = currentGateway?.online == true
        val lineReady = line != null && line.canSend && currentGateway != null && line.mappingRevision == currentGateway.mappingRevision
        val nativeReady = localSipEngineAvailable()
        val microphoneReady = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val notificationReady = CallNotificationManager.notificationsEnabled(this)
        val serverReady = callServerAvailable == true
        val canDial = serverReady && nativeReady && gatewayReady && lineReady && microphoneReady

        val serverText = when {
            callAvailabilityLoading -> "正在检查服务器呼叫服务…"
            callServerAvailable == true -> "服务器 SIP 配置可用。"
            callServerAvailable == false -> callServerReason ?: "服务器呼叫服务不可用。"
            else -> "服务器呼叫状态尚未检查。"
        }
        panel.addView(body(serverText))
        panel.addView(body(when {
            !nativeReady -> "此安装中没有可用的 SIP 原生引擎。"
            !gatewayReady -> "远程网关未在线。"
            line == null -> "请选择上方一张远程 SIM 卡。"
            !lineReady -> "所选 SIM 映射未确认或已变化，请刷新并重选。"
            !microphoneReady -> "拨出或接听前需要允许麦克风权限。"
            else -> "拨号线路：${line.label}${line.phoneNumber?.let { " · $it" }.orEmpty()} · 映射版本 ${line.mappingRevision}"
        }))
        if (!microphoneReady) {
            val microphone = smallButton("允许麦克风")
            microphone.setOnClickListener { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CALL_MICROPHONE) }
            panel.addView(microphone)
        }
        if (!notificationReady) {
            panel.addView(body("系统通知未允许；锁屏来电通知和通话状态可能无法显示。"))
            val notifications = smallButton(if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) "允许通话通知" else "通知设置")
            notifications.setOnClickListener {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CALL_NOTIFICATIONS)
                } else HostBackgroundRuntime.openNotificationSettings(this)
            }
            panel.addView(notifications)
        }
        if (Build.VERSION.SDK_INT >= 34 && !CallNotificationManager.fullScreenAllowed(this)) {
            panel.addView(body("全屏来电权限未允许；来电仍可从高优先级通知打开。"))
            val fullScreen = smallButton("全屏来电权限设置")
            fullScreen.setOnClickListener {
                runCatching {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
                }.onFailure { HostBackgroundRuntime.openNotificationSettings(this) }
            }
            panel.addView(fullScreen)
        }

        val destinationField = inputField("远程 SIM 拨出号码", InputType.TYPE_CLASS_PHONE)
        val destinationEdit = destinationField.editText as TextInputEditText
        destinationEdit.id = R.id.call_destination
        destinationEdit.setSingleLine(true)
        destinationEdit.setText(callDestination)
        callDestinationEdit = destinationEdit
        destinationEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                callDestination = s?.toString().orEmpty()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        panel.addView(destinationField)
        val dial = button("拨出此远程 SIM")
        dial.isEnabled = canDial
        dial.setOnClickListener {
            val recipient = callDestination.trim()
            if (!validDialAddress(recipient)) {
                destinationEdit.error = "请输入有效号码"
                return@setOnClickListener
            }
            confirmRemoteDial(line!!, currentGateway!!, recipient)
        }
        panel.addView(dial)

        val listen = smallButton(if (CallRuntime.isListeningRequested) "关闭后台来电接收" else "启用后台来电接收")
        listen.isEnabled = serverReady && nativeReady
        listen.setOnClickListener {
            if (CallRuntime.isListeningRequested) {
                CallRuntime.disableForegroundListening(this)
                renderCallPanel()
            } else if (!CallRuntime.enableForegroundListening(this)) {
                showToast("无法启动 SIP 前台来电接收。")
            }
        }
        panel.addView(listen)
        panel.addView(body("开启后使用系统可见的专用信令前台服务维持 SIP 注册和 HTTPS 来电检查；不启用麦克风。实际 SIP 来电匹配服务器状态后才交给 Telecom，用户接听后才请求麦克风和通话前台服务。网络中断、强行停止或系统资源限制仍可能延迟来电。"))

        val refresh = smallButton(if (callAvailabilityLoading) "正在检查…" else "刷新通话可用状态")
        refresh.isEnabled = !callAvailabilityLoading
        refresh.setOnClickListener { refreshCallAvailability(force = true) }
        panel.addView(refresh)
    }

    private fun confirmRemoteDial(line: SimLine, currentGateway: GatewaySnapshot, recipient: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("远程 SIM 拨号")
            .setMessage("将使用 ${line.label}${line.phoneNumber?.let { "（$it）" }.orEmpty()} 拨打 $recipient。\nSIM 映射版本 ${line.mappingRevision}。")
            .setNegativeButton("取消", null)
            .setPositiveButton("拨号") { _, _ ->
                val start = {
                    val accepted = CallRuntime.startOutbound(
                        this,
                        gatewayId = currentGateway.gatewayId,
                        simId = line.simId,
                        simLabel = line.label,
                        mappingRevision = line.mappingRevision,
                        destination = recipient
                    )
                    if (!accepted) showToast("呼叫未启动。请检查麦克风权限和通话服务状态。")
                    renderCallPanel()
                }
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
                else {
                    pendingDialAfterMicGrant = start
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CALL_MICROPHONE)
                }
            }
            .show()
    }

    private fun chooseAudioEndpoint(callId: String, endpoints: List<CallAudioEndpoint>) {
        val labels = endpoints.map { it.displayName() }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("通话音频设备")
            .setItems(labels) { _, which ->
                endpoints.getOrNull(which)?.let { CallRuntime.selectAudioEndpoint(this, callId, it) }
                renderCallPanel()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun validDialAddress(value: String): Boolean =
        value.length in 2..40 && value.firstOrNull()?.let { it.isDigit() || it == '+' || it == '*' || it == '#' } == true &&
            value.all { it.isDigit() || it in "+*#()-. " }

    private fun localSipEngineAvailable(): Boolean = runCatching {
        val factory = Class.forName("com.callagent.host.sip.SipEngineFactory")
        val singleton = runCatching { factory.getField("INSTANCE").get(null) }.getOrNull()
        val method = if (singleton != null) singleton.javaClass.getMethod("isAvailable", Context::class.java)
        else factory.getMethod("isAvailable", Context::class.java)
        method.invoke(singleton, this) as? Boolean ?: false
    }.getOrDefault(false)

    private fun callPhaseLabel(phase: CallPhase): String = when (phase) {
        CallPhase.AUTHORIZING_OUTBOUND -> "正在申请呼叫授权"
        CallPhase.REGISTERING -> "正在连接 SIP"
        CallPhase.DIALING -> "正在呼叫"
        CallPhase.OUTBOUND_RINGING -> "等待远端接听"
        CallPhase.INCOMING_MATCHING -> "验证来电"
        CallPhase.INCOMING_RINGING -> "来电"
        CallPhase.ANSWERING -> "正在接听"
        CallPhase.ACTIVE -> "通话中"
        CallPhase.DISCONNECTING -> "正在挂断"
        CallPhase.ENDED -> "通话结束"
        CallPhase.FAILED -> "通话失败"
        CallPhase.IDLE -> "空闲"
    }

    private fun isLiveCallPhase(phase: CallPhase): Boolean = phase !in setOf(CallPhase.IDLE, CallPhase.ENDED, CallPhase.FAILED)


    private fun refreshBackgroundStatus() {
        val current = runCatching { HostBackgroundRuntime.snapshot(this) }.getOrNull()
        backgroundStatus = current
        updateBackgroundCard(current)
    }

    private fun updateBackgroundCard(status: BackgroundStatus?) {
        if (status == null) {
            backgroundSummary?.text = "后台服务状态暂不可用。"
            return
        }
        backgroundSwitch?.let { toggle ->
            if (toggle.isChecked != status.enabled) {
                toggle.setOnCheckedChangeListener(null)
                toggle.isChecked = status.enabled
                toggle.setOnCheckedChangeListener { _, checked ->
                    val accepted = runCatching { HostBackgroundRuntime.setEnabled(this, checked) }.getOrDefault(false)
                    if (!accepted) showToast(if (checked) "后台服务无法启动，请检查通知权限。" else "无法停止后台接收。")
                    refreshBackgroundStatus()
                }
            }
        }
        val runningText = if (status.running) "正在运行" else "未运行"
        val enabledText = if (status.enabled) "已启用" else "未启用（默认关闭）"
        val notificationText = if (status.notificationsEnabled) "通知已允许" else "通知未允许"
        val batteryText = if (status.batteryExempt) "电池优化已豁免" else "受电池优化管理"
        backgroundRestartButton?.visibility = if (status.enabled && !status.running) View.VISIBLE else View.GONE
        val lastSync = if (status.lastSyncAt > 0L) {
            java.text.DateFormat.getDateTimeInstance().format(java.util.Date(status.lastSyncAt))
        } else "尚未同步"
        val issueText = status.issue?.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty()
        backgroundSummary?.text = "$enabledText · $runningText\n${localizedConnectionLabel(status.connectionLabel)}\n$notificationText · $batteryText\n上次同步：$lastSync$issueText\n开启后，系统会尽力在后台同步任务；通知和电池设置会影响持续运行。"
    }

    private fun localizedConnectionLabel(label: String): String = when {
        label == "Stopped" -> "已停止"
        label == "Connecting" || label == "Connecting to server" -> "正在连接服务器"
        label == "Waiting for network" -> "等待网络连接"
        label == "Live updates connected" -> "实时更新已连接"
        label == "Periodic HTTPS sync" -> "通过 HTTPS 定期同步"
        label == "Waiting for service restart" -> "等待后台服务重启"
        else -> label
    }

    private fun registerBackgroundReceiver() {
        if (backgroundReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(ACTION_BACKGROUND_SYNCED)
            addAction(ACTION_BACKGROUND_STATUS_CHANGED)
            addAction(CallRuntime.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(this, backgroundReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        backgroundReceiverRegistered = true
    }

    private fun unregisterBackgroundReceiver() {
        if (!backgroundReceiverRegistered) return
        runCatching { unregisterReceiver(backgroundReceiver) }
        backgroundReceiverRegistered = false
    }

    private fun refreshVisibleCache() {
        val currentSession = session ?: return
        val latestSession = sessionStore.read()
        if (latestSession == null || !currentSession.sameSessionInstance(latestSession)) {
            showCurrentSession("The paired account changed. Showing its own saved gateway and message cache.")
            return
        }
        saveVisibleDraft()
        gateway = database.loadGateway() ?: gateway
        sims = database.loadSims()
        if (selectedSimId != null && sims.none { it.simId == selectedSimId }) selectedSimId = null
        lastRenderedBackgroundSyncAt = backgroundStatus?.lastSyncAt ?: lastRenderedBackgroundSyncAt
        showDashboard()
    }

    private fun renderMessages() {
        val list = smsList ?: return
        list.removeAllViews()
        val filter = searchQuery.trim().lowercase()
        val simId = selectedSimId
        if (simId != null) {
            val records = database.loadMessages(simId).filter { matchesSearch(it, filter) }
            if (records.isEmpty()) list.addView(body("此线路暂时没有已保存的短信。")) else {
                records.groupBy { it.peerAddress().ifBlank { "Unknown contact" } }.forEach { (peer, messages) ->
                    list.addView(sectionTitle("对话 · $peer"))
                    messages.forEach { message -> list.addView(messageCardView(message, canReply = message.simId != null)) }
                }
            }
        } else {
            list.addView(body("选择一张 SIM 卡以查看对话。"))
        }
        val unknown = database.loadMessages(null).filter { matchesSearch(it, filter) }
        if (unknown.isNotEmpty()) {
            list.addView(sectionTitle("无法识别 SIM 卡的短信"))
            list.addView(body("无法识别线路的短信会单独保留，且不能在原 SIM 卡上回复。"))
            unknown.forEach { list.addView(messageCardView(it, canReply = false)) }
        }
    }

    private fun messageCardView(message: SmsRecord, canReply: Boolean): View {
        val card = MaterialCardView(this).apply {
            radius = dp(20).toFloat()
            cardElevation = dp(1).toFloat()
            strokeWidth = dp(1)
            strokeColor = materialColor(com.google.android.material.R.attr.colorOutlineVariant)
            setCardBackgroundColor(materialColor(com.google.android.material.R.attr.colorSurfaceVariant))
            layoutParams = fullWidthParams(top = 4, bottom = 4)
        }
        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        card.addView(cardContent)
        val address = message.peerAddress().ifBlank { "Unknown sender" }
        cardContent.addView(label(if (message.direction == "inbound") "来自 $address" else "发往 $address"))
        cardContent.addView(body(message.text))
        cardContent.addView(body("${SmsStatus.display(message.status)} · ${message.createdAt}"))
        if (message.partCount != null && message.partCount > 1) {
            cardContent.addView(body("${message.parts.count { it.state == "submitted" || it.state == "delivered" }} / ${message.partCount} 段已提交"))
        }
        message.parts.filter { !it.error.isNullOrBlank() }.forEach { part ->
            cardContent.addView(body("第 ${part.index + 1} 段：${part.error}"))
        }
        if (message.direction == "inbound" && canReply && !message.from.isNullOrBlank()) {
            val reply = smallButton("在原 SIM 卡上回复")
            reply.setOnClickListener {
                val originalLine = sims.firstOrNull { it.simId == message.simId }
                if (originalLine == null) {
                    statusMessage = "原 SIM 卡映射不可用。请刷新并选择已确认的线路。"
                    showDashboard()
                } else {
                    selectedSimId = originalLine.simId
                    showDashboard()
                    composeRecipient?.setText(message.from)
                    composeRecipient?.setSelection(message.from.length)
                }
            }
            cardContent.addView(reply)
        } else if (message.direction == "inbound" && message.simId == null) {
            cardContent.addView(body("SIM 卡未知，无法在原线路上回复。"))
        }
        if (message.taskKey != null && (message.status == SmsStatus.UNKNOWN || message.status == SmsStatus.SUBMITTING)) {
            val action = if (retryAction(message.taskKey, message.serverId) == RetryAction.RETRY_SAME_KEY) "使用相同任务编号重试" else "查询服务器状态"
            val button = smallButton(action)
            button.setOnClickListener { retryOrCheck(message) }
            cardContent.addView(button)
        }
        return card
    }

    private fun confirmSend(line: SimLine, recipientValue: String, textValue: String) {
        val recipient = recipientValue.trim()
        val text = textValue
        if (!line.canSend) {
            showToast("This SIM is not confirmed for sending. Refresh and select a verified line.")
            return
        }
        if (recipient.isBlank() || text.isBlank()) {
            showToast("Enter a recipient and message.")
            return
        }
        if (text.toByteArray(Charsets.UTF_8).size > 16_384) {
            showToast("Message exceeds the 16 KiB protocol limit.")
            return
        }
        val currentGateway = gateway
        if (currentGateway == null) {
            showToast("Gateway state is not loaded; refresh before sending.")
            return
        }
        val lineNumber = line.phoneNumber?.let { " · $it" }.orEmpty()
        val offlineNotice = if (currentGateway.online) "" else "\nGateway is currently offline; the server may queue this task until its 5 minute expiry."
        MaterialAlertDialogBuilder(this)
            .setTitle("从 ${line.label}$lineNumber 发送？")
            .setMessage("收件人：$recipient\n\n${text.take(240)}$offlineNotice")
            .setNegativeButton("取消", null)
            .setPositiveButton("创建短信任务") { _, _ -> submitNewTask(line, currentGateway, recipient, text) }
            .show()
    }

    private fun submitNewTask(line: SimLine, currentGateway: GatewaySnapshot, recipient: String, text: String) {
        val task = OutboundTask(
            localId = UUID.randomUUID().toString(),
            idempotencyKey = newTaskKey(),
            gatewayId = currentGateway.gatewayId,
            simId = line.simId,
            mappingRevision = line.mappingRevision,
            to = recipient,
            text = text,
            createdAt = Instant.now().toString()
        )
        try {
            database.insertOutbound(task)
        } catch (_: Exception) {
            statusMessage = "Could not save the SMS task locally. It was not submitted."
            showDashboard()
            return
        }
        val retryEnvelope = runCatching { database.loadTask(task.localId)?.toRetryEnvelope() }.getOrNull()
        if (retryEnvelope == null) {
            database.updateTask(task.localId, SmsStatus.FAILED)
            statusMessage = "Could not restore the saved SMS request. It was not submitted."
            showDashboard()
            return
        }
        statusMessage = "Submitting SMS task…"
        showDashboard()
        executor.execute {
            try {
                val accepted = client().createMessage(retryEnvelope)
                database.updateTask(task.localId, accepted.status, accepted)
                database.clearDraft(line.simId)
                mainHandler.post {
                    if (selectedSimId == line.simId) {
                        composeRecipient?.setText("")
                        composeBody?.setText("")
                    }
                    statusMessage = "Server committed the SMS task (${accepted.partCount?.let { "$it parts" } ?: "parts pending gateway dispatch"})."
                    showDashboard()
                }
            } catch (failure: Exception) {
                val definitelyRejected = failure is SessionNeedsPairing ||
                    (failure is ApiFailure && failure.httpStatus in 400..499 && !failure.retryable)
                val state = if (definitelyRejected) SmsStatus.FAILED else SmsStatus.UNKNOWN
                database.updateTask(task.localId, state)
                mainHandler.post {
                    statusMessage = if (definitelyRejected) {
                        "Server rejected the SMS task (${(failure as? ApiFailure)?.code ?: "request_rejected"}). Refresh SIM status before creating another task."
                    } else {
                        "Submission result is unknown. Check status or continue the same saved submission on its original SIM."
                    }
                    if (failure is SessionNeedsPairing) {
                        session = null
                        switchDatabase(null)
                        showPairing("Session revoked. Pair this phone again; uncertain earlier submissions stay saved with their original SIM assignment.")
                    } else if (failure is SessionChanged) {
                        showCurrentSession("The paired account changed. Earlier uncertain submissions remain with their original account.")
                    } else {
                        showDashboard()
                    }
                }
            }
        }
    }

    private fun retryOrCheck(record: SmsRecord) {
        if (retryAction(record.taskKey, record.serverId) == RetryAction.QUERY_EXISTING_MESSAGE) {
            val serverId = record.serverId ?: return
            statusMessage = "Checking the existing SMS status…"
            showDashboard()
            executor.execute {
                try {
                    val detail = client().getMessage(serverId)
                    if (detail != null) database.upsertRemoteMessage(detail)
                    mainHandler.post {
                        statusMessage = if (detail == null) "The server did not return this message." else "Message status refreshed: ${SmsStatus.display(detail.status)}."
                        showDashboard()
                    }
                } catch (failure: Exception) {
                    mainHandler.post {
                        if (failure is SessionChanged) {
                            showCurrentSession("The paired account changed while checking this message.")
                        } else {
                            statusMessage = apiFailureText(failure)
                            showDashboard()
                        }
                    }
                }
            }
            return
        }
        if (retryAction(record.taskKey, record.serverId) != RetryAction.RETRY_SAME_KEY) {
            showToast("This item has no safe retry action.")
            return
        }
        val envelope = runCatching { retryEnvelope(record) }.getOrElse {
            showToast("Saved task data is incomplete; it cannot be retried safely.")
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("重试同一条短信任务？")
            .setMessage("将在原 SIM 卡线路上继续这条已保存的提交。如果首次请求已到达服务器，服务器会返回同一条任务，不会重复创建。")
            .setNegativeButton("取消", null)
            .setPositiveButton("重试同一任务") { _, _ -> submitExistingTask(record, envelope) }
            .show()
    }

    private fun retryEnvelope(record: SmsRecord): RetryEnvelope {
        return record.toRetryEnvelope()
    }

    private fun submitExistingTask(record: SmsRecord, envelope: RetryEnvelope) {
        database.updateTask(record.localId, SmsStatus.SUBMITTING)
        statusMessage = "Retrying the same saved task…"
        showDashboard()
        executor.execute {
            try {
                val accepted = client().createMessage(envelope)
                database.updateTask(record.localId, accepted.status, accepted)
                mainHandler.post {
                    statusMessage = "Server task confirmed: ${shortId(accepted.messageId)}."
                    showDashboard()
                }
            } catch (failure: Exception) {
                database.updateTask(record.localId, SmsStatus.UNKNOWN)
                mainHandler.post {
                    if (failure is SessionNeedsPairing) {
                        session = null
                        switchDatabase(null)
                        showPairing("Session revoked. Pair this phone again to check the saved submission.")
                    } else if (failure is SessionChanged) {
                        showCurrentSession("The saved submission is still unknown. It stayed with its original account and SIM.")
                    } else {
                        statusMessage = "Still unknown. The saved message and SIM assignment were retained; no second task was created."
                        showDashboard()
                    }
                }
            }
        }
    }

    private fun syncFromServer(showProgress: Boolean) {
        if (session == null || syncing) return
        syncing = true
        if (showProgress) {
            statusMessage = "Refreshing server snapshots…"
            showDashboard()
        }
        executor.execute {
            try {
                val api = client()
                val oldRevision = database.loadGateway()?.mappingRevision
                val remoteGateways = api.listGateways()
                val current = remoteGateways.firstOrNull()
                    ?: throw IOException("No gateway is paired with this account")
                val (_, remoteSims) = api.listSims(current.gatewayId)
                val mappingChanged = oldRevision != null && oldRevision != current.mappingRevision
                database.saveGateway(current)
                database.replaceSims(remoteSims)
                syncSms(api)
                mainHandler.post {
                    syncing = false
                    session = sessionStore.read()
                    gateway = current
                    sims = remoteSims
                    if (mappingChanged) {
                        selectedSimId = null
                        statusMessage = "The gateway SIM mapping changed. Choose a line again before sending."
                    }
                    if (statusMessage.isBlank() || statusMessage.startsWith("Refreshing")) {
                        statusMessage = "Updated. Check the calling panel for live readiness."
                    }
                    showDashboard()
                }
            } catch (failure: Exception) {
                mainHandler.post {
                    syncing = false
                    if (failure is SessionChanged) {
                        showCurrentSession("The paired account changed. Showing its own saved gateway and message cache.")
                    } else if (failure is SessionNeedsPairing || sessionStore.read() == null) {
                        session = null
                        switchDatabase(null)
                        showPairing("Session expired or revoked. Pair this phone again.")
                    } else {
                        statusMessage = "Offline or server unavailable. Showing saved gateway, SIM and SMS cache. ${apiFailureText(failure)}"
                        showDashboard()
                    }
                }
            }
        }
    }

    private fun syncSms(api: ApiClient) {
        val expectedSession = sessionStore.read() ?: throw SessionChanged()
        val syncDatabase = database
        val sessionIsCurrent = {
            val latest = sessionStore.read()
            latest != null && expectedSession.sameSessionInstance(latest) && syncDatabase === database
        }
        if (!sessionIsCurrent()) throw SessionChanged()
        val historicalBaseline = syncDatabase.isHistoricalBaseline()
        if (historicalBaseline) syncDatabase.beginHistoricalBaseline()
        val existingCursor = syncDatabase.eventCursor()
        if (existingCursor == null) {
            var snapshotCursor: String? = null
            var pages = 0
            do {
                val page = api.listMessages(cursor = snapshotCursor)
                syncDatabase.applyRemoteSnapshotPage(page.items, sessionIsCurrent,
                    forceHistoricalBaseline = historicalBaseline)
                snapshotCursor = page.nextCursor
                pages++
            } while (snapshotCursor != null && pages < 10)
        }

        var cursor = existingCursor
        var pageCount = 0
        do {
            val page = api.listEvents(cursor)
            if (page.resyncRequired) {
                var snapshotCursor: String? = null
                var snapshots = 0
                val snapshotBaseline = syncDatabase.isHistoricalBaseline()
                do {
                    val snapshot = api.listMessages(cursor = snapshotCursor)
                    syncDatabase.applyRemoteSnapshotPage(snapshot.items, sessionIsCurrent,
                        forceHistoricalBaseline = snapshotBaseline)
                    snapshotCursor = snapshot.nextCursor
                    snapshots++
                } while (snapshotCursor != null && snapshots < 10)
            }
            val advance = page.nextCursor ?: page.lastItemCursor
            val result = syncDatabase.applyRemoteEventPage(
                page.messages, cursor, advance, sessionIsCurrent
            )
            if (!sessionIsCurrent()) throw SessionChanged()
            if (!result.committed) {
                cursor = syncDatabase.eventCursor()
            } else {
                cursor = if (page.nextCursor != null && page.nextCursor != cursor) page.nextCursor else null
            }
            pageCount++
        } while (cursor != null && pageCount < 10)

        if (cursor == null && historicalBaseline && sessionIsCurrent()) syncDatabase.finishHistoricalBaseline()
        syncDatabase.loadOutboundTasks()
            .filter { it.serverId != null && it.status !in setOf(SmsStatus.DELIVERED, SmsStatus.FAILED, SmsStatus.EXPIRED) }
            .takeLast(50)
            .forEach { task ->
                val detail = api.getMessage(task.serverId!!)
                if (!sessionIsCurrent()) throw SessionChanged()
                if (detail != null) syncDatabase.upsertRemoteMessage(detail)
            }
        if (sessionIsCurrent()) {
            syncDatabase.eventCursor()?.let { committedCursor ->
                runCatching { api.acknowledgeEventCursor(committedCursor) }
            }
        }
    }

    private fun confirmUnpair() {
        MaterialAlertDialogBuilder(this)
            .setTitle("解除此手机配对？")
            .setMessage("服务器将撤销此客户端的登录凭据。服务器确认后，会清除此手机上的短信和网关缓存，并关闭后台接收。")
            .setNegativeButton("取消", null)
            .setPositiveButton("解除配对") { _, _ ->
                executor.execute {
                    try {
                        client().revoke()
                        runCatching { HostBackgroundRuntime.setEnabled(applicationContext, false) }
                        database.clearAll()
                        mainHandler.post {
                            session = null
                            switchDatabase(null)
                            pairingForm.clear()
                            gateway = null
                            sims = emptyList()
                            selectedSimId = null
                            statusMessage = ""
                            showPairing()
                        }
                    } catch (failure: Exception) {
                        mainHandler.post {
                            if (failure is SessionChanged) {
                                showCurrentSession("The paired account changed during unpairing.")
                            } else {
                                statusMessage = "Could not confirm server revocation. Credentials and cache remain so you can retry. ${apiFailureText(failure)}"
                                showDashboard()
                            }
                        }
                    }
                }
            }
            .show()
    }

    private fun saveVisibleDraft() {
        val simId = selectedSimId ?: return
        val recipient = composeRecipient?.text?.toString().orEmpty()
        val text = composeBody?.text?.toString().orEmpty()
        runCatching { database.saveDraft(SmsDraft(simId, recipient, text)) }
    }

    private fun client(): ApiClient = ApiClient(sessionStore.read()?.apiBaseUrl ?: preferences.apiBaseUrl, sessionStore)

    private fun showCurrentSession(message: String) {
        session = sessionStore.read()
        switchDatabase(session)
        statusMessage = message
        if (session == null) showPairing(message) else showDashboard()
    }

    private fun switchDatabase(ownerSession: HostSession?) {
        val nextName = if (ownerSession == null) "host-unpaired.db" else {
            clientDatabaseName(ownerSession.apiBaseUrl, ownerSession.ownerId, ownerSession.deviceId)
        }
        if (databaseName == nextName && ::database.isInitialized) return
        if (databaseName.isNotBlank() && databaseName != nextName) {
            CallRuntime.disableForegroundListening(this)
        }
        CallRuntime.restoreForegroundPreference(this, ownerSession)
        if (::database.isInitialized) database.close()
        databaseName = nextName
        database = ClientDatabase(this, nextName)
        database.markInterruptedSubmissionsUnknown()
        gateway = database.loadGateway()
        sims = database.loadSims()
        if (selectedSimId != null && sims.none { it.simId == selectedSimId }) selectedSimId = null
    }

    private fun matchesSearch(message: SmsRecord, query: String): Boolean = query.isBlank() ||
        message.text.lowercase().contains(query) || message.peerAddress().lowercase().contains(query)

    private fun pairingFailureText(failure: Exception): String = when (failure) {
        is ApiFailure -> "Pairing failed (${failure.code.ifBlank { "HTTP ${failure.httpStatus}" }}). Check the server URL and one-time code."
        is IOException -> failure.message?.takeIf { it.startsWith("Server URL") || it.startsWith("Enter") || it.startsWith("Pairing code") }
            ?: "Could not reach the server or the pairing code was rejected."
        else -> "Pairing failed. Check the HTTPS URL and pairing code."
    }

    private fun handlePairingOutcome(outcome: com.callagent.host.ui.PairingOperationOutcome) {
        pairingForm.consumeOutcome(outcome.requestId)
        val failure = outcome.failure
        if (failure == null) {
            pairingForm.clear()
            val paired = outcome.session ?: sessionStore.read()
            if (paired == null) {
                statusMessage = "Pairing completed without a saved client session. Please try pairing again."
                showPairing(statusMessage)
                return
            }
            if (session?.sameSessionInstance(paired) != true) {
                switchDatabase(paired)
                session = paired
                CallRuntime.restoreForegroundPreference(this, paired)
                selectedSimId = null
                statusMessage = "Paired. Loading gateway state…"
                showDashboard()
                if (sessionStore.read()?.sameSessionInstance(paired) == true) {
                    syncFromServer(showProgress = true)
                }
            }
            return
        }

        if (failure is SessionChanged) {
            showCurrentSession("Another app screen changed the paired account.")
            return
        }
        if (failure is ApiFailure && failure.code == "INVALID_PAIRING_CODE") {
            pairingForm.clearPairingCode()
        }
        val safe = pairingFailureText(failure)
        statusMessage = safe
        showPairing(safe)
    }

    private fun apiFailureText(failure: Exception): String = when (failure) {
        is SessionNeedsPairing -> "Session revoked; pair this phone again."
        is SessionChanged -> "The active account changed."
        is ApiFailure -> "Server returned ${failure.httpStatus}${failure.code.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()}."
        else -> "Connection failed (${failure.javaClass.simpleName})."
    }

    private fun shortId(value: String): String = value.take(8)

    private fun verticalRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(16), dp(20), dp(28))
    }

    private fun installContent(
        content: LinearLayout,
        panels: LinearLayout? = null,
        primary: LinearLayout? = null,
        secondary: LinearLayout? = null,
        spacer: View? = null,
    ) {
        val previousScrollY = mainScroll?.scrollY ?: savedScrollY
        val previousTopY = horizontalTopScroll?.scrollY ?: savedTopScrollY
        val previousBottomY = horizontalBottomScroll?.scrollY ?: savedBottomScrollY
        val host = FrameLayout(this).apply {
            id = R.id.adaptive_content_host
            setBackgroundColor(materialColor(com.google.android.material.R.attr.colorSurface))
            clipChildren = false
            clipToPadding = false
        }
        val scroll = ScrollView(this).apply {
            id = R.id.main_content_scroll
            isFillViewport = true
            clipToPadding = false
            contentDescription = "GSM2SIP 主机页面"
        }
        val centered = FrameLayout(this)
        centered.addView(
            content,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        )
        scroll.addView(centered, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        scroll.setOnScrollChangeListener { _, _, scrollY, _, _ -> savedScrollY = scrollY }
        host.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        ViewCompat.setOnApplyWindowInsetsListener(host) { view, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            imeVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime())
            safeWindowInsets = Insets.of(
                maxOf(bars.left, cutout.left, ime.left),
                maxOf(bars.top, cutout.top, ime.top),
                maxOf(bars.right, cutout.right, ime.right),
                maxOf(bars.bottom, cutout.bottom, ime.bottom),
            )
            if (view.paddingLeft != safeWindowInsets.left || view.paddingTop != safeWindowInsets.top ||
                view.paddingRight != safeWindowInsets.right || view.paddingBottom != safeWindowInsets.bottom
            ) {
                view.setPadding(safeWindowInsets.left, safeWindowInsets.top, safeWindowInsets.right, safeWindowInsets.bottom)
            }
            view.post { updateAdaptiveLayout() }
            windowInsets
        }

        adaptiveHost = host
        mainScroll = scroll
        currentContent = content
        currentPanels = panels
        primaryPanel = primary
        secondaryPanel = secondary
        panelSpacer = spacer
        headerViews = if (panels == null) emptyList() else (0 until content.childCount)
            .map { content.getChildAt(it) }
            .filter { it !== panels }
        horizontalTopScroll = null
        horizontalBottomScroll = null
        horizontalTopContent = null
        horizontalBottomContent = null
        savedScrollY = previousScrollY
        savedTopScrollY = previousTopY
        savedBottomScrollY = previousBottomY

        setContentView(host)
        ViewCompat.requestApplyInsets(host)
        host.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                updateAdaptiveLayout()
            }
        }
        host.post {
            updateAdaptiveLayout()
            host.post {
                mainScroll?.scrollTo(0, savedScrollY)
                horizontalTopScroll?.scrollTo(0, savedTopScrollY)
                horizontalBottomScroll?.scrollTo(0, savedBottomScrollY)
                restoreInputFocusIfNeeded()
            }
        }
    }

    private fun updateAdaptiveLayout() {
        if (isDestroyed || isFinishing) return
        val host = adaptiveHost ?: return
        if (host.width <= 0 || host.height <= 0) return
        val metricsBounds = runCatching {
            WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(this).bounds
        }.getOrNull()
        // FoldingFeature bounds and child coordinates are window-local. Prefer WindowMetrics when
        // its extent matches the measured host; a differing absolute window rect must not skew hinge math.
        val windowWidth = metricsBounds?.width()?.takeIf { it == host.width } ?: host.width
        val windowHeight = metricsBounds?.height()?.takeIf { it == host.height } ?: host.height
        val density = resources.displayMetrics.density
        val decision = AdaptiveWindowLayoutPolicy.calculate(
            windowWidthPx = windowWidth,
            windowHeightPx = windowHeight,
            density = density,
            safeLeftPx = safeWindowInsets.left,
            safeTopPx = safeWindowInsets.top,
            safeRightPx = safeWindowInsets.right,
            safeBottomPx = safeWindowInsets.bottom,
            fold = foldGeometry,
        )
        var singleFoldPaneWidth: Int? = null
        if (decision.mode == AdaptiveLayoutDecision.Mode.HORIZONTAL_FOLD) {
            if (currentPanels != null && !imeVisible) {
                moveDashboardIntoHorizontalFold(host, decision, windowHeight)
                return
            }
            val focusToRestore = restoreDashboardToScroll(host)
            singleFoldPaneWidth = constrainSinglePageToHorizontalPane(host, decision)
            if (currentPanels != null && focusToRestore != null) {
                restoreAdaptiveFocusAfterLayout(host, focusToRestore, imeVisible)
            }
        } else if (foldGeometry?.let { it.separating && it.axis == FoldGeometry.Axis.VERTICAL } == true && currentPanels == null) {
            restoreDashboardToScroll(host)
            singleFoldPaneWidth = constrainSinglePageToVerticalPane(host)
        } else {
            restoreDashboardToScroll(host)
            mainScroll?.let {
                if (it.visibility != View.VISIBLE) it.visibility = View.VISIBLE
                setLayoutParamsIfChanged(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        }

        val availableWidthPx = singleFoldPaneWidth
            ?: (host.width - safeWindowInsets.left - safeWindowInsets.right).coerceAtLeast(0)
        val maxContentWidthPx = dp(decision.contentMaxWidthDp)
        val verticalFoldDashboard = currentPanels != null && foldGeometry?.let {
            it.separating && it.axis == FoldGeometry.Axis.VERTICAL
        } == true
        val contentWidth = if (verticalFoldDashboard) availableWidthPx else minOf(availableWidthPx, maxContentWidthPx)
        currentContent?.let {
            setLayoutParamsIfChanged(
                it,
                FrameLayout.LayoutParams(
                    contentWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                ),
            )
        }
        val panels = currentPanels
        val primary = primaryPanel
        val secondary = secondaryPanel
        if (panels != null && primary != null && secondary != null) {
            val twoColumns = decision.mode == AdaptiveLayoutDecision.Mode.TWO_COLUMNS
            val targetOrientation = if (twoColumns) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            if (panels.orientation != targetOrientation) panels.orientation = targetOrientation
            setLayoutParamsIfChanged(
                panels,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            if (twoColumns) {
                val primaryWeight = decision.leadingWeight.coerceAtLeast(1f)
                val secondaryWeight = decision.trailingWeight.coerceAtLeast(1f)
                setLayoutParamsIfChanged(primary, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, primaryWeight))
                setLayoutParamsIfChanged(secondary, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, secondaryWeight))
                panelSpacer?.let { spacer ->
                    if (spacer.visibility != View.VISIBLE) spacer.visibility = View.VISIBLE
                    setLayoutParamsIfChanged(spacer, LinearLayout.LayoutParams(dp(decision.hingeGapDp), 1))
                }
            } else {
                setLayoutParamsIfChanged(primary, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                setLayoutParamsIfChanged(secondary, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                panelSpacer?.let { spacer ->
                    if (spacer.visibility != View.GONE) spacer.visibility = View.GONE
                    setLayoutParamsIfChanged(spacer, LinearLayout.LayoutParams(0, 0))
                }
            }
        }
    }

    private fun moveDashboardIntoHorizontalFold(host: FrameLayout, decision: AdaptiveLayoutDecision, windowHeight: Int) {
        val panels = currentPanels ?: return
        val primary = primaryPanel ?: return
        val secondary = secondaryPanel ?: return
        var focusToRestore: String? = null
        if (horizontalTopScroll == null || horizontalBottomScroll == null) {
            focusToRestore = focusedInputKey()
            val mergedScrollY = mainScroll?.scrollY ?: savedScrollY
            when (focusToRestore) {
                "composeRecipient", "composeBody", "search" -> savedBottomScrollY = mergedScrollY
                "callDestination" -> savedTopScrollY = mergedScrollY
            }
            mainScroll?.let { host.removeView(it) }
            val topContent = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(16), dp(20), dp(16))
            }
            val bottomContent = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(16), dp(20), dp(28))
            }
            headerViews.forEach { header ->
                (header.parent as? ViewGroup)?.removeView(header)
                topContent.addView(header)
            }
            panels.removeView(primary)
            panelSpacer?.let(panels::removeView)
            panels.removeView(secondary)
            topContent.addView(primary, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            bottomContent.addView(secondary, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val topScroll = ScrollView(this).apply {
                id = R.id.horizontal_fold_top_scroll
                isFillViewport = false
                clipToPadding = false
                setOnScrollChangeListener { _, _, scrollY, _, _ -> savedTopScrollY = scrollY }
            }
            val bottomScroll = ScrollView(this).apply {
                id = R.id.horizontal_fold_bottom_scroll
                isFillViewport = false
                clipToPadding = false
                setOnScrollChangeListener { _, _, scrollY, _, _ -> savedBottomScrollY = scrollY }
            }
            topScroll.addView(topContent, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            bottomScroll.addView(bottomContent, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            horizontalTopContent = topContent
            horizontalBottomContent = bottomContent
            horizontalTopScroll = topScroll
            horizontalBottomScroll = bottomScroll
        }

        val topScroll = horizontalTopScroll ?: return
        val bottomScroll = horizontalBottomScroll ?: return
        if (mainScroll?.visibility != View.VISIBLE) mainScroll?.visibility = View.VISIBLE
        if (topScroll.parent == null) host.addView(topScroll)
        if (bottomScroll.parent == null) host.addView(bottomScroll)

        val fold = foldGeometry?.takeIf { it.separating && it.axis == FoldGeometry.Axis.HORIZONTAL }
        val safePanes = fold?.let { selectLargestSafeHorizontalPane(windowHeight, decision, it) }
        val availableHeight = (host.height - safeWindowInsets.top - safeWindowInsets.bottom).coerceAtLeast(0)
        val topHeight = minOf(availableHeight, safePanes?.topHeightPx ?: 0)
        val bottomHeight = minOf(availableHeight, safePanes?.bottomHeightPx ?: 0)
        topScroll.visibility = if (topHeight > 0) View.VISIBLE else View.GONE
        bottomScroll.visibility = if (bottomHeight > 0) View.VISIBLE else View.GONE
        setLayoutParamsIfChanged(topScroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, topHeight, Gravity.TOP))
        setLayoutParamsIfChanged(bottomScroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, bottomHeight, Gravity.BOTTOM))
        host.post {
            topScroll.scrollTo(0, savedTopScrollY)
            bottomScroll.scrollTo(0, savedBottomScrollY)
            focusToRestore?.let { restoreAdaptiveFocusAfterLayout(host, it, imeVisible) }
        }
    }

    private fun constrainSinglePageToHorizontalPane(
        host: FrameLayout,
        decision: AdaptiveLayoutDecision,
    ): Int {
        val fold = foldGeometry ?: return (host.width - safeWindowInsets.left - safeWindowInsets.right).coerceAtLeast(0)
        val selection = selectLargestSafeHorizontalPane(host.height, decision, fold)
        val availableHeight = (host.height - safeWindowInsets.top - safeWindowInsets.bottom).coerceAtLeast(0)
        val scroll = mainScroll ?: return (host.width - safeWindowInsets.left - safeWindowInsets.right).coerceAtLeast(0)
        val onTop = selection.selectedPane == HorizontalPaneSelection.Pane.TOP
        val paneHeight = minOf(availableHeight, selection.selectedHeightPx)
        val paneVisibility = if (paneHeight > 0) View.VISIBLE else View.GONE
        if (scroll.visibility != paneVisibility) {
            scroll.visibility = paneVisibility
        }
        setLayoutParamsIfChanged(
            scroll,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                paneHeight,
                if (onTop) Gravity.TOP else Gravity.BOTTOM,
            ),
        )
        return (host.width - safeWindowInsets.left - safeWindowInsets.right).coerceAtLeast(0)
    }

    private fun constrainSinglePageToVerticalPane(host: FrameLayout): Int {
        val fold = foldGeometry ?: return (host.width - safeWindowInsets.left - safeWindowInsets.right).coerceAtLeast(0)
        val leftWidth = (fold.startPx - safeWindowInsets.left).coerceAtLeast(0)
        val rightWidth = (host.width - safeWindowInsets.right - fold.endPx).coerceAtLeast(0)
        val useLeft = leftWidth >= rightWidth
        val paneWidth = if (useLeft) leftWidth else rightWidth
        val scroll = mainScroll ?: return paneWidth
        setLayoutParamsIfChanged(
            scroll,
            FrameLayout.LayoutParams(
                paneWidth.coerceAtLeast(dp(1)),
                ViewGroup.LayoutParams.MATCH_PARENT,
                if (useLeft) Gravity.LEFT else Gravity.RIGHT,
            ),
        )
        return paneWidth
    }

    private fun restoreDashboardToScroll(host: FrameLayout): String? {
        if (horizontalTopContent == null || horizontalBottomContent == null) return null
        val content = currentContent ?: return null
        val panels = currentPanels ?: return null
        val primary = primaryPanel ?: return null
        val secondary = secondaryPanel ?: return null
        val focusKey = focusedInputKey()
        savedTopScrollY = horizontalTopScroll?.scrollY ?: savedTopScrollY
        savedBottomScrollY = horizontalBottomScroll?.scrollY ?: savedBottomScrollY
        savedScrollY = when (focusKey) {
            "composeRecipient", "composeBody", "search" -> savedBottomScrollY
            "callDestination" -> savedTopScrollY
            else -> maxOf(savedTopScrollY, savedBottomScrollY)
        }
        headerViews.forEach { header ->
            (header.parent as? ViewGroup)?.removeView(header)
            content.addView(header, content.indexOfChild(panels).coerceAtLeast(0))
        }
        (primary.parent as? ViewGroup)?.removeView(primary)
        (secondary.parent as? ViewGroup)?.removeView(secondary)
        val spacer = panelSpacer
        panels.removeAllViews()
        panels.addView(primary, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (spacer != null) panels.addView(spacer, LinearLayout.LayoutParams(0, 0))
        panels.addView(secondary, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        horizontalTopScroll?.let(host::removeView)
        horizontalBottomScroll?.let(host::removeView)
        mainScroll?.let { if (it.parent == null) host.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)) }
        horizontalTopScroll = null
        horizontalBottomScroll = null
        horizontalTopContent = null
        horizontalBottomContent = null
        mainScroll?.let { scroll ->
            scroll.visibility = View.VISIBLE
            scroll.post { scroll.scrollTo(0, savedScrollY) }
        }
        return focusKey
    }

    private fun selectLargestSafeHorizontalPane(
        windowHeight: Int,
        decision: AdaptiveLayoutDecision,
        fold: FoldGeometry,
    ): HorizontalPaneSelection {
        val physicalGap = (fold.endPx - fold.startPx).coerceAtLeast(0)
        val totalClearance = (dp(decision.hingeGapDp) - physicalGap).coerceAtLeast(dp(16))
        val topClearance = totalClearance / 2
        return AdaptiveWindowLayoutPolicy.largestSafeHorizontalPane(
            windowHeightPx = windowHeight,
            safeTopPx = safeWindowInsets.top,
            safeBottomPx = safeWindowInsets.bottom,
            fold = fold,
            topClearancePx = topClearance,
            bottomClearancePx = totalClearance - topClearance,
        )
    }

    private fun restoreAdaptiveFocusAfterLayout(host: View, key: String, showIme: Boolean) {
        host.post {
            val target = when (key) {
                "composeRecipient" -> composeRecipient
                "composeBody" -> composeBody
                "callDestination" -> callDestinationEdit
                "search" -> searchEdit
                else -> null
            } ?: return@post
            target.requestFocus()
            if (showIme) target.post { WindowCompat.getInsetsController(window, target).show(WindowInsetsCompat.Type.ime()) }
        }
    }

    private fun focusedInputKey(): String? {
        val focused = currentFocus ?: return null
        return when {
            composeRecipient != null && focused === composeRecipient -> "composeRecipient"
            composeBody != null && focused === composeBody -> "composeBody"
            callDestinationEdit != null && focused === callDestinationEdit -> "callDestination"
            searchEdit != null && focused === searchEdit -> "search"
            else -> null
        }
    }

    private fun setLayoutParamsIfChanged(view: View, next: ViewGroup.LayoutParams) {
        val current = view.layoutParams
        if (current == null || !sameLayoutParams(current, next)) view.layoutParams = next
    }

    private fun sameLayoutParams(first: ViewGroup.LayoutParams, second: ViewGroup.LayoutParams): Boolean {
        if (first.javaClass != second.javaClass || first.width != second.width || first.height != second.height) return false
        if (first is ViewGroup.MarginLayoutParams && second is ViewGroup.MarginLayoutParams) {
            if (first.leftMargin != second.leftMargin || first.topMargin != second.topMargin ||
                first.rightMargin != second.rightMargin || first.bottomMargin != second.bottomMargin ||
                first.getMarginStart() != second.getMarginStart() || first.getMarginEnd() != second.getMarginEnd()
            ) return false
        }
        return when {
            first is FrameLayout.LayoutParams && second is FrameLayout.LayoutParams -> first.gravity == second.gravity
            first is LinearLayout.LayoutParams && second is LinearLayout.LayoutParams ->
                first.weight == second.weight && first.gravity == second.gravity
            else -> true
        }
    }

    private fun restoreInputFocusIfNeeded() {
        val key = restoreFocusedInput ?: return
        val target = when (key) {
            "composeRecipient" -> composeRecipient
            "composeBody" -> composeBody
            "callDestination" -> callDestinationEdit
            "search" -> searchEdit
            else -> null
        }
        if (target == null) {
            restoreFocusedInput = null
            return
        }
        restoreFocusedInput = null
        target.requestFocus()
        if (restoreImeVisible) {
            restoreImeVisible = false
            target.post { WindowCompat.getInsetsController(window, target).show(WindowInsetsCompat.Type.ime()) }
        }
    }

    private fun title(text: String): TextView = MaterialTextView(this).apply {
        this.text = text
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium)
        setTextColor(materialColor(com.google.android.material.R.attr.colorOnSurface))
        setPadding(0, dp(8), dp(8), dp(10))
    }

    private fun sectionTitle(text: String): TextView = MaterialTextView(this).apply {
        this.text = text
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
        setTextColor(materialColor(com.google.android.material.R.attr.colorOnSurface))
        setPadding(0, dp(22), 0, dp(8))
    }

    private fun label(text: String): TextView = MaterialTextView(this).apply {
        this.text = text
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
        setTextColor(materialColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        setPadding(0, dp(8), 0, dp(4))
    }

    private fun body(text: String): TextView = MaterialTextView(this).apply {
        this.text = text
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        setTextColor(materialColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        setPadding(0, dp(4), 0, dp(6))
        setTextIsSelectable(true)
    }

    private fun messageCard(text: String, error: Boolean = false): View = MaterialCardView(this).apply {
        radius = dp(20).toFloat()
        cardElevation = dp(1).toFloat()
        strokeWidth = dp(1)
        strokeColor = materialColor(com.google.android.material.R.attr.colorOutlineVariant)
        setCardBackgroundColor(
            materialColor(
                if (error) com.google.android.material.R.attr.colorErrorContainer
                else com.google.android.material.R.attr.colorSurfaceVariant
            )
        )
        layoutParams = fullWidthParams(top = 4, bottom = 8)
        addView(MaterialTextView(this@MainActivity).apply {
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(
                materialColor(
                    if (error) com.google.android.material.R.attr.colorOnErrorContainer
                    else com.google.android.material.R.attr.colorOnSurfaceVariant
                )
            )
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setTextIsSelectable(true)
        })
    }

    private fun inputField(hint: String, inputTypeValue: Int): TextInputLayout = TextInputLayout(this).apply {
        this.hint = hint
        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        setBoxCornerRadii(dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat())
        layoutParams = fullWidthParams(top = 4, bottom = 8)
        val input = TextInputEditText(this@MainActivity).apply {
            inputType = inputTypeValue
            textSize = 16f
            minHeight = dp(56)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun button(text: String): MaterialButton = MaterialButton(this).apply {
        this.text = text
        isAllCaps = false
        minHeight = dp(48)
        cornerRadius = dp(24)
        layoutParams = fullWidthParams(top = 6, bottom = 6)
    }

    private fun smallButton(text: String): MaterialButton = button(text).apply {
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
        backgroundTintList = android.content.res.ColorStateList.valueOf(materialColor(com.google.android.material.R.attr.colorSecondaryContainer))
        setTextColor(materialColor(com.google.android.material.R.attr.colorOnSecondaryContainer))
    }

    private fun fullWidthParams(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }

    private fun materialColor(attribute: Int): Int =
        com.google.android.material.color.MaterialColors.getColor(this, attribute, "AppTheme")

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showToast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
