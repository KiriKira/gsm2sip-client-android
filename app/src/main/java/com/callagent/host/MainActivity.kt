package com.callagent.host

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.core.view.WindowCompat
import androidx.appcompat.app.AppCompatActivity
import com.callagent.host.background.HostBackgroundRuntime
import com.callagent.host.background.BackgroundStatus
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
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var sessionStore: SessionStore
    private lateinit var preferences: ClientPreferences
    private lateinit var database: ClientDatabase
    private var session: HostSession? = null
    private var gateway: GatewaySnapshot? = null
    private var sims: List<SimLine> = emptyList()
    private var selectedSimId: String? = null
    private var statusMessage: String = ""
    private var syncing = false
    private var resumed = false
    private var pairingBusy = false
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
    private val backgroundReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_BACKGROUND_SYNCED -> refreshVisibleCache()
                ACTION_BACKGROUND_STATUS_CHANGED -> refreshBackgroundStatus()
            }
        }
    }

    companion object {
        private const val ACTION_BACKGROUND_SYNCED = "com.callagent.host.BACKGROUND_SYNCED"
        private const val ACTION_BACKGROUND_STATUS_CHANGED = "com.callagent.host.background.STATUS_CHANGED"
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
        sessionStore = SessionStore(this)
        preferences = ClientPreferences(this)
        session = sessionStore.read()
        switchDatabase(session)
        setUpWindow()
        if (session == null) showPairing() else {
            showDashboard()
            syncFromServer(showProgress = false)
        }
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
    }

    override fun onPause() {
        resumed = false
        mainHandler.removeCallbacks(periodicSync)
        unregisterBackgroundReceiver()
        super.onPause()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        database.close()
        super.onDestroy()
    }

    private fun setUpWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun showPairing(message: String? = null) {
        session = null
        backgroundSwitch = null
        backgroundSummary = null
        backgroundRestartButton = null
        val content = verticalRoot()
        content.addView(title("GSM2SIP 主机"))
        content.addView(body("将这台手机与服务器配对，即可查看网关中的远程 SIM 卡并收发短信。"))
        message?.let { content.addView(messageCard(it, error = true)) }

        val serverField = inputField("服务器地址", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val serverInput = serverField.editText as TextInputEditText
        serverInput.setText(preferences.apiBaseUrl)
        serverInput.setSingleLine(true)
        content.addView(serverField)

        val codeField = inputField("一次性配对码", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val pairingCode = codeField.editText as TextInputEditText
        pairingCode.maxLines = 1
        content.addView(codeField)

        val nameField = inputField("设备名称", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME)
        val deviceName = nameField.editText as TextInputEditText
        deviceName.setText(android.os.Build.MODEL.orEmpty().take(100))
        deviceName.setSingleLine(true)
        content.addView(nameField)

        val pairButton = button("配对此手机")
        pairButton.setOnClickListener {
            val base = serverInput.text.toString().trim()
            val code = pairingCode.text.toString().trim()
            val name = deviceName.text.toString().trim().ifBlank { "Android host" }.take(100)
            if (base.isBlank() || code.isBlank()) {
                showToast("请输入 HTTPS 服务器地址和一次性配对码。")
                if (base.isBlank()) serverInput.requestFocus() else pairingCode.requestFocus()
                return@setOnClickListener
            }
            if (pairingBusy) return@setOnClickListener
            pairingBusy = true
            pairButton.isEnabled = false
            statusMessage = "Pairing…"
            executor.execute {
                try {
                    val candidate = ApiClient(base, sessionStore)
                    val paired = candidate.pair(code, name)
                    preferences.apiBaseUrl = base
                    mainHandler.post {
                        pairingBusy = false
                        switchDatabase(paired)
                        session = paired
                        selectedSimId = null
                        statusMessage = "Paired. Loading gateway state…"
                        showDashboard()
                        syncFromServer(showProgress = true)
                    }
                } catch (failure: Exception) {
                    mainHandler.post {
                        pairingBusy = false
                        if (failure is SessionChanged) {
                            showCurrentSession("Another app screen changed the paired account.")
                        } else {
                            val safe = pairingFailureText(failure)
                            statusMessage = safe
                            showPairing(safe)
                        }
                    }
                }
            }
        }
        content.addView(pairButton)
        content.addView(messageCard("通话功能尚未接入。网关音频链路和服务器通话配置仍需验证。"))
        installContent(content)
    }

    private fun showDashboard() {
        val currentSession = session ?: return showPairing()
        gateway = database.loadGateway() ?: gateway
        if (sims.isEmpty()) sims = database.loadSims()
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

        content.addView(sectionTitle("网关"))
        val currentGateway = gateway
        if (currentGateway == null) {
            content.addView(messageCard("尚无网关缓存。点击刷新以载入已配对的网关。"))
        } else {
            val connection = if (currentGateway.online) "在线" else "离线"
            val heartbeat = currentGateway.lastSeenAt?.let { " · 最近心跳 $it" } ?: " · 暂无心跳时间"
            content.addView(messageCard("${currentGateway.deviceName}: $connection$heartbeat\nSIM 映射版本 ${currentGateway.mappingRevision}"))
            val rootState = currentGateway.root?.let { if (it) "Root 可用" else "Root 不可用" } ?: "Root 状态未知"
            val sipState = currentGateway.sipRegistered?.let { if (it) "SIP 已注册" else "SIP 未注册" } ?: "SIP 状态未知"
            val power = currentGateway.batteryPercent?.let { " · 电量 $it%" }.orEmpty()
            content.addView(body("$rootState · $sipState$power"))
        }

        content.addView(sectionTitle("远程 SIM 卡"))
        if (sims.isEmpty()) {
            content.addView(messageCard("当前没有可用的 SIM 绑定。请先由网关确认 SIM 卡映射。"))
        } else {
            val selectedStillPresent = sims.any { it.simId == selectedSimId }
            if (!selectedStillPresent) selectedSimId = null
            val simChips = ChipGroup(this).apply {
                isSingleSelection = true
                isSelectionRequired = false
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
            content.addView(simChips)
            sims.sortedBy { it.slotIndex }.forEach { line ->
                val carrier = line.carrierName?.let { " · $it" }.orEmpty()
                val number = line.phoneNumber?.let { " · $it" }.orEmpty()
                val status = if (line.canSend) "已确认" else line.stateLabel()
                content.addView(body("卡槽 ${line.slotIndex + 1}: ${line.label}$carrier$number · $status · 版本 ${line.mappingRevision}"))
            }
        }

        addBackgroundCard(content)

        content.addView(sectionTitle("通话"))
        content.addView(messageCard("通话待接入。网关音频链路和服务器通话配置仍需验证。"))

        content.addView(sectionTitle("短信收件箱"))
        val searchField = inputField("搜索短信内容或号码", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        val search = searchField.editText as TextInputEditText
        search.setText(searchQuery)
        content.addView(searchField)
        smsList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(smsList)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty()
                renderMessages()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        renderMessages()

        content.addView(sectionTitle("撰写短信"))
        val selected = sims.firstOrNull { it.simId == selectedSimId }
        if (selected == null) {
            content.addView(body("请选择上方远程 SIM 卡后再撰写短信。若映射已变化，请刷新并重新选择。"))
        } else {
            val readiness = if (selected.canSend) "发送线路：${selected.label}${selected.phoneNumber?.let { " · $it" }.orEmpty()}" else "暂不能发送：${selected.label} 当前${selected.stateLabel()}。请刷新 SIM 映射。"
            content.addView(messageCard(readiness, error = !selected.canSend))
            val recipientField = inputField("收件人号码或短码", InputType.TYPE_CLASS_PHONE)
            val recipient = recipientField.editText as TextInputEditText
            recipient.setSingleLine(true)
            val bodyField = inputField("短信内容", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
            val body = bodyField.editText as TextInputEditText
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
            content.addView(recipientField)
            content.addView(bodyField)
            val send = button("发送短信")
            send.isEnabled = selected.canSend
            send.setOnClickListener { confirmSend(selected, recipient.text.toString(), body.text.toString()) }
            content.addView(send)
        }

        val pending = database.loadPendingTasks()
        if (pending.isNotEmpty()) {
            content.addView(sectionTitle("结果待确认的提交"))
            content.addView(body("这些提交已保存，但服务器结果未知。可以查询状态或继续提交同一条已保存任务。"))
            pending.forEach { record ->
                val label = if (retryAction(record.taskKey, record.serverId) == RetryAction.RETRY_SAME_KEY) "重试同一任务" else "查询状态"
                val retry = smallButton("$label · ${record.to.orEmpty()}")
                retry.setOnClickListener { retryOrCheck(record) }
                content.addView(retry)
            }
        }

        val unpair = smallButton("解除此手机配对")
        unpair.setOnClickListener { confirmUnpair() }
        content.addView(unpair)
        installContent(content)
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
                        statusMessage = "Updated. Calling is not enabled yet."
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
        val existingCursor = database.eventCursor()
        if (existingCursor == null) {
            var snapshotCursor: String? = null
            var pages = 0
            do {
                val page = api.listMessages(cursor = snapshotCursor)
                page.items.forEach(database::upsertRemoteMessage)
                snapshotCursor = page.nextCursor
                pages++
            } while (snapshotCursor != null && pages < 10)
        }

        var cursor = existingCursor
        var pageCount = 0
        do {
            val page = api.listEvents(cursor)
            page.messages.forEach(database::upsertRemoteMessage)
            if (page.resyncRequired) {
                var snapshotCursor: String? = null
                var snapshots = 0
                do {
                    val snapshot = api.listMessages(cursor = snapshotCursor)
                    snapshot.items.forEach(database::upsertRemoteMessage)
                    snapshotCursor = snapshot.nextCursor
                    snapshots++
                } while (snapshotCursor != null && snapshots < 10)
            }
            val advance = page.nextCursor ?: page.lastItemCursor
            if (advance != null) {
                database.saveEventCursor(advance)
                cursor = if (page.nextCursor != null && page.nextCursor != cursor) page.nextCursor else null
            } else {
                cursor = null
            }
            pageCount++
        } while (cursor != null && pageCount < 10)

        database.loadOutboundTasks()
            .filter { it.serverId != null && it.status !in setOf(SmsStatus.DELIVERED, SmsStatus.FAILED, SmsStatus.EXPIRED) }
            .takeLast(50)
            .forEach { task ->
                val detail = api.getMessage(task.serverId!!)
                if (detail != null) database.upsertRemoteMessage(detail)
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

    private fun installContent(content: LinearLayout) {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            contentDescription = "GSM2SIP 主机页面"
        }
        val centered = FrameLayout(this)
        centered.addView(
            content,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        )
        scroll.addView(centered, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        scroll.post {
            val available = (scroll.width - dp(32)).coerceAtLeast(dp(280))
            val width = minOf(dp(760), available)
            content.layoutParams = FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        }
        setContentView(scroll)
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
