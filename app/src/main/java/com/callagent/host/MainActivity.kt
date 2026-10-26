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
import android.os.SystemClock
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
import android.widget.GridLayout
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.telephony.TelephonyManager
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
import com.callagent.host.calls.CallPreferences
import com.callagent.host.calls.CallEngineFactory
import com.callagent.host.calls.CallRuntime
import com.callagent.host.calls.CallActionActivity
import com.callagent.host.calls.startCallUi
import com.callagent.host.data.ApiClient
import com.callagent.host.data.ApiFailure
import com.callagent.host.data.ClientDatabase
import com.callagent.host.data.ClientPreferences
import com.callagent.host.data.GatewaySnapshot
import com.callagent.host.data.HostSession
import com.callagent.host.data.PairedHost
import com.callagent.host.data.RemoteCall
import com.callagent.host.data.pairingStateLabel
import com.callagent.host.data.OutboundTask
import com.callagent.host.data.RetryEnvelope
import com.callagent.host.data.RetryAction
import com.callagent.host.data.SessionNeedsPairing
import com.callagent.host.data.SessionChanged
import com.callagent.host.data.SessionStore
import com.callagent.host.data.SimLine
import com.callagent.host.data.SmsDraft
import com.callagent.host.data.SmsRecord
import com.callagent.host.data.CallHistoryCache
import com.callagent.host.data.SmsSubmissionClients
import com.callagent.host.data.SmsStatus
import com.callagent.host.data.toRetryEnvelope
import com.callagent.host.data.databaseName
import com.callagent.host.data.sameProfile
import com.callagent.host.data.newTaskKey
import com.callagent.host.data.retryAction
import com.callagent.host.data.sameSessionInstance
import com.callagent.host.data.parsePairedHosts
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.callagent.host.ui.AdaptiveLayoutDecision
import com.callagent.host.ui.AdaptiveWindowLayoutPolicy
import com.callagent.host.ui.FoldGeometry
import com.callagent.host.ui.ServerLink
import com.callagent.host.ui.HorizontalPaneSelection
import com.callagent.host.ui.PairingOperationState
import com.callagent.host.ui.PairingFormViewModel
import com.callagent.host.ui.HostDestination
import com.callagent.host.ui.HostNavigationState
import com.callagent.host.ui.HostTab
import com.callagent.host.ui.DialerSimChoice
import com.callagent.host.ui.PhoneRowView
import com.callagent.host.ui.SmsPeerKey
import com.callagent.host.ui.SmsThread
import com.callagent.host.ui.SmsThreadIndex
import com.callagent.host.ui.GatewayStatusPresenter
import com.callagent.host.ui.CallReadiness
import com.callagent.host.ui.GatewayStatusView
import com.callagent.host.ui.DialPadKeyView
import com.callagent.host.ui.HostUi
import com.callagent.host.ui.NodeListView
import com.callagent.host.ui.NodeRowPresenter
import com.callagent.host.notify.InboundSmsNotifications
import com.callagent.host.mesh.HostNodes
import com.callagent.host.mesh.JoinEntry
import com.callagent.host.mesh.JoinLink
import com.callagent.host.mesh.NodeMonitor
import com.callagent.host.contacts.ContactEntry
import com.callagent.host.contacts.HostContactsRepository
import androidx.core.util.Consumer
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private sealed class PairedHostsRefresh {
        data class Loaded(val items: List<PairedHost>) : PairedHostsRefresh()
        object Unsupported : PairedHostsRefresh()
        object TemporarilyUnavailable : PairedHostsRefresh()
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    /** Node probes may take seconds; they never delay user actions on [executor]. */
    private val nodeExecutor = Executors.newSingleThreadExecutor()
    private lateinit var sessionStore: SessionStore
    private lateinit var preferences: ClientPreferences
    private lateinit var pairingForm: PairingFormViewModel
    private lateinit var database: ClientDatabase
    private var session: HostSession? = null
    private var gateway: GatewaySnapshot? = null
    /** Result of this screen's latest server sync; a stale gateway snapshot is only "offline" when Connected. */
    private var serverLink: ServerLink = ServerLink.Checking
    private var pairedHosts: List<PairedHost> = emptyList()
    private var pairedHostsAvailable: Boolean? = null
    private var pairedHostsRefreshIssue: String? = null
    private var sims: List<SimLine> = emptyList()
    private var selectedSimId: String? = null
    /** Latest user-facing outcome; each new non-blank value is shown once as a snackbar. */
    private var statusMessage: String = ""
        set(value) {
            val changed = field != value
            field = value
            if (changed && value.isNotBlank()) announceStatus(value)
        }
    private var syncing = false
    private var resumed = false
    private var pairingButton: MaterialButton? = null
    private var searchQuery = ""
    private var callSearchQuery = ""
    private var callSearchEdit: EditText? = null
    private var navigation = HostNavigationState()
    private var draftSimId: String? = null
    private var composerTransfer: Pair<String, String>? = null
    private var contactsQueryGeneration = 0L
    private var phoneContactsGeneration = 0L
    private var smsThreadVisibleCount = 100
    private var smsConversationVisibleCount = 100
    private var smsConversationList: LinearLayout? = null
    private var smsThreadList: LinearLayout? = null
    private var smsThreadCache: List<SmsThread>? = null
    private var callHistoryList: LinearLayout? = null
    private var dialerContactsList: LinearLayout? = null
    private var phoneContactsList: LinearLayout? = null
    private var callHistory: List<RemoteCall> = emptyList()
    private var callHistoryCursor: String? = null
    private var callHistoryLoading = false
    private var contactsQuery = ""
    private var dialerContacts: List<ContactEntry> = emptyList()
    private var dialerCallButton: MaterialButton? = null
    private var dialerReadinessText: TextView? = null
    private var smsThreadSendButton: MaterialButton? = null
    private val contactsRepository by lazy { HostContactsRepository(applicationContext) }
    private var smsList: LinearLayout? = null
    private var composeRecipient: EditText? = null
    private var composeBody: EditText? = null
    private var bindingDraft = false
    private var previousMappingRevision: Long? = null
    private var databaseName: String = ""
    private var databaseScope: String = ""
    /** Join-link nodes to add once the first entry's pairing succeeds. */
    private var pendingJoinNodes: List<JoinEntry> = emptyList()
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
    private var callAvailabilityGeneration = 0L
    private var pendingDialAfterMicGrant: (() -> Unit)? = null
    private var selectedRestoreSimId: String? = null
    private var savedScrollY = 0
    private var savedTopScrollY = 0
    private var savedBottomScrollY = 0
    private var restoreImeVisible = false
    private var restoreFocusedInput: String? = null
    private var restoreFocusedSelection = -1
    private var safeWindowInsets = Insets.NONE
    /** Whether [safeWindowInsets] came from the window yet; until then the first frame would draw unpadded. */
    private var windowInsetsKnown = false
    private var imeVisible = false
    private var foldGeometry: FoldGeometry? = null
    private var windowLayoutListening = false
    private var adaptiveHost: FrameLayout? = null
    private var mainScroll: ScrollView? = null
    private var fixedBottomChrome: View? = null
    private var fixedBottomChromeIsComposer = false
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
                CallRuntime.ACTION_STATE_CHANGED -> renderCurrentScreen()
                NodeMonitor.ACTION_NODE_CHANGED -> if (sessionStore.read()?.sameSessionInstance(session ?: return) != true) {
                    showCurrentSession("")
                } else if (navigation.tab == HostTab.SETTINGS) {
                    renderCurrentScreen()
                }
            }
        }
    }

    companion object {
        private const val REFRESHING_STATUS = "正在刷新服务器状态…"
        private const val OFFLINE_STATUS_PREFIX = "无法连接服务器"
        private const val ACTION_BACKGROUND_SYNCED = "com.callagent.host.BACKGROUND_SYNCED"
        private const val ACTION_BACKGROUND_STATUS_CHANGED = "com.callagent.host.background.STATUS_CHANGED"
        private const val REQUEST_CALL_MICROPHONE = 7311
        private const val REQUEST_CALL_NOTIFICATIONS = 7312
        private const val REQUEST_CONTACTS = 7313
        private const val STATE_SELECTED_SIM = "adaptive.selectedSim"
        private const val STATE_SEARCH_QUERY = "adaptive.searchQuery"
        private const val STATE_CALL_DESTINATION = "adaptive.callDestination"
        private const val STATE_SCROLL_Y = "adaptive.scrollY"
        private const val STATE_TOP_SCROLL_Y = "adaptive.topScrollY"
        private const val STATE_BOTTOM_SCROLL_Y = "adaptive.bottomScrollY"
        private const val STATE_IME_VISIBLE = "adaptive.imeVisible"
        private const val STATE_FOCUSED_INPUT = "adaptive.focusedInput"
        private const val STATE_FOCUSED_SELECTION = "adaptive.focusedSelection"
        private const val STATE_PAIRING_SERVER = "pairing.server"
        private const val STATE_PAIRING_DEVICE_NAME = "pairing.deviceName"
        private const val SYNC_KEY_PAIRED_HOSTS = "paired_hosts_snapshot"
        private const val SYNC_KEY_CALL_HISTORY = "call_history_snapshot_v1"
        private const val PAIRED_HOSTS_UNSUPPORTED = "unsupported"
        const val ACTION_SHOW_SMS = "com.callagent.host.SHOW_SMS"
        private const val STATE_NAV_TAB = "host.navigation.tab"
        private const val STATE_NAV_DESTINATION = "host.navigation.destination"
        private const val STATE_NAV_SIM = "host.navigation.sim"
        private const val STATE_NAV_PEER = "host.navigation.peer"
        private const val STATE_NAV_NEW = "host.navigation.new"
        private const val STATE_CALL_SEARCH = "host.navigation.callSearch"
    }

    private val periodicSync = object : Runnable {
        override fun run() {
            if (!resumed || session == null) return
            probeNodes(force = false)
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
        restoreFocusedSelection = savedInstanceState?.getInt(STATE_FOCUSED_SELECTION, -1) ?: -1
        callSearchQuery = savedInstanceState?.getString(STATE_CALL_SEARCH).orEmpty()
        navigation = restoreNavigation(savedInstanceState)
        sessionStore = SessionStore(this)
        preferences = ClientPreferences(this)
        pairingForm = ViewModelProvider(this)[PairingFormViewModel::class.java]
        pairingForm.initialize(
            defaultServerUrl = preferences.apiBaseUrl,
            defaultDeviceName = Build.MODEL.orEmpty().take(100),
            restoredServerUrl = savedInstanceState?.getString(STATE_PAIRING_SERVER),
            restoredDeviceName = savedInstanceState?.getString(STATE_PAIRING_DEVICE_NAME),
        )
        session = sessionStore.read() ?: runCatching { HostNodes.recoverActive(this) }.getOrNull()
        CallRuntime.restoreForegroundPreference(this, session)
        switchDatabase(session)
        selectedSimId = selectedRestoreSimId
        setUpWindow()
        if (savedInstanceState == null && intent?.action == ACTION_SHOW_SMS) {
            navigation = smsNotificationNavigation(intent)
        }
        if (savedInstanceState == null) dialIntentNumber(intent)?.let { number ->
            callDestination = number
            navigation = navigation.openDialer()
        }
        showDashboard()
        if (session != null) syncFromServer(showProgress = false)
        if (savedInstanceState == null) handleJoinLink(intent)
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
        saveNavigation(outState)
        outState.putString(STATE_CALL_SEARCH, callSearchEdit?.text?.toString() ?: callSearchQuery)
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
        outState.putInt(STATE_FOCUSED_SELECTION, (currentFocus as? EditText)?.selectionStart ?: restoreFocusedSelection)
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) ensurePairedRuntimeStarted()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_SHOW_SMS) {
            navigate(smsNotificationNavigation(intent))
        }
        dialIntentNumber(intent)?.let { number ->
            callDestination = number
            navigate(navigation.openDialer())
            callDestinationEdit?.setText(number)
        }
        handleJoinLink(intent)
    }

    /**
     * A phone number another app asked to dial (a tapped tel: link offers
     * Madoka next to the system phone app). It only fills the dial pad: the
     * user still picks the SIM and presses call. Null when [source] is not a
     * dial request; empty when it names no number.
     */
    private fun dialIntentNumber(source: Intent?): String? {
        val action = source?.action ?: return null
        if (action != Intent.ACTION_DIAL && action != Intent.ACTION_VIEW) return null
        val data = source.data
        if (data == null) return if (action == Intent.ACTION_DIAL) "" else null
        if (!data.scheme.equals("tel", ignoreCase = true)) return null
        return normalizeDialNumber(data.schemeSpecificPart.orEmpty())
    }

    /** An SMS notification opens its conversation; the group summary opens the message list. */
    private fun smsNotificationNavigation(source: Intent): HostNavigationState {
        runCatching { InboundSmsNotifications.cancelAllInbound(this) }
        val peer = source.getStringExtra(InboundSmsNotifications.EXTRA_SMS_PEER)?.takeIf { it.isNotBlank() }
            ?: return navigation.selectTab(HostTab.MESSAGES)
        val simId = source.getStringExtra(InboundSmsNotifications.EXTRA_SMS_SIM)
        selectedSimId = simId
        return navigation.openConversation(simId, peer)
    }

    /**
     * A mahogsm://join link lists join codes for several server nodes; both
     * phones scan the same one. Unpaired, the first entry becomes the pairing and the rest are
     * added as nodes; paired, every entry is added as a node.
     */
    private fun handleJoinLink(source: Intent?) {
        val link = source?.dataString
        if (!JoinLink.isJoinLink(link)) return
        // The link carries one-time secrets: never keep it around for a recreate.
        source?.data = null
        val entries = JoinLink.parse(link!!)
        if (entries == null) {
            showToast("这个加入链接无效或已过时，请在服务器上重新生成。")
            return
        }
        val paired = session != null
        val hosts = entries.joinToString("\n") { "• ${HostNodes.hostLabel(it.serverUrl)}" }
        MaterialAlertDialogBuilder(this)
            .setTitle(if (paired) "添加 ${entries.size} 个服务器节点？" else "加入 ${entries.size} 个服务器？")
            .setMessage(if (paired) hosts else "先与第一个服务器配对，其余作为备用节点：\n$hosts")
            .setNegativeButton("取消", null)
            .setPositiveButton("加入") { _, _ ->
                if (session != null) {
                    addJoinNodes(entries)
                } else {
                    val first = entries.first()
                    pendingJoinNodes = entries.drop(1)
                    val deviceName = Build.MODEL.orEmpty().ifBlank { "Android host" }.take(100)
                    navigation = navigation.selectTab(HostTab.SETTINGS)
                    if (!pairingForm.startPairing(applicationContext, first.serverUrl, first.pairingCode, deviceName)) {
                        pendingJoinNodes = emptyList()
                        showToast("已有配对正在进行，请稍后再试。")
                    }
                    renderCurrentScreen()
                }
            }
            .show()
    }

    private fun addJoinNodes(entries: List<JoinEntry>) {
        if (entries.isEmpty()) return
        val app = applicationContext
        val deviceName = Build.MODEL.orEmpty().ifBlank { "Android host" }.take(100)
        showToast("正在加入 ${entries.size} 个节点…")
        nodeExecutor.execute {
            val lines = entries.map { entry ->
                val label = HostNodes.hostLabel(entry.serverUrl)
                runCatching {
                    HostNodes.add(app, entry.serverUrl, entry.pairingCode, deviceName,
                        onWaiting = { mainHandler.post { showToast("正在等待旧手机加入 $label…") } })
                }
                    .fold(
                        onSuccess = { node -> HostNodes.catchUpSilently(app, node); "$label：已加入" },
                        onFailure = { failure ->
                            "$label：未加入（${(failure as? com.callagent.host.mesh.NodeSetupFailure)?.message
                                ?: apiFailureText(failure as? Exception ?: Exception(failure))}）"
                        }
                    )
            }
            com.callagent.host.push.PushRegistration.sync(app, force = true)
            mainHandler.post {
                if (isDestroyed) return@post
                session = sessionStore.read() ?: session
                settingsMessage = lines.joinToString("\n")
                navigation = navigation.selectTab(HostTab.SETTINGS)
                renderCurrentScreen()
                probeNodes(force = true)
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (navigation.destination != HostDestination.Home || navigation.tab != HostTab.PHONE) {
            navigate(navigation.back())
        } else {
            super.onBackPressed()
        }
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
        nodeExecutor.shutdownNow()
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
            renderCurrentScreen()
        } else if (requestCode == REQUEST_CALL_NOTIFICATIONS) {
            renderCurrentScreen()
        } else if (requestCode == REQUEST_CONTACTS) {
            if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) dialerContacts = emptyList()
            renderCurrentScreen()
        }
    }

    private fun setUpWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }


    private fun showPairing(message: String? = null) {
        session = null
        navigation = navigation.selectTab(HostTab.SETTINGS)
        settingsMessage = message
        showDashboard()
    }

    private var settingsMessage: String? = null

    private fun showDashboard() {
        if (!::database.isInitialized) return
        gateway = database.loadGateway() ?: gateway
        if (sims.isEmpty()) sims = database.loadSims()
        renderCurrentScreen()
    }

    private fun saveNavigation(outState: Bundle) {
        outState.putString(STATE_NAV_TAB, navigation.tab.name)
        when (val destination = navigation.destination) {
            HostDestination.Home -> outState.putString(STATE_NAV_DESTINATION, "home")
            HostDestination.Dialer -> outState.putString(STATE_NAV_DESTINATION, "dialer")
            is HostDestination.Conversation -> {
                outState.putString(STATE_NAV_DESTINATION, "conversation")
                outState.putString(STATE_NAV_SIM, destination.simId)
                outState.putString(STATE_NAV_PEER, destination.peer)
                outState.putBoolean(STATE_NAV_NEW, destination.isNew)
            }
        }
    }

    private fun restoreNavigation(state: Bundle?): HostNavigationState {
        if (state == null) return HostNavigationState()
        val tab = runCatching { HostTab.valueOf(state.getString(STATE_NAV_TAB).orEmpty()) }.getOrDefault(HostTab.PHONE)
        return when (state.getString(STATE_NAV_DESTINATION)) {
            "dialer" -> HostNavigationState(tab, HostDestination.Dialer)
            "conversation" -> HostNavigationState(
                HostTab.MESSAGES,
                HostDestination.Conversation(state.getString(STATE_NAV_SIM), state.getString(STATE_NAV_PEER).orEmpty(), state.getBoolean(STATE_NAV_NEW))
            )
            else -> HostNavigationState(tab, HostDestination.Home)
        }
    }

    private fun ensurePairedRuntimeStarted() {
        val expected = session ?: return
        if (!hasWindowFocus() || sessionStore.read()?.sameSessionInstance(expected) != true) return
        runCatching { HostBackgroundRuntime.ensureStartedForPairedSession(this) }
        runCatching { CallRuntime.ensureIncomingCallSignalingForPairedSession(this, availabilityOverride = callServerAvailable) }
    }

    private fun navigate(next: HostNavigationState) {
        if (navigation == next) return
        saveVisibleDraft()
        if (navigation.destination != next.destination) smsConversationVisibleCount = 100
        navigation = next
        savedScrollY = 0
        savedTopScrollY = 0
        savedBottomScrollY = 0
        restoreFocusedInput = null
        restoreFocusedSelection = -1
        restoreImeVisible = false
        if (next.destination is HostDestination.Conversation) {
            selectedSimId = next.destination.simId
        }
        renderCurrentScreen()
    }

    private fun renderCurrentScreen(preserveInput: Boolean = true) {
        // Rebuilt once per render; search keystrokes only filter this in memory.
        smsThreadCache = null
        val focusKey = if (preserveInput) focusedInputKey() ?: restoreFocusedInput else null
        val focusSelection = if (preserveInput) (currentFocus as? EditText)?.selectionStart ?: restoreFocusedSelection else -1
        val showIme = preserveInput && (adaptiveHost?.let { ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime()) } == true || restoreImeVisible)
        if (preserveInput) saveVisibleDraft()
        composeRecipient = null
        composeBody = null
        callDestinationEdit = null
        searchEdit = null
        callSearchEdit = null
        smsList = null
        smsThreadList = null
        smsConversationList = null
        callHistoryList = null
        dialerContactsList = null
        phoneContactsList = null
        dialerCallButton = null
        dialerReadinessText = null
        smsThreadSendButton = null
        val content = verticalRoot()
        val fixed = when (val destination = navigation.destination) {
            HostDestination.Home -> when (navigation.tab) {
                HostTab.PHONE -> {
                    renderPhoneHome(content)
                    homeChrome(HostTab.PHONE)
                }
                HostTab.MESSAGES -> {
                    renderMessagesHome(content)
                    homeChrome(HostTab.MESSAGES)
                }
                HostTab.SETTINGS -> {
                    renderSettingsHome(content)
                    homeChrome(HostTab.SETTINGS)
                }
            }
            HostDestination.Dialer -> renderDialer(content)
            is HostDestination.Conversation -> {
                val composer = renderConversation(content, destination)
                composer
            }
        }
        if (preserveInput && focusKey != null) {
            restoreFocusedInput = focusKey
            restoreFocusedSelection = focusSelection
            restoreImeVisible = showIme
            val target = when (focusKey) {
                "composeRecipient" -> composeRecipient
                "composeBody" -> composeBody
                "callDestination" -> callDestinationEdit
                "search" -> searchEdit
                "callSearch" -> callSearchEdit
                else -> null
            }
            target?.let { if (focusSelection in 0..it.length()) it.setSelection(focusSelection) }
        }
        installContent(content, fixedBottomView = fixed)
    }

    /**
     * One bottom bar for the activity's lifetime. Every render rebuilds the
     * page, and a freshly built bar draws its first frame without the
     * selected tab's indicator and then animates it in: on launch (the first
     * data refresh re-renders) and on every tab switch the bar visibly
     * jumped. Reusing it keeps its drawn state; only the selection changes.
     */
    private var homeNavigationBar: BottomNavigationView? = null

    private fun homeNavigation(selected: HostTab): BottomNavigationView {
        val nav = homeNavigationBar ?: BottomNavigationView(this).apply {
            id = R.id.main_bottom_navigation
            inflateMenu(R.menu.main_navigation)
            labelVisibilityMode = BottomNavigationView.LABEL_VISIBILITY_LABELED
            // The content host already pads for system bars and the keyboard; Material would
            // add the same bottom inset again and stretch the bar over the page while typing.
            ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets -> insets }
            setPadding(0, 0, 0, 0)
        }.also { homeNavigationBar = it }
        (nav.parent as? ViewGroup)?.removeView(nav)
        val itemId = when (selected) {
            HostTab.PHONE -> R.id.tab_phone
            HostTab.MESSAGES -> R.id.tab_messages
            HostTab.SETTINGS -> R.id.tab_settings
        }
        nav.setOnItemSelectedListener(null)
        if (nav.selectedItemId != itemId) nav.selectedItemId = itemId
        nav.setOnItemSelectedListener { item ->
            val tab = when (item.itemId) {
                R.id.tab_phone -> HostTab.PHONE
                R.id.tab_messages -> HostTab.MESSAGES
                R.id.tab_settings -> HostTab.SETTINGS
                else -> return@setOnItemSelectedListener false
            }
            navigate(navigation.selectTab(tab))
            true
        }
        nav.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80), Gravity.BOTTOM)
        return nav
    }

    private fun homeChrome(tab: HostTab): View {
        val frame = FrameLayout(this)
        val nav = homeNavigation(tab)
        frame.addView(nav, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80), Gravity.BOTTOM))
        val containerColor = android.content.res.ColorStateList.valueOf(materialColor(com.google.android.material.R.attr.colorPrimaryContainer))
        val onContainer = android.content.res.ColorStateList.valueOf(materialColor(com.google.android.material.R.attr.colorOnPrimaryContainer))
        // Every tab puts the same 80dp bar in a frame; Settings used to hand the
        // bar over bare, where it was measured at its own height instead.
        if (tab == HostTab.SETTINGS) {
            frame.minimumHeight = dp(80)
            return frame
        }
        val fab: View = if (tab == HostTab.MESSAGES) {
            // Google Messages: an extended "start chat" button.
            ExtendedFloatingActionButton(this).apply {
                id = R.id.sms_compose_fab
                text = "写短信"
                setIconResource(R.drawable.host_action_compose)
                contentDescription = "写短信"
                backgroundTintList = containerColor
                iconTint = onContainer
                setTextColor(onContainer)
                setOnClickListener { openNewConversation() }
            }
        } else {
            FloatingActionButton(this).apply {
                id = R.id.dialer_open
                setImageResource(R.drawable.host_action_dial)
                contentDescription = "拨号"
                backgroundTintList = containerColor
                imageTintList = onContainer
                setOnClickListener { navigate(navigation.openDialer()) }
            }
        }
        val fabParams = FrameLayout.LayoutParams(
            if (tab == HostTab.MESSAGES) ViewGroup.LayoutParams.WRAP_CONTENT else dp(56), dp(56), Gravity.TOP or Gravity.END
        ).apply {
            topMargin = dp(4)
            marginEnd = dp(16)
        }
        frame.addView(fab, fabParams)
        frame.minimumHeight = dp(80 + 56 + 16)
        return frame
    }

    /** Remote old-phone status: compact strip on home tabs, full card in settings. */
    private fun gatewayStatusCard(compact: Boolean): View {
        val status = GatewayStatusPresenter.present(gateway, sims, System.currentTimeMillis(), server = serverLink)
        return GatewayStatusView(this, status, compact,
            onClick = if (compact) ({ navigate(navigation.selectTab(HostTab.SETTINGS)) }) else null).apply {
            id = R.id.gateway_status_card
            tag = compact
            layoutParams = fullWidthParams(bottom = 12)
        }
    }

    /** Replace only the visible status card, e.g. so "last seen" ages without rebuilding lists. */
    private fun refreshGatewayStatusCard() {
        val current = adaptiveHost?.findViewById<View>(R.id.gateway_status_card) ?: return
        val parent = current.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(current)
        parent.removeViewAt(index)
        parent.addView(gatewayStatusCard(compact = current.tag as? Boolean ?: true), index)
    }

    private fun renderPhoneHome(content: LinearLayout) {
        val search = roundedSearchField("搜索联系人和号码", R.id.call_history_search)
        search.setText(callSearchQuery)
        callSearchEdit = search
        content.addView(search)
        if (session != null) content.addView(gatewayStatusCard(compact = true))
        phoneContactsList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(phoneContactsList)
        callHistoryList = LinearLayout(this).apply {
            id = R.id.call_history_list
            orientation = LinearLayout.VERTICAL
        }
        content.addView(callHistoryList)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                callSearchQuery = s?.toString().orEmpty()
                renderCallHistoryRows()
                queryPhoneContacts(callSearchQuery)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        renderCallHistoryRows()
        if (callSearchQuery.isNotBlank()) queryPhoneContacts(callSearchQuery)
        if (session == null) content.addView(pairingPrompt("完成配对后，通话记录会显示在这里。"))
    }

    /** Empty-state card pointing an unpaired phone at Settings. */
    private fun pairingPrompt(message: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(24), dp(24), dp(24), dp(24))
        background = HostUi.tileBackground(this@MainActivity, HostUi.Position.SINGLE)
        layoutParams = fullWidthParams(top = 16)
        addView(HostUi.iconBadge(this@MainActivity, R.drawable.ic_ui_server, 48))
        addView(body(message, selectable = false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(4))
        }, fullWidthParams())
        val settings = button("前往设置完成配对").apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
        }
        settings.setOnClickListener { navigate(navigation.selectTab(HostTab.SETTINGS)) }
        addView(settings)
    }

    private fun renderMessagesHome(content: LinearLayout) {
        val search = roundedSearchField("搜索短信", R.id.sms_search)
        search.setText(searchQuery)
        searchEdit = search
        content.addView(search)
        if (session != null) content.addView(gatewayStatusCard(compact = true))
        smsThreadList = LinearLayout(this).apply {
            id = R.id.sms_thread_list
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        smsList = smsThreadList
        content.addView(smsThreadList)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, before: Int) {
                searchQuery = s?.toString().orEmpty()
                smsThreadVisibleCount = 100
                renderSmsThreads()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        renderSmsThreads()
        if (session == null) content.addView(pairingPrompt("完成配对后可在这里收发短信。"))
    }

    private fun renderCallHistoryRows() {
        val list = callHistoryList ?: return
        list.removeAllViews()
        val query = callSearchQuery.trim().lowercase()
        val filtered = callHistory.filter { call ->
            query.isBlank() || listOfNotNull(call.from, call.to, call.reason).any { it.lowercase().contains(query) }
        }
        if (filtered.isEmpty()) {
            list.addView(emptyState("暂无通话记录"))
        } else {
            // Google Phone groups recents by day: 今天 / 昨天 / 更早.
            filtered.groupBy { dayGroup(it.createdAt) }.forEach { (header, calls) ->
                list.addView(HostUi.groupHeader(this, header))
                calls.forEachIndexed { index, call ->
                    val incoming = call.direction in setOf("incoming", "inbound")
                    val number = if (incoming) call.from.orEmpty() else call.to.orEmpty()
                    val missed = call.state == "ended" && incoming && call.answeredAt == null && call.reason != "answered_elsewhere"
                    val note = when {
                        call.reason == "answered_elsewhere" -> "其他设备已接听"
                        missed -> "未接来电"
                        call.state != "ended" -> "进行中"
                        else -> null
                    }
                    val name = contactName(number)
                    val detail = listOfNotNull(
                        note,
                        sims.firstOrNull { it.simId == call.simId }?.let { "SIM ${it.slotIndex + 1}" },
                        formatCallTime(call.createdAt),
                    ).joinToString(" · ")
                    val row = PhoneRowView(
                        context = this,
                        name = name ?: number.ifBlank { "未知号码" },
                        detail = detail,
                        missed = missed,
                        direction = when {
                            missed -> PhoneRowView.Direction.MISSED
                            incoming -> PhoneRowView.Direction.INCOMING
                            else -> PhoneRowView.Direction.OUTGOING
                        },
                        avatarName = name,
                        avatarKey = number,
                        position = HostUi.position(index, calls.size),
                        onOpen = { openCall(call) },
                        onCall = { quickDial(call) },
                    ).apply { id = R.id.call_history_item }
                    list.addView(row)
                }
            }
        }
        if (callHistoryCursor != null) {
            val more = smallButton(if (callHistoryLoading) "正在载入…" else "更早通话")
            more.isEnabled = !callHistoryLoading
            more.setOnClickListener { loadMoreCallHistory() }
            list.addView(more)
        }
    }

    /** The trailing call button dials the same number on the same SIM at once, like Google Phone. */
    private fun quickDial(call: RemoteCall) {
        val number = (if (call.direction in setOf("incoming", "inbound")) call.from else call.to)?.let(::normalizeDialNumber)
        val line = sims.firstOrNull { it.simId == call.simId }
        if (number.isNullOrBlank() || line == null || !callReadiness(line).ready || !validDialAddress(number)) {
            openCall(call)
            return
        }
        selectedSimId = line.simId
        beginDial(line, number)
    }

    private fun dayGroup(value: String): String {
        val millis = timestampMillis(value)
        return when {
            millis <= 0L -> "更早"
            android.text.format.DateUtils.isToday(millis) -> "今天"
            android.text.format.DateUtils.isToday(millis + android.text.format.DateUtils.DAY_IN_MILLIS) -> "昨天"
            else -> "更早"
        }
    }

    private fun formatCallTime(value: String): String = runCatching {
        val millis = timestampMillis(value)
        val today = android.text.format.DateUtils.isToday(millis) ||
            android.text.format.DateUtils.isToday(millis + android.text.format.DateUtils.DAY_IN_MILLIS)
        java.text.SimpleDateFormat(if (today) "HH:mm" else "M月d日", java.util.Locale.getDefault()).format(java.util.Date(millis))
    }.getOrDefault(value.take(16))

    private fun emptyState(message: String): View = body(message, selectable = false).apply {
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(32), dp(16), dp(32))
        layoutParams = fullWidthParams()
    }

    private fun openCall(call: RemoteCall) {
        val active = CallRuntime.currentSession?.takeIf { it.callId == call.callId && isLiveCallPhase(it.phase) }
        if (active != null) {
            startActivity(Intent(this, CallActionActivity::class.java)
                .setAction(CallActionActivity.ACTION_SHOW_CALL)
                .putExtra(CallActionActivity.EXTRA_CALL_ID, call.callId))
            return
        }
        val number = if (call.direction in setOf("incoming", "inbound")) call.from else call.to
        if (number.isNullOrBlank()) return
        selectedSimId = sims.firstOrNull { it.simId == call.simId }?.simId
        callDestination = normalizeDialNumber(number)
        navigate(navigation.openDialer())
    }

    private fun contactName(number: String): String? {
        val normalized = normalizeDialNumber(number)
        return dialerContacts.firstOrNull { normalizeDialNumber(it.normalizedNumber ?: it.number) == normalized }?.name
    }

    private fun renderSmsThreads() {
        val list = smsThreadList ?: return
        list.removeAllViews()
        if (session == null) {
            list.addView(body("本机导入的短信归档可在设置中查看。"))
            return
        }
        val allThreads = smsThreadCache ?: SmsThreadIndex.build(
            sims.flatMap { line -> database.loadMessages(line.simId) } + database.loadMessages(null),
            smsCountryLookup()
        ).also { smsThreadCache = it }
        val threads = SmsThreadIndex.filter(allThreads, searchQuery)
        if (threads.isEmpty()) {
            list.addView(emptyState("暂无短信对话"))
        } else {
            val visibleThreads = threads.take(smsThreadVisibleCount)
            visibleThreads.forEachIndexed { index, thread ->
                val position = HostUi.position(index, visibleThreads.size)
                val row = LinearLayout(this).apply {
                    id = R.id.sms_thread_item
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(76)
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    background = HostUi.tileBackground(this@MainActivity, position)
                    layoutParams = HostUi.tileParams(this@MainActivity, position)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        selectedSimId = thread.simId
                        navigate(navigation.openConversation(thread.simId, thread.key))
                    }
                }
                val name = contactName(thread.peer)
                row.addView(HostUi.avatar(this, name, thread.key, 48), LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(16) })
                val textColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val peer = MaterialTextView(this).apply {
                    text = name ?: thread.peer
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                    setTextColor(materialColor(com.google.android.material.R.attr.colorOnSurface))
                }
                top.addView(peer, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val badge = MaterialTextView(this).apply {
                    id = R.id.sms_thread_sim_badge
                    text = simBadgeLabel(thread.simId)
                    contentDescription = text.toString()
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall)
                    setTextColor(materialColor(com.google.android.material.R.attr.colorOnSecondaryContainer))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(8).toFloat()
                        setColor(materialColor(com.google.android.material.R.attr.colorSecondaryContainer))
                    }
                    setPadding(dp(6), dp(2), dp(6), dp(2))
                }
                top.addView(badge, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8); marginEnd = dp(8) })
                top.addView(MaterialTextView(this).apply {
                    text = formatHistoryTime(thread.latest.createdAt)
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
                    setTextColor(materialColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                })
                textColumn.addView(top)
                val summary = thread.latest.text.replace('\n', ' ').trim().ifBlank { SmsStatus.display(thread.latest.status) }
                val prefix = if (thread.latest.direction == "inbound") "" else "你："
                textColumn.addView(HostUi.subtitle(this, prefix + summary.take(90)).apply { setPadding(0, dp(2), 0, 0) })
                row.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                list.addView(row)
            }
            if (threads.size > visibleThreads.size) {
                val more = smallButton("更多会话")
                more.setOnClickListener { smsThreadVisibleCount += 100; renderSmsThreads() }
                list.addView(more)
            }
        }
    }

    /**
     * Country used to key SMS peers on each SIM: the gateway SIM's reported
     * country, else this phone's own. Threads, conversation detail and search
     * must all key peers through this one lookup.
     */
    private fun smsCountryLookup(): (String?) -> String? {
        val simCountries = SmsPeerKey.simCountries(sims)
        val fallback = hostCountryIso()
        return { simId -> SmsPeerKey.countryFor(simId, simCountries, fallback) }
    }

    private fun hostCountryIso(): String? {
        val telephony = getSystemService(TelephonyManager::class.java)
        return SmsPeerKey.hostCountryIso(
            runCatching { telephony?.simCountryIso }.getOrNull(),
            runCatching { telephony?.networkCountryIso }.getOrNull(),
            java.util.Locale.getDefault().country,
        )
    }

    private fun simBadgeLabel(simId: String?): String {
        val line = sims.firstOrNull { it.simId == simId } ?: return "SIM ?"
        return "SIM ${line.slotIndex + 1}"
    }

    private fun timestampMillis(value: String): Long = runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)

    private fun formatHistoryTime(value: String): String = runCatching {
        val date = java.util.Date(timestampMillis(value))
        val pattern = if (android.text.format.DateUtils.isToday(date.time)) "HH:mm" else "M月d日"
        java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(date)
    }.getOrDefault(value.take(16))

    private fun divider(): View = View(this).apply {
        setBackgroundColor(materialColor(com.google.android.material.R.attr.colorOutlineVariant))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { marginStart = dp(72) }
    }

    private fun selectableItemBackground(): android.graphics.drawable.Drawable? =
        android.util.TypedValue().let { value ->
            theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
            if (value.resourceId != 0) ContextCompat.getDrawable(this, value.resourceId) else null
        }

    private fun renderSettingsHome(content: LinearLayout) {
        content.addView(title("设置"))
        settingsMessage?.let { content.addView(messageCard(it, error = true)) }
        if (session == null) {
            content.addView(HostUi.groupHeader(this, "服务器与配对").apply { id = R.id.settings_pairing_section })
            val form = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(12))
                background = HostUi.tileBackground(this@MainActivity, HostUi.Position.SINGLE)
                layoutParams = HostUi.tileParams(this@MainActivity, HostUi.Position.SINGLE)
            }
            form.addView(body("连接服务器以使用远程电话和短信。", selectable = false))
            addPairingForm(form)
            content.addView(form)
        } else {
            content.addView(HostUi.groupHeader(this, "服务器与设备"))
            content.addView(HostUi.tile(this, HostUi.Position.SINGLE,
                title = session?.apiBaseUrl.orEmpty(),
                summary = "已配对此手机 · 账号 ${shortId(session?.ownerId.orEmpty())}",
                icon = R.drawable.ic_ui_server))
            addServerNodes(content)
            content.addView(HostUi.groupHeader(this, "已配对设备"))
            val devices = LinearLayout(this).apply {
                id = R.id.settings_device_list
                orientation = LinearLayout.VERTICAL
            }
            when {
                pairedHostsAvailable == false -> devices.addView(HostUi.tile(this, HostUi.Position.SINGLE, "当前服务器不提供设备列表。", icon = R.drawable.ic_ui_device))
                pairedHosts.isEmpty() -> devices.addView(HostUi.tile(this, HostUi.Position.SINGLE, pairedHostsRefreshIssue ?: "暂无设备记录。", icon = R.drawable.ic_ui_device))
                else -> pairedHosts.forEachIndexed { index, host ->
                    devices.addView(HostUi.tile(this, HostUi.position(index, pairedHosts.size),
                        title = host.name,
                        summary = listOfNotNull(if (host.isSelf) "此手机" else null, host.pairingStateLabel()).joinToString(" · "),
                        icon = R.drawable.ic_ui_device))
                }
            }
            content.addView(devices)
            pairedHostsRefreshIssue?.takeIf { pairedHosts.isNotEmpty() }?.let { content.addView(body(it)) }
            content.addView(HostUi.groupHeader(this, "旧手机与 SIM 卡"))
            content.addView(gatewayStatusCard(compact = false))
            content.addView(HostUi.tile(this, HostUi.Position.SINGLE, "解除此手机配对",
                summary = "删除此手机上的配对凭据和本机缓存",
                icon = R.drawable.ic_ui_link_off,
                onClick = { confirmUnpair() }).apply {
                id = R.id.settings_unpair
                (getChildAt(1) as? LinearLayout)?.getChildAt(0)?.let { (it as? TextView)?.setTextColor(materialColor(androidx.appcompat.R.attr.colorError)) }
            })
        }
        content.addView(HostUi.groupHeader(this, "通知与权限"))
        addPermissionSettings(content)
        content.addView(HostUi.groupHeader(this, "待机诊断"))
        content.addView(HostUi.tile(this, HostUi.Position.SINGLE, "查看与导出待机统计",
            summary = "查看信令锁、同步请求及最近收到的推送；统计不含短信或号码",
            icon = R.drawable.ic_ui_refresh,
            onClick = {
                val report = com.callagent.host.data.StandbyDiagnostics.export(this)
                MaterialAlertDialogBuilder(this).setTitle("待机统计")
                    .setMessage(com.callagent.host.data.StandbyDiagnostics.summary()).setPositiveButton("关闭", null)
                    .setNeutralButton("分享统计") { _, _ ->
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, report)
                        }, "导出待机统计"))
                    }.show()
            }))
        content.addView(HostUi.groupHeader(this, "备份"))
        content.addView(HostUi.tile(this, HostUi.Position.SINGLE, "全量备份与恢复",
            summary = "设置、所有节点的配对和本机数据，加密导出到一个文件；可恢复到本机或新手机",
            icon = R.drawable.ic_ui_archive,
            trailing = HostUi.chevron(this),
            onClick = { startActivity(Intent(this, com.callagent.host.backup.HostFullBackupActivity::class.java)) }
        ).apply { id = R.id.full_backup_entry })
        content.addView(HostUi.groupHeader(this, "短信备份与归档"))
        content.addView(smsArchiveEntryButton())
        settingsMessage = null
    }

    private fun probeNodes(force: Boolean) {
        if (!HostNodes.isMultiNode(this)) return
        val app = applicationContext
        // A switch is announced by broadcast and handled by the receiver.
        runCatching { nodeExecutor.execute { runCatching { NodeMonitor.evaluate(app, force) } } }
    }

    /** Server nodes in priority order; the phone uses the first healthy one. */
    private fun addServerNodes(content: LinearLayout) {
        content.addView(HostUi.groupHeader(this, "服务器节点").apply { id = R.id.settings_nodes_section })
        val nodes = HostNodes.nodes(this)
        if (nodes.size < 2) {
            content.addView(body("可再添加一台服务器作为备用节点（例如家里的局域网服务器和外网 VPS）。当前节点不通或延迟过高时自动切换，恢复后切回优先级更高的节点。"))
            session?.sessionInstanceId?.let { instance ->
                val push = com.callagent.host.push.PushRegistration.status(this, instance)
                content.addView(HostUi.tile(this, HostUi.Position.FIRST, "推送唤醒",
                    summary = NodeRowPresenter.pushDetail(push, System.currentTimeMillis()),
                    icon = R.drawable.ic_ui_refresh,
                    onClick = { retryPush() }).apply { id = R.id.settings_node_push })
            }
        } else {
            content.addView(body("按住 ≡ 拖动调整优先级。正在使用的节点不可用时切换到下一个可用节点，优先级更高的节点稳定恢复后切回。"))
            val push = nodes.associate { it.key to com.callagent.host.push.PushRegistration.status(this, it.sessionInstanceId) }
            val rows = NodeRowPresenter.rows(nodes, HostNodes.health(this), HostNodes.activeNode(this)?.key, push)
            content.addView(NodeListView(this, rows,
                onReorder = { order ->
                    if (HostNodes.reorder(this, order)) probeNodes(force = true)
                    // Re-render after the drag gesture has fully finished.
                    mainHandler.post { if (!isDestroyed) renderCurrentScreen() }
                },
                onRemove = { row -> confirmRemoveNode(row.key, row.title) },
                onSelect = { row -> showNodeDetails(row.key) }
            ).apply { id = R.id.settings_node_list; layoutParams = fullWidthParams(top = 4, bottom = 4) })
            content.addView(HostUi.tile(this, HostUi.Position.FIRST, "立即检测节点", icon = R.drawable.ic_ui_refresh,
                onClick = {
                    probeNodes(force = true)
                    com.callagent.host.push.PushRegistration.retryNow(this)
                    showToast("正在检测各节点…")
                }).apply { id = R.id.settings_nodes_probe })
        }
        val addPosition = when {
            nodes.size >= 2 || session?.sessionInstanceId != null -> HostUi.Position.LAST
            else -> HostUi.Position.SINGLE
        }
        content.addView(HostUi.tile(this, addPosition, "添加服务器节点",
            icon = R.drawable.ic_ui_add, onClick = { showAddNodeDialog() }).apply { id = R.id.settings_nodes_add })
    }

    private fun showNodeDetails(key: String) {
        val node = HostNodes.nodes(this).firstOrNull { it.key == key } ?: return
        val details = NodeRowPresenter.details(node, HostNodes.health(this)[key],
            HostNodes.activeNode(this)?.key == key, System.currentTimeMillis(),
            com.callagent.host.push.PushRegistration.status(this, node.sessionInstanceId))
        MaterialAlertDialogBuilder(this)
            .setTitle(node.label)
            .setMessage(details.joinToString("\n") { (label, value) -> "$label：$value" })
            .setPositiveButton("关闭", null)
            .setNeutralButton("立即检测") { _, _ ->
                probeNodes(force = true)
                com.callagent.host.push.PushRegistration.retryNow(this)
            }
            .show()
    }

    /** Asks nodes without a push registration again now; the list refreshes when they answer. */
    private fun retryPush() {
        com.callagent.host.push.PushRegistration.retryNow(this)
        showToast("正在重新登记推送…")
    }

    private fun showAddNodeDialog() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val serverField = inputField("节点服务器地址（http:// 或 https://）", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val codeField = inputField("该服务器的一次性配对码", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        form.addView(body("先在旧手机的“其他节点”里添加同一台服务器，再在该服务器上为此手机生成配对码。", selectable = false))
        form.addView(serverField)
        form.addView(codeField)
        MaterialAlertDialogBuilder(this)
            .setTitle("添加服务器节点")
            .setView(form)
            .setNegativeButton("取消", null)
            .setPositiveButton("添加") { _, _ ->
                val url = serverField.editText?.text?.toString()?.trim().orEmpty()
                val code = codeField.editText?.text?.toString()?.trim().orEmpty()
                if (url.isBlank() || code.isBlank()) {
                    showToast("请输入服务器地址和配对码。")
                    return@setPositiveButton
                }
                val deviceName = Build.MODEL.orEmpty().ifBlank { "Android host" }.take(100)
                val app = applicationContext
                settingsMessage = null
                statusMessage = "正在验证并添加节点…"
                showToast("正在验证并添加节点…")
                nodeExecutor.execute {
                    val result = runCatching {
                        HostNodes.add(app, url, code, deviceName,
                            onWaiting = { mainHandler.post { showToast("正在等待旧手机加入这个服务器…") } })
                    }
                    // History catch-up runs after the result is shown; it is silent and best-effort.
                    result.getOrNull()?.let { node ->
                        nodeExecutor.execute { HostNodes.catchUpSilently(app, node) }
                        com.callagent.host.push.PushRegistration.sync(app, force = true)
                    }
                    mainHandler.post {
                        if (isDestroyed) return@post
                        result.onSuccess { node ->
                            // The active pairing now belongs to a profile.
                            session = sessionStore.read() ?: session
                            showToast("已添加节点 ${node.label}")
                            probeNodes(force = true)
                        }.onFailure { failure ->
                            val reason = if (failure is com.callagent.host.mesh.NodeSetupFailure) failure.message.orEmpty()
                                else apiFailureText(failure as? Exception ?: Exception(failure))
                            settingsMessage = "未添加节点：$reason"
                        }
                        renderCurrentScreen()
                    }
                }
            }
            .show()
    }

    private fun confirmRemoveNode(key: String, title: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("移除节点？")
            .setMessage("将撤销此手机在 $title 上的登录凭据，之后不会再切换到这个节点。")
            .setNegativeButton("取消", null)
            .setPositiveButton("移除") { _, _ ->
                val app = applicationContext
                nodeExecutor.execute {
                    val result = runCatching { HostNodes.remove(app, key) }
                    mainHandler.post {
                        if (isDestroyed) return@post
                        result.onFailure { settingsMessage = it.message ?: "未能移除节点。" }
                        renderCurrentScreen()
                    }
                }
            }
            .show()
    }

    private fun addPermissionSettings(content: LinearLayout) {
        val status = runCatching { HostBackgroundRuntime.snapshot(this) }.getOrNull()
        content.addView(HostUi.tile(this, HostUi.Position.FIRST, "通知",
            summary = if (status?.notificationsEnabled == true) "已允许 · 来电弹窗、短信内容和后台同步" else "未允许 · 来电和短信将无法提醒",
            icon = R.drawable.ic_ui_notifications,
            trailing = HostUi.chevron(this),
            onClick = {
                val latest = runCatching { HostBackgroundRuntime.snapshot(this@MainActivity) }.getOrNull()
                if (latest?.notificationsEnabled == true) {
                    HostBackgroundRuntime.openNotificationSettings(this@MainActivity)
                } else if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CALL_NOTIFICATIONS)
                } else {
                    HostBackgroundRuntime.openNotificationSettings(this@MainActivity)
                }
            }).apply { id = R.id.settings_notifications })

        val autoCopy = MaterialSwitch(this).apply {
            isChecked = InboundSmsNotifications.autoCopyCodes(this@MainActivity)
            isSaveEnabled = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
        }
        content.addView(HostUi.tile(this, HostUi.Position.MIDDLE, "自动复制验证码",
            summary = "收到验证码短信时直接复制到剪贴板；通知上也有“复制验证码”按钮",
            icon = R.drawable.ic_ui_copy,
            trailing = autoCopy,
            onClick = {
                autoCopy.isChecked = !autoCopy.isChecked
                InboundSmsNotifications.setAutoCopyCodes(this@MainActivity, autoCopy.isChecked)
            }).apply {
            id = R.id.settings_auto_copy_codes
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = android.widget.Switch::class.java.name
                    info.isCheckable = true
                    info.isChecked = autoCopy.isChecked
                }
            }
        })

        val autoFill = com.callagent.host.notify.OtpAutoFillService.enabled(this)
        content.addView(HostUi.tile(this, HostUi.Position.MIDDLE, "自动填入验证码",
            summary = if (autoFill) "已开启 · 收到验证码后填入正在输入的验证码框"
                else "在系统无障碍设置中开启“Madoka 验证码自动填入”后可用",
            icon = R.drawable.ic_ui_copy,
            trailing = HostUi.chevron(this),
            onClick = {
                runCatching { startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }).apply { id = R.id.settings_otp_autofill })

        content.addView(HostUi.tile(this, HostUi.Position.MIDDLE, "电池优化",
            summary = if (status?.batteryExempt == true) "已豁免 · 后台同步不受限制" else "建议豁免，避免后台收不到短信和来电",
            icon = R.drawable.ic_ui_battery,
            trailing = HostUi.chevron(this),
            onClick = { HostBackgroundRuntime.openBatterySettings(this@MainActivity) }).apply { id = R.id.settings_battery })

        val contactsGranted = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        content.addView(HostUi.tile(this, HostUi.Position.LAST, if (contactsGranted) "联系人" else "允许访问联系人",
            summary = if (contactsGranted) "已允许 · 通话记录、短信和来电显示联系人姓名" else "用本机联系人显示姓名并搜索号码",
            icon = R.drawable.ic_ui_contacts,
            trailing = HostUi.chevron(this),
            onClick = {
                if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                } else {
                    requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_CONTACTS)
                }
            }).apply { id = R.id.settings_contacts })
    }

    private fun addPairingForm(content: LinearLayout) {
        val serverField = inputField("服务器地址（http:// 或 https://）", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val serverInput = serverField.editText as TextInputEditText
        serverInput.id = R.id.pairing_server
        serverInput.setText(pairingForm.serverUrl)
        serverInput.setSingleLine(true)
        serverInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { pairingForm.updateServerUrl(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        content.addView(serverField)
        val codeField = inputField("一次性配对码", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val code = codeField.editText as TextInputEditText
        code.id = R.id.pairing_code
        code.isSaveEnabled = false
        code.isSaveFromParentEnabled = false
        code.setText(pairingForm.pairingCode)
        code.maxLines = 1
        code.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { pairingForm.updatePairingCode(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        content.addView(codeField)
        val nameField = inputField("设备名称", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME)
        val name = nameField.editText as TextInputEditText
        name.id = R.id.pairing_device_name
        name.setText(pairingForm.deviceName)
        name.setSingleLine(true)
        name.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { pairingForm.updateDeviceName(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        content.addView(nameField)
        val pair = button("配对此手机")
        pairingButton = pair
        pair.isEnabled = !pairingForm.hasPendingOrRunningPairing
        pair.setOnClickListener {
            val base = serverInput.text.toString().trim()
            val pairingCode = code.text.toString().trim()
            val deviceName = name.text.toString().trim().ifBlank { "Android host" }.take(100)
            if (base.isBlank() || pairingCode.isBlank()) {
                showToast("请输入服务器地址和一次性配对码。")
                if (base.isBlank()) serverInput.requestFocus() else code.requestFocus()
                return@setOnClickListener
            }
            if (pairingForm.startPairing(applicationContext, base, pairingCode, deviceName)) pair.isEnabled = false
        }
        content.addView(pair)
    }

    /** Top app bar: a round back arrow and the page title (the arrow keeps the "返回" id and description). */
    private fun topBar(titleText: String, extra: View? = null): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64)
            layoutParams = fullWidthParams()
        }
        val back = MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            id = R.id.screen_back
            setIconResource(R.drawable.call_ui_back)
            iconTint = android.content.res.ColorStateList.valueOf(materialColor(com.google.android.material.R.attr.colorOnSurface))
            iconSize = dp(24)
            iconPadding = 0
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            contentDescription = "返回"
            cornerRadius = dp(24)
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            setOnClickListener { navigate(navigation.goHome()) }
        }
        bar.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        extra?.let { bar.addView(it) }
        bar.addView(MaterialTextView(this).apply {
            text = titleText
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
            setTextColor(materialColor(com.google.android.material.R.attr.colorOnSurface))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(12), 0, dp(8), 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return bar
    }

    /**
     * Dial pad in the style of Google Phone: the page scrolls only the contact
     * suggestions, while the number, keypad, SIM choice and call button sit in a
     * panel pinned to the bottom, so the keys never move as suggestions change.
     * Returns that panel for [installContent] to pin.
     */
    private fun renderDialer(content: LinearLayout): View {
        // Removed SIMs remain in the cache for history, but are no longer
        // choices for a new call, even when a replacement uses the same slot.
        val dialerSims = sims.filter { it.state != "removed" }
        selectedSimId = DialerSimChoice.choose(selectedSimId, CallPreferences(this).lastDialSim, dialerSims)
        content.addView(topBar("拨号"))

        val contactHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            val allow = smallButton("允许访问联系人")
            allow.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            allow.setOnClickListener { requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_CONTACTS) }
            contactHeader.addView(allow)
        }
        content.addView(contactHeader)
        dialerContactsList = LinearLayout(this).apply {
            id = R.id.dialer_contact_list
            orientation = LinearLayout.VERTICAL
        }
        content.addView(dialerContactsList)

        // Size the keys to the window so the pinned panel leaves room for suggestions.
        val screenHeight = resources.configuration.screenHeightDp
        val keyHeight = ((screenHeight - 400) / 4).coerceIn(44, 60)
        val compact = keyHeight < 52
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(12))
            background = GradientDrawable().apply {
                val r = dp(28).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                setColor(materialColor(com.google.android.material.R.attr.colorSurfaceContainer))
            }
            isClickable = true
        }

        // Number row: the number centered, backspace on the right.
        val numberRow = FrameLayout(this)
        val destination = TextInputEditText(this).apply {
            // The smoke fixture recognises an empty field by this hint.
            hint = "联系人或号码"
            inputType = InputType.TYPE_CLASS_PHONE
            gravity = Gravity.CENTER
            background = null
            textSize = if (compact) 28f else 34f
            maxLines = 1
            showSoftInputOnFocus = false
            setTextColor(materialColor(com.google.android.material.R.attr.colorOnSurface))
            setHintTextColor(materialColor(com.google.android.material.R.attr.colorOutline))
            setPadding(dp(56), dp(4), dp(56), dp(4))
        }
        destination.id = R.id.dialer_destination
        destination.setSingleLine(true)
        destination.setText(callDestination)
        callDestinationEdit = destination
        numberRow.addView(destination, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(if (compact) 52 else 64), Gravity.CENTER_VERTICAL))
        val backspace = MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            id = R.id.dialer_backspace
            setIconResource(R.drawable.host_action_backspace)
            iconTint = android.content.res.ColorStateList.valueOf(materialColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            iconSize = dp(26)
            iconPadding = 0
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            contentDescription = "删除号码"
            cornerRadius = dp(24)
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
        }
        backspace.setOnClickListener {
            val input = destination.text ?: return@setOnClickListener
            val start = destination.selectionStart.coerceAtLeast(0)
            val end = destination.selectionEnd.coerceAtLeast(0)
            if (start != end) input.delete(start, end) else if (start > 0) input.delete(start - 1, start)
        }
        backspace.setOnLongClickListener { destination.text?.clear(); true }
        numberRow.addView(backspace, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER_VERTICAL or Gravity.END))
        panel.addView(numberRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        destination.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                callDestination = s?.toString().orEmpty()
                contactsQuery = callDestination
                queryDialerContacts(contactsQuery)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        renderDialerContacts()

        val keypad = GridLayout(this).apply {
            id = R.id.dialer_keypad
            columnCount = 3
            rowCount = 4
            useDefaultMargins = false
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }
        val keys = listOf(
            "1" to R.id.keypad_1, "2" to R.id.keypad_2, "3" to R.id.keypad_3,
            "4" to R.id.keypad_4, "5" to R.id.keypad_5, "6" to R.id.keypad_6,
            "7" to R.id.keypad_7, "8" to R.id.keypad_8, "9" to R.id.keypad_9,
            "*" to R.id.keypad_star, "0" to R.id.keypad_0, "#" to R.id.keypad_hash,
        )
        keys.forEach { (key, id) ->
            val keyButton = DialPadKeyView(this, key).apply {
                this.id = id
                minHeight = dp(keyHeight)
                minimumHeight = dp(keyHeight)
                if (compact) textSize = 24f
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(keyHeight)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(5), dp(4), dp(5), dp(4))
                }
                setOnClickListener {
                    val at = destination.selectionStart.coerceAtLeast(0)
                    destination.text?.insert(at, key)
                    destination.setSelection((at + key.length).coerceAtMost(destination.length()))
                }
            }
            keypad.addView(keyButton)
        }
        panel.addView(keypad, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Bottom row: SIM choice on the left, the call button in the middle.
        val bottomRow = FrameLayout(this).apply { setPadding(0, dp(8), 0, 0) }
        val selector = ChipGroup(this).apply {
            id = R.id.dialer_sim_selector
            isSaveEnabled = false
            isSaveFromParentEnabled = false
            isSingleSelection = true
            isSelectionRequired = false
            isSingleLine = true
            chipSpacingHorizontal = dp(6)
        }
        dialerSims.sortedBy { it.slotIndex }.forEach { line ->
            val chip = Chip(this).apply {
                text = DialerSimChoice.chipLabel(line)
                isCheckable = true
                isSingleLine = true
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                maxWidth = dp(160)
                minHeight = dp(48)
                isChecked = line.simId == selectedSimId
                isCheckedIconVisible = false
                contentDescription = "SIM ${line.slotIndex + 1}, ${line.label}${line.phoneNumber?.let { ", $it" }.orEmpty()}"
                isSaveEnabled = false
                isSaveFromParentEnabled = false
            }
            selector.addView(chip)
            chip.setOnClickListener {
                selectedSimId = line.simId
                renderCurrentScreen(preserveInput = true)
            }
        }
        val selected = dialerSims.firstOrNull { it.simId == selectedSimId }
        val dial = button("拨打").apply {
            id = R.id.dialer_call_button
            contentDescription = "拨出"
            setIconResource(R.drawable.call_ui_phone)
            iconSize = dp(24)
            iconPadding = dp(8)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(24, 128, 56))
            iconTint = android.content.res.ColorStateList.valueOf(Color.WHITE)
            setTextColor(Color.WHITE)
            textSize = 16f
            cornerRadius = dp(28)
            insetTop = 0
            insetBottom = 0
            setOnClickListener { beginDial(selected, destination.text?.toString().orEmpty()) }
        }
        dialerCallButton = dial
        bottomRow.addView(dial, FrameLayout.LayoutParams(dp(136), dp(if (compact) 48 else 56), Gravity.CENTER))
        bottomRow.addView(selector, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL or Gravity.START))
        // Two SIM chips and a centered call button overlap on narrow windows; stack them there.
        bottomRow.addOnLayoutChangeListener { row, _, _, _, _, _, _, _, _ ->
            val chipsEnd = selector.right
            val callStart = dial.left
            if (selector.childCount > 0 && chipsEnd > callStart && selector.layoutParams.let { (it as FrameLayout.LayoutParams).gravity != (Gravity.TOP or Gravity.CENTER_HORIZONTAL) }) {
                row.post {
                    selector.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                    dial.layoutParams = (dial.layoutParams as FrameLayout.LayoutParams).apply {
                        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                        topMargin = dp(52)
                    }
                }
            }
        }
        panel.addView(bottomRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        dialerReadinessText = body("").apply {
            id = R.id.dialer_readiness
            gravity = Gravity.CENTER_HORIZONTAL
            maxLines = 2
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setPadding(0, dp(6), 0, 0)
        }
        panel.addView(dialerReadinessText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        updateDialButtonState()
        if (session != null && callServerAvailable == null) refreshCallAvailability()
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED && dialerContacts.isEmpty()) {
            queryDialerContacts(contactsQuery)
        }
        return panel
    }

    private fun contactTile(entry: ContactEntry, position: HostUi.Position, onClick: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = HostUi.tileBackground(this@MainActivity, position)
            layoutParams = HostUi.tileParams(this@MainActivity, position)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            addView(HostUi.avatar(this@MainActivity, entry.name, entry.normalizedNumber ?: entry.number),
                LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(16) })
            val texts = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(HostUi.title(this@MainActivity, entry.name))
            texts.addView(HostUi.subtitle(this@MainActivity, entry.number))
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

    private fun renderDialerContacts() {
        val list = dialerContactsList ?: return
        list.removeAllViews()
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            list.addView(body("允许访问联系人以查找姓名和号码。", selectable = false).apply { gravity = Gravity.CENTER })
            return
        }
        val query = contactsQuery.trim()
        val matching = dialerContacts.filter { query.isBlank() || HostContactsRepository.matches(it, query) }.take(3)
        if (matching.isEmpty()) {
            if (query.isNotBlank()) list.addView(body("没有匹配的联系人。", selectable = false).apply { gravity = Gravity.CENTER })
            return
        }
        matching.forEachIndexed { index, entry ->
            list.addView(contactTile(entry, HostUi.position(index, matching.size)) {
                val number = entry.normalizedNumber?.takeIf { it.isNotBlank() } ?: entry.number
                callDestination = normalizeDialNumber(number)
                callDestinationEdit?.setText(callDestination)
                callDestinationEdit?.setSelection(callDestination.length)
            })
        }
    }

    private fun renderPhoneContacts(matches: List<ContactEntry>) {
        val list = phoneContactsList ?: return
        list.removeAllViews()
        val visible = matches.take(3)
        if (visible.isEmpty()) return
        list.addView(HostUi.groupHeader(this, "联系人"))
        visible.forEachIndexed { index, entry ->
            list.addView(contactTile(entry, HostUi.position(index, visible.size)) {
                callDestination = normalizeDialNumber(entry.normalizedNumber?.takeIf(String::isNotBlank) ?: entry.number)
                navigate(navigation.openDialer())
            })
        }
    }

    private fun queryPhoneContacts(query: String) {
        if (query.isBlank() || checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            phoneContactsGeneration++
            renderPhoneContacts(emptyList())
            return
        }
        val generation = ++phoneContactsGeneration
        contactsRepository.queryAsync(query) { result ->
            if (isDestroyed || generation != phoneContactsGeneration || navigation.destination != HostDestination.Home || navigation.tab != HostTab.PHONE) return@queryAsync
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return@queryAsync
            renderPhoneContacts(result.getOrDefault(emptyList()))
        }
    }

    /** Google-style search pill with a leading magnifier. */
    private fun roundedSearchField(hint: String, id: Int): TextInputEditText = TextInputEditText(this).apply {
        this.id = id
        this.hint = hint
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        maxLines = 1
        minHeight = dp(56)
        setPadding(dp(20), dp(12), dp(20), dp(12))
        background = HostUi.pill(this@MainActivity)
        setCompoundDrawablesRelativeWithIntrinsicBounds(
            HostUi.tint(this@MainActivity, R.drawable.ic_ui_search, com.google.android.material.R.attr.colorOnSurfaceVariant), null, null, null
        )
        compoundDrawablePadding = dp(16)
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
        setTextColor(materialColor(com.google.android.material.R.attr.colorOnSurface))
        setHintTextColor(materialColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        setSingleLine(true)
        layoutParams = fullWidthParams(top = 4, bottom = 12)
    }

    private fun normalizeDialNumber(value: String): String = value.filter { it.isDigit() || it in "+*#" }

    private fun queryDialerContacts(query: String?) {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return
        val generation = ++contactsQueryGeneration
        contactsRepository.queryAsync(query) { result ->
            if (isDestroyed || generation != contactsQueryGeneration || navigation.destination != HostDestination.Dialer) return@queryAsync
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return@queryAsync
            dialerContacts = result.getOrDefault(emptyList())
            renderDialerContacts()
        }
    }

    private fun callReadiness(line: SimLine? = sims.firstOrNull { it.simId == selectedSimId }): CallReadiness.Result {
        val current = gateway
        return CallReadiness.evaluate(
            paired = session != null,
            gatewayOnline = current != null && GatewayStatusPresenter.isOnline(current,
                current.lastSeenAt?.let { runCatching { Instant.parse(it) }.getOrNull() }, System.currentTimeMillis()),
            gatewayVoiceReady = current?.voiceReady,
            gatewayRevision = current?.mappingRevision,
            line = line,
            callServerAvailable = callServerAvailable,
            callServerReason = callServerReason,
            localEngineAvailable = localCallEngineAvailable(),
            server = serverLink
        )
    }

    private fun updateDialButtonState() {
        val readiness = callReadiness()
        dialerCallButton?.let { button ->
            button.isEnabled = readiness.ready
            // The fixed green tint hides Material's disabled state, so dim it explicitly.
            button.alpha = if (readiness.ready) 1f else 0.38f
        }
        dialerReadinessText?.let { text ->
            val message = readiness.blocker ?: readiness.hint
            text.text = message.orEmpty()
            text.visibility = if (message == null) View.GONE else View.VISIBLE
        }
    }

    private fun beginDial(line: SimLine?, numberValue: String) {
        val number = normalizeDialNumber(numberValue)
        val readiness = callReadiness(line)
        if (line == null || !readiness.ready) {
            showToast(readiness.blocker ?: "请先选择在线且已确认的 SIM 卡。")
            return
        }
        if (!validDialAddress(number)) {
            callDestinationEdit?.error = "请输入有效号码"
            return
        }
        val start = {
            val accepted = CallRuntime.startOutbound(
                this, gateway!!.gatewayId, line.simId, line.label, line.mappingRevision, number
            )
            if (accepted) {
                CallPreferences(this).lastDialSim = line.simId
                startActivity(Intent(this, CallActionActivity::class.java)
                    .setAction(CallActionActivity.ACTION_SHOW_CALL)
                    .putExtra(CallActionActivity.EXTRA_CALL_ID, CallRuntime.currentSession?.callId ?: "pending")
                    .putExtra(CallActionActivity.EXTRA_PENDING_SIM, line.simId)
                    .putExtra(CallActionActivity.EXTRA_PENDING_NUMBER, number))
            } else showToast("无法启动呼叫。")
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
        else {
            pendingDialAfterMicGrant = start
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CALL_MICROPHONE)
        }
    }

    private fun openNewConversation() {
        if (session == null) {
            navigate(navigation.selectTab(HostTab.SETTINGS))
            showToast("请先在设置中完成配对。")
            return
        }
        val line = sims.firstOrNull { it.simId == selectedSimId }
            ?: sims.firstOrNull { it.canSend && gateway?.mappingRevision == it.mappingRevision }
        if (line == null) {
            showToast("当前没有可用的 SIM 卡线路。")
            return
        }
        selectedSimId = line.simId
        navigate(navigation.openConversation(line.simId, "", isNew = true))
    }

    private fun renderConversation(content: LinearLayout, route: HostDestination.Conversation): View {
        if (route.simId != null && selectedSimId != route.simId) selectedSimId = route.simId
        // route.peer is a thread key or a typed recipient; both compare by key.
        val countryIso = smsCountryLookup()(route.simId)
        val routeKey = if (route.peer.isBlank()) "" else SmsThreadIndex.key(route.peer, countryIso)
        val headerText = if (routeKey.isBlank()) "新短信" else SmsPeerKey.display(routeKey, countryIso)
        val bar = topBar(headerText, extra = if (routeKey.isBlank()) null else
            HostUi.avatar(this, contactName(headerText), routeKey, 40).apply {
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginStart = dp(4) }
            })
        // The title keeps its smoke/test anchor.
        (0 until bar.childCount).map(bar::getChildAt).filterIsInstance<MaterialTextView>().lastOrNull()?.apply {
            id = R.id.sms_thread_header
            contactName(headerText)?.let { name -> contentDescription = "$name $headerText" }
        }
        content.addView(bar)

        val recipient = if (route.isNew) {
            val recipientField = inputField("收件人号码", InputType.TYPE_CLASS_PHONE)
            (recipientField.editText as TextInputEditText).apply {
                id = R.id.sms_recipient
                setSingleLine(true)
            }.also { content.addView(recipientField) }
        } else null
        val line = sims.firstOrNull { it.simId == selectedSimId }
        val savedDraft = line?.let { database.loadDraft(it.simId) }
        val transferred = composerTransfer
        val draftMatches = savedDraft != null && (savedDraft.recipient == route.peer ||
            (routeKey.isNotBlank() && SmsThreadIndex.key(savedDraft.recipient, countryIso) == routeKey))
        val savedBody = transferred?.second ?: if (route.isNew || draftMatches) savedDraft?.text.orEmpty() else ""
        bindingDraft = true
        recipient?.setText(transferred?.first ?: savedDraft?.recipient.orEmpty())

        smsConversationList = LinearLayout(this).apply {
            id = R.id.sms_conversation_list
            orientation = LinearLayout.VERTICAL
        }
        val records = if (route.simId == null) database.loadMessages(null) else database.loadMessages(route.simId)
        val peerKeys = HashMap<String, String>()
        val threadRecords = if (routeKey.isBlank()) emptyList() else records
            .filter { peerKeys.getOrPut(it.peerAddress()) { SmsThreadIndex.key(it.peerAddress(), countryIso) } == routeKey }
            .sortedBy { timestampMillis(it.createdAt) }
        if (threadRecords.isEmpty()) {
            smsConversationList?.addView(emptyState(if (route.simId == null && !route.isNew) "此对话的 SIM 卡未知。选择线路后才能发送。" else "开始对话"))
        } else {
            val visibleRecords = threadRecords.takeLast(smsConversationVisibleCount)
            if (threadRecords.size > visibleRecords.size) {
                val earlier = smallButton("较早短信")
                earlier.setOnClickListener { smsConversationVisibleCount += 100; renderCurrentScreen(preserveInput = true) }
                smsConversationList?.addView(earlier)
            }
            var previousDay: String? = null
            visibleRecords.forEach { message ->
                val day = conversationDay(message.createdAt)
                if (day != previousDay) {
                    smsConversationList?.addView(MaterialTextView(this).apply {
                        text = day
                        gravity = Gravity.CENTER
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
                        setTextColor(materialColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                        setPadding(0, dp(16), 0, dp(8))
                    }, fullWidthParams())
                    previousDay = day
                }
                smsConversationList?.addView(conversationMessage(message))
            }
        }
        content.addView(smsConversationList)

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
            setBackgroundColor(materialColor(com.google.android.material.R.attr.colorSurface))
        }
        // Google Messages: SIM switch, a pill-shaped text box and a round send button in one row.
        val simButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonTonalStyle).apply {
            id = R.id.sms_thread_sim_selector
            text = line?.let { "SIM ${it.slotIndex + 1}" } ?: "SIM"
            contentDescription = line?.let { "发送线路 SIM ${it.slotIndex + 1} · ${it.label}，点按切换" } ?: "选择 SIM 卡"
            isAllCaps = false
            setIconResource(R.drawable.ic_ui_sim)
            iconSize = dp(18)
            iconPadding = dp(4)
            cornerRadius = dp(24)
            minWidth = 0
            minimumWidth = 0
            insetTop = 0
            insetBottom = 0
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { chooseConversationSim(route) }
        }
        simButton.isEnabled = sims.isNotEmpty()
        val inputRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        inputRow.addView(simButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)).apply { marginEnd = dp(8) })
        val bodyInput = TextInputEditText(this).apply {
            hint = "短信"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            background = HostUi.pill(this@MainActivity)
            minHeight = dp(52)
            setPadding(dp(20), dp(12), dp(20), dp(12))
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            setTextColor(materialColor(com.google.android.material.R.attr.colorOnSurface))
            setHintTextColor(materialColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        }
        bodyInput.id = R.id.sms_body
        bodyInput.minLines = 1
        bodyInput.maxLines = 4
        bodyInput.gravity = Gravity.TOP or Gravity.START
        bodyInput.setText(savedBody)
        composerTransfer = null
        composeRecipient = recipient
        composeBody = bodyInput
        draftSimId = selectedSimId
        bindingDraft = false
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSendButtonState()
                saveVisibleDraft()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        }
        recipient?.addTextChangedListener(watcher)
        bodyInput.addTextChangedListener(watcher)
        inputRow.addView(bodyInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val send = MaterialButton(this).apply {
            id = R.id.sms_thread_send
            contentDescription = "发送"
            setIconResource(R.drawable.ic_ui_send)
            iconSize = dp(22)
            iconPadding = 0
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            cornerRadius = dp(26)
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setOnClickListener {
                val selected = sims.firstOrNull { it.simId == selectedSimId }
                if (selected == null) showToast("请选择 SIM 卡。")
                else confirmSend(selected, recipient?.text?.toString() ?: route.peer, bodyInput.text?.toString().orEmpty())
            }
        }
        smsThreadSendButton = send
        inputRow.addView(send, LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginStart = dp(8) })
        composer.addView(inputRow)
        updateSendButtonState()
        return composer
    }

    private fun conversationDay(value: String): String = runCatching {
        val millis = timestampMillis(value)
        if (android.text.format.DateUtils.isToday(millis)) "今天"
        else java.text.SimpleDateFormat("M月d日 EEEE", java.util.Locale.getDefault()).format(java.util.Date(millis))
    }.getOrDefault(value.take(10))

    /** A chat bubble: incoming on the left in a neutral tone, sent on the right in the primary color. */
    private fun conversationMessage(message: SmsRecord): View {
        val inbound = message.direction == "inbound"
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (inbound) Gravity.START else Gravity.END
            layoutParams = fullWidthParams(top = 2, bottom = 2)
        }
        val large = dp(20).toFloat()
        val small = dp(6).toFloat()
        val radii = if (inbound) floatArrayOf(large, large, large, large, large, large, small, small)
        else floatArrayOf(large, large, large, large, small, small, large, large)
        val bubbleText = MaterialTextView(this).apply {
            text = message.text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            setTextColor(materialColor(if (inbound) com.google.android.material.R.attr.colorOnSurface else com.google.android.material.R.attr.colorOnPrimary))
            setLinkTextColor(materialColor(if (inbound) androidx.appcompat.R.attr.colorPrimary else com.google.android.material.R.attr.colorOnPrimary))
            setTextIsSelectable(true)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                cornerRadii = radii
                setColor(materialColor(if (inbound) com.google.android.material.R.attr.colorSurfaceContainerHigh else androidx.appcompat.R.attr.colorPrimary))
            }
            maxWidth = (resources.displayMetrics.widthPixels * 0.75f).toInt().coerceAtMost(dp(520))
        }
        column.addView(bubbleText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            if (inbound) marginEnd = dp(48) else marginStart = dp(48)
        })
        val time = runCatching {
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestampMillis(message.createdAt)))
        }.getOrDefault("")
        val state = if (inbound) time else "$time · ${SmsStatus.display(message.status)}"
        column.addView(MaterialTextView(this).apply {
            text = state
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall)
            setTextColor(materialColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(dp(8), dp(2), dp(8), dp(4))
        })
        if (message.taskKey != null && (message.status == SmsStatus.UNKNOWN || message.status == SmsStatus.SUBMITTING)) {
            val action = if (retryAction(message.taskKey, message.serverId) == RetryAction.RETRY_SAME_KEY) "重试发送" else "查询状态"
            val retry = smallButton(action).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            retry.setOnClickListener { retryOrCheck(message) }
            column.addView(retry)
        }
        return column
    }

    private fun chooseConversationSim(route: HostDestination.Conversation) {
        if (sims.isEmpty()) return
        val labels = sims.sortedBy { it.slotIndex }.map { "SIM ${it.slotIndex + 1} · ${it.label}" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("选择发送线路")
            .setItems(labels) { _, which ->
                val line = sims.sortedBy { it.slotIndex }.getOrNull(which) ?: return@setItems
                composerTransfer = (composeRecipient?.text?.toString() ?: route.peer) to composeBody?.text?.toString().orEmpty()
                saveVisibleDraft()
                selectedSimId = line.simId
                navigate(navigation.openConversation(line.simId, route.peer, route.isNew))
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateSendButtonState() {
        val line = sims.firstOrNull { it.simId == selectedSimId }
        smsThreadSendButton?.isEnabled = line?.canSend == true && gateway?.mappingRevision == line.mappingRevision
    }

    private fun resetCallAvailability() {
        callAvailabilityGeneration += 1L
        callAvailabilityLoading = false
        callAvailabilityCheckedAt = 0L
        callServerAvailable = null
        callServerReason = null
    }

    private fun refreshCallAvailability(force: Boolean = false) {
        if (session == null || callAvailabilityLoading) return
        if (!force && System.currentTimeMillis() - callAvailabilityCheckedAt < 60_000L) return
        callAvailabilityLoading = true
        val generation = callAvailabilityGeneration
        renderCurrentScreen()
        val expected = session ?: return
        val expectedDatabase = database
        val expectedApi = ApiClient(expected.apiBaseUrl, sessionStore)
        executor.execute {
            val result = runCatching {
                if (sessionStore.read()?.sameSessionInstance(expected) != true || database !== expectedDatabase) throw SessionChanged()
                if (expectedApi.rtcControlAvailable()) true to null
                else false to "服务器版本过旧，不支持 WebRTC 通话。请升级服务端。"
            }
            mainHandler.post {
                if (generation != callAvailabilityGeneration) return@post
                callAvailabilityLoading = false
                val latest = sessionStore.read()
                val currentStillExpected = session?.sameSessionInstance(expected) == true && database === expectedDatabase &&
                    (latest == null || latest.sameSessionInstance(expected))
                val revoked = result.exceptionOrNull() is SessionNeedsPairing && latest == null && currentStillExpected
                if (!currentStillExpected && !revoked) return@post
                callAvailabilityCheckedAt = System.currentTimeMillis()
                result.onSuccess {
                    callServerAvailable = it.first
                    callServerReason = it.second
                }.onFailure { failure ->
                    callServerAvailable = false
                    callServerReason = when (failure) {
                        is SessionNeedsPairing -> "配对会话已失效，请重新配对。"
                        is SessionChanged -> "配对账户已变化，请刷新。"
                        is ApiFailure -> "服务器暂不可用（${failure.code}）。"
                        else -> "无法读取服务器语音可用状态。"
                    }
                }
                if (revoked) {
                    session = null
                    switchDatabase(null)
                    showPairing("配对已失效，请重新配对。")
                    return@post
                }
                ensurePairedRuntimeStarted()
                updateDialButtonState()
                renderCurrentScreen()
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
                    val speakerOn = CallRuntime.selectedAudioEndpoint(call.callId)?.let { it == CallAudioEndpoint.SPEAKER } ?: call.speaker
                    val route = smallButton(if (speakerOn) "切换到听筒" else "切换到扬声器")
                    route.setOnClickListener {
                        CallRuntime.toggleSpeaker(this, call.callId, !speakerOn)
                        renderCallPanel()
                    }
                    panel.addView(route)
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
                "${if (call.phase == CallPhase.FAILED) "通话失败" else "通话已结束"}${(call.failure ?: call.endNotice)?.let { "：$it" }.orEmpty()}"
            ))
            val dismiss = smallButton("关闭通话记录")
            dismiss.setOnClickListener { CallRuntime.clearTerminal(call.callId); renderCallPanel() }
            panel.addView(dismiss)
        }

        val line = sims.firstOrNull { it.simId == selectedSimId }
        val currentGateway = gateway
        val gatewayReady = currentGateway?.online == true
        val lineReady = line != null && line.canSend && currentGateway != null && line.mappingRevision == currentGateway.mappingRevision
        val nativeReady = localCallEngineAvailable()
        val microphoneReady = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val notificationReady = CallNotificationManager.notificationsEnabled(this)
        val serverReady = callServerAvailable == true
        val canDial = serverReady && nativeReady && gatewayReady && lineReady && microphoneReady

        val serverText = when {
            callAvailabilityLoading -> "正在检查服务器呼叫服务…"
            callServerAvailable == true -> "服务器呼叫控制可用。"
            callServerAvailable == false -> callServerReason ?: "服务器呼叫服务不可用。"
            else -> "服务器呼叫状态尚未检查。"
        }
        panel.addView(body(serverText))
        panel.addView(body(when {
            !nativeReady -> "此安装中没有可用的 WebRTC 原生引擎。"
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
                showToast("无法启动前台来电接收。")
            }
        }
        panel.addView(listen)
        panel.addView(body("开启后使用系统可见的专用信令前台服务保持呼叫控制和 HTTPS 来电检查；不启用麦克风。来电匹配服务器状态后才交给 Telecom，用户接听后才请求麦克风和通话前台服务。网络中断、强行停止或系统资源限制仍可能延迟来电。"))

        val refresh = smallButton(if (callAvailabilityLoading) "正在检查…" else "刷新通话可用状态")
        refresh.isEnabled = !callAvailabilityLoading
        refresh.setOnClickListener { refreshCallAvailability(force = true) }
        panel.addView(refresh)
    }

    private fun confirmRemoteDial(line: SimLine, currentGateway: GatewaySnapshot, recipient: String) {
        beginDial(line, recipient)
    }

    private fun validDialAddress(value: String): Boolean =
        value.length in 2..40 && value.firstOrNull()?.let { it.isDigit() || it == '+' || it == '*' || it == '#' } == true &&
            value.all { it.isDigit() || it in "+*#()-. " }

    private fun localCallEngineAvailable(): Boolean = runCatching { CallEngineFactory.isAvailable(this) }.getOrDefault(false)

    private fun callPhaseLabel(phase: CallPhase): String = when (phase) {
        CallPhase.AUTHORIZING_OUTBOUND -> "正在申请呼叫授权"
        CallPhase.REGISTERING -> "正在连接呼叫控制"
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
            addAction(NodeMonitor.ACTION_NODE_CHANGED)
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
            showCurrentSession("配对账户已变化，已切换到该账户自己的缓存。")
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
                val countryIso = smsCountryLookup()(simId)
                records.groupBy { SmsThreadIndex.key(it.peerAddress(), countryIso) }.forEach { (key, messages) ->
                    list.addView(sectionTitle("对话 · ${SmsPeerKey.display(key, countryIso)}"))
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
        val countryIso = smsCountryLookup()(message.simId)
        val address = message.peerAddress().takeIf { it.isNotBlank() }
            ?.let { SmsPeerKey.display(SmsPeerKey.key(it, countryIso), countryIso) } ?: "Unknown sender"
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
        val route = navigation.destination as? HostDestination.Conversation ?: return
        val currentRecipient = composeRecipient?.text?.toString() ?: route.peer
        val currentText = composeBody?.text?.toString() ?: return
        if (navigation.tab != HostTab.MESSAGES || route.simId != line.simId || selectedSimId != line.simId ||
            currentRecipient.trim() != recipientValue.trim() || currentText != textValue
        ) return
        val recipient = recipientValue.trim()
        val text = textValue
        val currentSession = session
        val currentGateway = gateway
        if (currentSession == null) {
            showToast("请先在设置中完成配对。")
            return
        }
        if (line.simId != selectedSimId || !line.canSend || currentGateway?.mappingRevision != line.mappingRevision) {
            showToast("所选 SIM 卡状态已变化，请重新选择线路。")
            return
        }
        if (recipient.isBlank() || text.isBlank()) { showToast("请输入收件人和短信内容。"); return }
        if (text.toByteArray(Charsets.UTF_8).size > 16_384) {
            showToast("短信内容不能超过 16 KiB。")
            return
        }
        if (currentGateway == null) {
            showToast("网关状态尚未载入。")
            return
        }
        submitNewTask(line, currentGateway, recipient, text)
    }

    private fun submitNewTask(line: SimLine, currentGateway: GatewaySnapshot, recipient: String, text: String) {
        val expectedSession = session ?: return showToast("请先完成配对。")
        val expectedDatabase = database
        val expectedApi = SmsSubmissionClients.capture(ApiClient(expectedSession.apiBaseUrl, sessionStore))
        if (sessionStore.read()?.sameSessionInstance(expectedSession) != true || database !== expectedDatabase) {
            showToast("配对账户已变化，请重试。")
            return
        }
        val previousDraft = runCatching { expectedDatabase.loadDraft(line.simId) }.getOrNull()
        val task = OutboundTask(
            localId = UUID.randomUUID().toString(),
            idempotencyKey = newTaskKey(),
            gatewayId = currentGateway.gatewayId,
            simId = line.simId,
            mappingRevision = line.mappingRevision,
            to = recipient,
            text = text,
            createdAt = Instant.now().toString(),
            // Every node of a profile supports the mesh extension (checked when
            // it was added), so this phone mints the global message ID.
            messageId = if (expectedSession.profileId != null) UUID.randomUUID().toString() else null
        )
        try {
            expectedDatabase.insertOutbound(task)
        } catch (_: Exception) {
            statusMessage = "无法保存短信任务，尚未提交。"
            showDashboard()
            return
        }
        val retryEnvelope = runCatching { expectedDatabase.loadTask(task.localId)?.toRetryEnvelope() }.getOrNull()
        if (retryEnvelope == null) {
            expectedDatabase.updateTask(task.localId, SmsStatus.FAILED)
            statusMessage = "无法恢复已保存的短信任务，尚未提交。"
            showDashboard()
            return
        }
        if (previousDraft?.recipient == recipient && previousDraft.text == text) {
            runCatching { expectedDatabase.clearDraft(line.simId) }
        }
        selectedSimId = line.simId
        navigation = navigation.openConversation(line.simId, SmsThreadIndex.key(recipient, smsCountryLookup()(line.simId)), isNew = false)
        composerTransfer = null
        statusMessage = "短信已加入发送队列。"
        renderCurrentScreen(preserveInput = false)
        executor.execute {
            try {
                if (sessionStore.read()?.sameSessionInstance(expectedSession) != true || database !== expectedDatabase) throw SessionChanged()
                val accepted = NodeMonitor.holdingNode { expectedApi.createMessage(retryEnvelope) }
                expectedDatabase.updateTask(task.localId, accepted.status, accepted)
                mainHandler.post {
                    if (isDestroyed || sessionStore.read()?.sameSessionInstance(expectedSession) != true || database !== expectedDatabase) return@post
                    statusMessage = "短信已提交。"
                    showDashboard()
                }
            } catch (failure: Exception) {
                val definitelyRejected = failure is SessionNeedsPairing ||
                    (failure is ApiFailure && failure.httpStatus in 400..499 && !failure.retryable)
                val state = if (definitelyRejected) SmsStatus.FAILED else SmsStatus.UNKNOWN
                runCatching { expectedDatabase.updateTask(task.localId, state) }
                mainHandler.post {
                    if (isDestroyed || handleRetrySessionFailure(expectedSession, expectedDatabase, failure)) return@post
                    if (!retryContextIsCurrent(expectedSession, expectedDatabase)) return@post
                    statusMessage = if (definitelyRejected) "短信未被服务器接受。" else "发送结果尚未确认；可在此条短信上查询状态或安全重试。"
                    showDashboard()
                }
            }
        }
    }

    private fun retryOrCheck(record: SmsRecord) {
        val expectedSession = session ?: return showToast("请先完成配对。")
        val expectedDatabase = database
        if (!retryContextIsCurrent(expectedSession, expectedDatabase)) {
            showToast("配对账户已变化，请重试。")
            return
        }
        val verified = loadVerifiedRetryTask(expectedDatabase, record)
        if (verified == null) {
            showToast("这条短信与当前账户保存的任务不一致，无法查询或重试。")
            return
        }
        val (storedRecord, originalEnvelope) = verified
        when (retryAction(storedRecord.taskKey, storedRecord.serverId)) {
            RetryAction.QUERY_EXISTING_MESSAGE -> {
                val serverId = storedRecord.serverId ?: return
                val expectedApi = SmsSubmissionClients.capture(ApiClient(expectedSession.apiBaseUrl, sessionStore))
                statusMessage = "正在查询短信状态…"
                showDashboard()
                executor.execute {
                    try {
                        if (!retryContextIsCurrent(expectedSession, expectedDatabase)) throw SessionChanged()
                        if (loadVerifiedRetryTask(expectedDatabase, record) == null) {
                            mainHandler.post { showToast("这条短信任务已变化，请刷新后重试。") }
                            return@execute
                        }
                        val detail = expectedApi.getMessage(serverId)
                        if (!retryContextIsCurrent(expectedSession, expectedDatabase)) throw SessionChanged()
                        if (loadVerifiedRetryTask(expectedDatabase, record) == null) {
                            mainHandler.post { showToast("这条短信任务已变化，请刷新后重试。") }
                            return@execute
                        }
                        if (detail != null) expectedDatabase.upsertRemoteMessage(detail)
                        mainHandler.post {
                            if (!retryContextIsCurrent(expectedSession, expectedDatabase)) {
                                refreshRetryAccountIfChanged(expectedSession, expectedDatabase)
                                return@post
                            }
                            statusMessage = if (detail == null) "服务器未返回这条短信。" else "短信状态：${SmsStatus.display(detail.status)}。"
                            showDashboard()
                        }
                    } catch (failure: Exception) {
                        mainHandler.post {
                            if (handleRetrySessionFailure(expectedSession, expectedDatabase, failure)) return@post
                            if (!retryContextIsCurrent(expectedSession, expectedDatabase)) return@post
                            statusMessage = apiFailureText(failure)
                            showDashboard()
                        }
                    }
                }
            }
            RetryAction.RETRY_SAME_KEY -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle("重试同一条短信任务？")
                    .setMessage("将在原 SIM 卡线路上继续这条已保存的提交。如果首次请求已到达服务器，服务器会返回同一条任务，不会重复创建。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("重试同一任务") { _, _ ->
                        if (!retryContextIsCurrent(expectedSession, expectedDatabase)) {
                            refreshRetryAccountIfChanged(expectedSession, expectedDatabase)
                            showToast("配对账户已变化，未提交重试。")
                            return@setPositiveButton
                        }
                        val current = loadVerifiedRetryTask(expectedDatabase, record)
                        if (current == null || current.second != originalEnvelope ||
                            retryAction(current.first.taskKey, current.first.serverId) != RetryAction.RETRY_SAME_KEY
                        ) {
                            showToast("保存的任务已变化，未提交重试。")
                            return@setPositiveButton
                        }
                        submitExistingTaskForContext(record, current.second, expectedSession, expectedDatabase)
                    }
                    .show()
            }
            RetryAction.NONE -> showToast("这条短信没有可安全执行的重试操作。")
        }
    }

    private fun retryEnvelope(record: SmsRecord): RetryEnvelope = record.toRetryEnvelope()

    private fun loadVerifiedRetryTask(expectedDatabase: ClientDatabase, original: SmsRecord): Pair<SmsRecord, RetryEnvelope>? {
        val stored = runCatching { expectedDatabase.loadTask(original.localId) }.getOrNull() ?: return null
        val originalEnvelope = runCatching { retryEnvelope(original) }.getOrNull() ?: return null
        val storedEnvelope = runCatching { retryEnvelope(stored) }.getOrNull() ?: return null
        val sameBinding = original.localId == stored.localId &&
            original.serverId == stored.serverId && original.commandId == stored.commandId &&
            original.simId == stored.simId && original.mappingRevision == stored.mappingRevision &&
            original.direction == "outbound" && stored.direction == "outbound" &&
            original.to == stored.to && original.text == stored.text && original.createdAt == stored.createdAt &&
            original.taskKey == stored.taskKey && original.gatewayId == stored.gatewayId &&
            original.idempotencyBody == stored.idempotencyBody &&
            originalEnvelope.idempotencyKey == storedEnvelope.idempotencyKey &&
            originalEnvelope.requestBody() == storedEnvelope.requestBody()
        if (!sameBinding) return null
        return stored to storedEnvelope
    }

    private fun retryContextIsCurrent(expectedSession: HostSession, expectedDatabase: ClientDatabase): Boolean =
        !isDestroyed && sessionStore.read()?.sameSessionInstance(expectedSession) == true &&
            session?.sameSessionInstance(expectedSession) == true && database === expectedDatabase

    private fun refreshRetryAccountIfChanged(expectedSession: HostSession, expectedDatabase: ClientDatabase): Boolean {
        val latest = sessionStore.read()
        val activityStillOwnsOldAccount = !isDestroyed && session?.sameSessionInstance(expectedSession) == true && database === expectedDatabase
        if (activityStillOwnsOldAccount && latest?.sameSessionInstance(expectedSession) != true) {
            showCurrentSession("配对账户已变化。")
            return true
        }
        return !retryContextIsCurrent(expectedSession, expectedDatabase)
    }

    private fun handleRetrySessionFailure(
        expectedSession: HostSession,
        expectedDatabase: ClientDatabase,
        failure: Exception,
    ): Boolean {
        val latest = sessionStore.read()
        val activityStillOwnsOldAccount = !isDestroyed && session?.sameSessionInstance(expectedSession) == true && database === expectedDatabase
        if (failure is SessionNeedsPairing && latest == null && activityStillOwnsOldAccount) {
            session = null
            switchDatabase(null)
            showPairing("配对已失效，请重新配对。")
            return true
        }
        return refreshRetryAccountIfChanged(expectedSession, expectedDatabase)
    }

    private fun submitExistingTask(record: SmsRecord, envelope: RetryEnvelope) {
        val expectedSession = session ?: return showToast("请先完成配对。")
        submitExistingTaskForContext(record, envelope, expectedSession, database)
    }

    private fun submitExistingTaskForContext(
        record: SmsRecord,
        envelope: RetryEnvelope,
        expectedSession: HostSession,
        expectedDatabase: ClientDatabase,
    ) {
        if (!retryContextIsCurrent(expectedSession, expectedDatabase)) {
            refreshRetryAccountIfChanged(expectedSession, expectedDatabase)
            showToast("配对账户已变化，请重试。")
            return
        }
        val verified = loadVerifiedRetryTask(expectedDatabase, record)
        if (verified == null || verified.second != envelope ||
            retryAction(verified.first.taskKey, verified.first.serverId) != RetryAction.RETRY_SAME_KEY
        ) {
            showToast("保存的任务与原提交不一致，无法安全重试。")
            return
        }
        val (task, taskEnvelope) = moveTaskToActiveNode(expectedDatabase, verified.first, verified.second) ?: run {
            showToast("当前节点上这张 SIM 卡不可用，暂时无法重试。")
            return
        }
        submitVerifiedTask(task, taskEnvelope, expectedSession, expectedDatabase)
    }

    /**
     * A mesh task saved for another node's gateway is re-addressed to the
     * active node before retrying: same global message ID and SIM, this
     * node's gateway and revision, a new idempotency key. The old phone runs
     * the message ID at most once, whichever node delivers it.
     */
    private fun moveTaskToActiveNode(
        expectedDatabase: ClientDatabase,
        record: SmsRecord,
        envelope: RetryEnvelope
    ): Pair<SmsRecord, RetryEnvelope>? {
        val activeGateway = gateway ?: return null
        if (envelope.gatewayId == activeGateway.gatewayId || envelope.messageId == null) return record to envelope
        val line = sims.firstOrNull {
            it.simId == envelope.simId && it.canSend && it.mappingRevision == activeGateway.mappingRevision
        } ?: return null
        val moved = envelope.forNode(activeGateway.gatewayId, line.mappingRevision)
        if (!expectedDatabase.moveTask(record.localId, envelope.idempotencyKey, moved)) return null
        val stored = runCatching { expectedDatabase.loadTask(record.localId) }.getOrNull() ?: return null
        val storedEnvelope = runCatching { stored.toRetryEnvelope() }.getOrNull() ?: return null
        return stored to storedEnvelope
    }

    private fun submitVerifiedTask(
        record: SmsRecord,
        envelope: RetryEnvelope,
        expectedSession: HostSession,
        expectedDatabase: ClientDatabase,
    ) {
        val expectedApi = SmsSubmissionClients.capture(ApiClient(expectedSession.apiBaseUrl, sessionStore))
        expectedDatabase.updateTask(record.localId, SmsStatus.SUBMITTING)
        statusMessage = "正在安全重试原短信任务…"
        showDashboard()
        executor.execute {
            try {
                if (!retryContextIsCurrent(expectedSession, expectedDatabase)) throw SessionChanged()
                val current = loadVerifiedRetryTask(expectedDatabase, record)
                if (current == null || current.second != envelope ||
                    retryAction(current.first.taskKey, current.first.serverId) != RetryAction.RETRY_SAME_KEY
                ) {
                    mainHandler.post { showToast("保存的任务已变化，未提交重试。") }
                    return@execute
                }
                val accepted = NodeMonitor.holdingNode { expectedApi.createMessage(current.second) }
                if (!retryContextIsCurrent(expectedSession, expectedDatabase)) throw SessionChanged()
                val afterResponse = loadVerifiedRetryTask(expectedDatabase, record)
                if (afterResponse == null || afterResponse.second != envelope) return@execute
                expectedDatabase.updateTask(record.localId, accepted.status, accepted)
                mainHandler.post {
                    if (!retryContextIsCurrent(expectedSession, expectedDatabase)) {
                        refreshRetryAccountIfChanged(expectedSession, expectedDatabase)
                        return@post
                    }
                    statusMessage = "已确认原短信任务。"
                    showDashboard()
                }
            } catch (failure: Exception) {
                if (runCatching { loadVerifiedRetryTask(expectedDatabase, record) }.getOrNull()?.second == envelope) {
                    runCatching { expectedDatabase.updateTask(record.localId, SmsStatus.UNKNOWN) }
                }
                mainHandler.post {
                    if (handleRetrySessionFailure(expectedSession, expectedDatabase, failure)) return@post
                    if (!retryContextIsCurrent(expectedSession, expectedDatabase)) return@post
                    statusMessage = "发送结果仍未确认。任务和原SIM已保留，可稍后安全重试。"
                    showDashboard()
                }
            }
        }
    }

    private fun syncFromServer(showProgress: Boolean) {
        if (session == null || syncing) return
        val expectedSession = session ?: return
        val expectedDatabase = database
        syncing = true
        if (showProgress) {
            statusMessage = REFRESHING_STATUS
            showDashboard()
        }
        executor.execute {
            // Set once the old phone's status and SIMs came back: a later failure
            // (SMS, call history, hosts) does not make the server unreachable.
            var fetchedGateway: Pair<GatewaySnapshot, List<SimLine>>? = null
            try {
                if (sessionStore.read()?.sameSessionInstance(expectedSession) != true) throw SessionChanged()
                val api = ApiClient(expectedSession.apiBaseUrl, sessionStore)
                val oldRevision = expectedDatabase.loadGateway()?.mappingRevision
                val remoteGateways = api.listGateways()
                val current = remoteGateways.firstOrNull()
                    ?: throw NoPairedGateway()
                val (_, remoteSims) = api.listSims(current.gatewayId)
                fetchedGateway = current to remoteSims
                val mappingChanged = oldRevision != null && oldRevision != current.mappingRevision
                expectedDatabase.saveGateway(current)
                expectedDatabase.replaceSims(remoteSims)
                syncSms(api)
                val remoteCallPage = runCatching { api.listCallHistoryPage(cursor = null, limit = 100) }.getOrNull()
                val mergedCallSnapshot = remoteCallPage?.let { page ->
                    val prior = expectedDatabase.syncStateValue(SYNC_KEY_CALL_HISTORY)?.let(CallHistoryCache::parse)
                    val merged = CallHistoryCache.merge(prior?.first.orEmpty(), page.first)
                    val stale = CallHistoryCache.staleLiveCalls(merged, page.first.map { it.callId }.toSet(), System.currentTimeMillis())
                    val refreshed = if (stale.isEmpty()) emptyList() else CallHistoryCache.refreshStale(stale, callLookups(api))
                    CallHistoryCache.merge(merged, refreshed) to page.second
                }
                if (mergedCallSnapshot != null && sessionStore.read()?.sameSessionInstance(expectedSession) == true) {
                    expectedDatabase.saveSyncStateValue(SYNC_KEY_CALL_HISTORY, CallHistoryCache.encode(mergedCallSnapshot.first, mergedCallSnapshot.second))
                }
                val pairedHostsRefresh = refreshPairedHosts(api)
                if (sessionStore.read()?.sameSessionInstance(expectedSession) != true) throw SessionChanged()
                when (pairedHostsRefresh) {
                    is PairedHostsRefresh.Loaded -> expectedDatabase.saveSyncStateValue(
                        SYNC_KEY_PAIRED_HOSTS,
                        encodePairedHosts(pairedHostsRefresh.items)
                    )
                    PairedHostsRefresh.Unsupported -> expectedDatabase.saveSyncStateValue(
                        SYNC_KEY_PAIRED_HOSTS,
                        PAIRED_HOSTS_UNSUPPORTED
                    )
                    PairedHostsRefresh.TemporarilyUnavailable -> Unit
                }
                mainHandler.post {
                    syncing = false
                    if (sessionStore.read()?.sameSessionInstance(expectedSession) != true || database !== expectedDatabase) {
                        showCurrentSession("配对账户已变化，已切换到该账户自己的缓存。")
                        return@post
                    }
                    session = sessionStore.read()
                    serverLink = ServerLink.Connected
                    gateway = current
                    sims = remoteSims
                    if (remoteCallPage != null) {
                        callHistory = CallHistoryCache.merge(callHistory, mergedCallSnapshot?.first ?: remoteCallPage.first)
                        callHistoryCursor = mergedCallSnapshot?.second ?: remoteCallPage.second
                    }
                    when (val hostRefresh = pairedHostsRefresh) {
                        is PairedHostsRefresh.Loaded -> {
                            pairedHosts = hostRefresh.items
                            pairedHostsAvailable = true
                            pairedHostsRefreshIssue = null
                        }
                        PairedHostsRefresh.Unsupported -> {
                            pairedHosts = emptyList()
                            pairedHostsAvailable = false
                            pairedHostsRefreshIssue = null
                        }
                        PairedHostsRefresh.TemporarilyUnavailable -> {
                            pairedHostsRefreshIssue = if (pairedHostsAvailable == true) {
                                "主机列表暂时无法更新，当前显示上次结果。点击刷新重试。"
                            } else {
                                "主机列表暂时无法载入。点击刷新重试。"
                            }
                        }
                    }
                    if (mappingChanged) {
                        selectedSimId = null
                        statusMessage = "旧手机的 SIM 绑定已变化，发送前请重新选择 SIM。"
                    }
                    if (statusMessage == REFRESHING_STATUS || statusMessage.startsWith(OFFLINE_STATUS_PREFIX)) statusMessage = ""
                    showDashboard()
                }
            } catch (failure: Exception) {
                mainHandler.post {
                    syncing = false
                    if (failure is SessionChanged) {
                        showCurrentSession("配对账户已变化，已切换到该账户自己的缓存。")
                    } else if (failure is SessionNeedsPairing || sessionStore.read() == null) {
                        session = null
                        switchDatabase(null)
                        showPairing("配对会话已过期或被撤销，请重新配对此手机。")
                    } else if (failure is NoPairedGateway) {
                        serverLink = ServerLink.Connected
                        statusMessage = failure.message.orEmpty()
                        refreshGatewayStatusCard()
                        updateDialButtonState()
                    } else if (fetchedGateway != null && database === expectedDatabase &&
                        sessionStore.read()?.sameSessionInstance(expectedSession) == true
                    ) {
                        // The server answered for the old phone; only a later part of the sync failed.
                        serverLink = ServerLink.Connected
                        gateway = fetchedGateway?.first
                        sims = fetchedGateway?.second.orEmpty()
                        statusMessage = "部分数据同步失败，稍后会自动重试。${apiFailureText(failure)}"
                        refreshGatewayStatusCard()
                        updateDialButtonState()
                    } else {
                        serverLink = ServerLink.Unreachable(serverUnreachableReason(failure))
                        statusMessage = "$OFFLINE_STATUS_PREFIX，当前显示缓存的旧手机、SIM 和短信。${apiFailureText(failure)}"
                        // Cached data did not change; rebuilding the whole screen every
                        // failed 30 s sync would flicker and drop taps. Only re-age the status card.
                        refreshGatewayStatusCard()
                        updateDialButtonState()
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

    private fun refreshPairedHosts(api: ApiClient): PairedHostsRefresh = try {
        PairedHostsRefresh.Loaded(api.listPairedHosts())
    } catch (failure: ApiFailure) {
        when (failure.httpStatus) {
            404 -> PairedHostsRefresh.Unsupported
            401 -> throw failure
            else -> PairedHostsRefresh.TemporarilyUnavailable
        }
    } catch (failure: SessionChanged) {
        throw failure
    } catch (failure: SessionNeedsPairing) {
        throw failure
    } catch (_: Exception) {
        PairedHostsRefresh.TemporarilyUnavailable
    }

    private fun confirmUnpair() {
        MaterialAlertDialogBuilder(this)
            .setTitle("解除此手机配对？")
            .setMessage("服务器将撤销此客户端的登录凭据。确认后会关闭后台接收，并清除此手机同步的短信和网关缓存。请先导出需要保留的短信；已导入本机归档和外部备份会保留。")
            .setNegativeButton("取消", null)
            .setPositiveButton("解除配对") { _, _ ->
                executor.execute {
                    try {
                        // Other nodes first, so none can take over after this one is revoked.
                        runCatching { HostNodes.forgetAll(applicationContext) }
                        client().revoke()
                        runCatching { HostBackgroundRuntime.setEnabled(applicationContext, false) }
                        database.clearAll()
                        mainHandler.post {
                            session = null
                            switchDatabase(null)
                            pairingForm.clear()
                            gateway = null
                            sims = emptyList()
                            pairedHosts = emptyList()
                            pairedHostsAvailable = null
                            pairedHostsRefreshIssue = null
                            selectedSimId = null
                            statusMessage = ""
                            showPairing()
                        }
                    } catch (failure: Exception) {
                        mainHandler.post {
                            if (failure is SessionChanged) {
                                showCurrentSession("解除配对期间账户已变化。")
                            } else {
                                statusMessage = "未能确认服务器已撤销此手机，凭据和缓存已保留，可稍后重试。${apiFailureText(failure)}"
                                showDashboard()
                            }
                        }
                    }
                }
            }
            .show()
    }

    private fun saveVisibleDraft() {
        if (bindingDraft || composeBody == null) return
        val simId = draftSimId ?: return
        val recipient = composeRecipient?.text?.toString()
            ?: (navigation.destination as? HostDestination.Conversation)?.peer
            ?: return
        val text = composeBody?.text?.toString().orEmpty()
        runCatching { database.saveDraft(SmsDraft(simId, recipient, text)) }
    }

    private fun client(): ApiClient = ApiClient(sessionStore.read()?.apiBaseUrl ?: preferences.apiBaseUrl, sessionStore)

    private fun showCurrentSession(message: String) {
        val previous = session
        val latest = sessionStore.read() ?: runCatching { HostNodes.recoverActive(this) }.getOrNull()
        if (previous != null && latest != null && previous.sameProfile(latest)) {
            // Another server node of the same profile: same local data, so
            // keep the screen and only rebind to the new node.
            session = latest
            switchDatabase(latest)
            statusMessage = "已切换到节点 ${HostNodes.hostLabel(latest.apiBaseUrl)}。"
            showDashboard()
            ensurePairedRuntimeStarted()
            refreshCallAvailability(force = true)
            syncFromServer(showProgress = false)
            return
        }
        session = latest
        navigation = HostNavigationState()
        composerTransfer = null
        selectedSimId = null
        switchDatabase(session)
        statusMessage = message
        if (session == null) showPairing(message) else {
            showDashboard()
            refreshCallAvailability(force = true)
        }
    }

    private fun switchDatabase(ownerSession: HostSession?) {
        val nextName = ownerSession?.databaseName() ?: "host-unpaired.db"
        val nextScope = ownerSession?.nodeScope.orEmpty()
        if (databaseName == nextName && databaseScope == nextScope && ::database.isInitialized) return
        // Availability belongs to this node and account. A failed retired
        // node's late response must not disable the healthy replacement.
        resetCallAvailability()
        if (databaseName.isNotBlank() && databaseName != nextName) {
            CallRuntime.disableForegroundListening(this)
        }
        CallRuntime.restoreForegroundPreference(this, ownerSession)
        if (::database.isInitialized) database.close()
        databaseName = nextName
        databaseScope = nextScope
        database = ClientDatabase(this, nextName, nextScope)
        database.markInterruptedSubmissionsUnknown()
        serverLink = ServerLink.Checking
        gateway = database.loadGateway()
        sims = database.loadSims()
        loadPairedHostsFromCache()
        loadCallHistoryFromCache()
        if (selectedSimId != null && sims.none { it.simId == selectedSimId }) selectedSimId = null
    }

    private fun loadPairedHostsFromCache() {
        val saved = database.syncStateValue(SYNC_KEY_PAIRED_HOSTS)
        when {
            saved == null -> {
                pairedHosts = emptyList()
                pairedHostsAvailable = null
                pairedHostsRefreshIssue = null
            }
            saved == PAIRED_HOSTS_UNSUPPORTED -> {
                pairedHosts = emptyList()
                pairedHostsAvailable = false
                pairedHostsRefreshIssue = null
            }
            else -> runCatching {
                parsePairedHosts(JSONObject().put("items", JSONArray(saved)))
            }.onSuccess { hosts ->
                pairedHosts = hosts
                pairedHostsAvailable = true
                pairedHostsRefreshIssue = "显示上次读取的主机列表；刷新后更新。"
            }.onFailure {
                pairedHosts = emptyList()
                pairedHostsAvailable = null
                pairedHostsRefreshIssue = null
            }
        }
    }

    private fun encodePairedHosts(hosts: List<PairedHost>): String = JSONArray().apply {
        hosts.forEach { host ->
            put(JSONObject()
                .put("id", host.id)
                .put("name", host.name)
                .put("platform", host.platform)
                .put("state", host.state)
                .put("is_self", host.isSelf))
        }
    }.toString()

    /** Reads one call from the active node, then from every other paired node. */
    private fun callLookups(active: ApiClient): List<(String) -> RemoteCall?> {
        val app = applicationContext
        val activeSession = sessionStore.read()?.sessionInstanceId
        val others = HostNodes.nodes(app).filter { it.sessionInstanceId != activeSession }.mapNotNull { node ->
            HostNodes.sessionFor(app, node)?.let { session -> ApiClient(session.apiBaseUrl, HostNodes.storeFor(app, node)) }
        }
        return (listOf(active) + others).map { client -> { callId: String -> client.getCall(callId) } }
    }

    private fun loadCallHistoryFromCache() {
        val raw = database.syncStateValue(SYNC_KEY_CALL_HISTORY) ?: run {
            callHistory = emptyList()
            callHistoryCursor = null
            return
        }
        runCatching {
            val parsed = CallHistoryCache.parse(raw)
            callHistory = CallHistoryCache.merge(emptyList(), parsed.first)
            callHistoryCursor = parsed.second
        }.onFailure {
            callHistory = emptyList()
            callHistoryCursor = null
        }
    }

    private fun loadMoreCallHistory() {
        val expectedSession = session ?: return
        val expectedDatabase = database
        val cursor = callHistoryCursor ?: return
        if (callHistoryLoading) return
        callHistoryLoading = true
        renderCallHistoryRows()
        executor.execute {
            val result = runCatching { ApiClient(expectedSession.apiBaseUrl, sessionStore).listCallHistoryPage(cursor, 100) }
            mainHandler.post {
                callHistoryLoading = false
                if (isDestroyed || sessionStore.read()?.sameSessionInstance(expectedSession) != true || database !== expectedDatabase) return@post
                result.onSuccess { (items, nextCursor) ->
                    callHistory = CallHistoryCache.merge(callHistory, items)
                    callHistoryCursor = nextCursor
                    expectedDatabase.saveSyncStateValue(SYNC_KEY_CALL_HISTORY, CallHistoryCache.encode(callHistory, nextCursor))
                }.onFailure { failure -> statusMessage = apiFailureText(failure as? Exception ?: IOException("通话记录暂不可用")) }
                renderCallHistoryRows()
            }
        }
    }

    private fun matchesSearch(message: SmsRecord, query: String): Boolean = query.isBlank() ||
        message.text.lowercase().contains(query) || message.peerAddress().lowercase().contains(query) ||
        SmsPeerKey.digitsMatch(message.peerAddress(), query)

    private fun pairingFailureText(failure: Exception): String = when (failure) {
        is ApiFailure -> "配对失败（${failure.code.ifBlank { "HTTP ${failure.httpStatus}" }}）。请检查服务器地址和一次性配对码。"
        is IOException -> failure.message?.takeIf { it.startsWith("Server URL") || it.startsWith("Enter") || it.startsWith("Pairing code") }
            ?: "无法连接服务器，或配对码被拒绝。"
        else -> "配对失败，请检查服务器地址和配对码。"
    }

    private fun handlePairingOutcome(outcome: com.callagent.host.ui.PairingOperationOutcome) {
        pairingForm.consumeOutcome(outcome.requestId)
        val failure = outcome.failure
        if (failure == null) {
            pairingForm.clear()
            val paired = outcome.session ?: sessionStore.read()
            if (paired == null) {
                statusMessage = "配对已完成但未保存会话，请重新配对。"
                showPairing(statusMessage)
                return
            }
            if (session?.sameSessionInstance(paired) != true) {
                switchDatabase(paired)
                session = paired
                CallRuntime.restoreForegroundPreference(this, paired)
                selectedSimId = null
                navigation = HostNavigationState()
                composerTransfer = null
                statusMessage = "已配对，正在载入旧手机状态…"
                showDashboard()
                adaptiveHost?.post { if (hasWindowFocus()) ensurePairedRuntimeStarted() }
                if (sessionStore.read()?.sameSessionInstance(paired) == true) {
                    syncFromServer(showProgress = true)
                }
                com.callagent.host.push.PushRegistration.sync(this, force = true)
            }
            pendingJoinNodes.takeIf { it.isNotEmpty() }?.let { rest ->
                pendingJoinNodes = emptyList()
                addJoinNodes(rest)
            }
            return
        }
        pendingJoinNodes = emptyList()

        if (failure is SessionChanged) {
            showCurrentSession("其他界面更改了配对账户。")
            return
        }
        if (failure is ApiFailure && failure.code == "INVALID_PAIRING_CODE") {
            pairingForm.clearPairingCode()
        }
        val safe = pairingFailureText(failure)
        statusMessage = safe
        showPairing(safe)
    }

    /** Short cause for the status card: the server answered with an error, or could not be reached at all. */
    private fun serverUnreachableReason(failure: Exception): String = when (failure) {
        is ApiFailure -> "服务器返回错误 ${failure.httpStatus}"
        else -> "无法连接服务器"
    }

    private fun apiFailureText(failure: Exception): String = when (failure) {
        is SessionNeedsPairing -> "会话已撤销，请重新配对此手机。"
        is SessionChanged -> "当前账户已变化。"
        is ApiFailure -> "服务器返回 ${failure.httpStatus}${failure.code.takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty()}。"
        else -> "连接失败（${failure.javaClass.simpleName}）。"
    }

    private fun shortId(value: String): String = value.take(8)

    private fun announceStatus(message: String) {
        if (!resumed) return
        val host = adaptiveHost ?: return
        mainHandler.post {
            if (!resumed || statusMessage != message) return@post
            val snackbar = Snackbar.make(host, message, Snackbar.LENGTH_LONG)
            findViewById<View>(R.id.main_bottom_navigation)?.takeIf { it.isShown }?.let { snackbar.anchorView = it }
            snackbar.show()
        }
    }

    private fun verticalRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(24))
    }

    private fun installContent(
        content: LinearLayout,
        panels: LinearLayout? = null,
        primary: LinearLayout? = null,
        secondary: LinearLayout? = null,
        spacer: View? = null,
        fixedBottomView: View? = null,
    ) {
        val previousScrollY = mainScroll?.scrollY ?: savedScrollY
        val previousTopY = horizontalTopScroll?.scrollY ?: savedTopScrollY
        val previousBottomY = horizontalBottomScroll?.scrollY ?: savedBottomScrollY
        val host = FrameLayout(this).apply {
            id = R.id.adaptive_content_host
            setBackgroundColor(materialColor(com.google.android.material.R.attr.colorSurface))
            clipChildren = false
            clipToPadding = false
            // Each render builds a new host. Start it at the last known insets, so
            // a tab switch does not draw one frame unpadded and then jump; before
            // the window has reported any, keep it hidden for that one frame.
            if (windowInsetsKnown) {
                setPadding(safeWindowInsets.left, safeWindowInsets.top, safeWindowInsets.right, safeWindowInsets.bottom)
            } else {
                visibility = View.INVISIBLE
                // Insets arrive in the first traversal; never stay hidden if they do not.
                post { visibility = View.VISIBLE }
            }
        }
        val scroll = ScrollView(this).apply {
            id = R.id.main_content_scroll
            isFillViewport = true
            clipToPadding = false
            contentDescription = "Madoka 主机页面"
        }
        val centered = FrameLayout(this)
        centered.addView(
            content,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        )
        scroll.addView(centered, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        scroll.setOnScrollChangeListener { _, _, scrollY, _, _ -> savedScrollY = scrollY }
        host.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        fixedBottomView?.let { fixed ->
            host.addView(fixed, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
            val updateClearance = {
                val bottom = fixed.height + dp(12)
                if (scroll.paddingBottom != bottom) scroll.setPadding(scroll.paddingLeft, scroll.paddingTop, scroll.paddingRight, bottom)
            }
            fixed.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateClearance() }
            fixed.post { updateClearance() }
        }
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
            windowInsetsKnown = true
            view.visibility = View.VISIBLE
            view.post { updateAdaptiveLayout() }
            windowInsets
        }

        adaptiveHost = host
        mainScroll = scroll
        currentContent = content
        fixedBottomChrome = fixedBottomView
        // The SMS composer and the dial pad panel are centered and width-capped like page content.
        fixedBottomChromeIsComposer = navigation.destination is HostDestination.Conversation ||
            navigation.destination == HostDestination.Dialer
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
        positionFixedBottomChrome(host, decision, windowHeight)
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

    private fun positionFixedBottomChrome(host: FrameLayout, decision: AdaptiveLayoutDecision, windowHeight: Int) {
        val chrome = fixedBottomChrome ?: return
        if (chrome.parent !== host) return
        val safeWidth = (host.width - safeWindowInsets.left - safeWindowInsets.right).coerceAtLeast(0)
        var paneLeft = safeWindowInsets.left
        var paneWidth = safeWidth
        val verticalGravity = Gravity.BOTTOM
        var bottomMargin = 0
        val fold = foldGeometry?.takeIf { it.separating }
        if (fold?.axis == FoldGeometry.Axis.VERTICAL) {
            val leftWidth = (fold.startPx - safeWindowInsets.left).coerceAtLeast(0)
            val rightWidth = (host.width - safeWindowInsets.right - fold.endPx).coerceAtLeast(0)
            if (leftWidth >= rightWidth) {
                paneLeft = safeWindowInsets.left
                paneWidth = leftWidth
            } else {
                paneLeft = fold.endPx
                paneWidth = rightWidth
            }
        } else if (fold?.axis == FoldGeometry.Axis.HORIZONTAL) {
            val selection = selectLargestSafeHorizontalPane(windowHeight, decision, fold)
            val availableBottom = (host.height - safeWindowInsets.bottom).coerceAtLeast(safeWindowInsets.top)
            val topPane = selection.selectedPane == HorizontalPaneSelection.Pane.TOP
            val physicalGap = (fold.endPx - fold.startPx).coerceAtLeast(0)
            val totalClearance = (dp(decision.hingeGapDp) - physicalGap).coerceAtLeast(dp(16))
            val topClearance = totalClearance / 2
            if (topPane) {
                val topPaneBottom = (fold.startPx - topClearance).coerceAtLeast(safeWindowInsets.top)
                bottomMargin = (availableBottom - topPaneBottom).coerceAtLeast(0)
            } else {
                bottomMargin = (availableBottom - (host.height - safeWindowInsets.bottom)).coerceAtLeast(0)
            }
        }
        val contentMax = dp(decision.contentMaxWidthDp)
        val width = if (fixedBottomChromeIsComposer) minOf(paneWidth, contentMax) else paneWidth
        if (fixedBottomChromeIsComposer) paneLeft += (paneWidth - width).coerceAtLeast(0) / 2
        val horizontalGravity = if (fold?.axis == FoldGeometry.Axis.VERTICAL || fixedBottomChromeIsComposer) Gravity.LEFT else Gravity.FILL_HORIZONTAL
        val gravity = horizontalGravity or verticalGravity
        val params = FrameLayout.LayoutParams(
            width.coerceAtLeast(dp(1)),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            gravity,
        ).apply {
            leftMargin = (paneLeft - safeWindowInsets.left).coerceAtLeast(0)
            this.bottomMargin = bottomMargin
        }
        setLayoutParamsIfChanged(chrome, params)
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
                "callSearch" -> callSearchEdit
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
            callSearchEdit != null && focused === callSearchEdit -> "callSearch"
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
            "callSearch" -> callSearchEdit
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
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_DisplaySmall)
        setTextColor(materialColor(com.google.android.material.R.attr.colorOnSurface))
        setPadding(dp(4), dp(32), dp(8), dp(12))
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

    /** Body text; [selectable] must be false inside clickable rows, where selection would swallow the tap. */
    private fun body(text: String, selectable: Boolean = true): TextView = MaterialTextView(this).apply {
        this.text = text
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        setTextColor(materialColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        setPadding(0, dp(4), 0, dp(6))
        setTextIsSelectable(selectable)
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
        iconTint = android.content.res.ColorStateList.valueOf(materialColor(com.google.android.material.R.attr.colorOnSecondaryContainer))
    }

    private fun smsArchiveEntryButton(): View = HostUi.tile(this, HostUi.Position.SINGLE, "短信备份与归档",
        summary = "导出加密备份、导入 SMS Backup & Restore 归档",
        icon = R.drawable.ic_ui_archive,
        trailing = HostUi.chevron(this),
        onClick = { startActivity(Intent(this, com.callagent.host.backup.HostSmsBackupActivity::class.java)) }
    ).apply {
        id = R.id.sms_backup_archive_entry
        contentDescription = "短信备份与归档"
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

/** The server answered, but this account has no paired old phone yet. */
private class NoPairedGateway : IOException("此账户还没有配对旧手机网关")
