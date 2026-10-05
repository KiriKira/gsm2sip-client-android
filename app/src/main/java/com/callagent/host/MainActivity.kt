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
import com.callagent.host.data.SmsSubmissionClients
import com.callagent.host.data.SmsStatus
import com.callagent.host.data.toRetryEnvelope
import com.callagent.host.data.clientDatabaseName
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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.callagent.host.ui.AdaptiveLayoutDecision
import com.callagent.host.ui.AdaptiveWindowLayoutPolicy
import com.callagent.host.ui.FoldGeometry
import com.callagent.host.ui.HorizontalPaneSelection
import com.callagent.host.ui.PairingOperationState
import com.callagent.host.ui.PairingFormViewModel
import com.callagent.host.ui.HostDestination
import com.callagent.host.ui.HostNavigationState
import com.callagent.host.ui.HostTab
import com.callagent.host.ui.PhoneRowView
import com.callagent.host.ui.DialPadKeyView
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
    private lateinit var sessionStore: SessionStore
    private lateinit var preferences: ClientPreferences
    private lateinit var pairingForm: PairingFormViewModel
    private lateinit var database: ClientDatabase
    private var session: HostSession? = null
    private var gateway: GatewaySnapshot? = null
    private var pairedHosts: List<PairedHost> = emptyList()
    private var pairedHostsAvailable: Boolean? = null
    private var pairedHostsRefreshIssue: String? = null
    private var sims: List<SimLine> = emptyList()
    private var selectedSimId: String? = null
    private var statusMessage: String = ""
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
    private var callHistoryList: LinearLayout? = null
    private var dialerContactsList: LinearLayout? = null
    private var phoneContactsList: LinearLayout? = null
    private var callHistory: List<RemoteCall> = emptyList()
    private var callHistoryCursor: String? = null
    private var callHistoryLoading = false
    private var contactsQuery = ""
    private var dialerContacts: List<ContactEntry> = emptyList()
    private var dialerCallButton: MaterialButton? = null
    private var smsThreadSendButton: MaterialButton? = null
    private val contactsRepository by lazy { HostContactsRepository(applicationContext) }
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
    private var restoreFocusedSelection = -1
    private var safeWindowInsets = Insets.NONE
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
            }
        }
    }

    companion object {
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
        session = sessionStore.read()
        CallRuntime.restoreForegroundPreference(this, session)
        switchDatabase(session)
        selectedSimId = selectedRestoreSimId
        setUpWindow()
        if (savedInstanceState == null && intent?.action == ACTION_SHOW_SMS) {
            navigation = navigation.selectTab(HostTab.MESSAGES)
        }
        showDashboard()
        if (session != null) syncFromServer(showProgress = false)
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
            navigate(navigation.selectTab(HostTab.MESSAGES))
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

    private data class SmsThread(val simId: String?, val peer: String, val latest: SmsRecord, val records: List<SmsRecord>)

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
                    homeNavigation(HostTab.SETTINGS)
                }
            }
            HostDestination.Dialer -> {
                renderDialer(content)
                null
            }
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

    private fun homeNavigation(selected: HostTab): BottomNavigationView = BottomNavigationView(this).apply {
        id = R.id.main_bottom_navigation
        inflateMenu(R.menu.main_navigation)
        labelVisibilityMode = BottomNavigationView.LABEL_VISIBILITY_LABELED
        selectedItemId = when (selected) {
            HostTab.PHONE -> R.id.tab_phone
            HostTab.MESSAGES -> R.id.tab_messages
            HostTab.SETTINGS -> R.id.tab_settings
        }
        setOnItemSelectedListener { item ->
            val tab = when (item.itemId) {
                R.id.tab_phone -> HostTab.PHONE
                R.id.tab_messages -> HostTab.MESSAGES
                R.id.tab_settings -> HostTab.SETTINGS
                else -> return@setOnItemSelectedListener false
            }
            navigate(navigation.selectTab(tab))
            true
        }
        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72), Gravity.BOTTOM)
    }

    private fun homeChrome(tab: HostTab): View {
        val frame = FrameLayout(this)
        val nav = homeNavigation(tab)
        frame.addView(nav, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72), Gravity.BOTTOM))
        val fab = FloatingActionButton(this).apply {
            id = if (tab == HostTab.PHONE) R.id.dialer_open else R.id.sms_compose_fab
            setImageResource(if (tab == HostTab.PHONE) R.drawable.host_action_dial else R.drawable.host_action_compose)
            contentDescription = if (tab == HostTab.PHONE) "拨号" else "写短信"
            backgroundTintList = android.content.res.ColorStateList.valueOf(materialColor(androidx.appcompat.R.attr.colorPrimary))
            imageTintList = android.content.res.ColorStateList.valueOf(materialColor(com.google.android.material.R.attr.colorOnPrimary))
            setOnClickListener {
                when (tab) {
                    HostTab.PHONE -> navigate(navigation.openDialer())
                    HostTab.MESSAGES -> openNewConversation()
                    HostTab.SETTINGS -> Unit
                }
            }
        }
        val fabParams = FrameLayout.LayoutParams(dp(56), dp(56), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(4)
            marginEnd = dp(20)
        }
        frame.addView(fab, fabParams)
        frame.minimumHeight = dp(136)
        return frame
    }

    private fun renderPhoneHome(content: LinearLayout) {
        val search = roundedSearchField("搜索联系人或号码", R.id.call_history_search)
        search.setText(callSearchQuery)
        callSearchEdit = search
        content.addView(search)
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
        if (session == null) {
            content.addView(body("完成配对后，通话记录会显示在这里。"))
            val settings = smallButton("前往设置完成配对")
            settings.setOnClickListener { navigate(navigation.selectTab(HostTab.SETTINGS)) }
            content.addView(settings)
        }
    }

    private fun renderMessagesHome(content: LinearLayout) {
        val search = roundedSearchField("搜索短信", R.id.sms_search)
        search.setText(searchQuery)
        searchEdit = search
        content.addView(search)
        smsThreadList = LinearLayout(this).apply {
            id = R.id.sms_thread_list
            orientation = LinearLayout.VERTICAL
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
        if (session == null) {
            content.addView(body("完成配对后可在这里收发短信。"))
            val settings = smallButton("前往设置完成配对")
            settings.setOnClickListener { navigate(navigation.selectTab(HostTab.SETTINGS)) }
            content.addView(settings)
        }
    }

    private fun renderCallHistoryRows() {
        val list = callHistoryList ?: return
        list.removeAllViews()
        val query = callSearchQuery.trim().lowercase()
        val filtered = callHistory.filter { call ->
            query.isBlank() || listOfNotNull(call.from, call.to, call.reason).any { it.lowercase().contains(query) }
        }
        if (filtered.isEmpty()) {
            list.addView(body("暂无通话记录"))
        } else {
            filtered.forEachIndexed { index, call ->
                val incoming = call.direction in setOf("incoming", "inbound")
                val number = if (incoming) call.from.orEmpty() else call.to.orEmpty()
                val direction = if (incoming) "来电" else "拨出"
                val state = when {
                    call.reason == "answered_elsewhere" -> "其他设备已接听"
                    call.state == "ended" && incoming && call.answeredAt == null -> "未接"
                    call.state == "ended" -> "已结束"
                    else -> "进行中"
                }
                val row = PhoneRowView(
                    context = this,
                    name = contactName(number) ?: number.ifBlank { "未知号码" },
                    detail = "$direction · $state · ${formatHistoryTime(call.createdAt)}",
                    missed = state == "未接",
                    onCall = { openCall(call) },
                ).apply { id = R.id.call_history_item }
                list.addView(row)
                if (index < filtered.lastIndex) list.addView(divider())
            }
        }
        if (callHistoryCursor != null) {
            val more = smallButton(if (callHistoryLoading) "正在载入…" else "更早通话")
            more.isEnabled = !callHistoryLoading
            more.setOnClickListener { loadMoreCallHistory() }
            list.addView(more)
        }
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
        val records = sims.flatMap { line -> database.loadMessages(line.simId) } + database.loadMessages(null)
        val query = searchQuery.trim().lowercase()
        val threads = records
            .groupBy { it.simId to it.peerAddress().ifBlank { "未知号码" } }
            .map { (key, messages) ->
                val sorted = messages.sortedByDescending { timestampMillis(it.createdAt) }
                SmsThread(key.first, key.second, sorted.first(), sorted)
            }
            .filter { thread -> query.isBlank() || thread.peer.lowercase().contains(query) || thread.latest.text.lowercase().contains(query) }
            .sortedByDescending { timestampMillis(it.latest.createdAt) }
        if (threads.isEmpty()) {
            list.addView(body("暂无短信对话"))
        } else {
            val visibleThreads = threads.take(smsThreadVisibleCount)
            visibleThreads.forEachIndexed { index, thread ->
                val row = LinearLayout(this).apply {
                    id = R.id.sms_thread_item
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(76)
                    setPadding(dp(4), dp(8), dp(4), dp(8))
                    background = selectableItemBackground()
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        selectedSimId = thread.simId
                        navigate(navigation.openConversation(thread.simId, thread.peer))
                    }
                }
                val badge = MaterialTextView(this).apply {
                    id = R.id.sms_thread_sim_badge
                    text = simBadgeLabel(thread.simId)
                    gravity = Gravity.CENTER
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    contentDescription = text.toString()
                    setTextColor(materialColor(com.google.android.material.R.attr.colorOnSecondaryContainer))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(materialColor(com.google.android.material.R.attr.colorSecondaryContainer))
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(14) }
                }
                row.addView(badge)
                val textColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val peer = MaterialTextView(this).apply {
                    text = thread.peer
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                    setTextColor(materialColor(com.google.android.material.R.attr.colorOnSurface))
                }
                top.addView(peer, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                top.addView(body(formatHistoryTime(thread.latest.createdAt)))
                textColumn.addView(top)
                val summary = thread.latest.text.replace('\n', ' ').trim().ifBlank { SmsStatus.display(thread.latest.status) }
                textColumn.addView(body(summary.take(90)).apply {
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                row.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                list.addView(row)
                if (index < visibleThreads.lastIndex) list.addView(divider())
            }
            if (threads.size > visibleThreads.size) {
                val more = smallButton("更多会话")
                more.setOnClickListener { smsThreadVisibleCount += 100; renderSmsThreads() }
                list.addView(more)
            }
        }
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
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { marginStart = dp(62) }
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
            content.addView(sectionTitle("服务器与配对").apply { id = R.id.settings_pairing_section })
            content.addView(body("连接服务器以使用远程电话和短信。"))
            addPairingForm(content)
        } else {
            content.addView(sectionTitle("服务器与设备"))
            content.addView(body(session?.apiBaseUrl.orEmpty()))
            content.addView(body("已配对此手机 · 账号 ${shortId(session?.ownerId.orEmpty())}"))
            content.addView(sectionTitle("已配对设备"))
            val devices = LinearLayout(this).apply {
                id = R.id.settings_device_list
                orientation = LinearLayout.VERTICAL
            }
            when {
                pairedHostsAvailable == false -> devices.addView(body("当前服务器不提供设备列表。"))
                pairedHosts.isEmpty() -> devices.addView(body(pairedHostsRefreshIssue ?: "暂无设备记录。"))
                else -> pairedHosts.forEach { host ->
                    val self = if (host.isSelf) " · 此手机" else ""
                    devices.addView(body("${host.name}$self · ${host.pairingStateLabel()}"))
                }
            }
            content.addView(devices)
            pairedHostsRefreshIssue?.let { content.addView(body(it)) }
            content.addView(sectionTitle("SIM 卡"))
            if (sims.isEmpty()) content.addView(body("暂无可用线路。")) else sims.sortedBy { it.slotIndex }.forEach { line ->
                val status = if (line.canSend) "可用" else line.stateLabel()
                content.addView(body("SIM ${line.slotIndex + 1} · ${line.label}${line.phoneNumber?.let { " · $it" }.orEmpty()} · $status"))
            }
            val unpair = smallButton("解除此手机配对").apply { id = R.id.settings_unpair }
            unpair.setOnClickListener { confirmUnpair() }
            content.addView(unpair)
        }
        content.addView(sectionTitle("权限与后台"))
        addPermissionSettings(content)
        content.addView(sectionTitle("短信备份与归档"))
        content.addView(smsArchiveEntryButton())
        settingsMessage = null
    }

    private fun addPermissionSettings(content: LinearLayout) {
        val status = runCatching { HostBackgroundRuntime.snapshot(this) }.getOrNull()
        val notifications = smallButton("通知权限 · ${if (status?.notificationsEnabled == true) "已允许" else "未允许"}").apply {
            id = R.id.settings_notifications
            setOnClickListener {
                val latest = runCatching { HostBackgroundRuntime.snapshot(this@MainActivity) }.getOrNull()
                if (latest?.notificationsEnabled == true) {
                    HostBackgroundRuntime.openNotificationSettings(this@MainActivity)
                } else if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CALL_NOTIFICATIONS)
                } else {
                    HostBackgroundRuntime.openNotificationSettings(this@MainActivity)
                }
            }
        }
        content.addView(notifications)

        val battery = smallButton(if (status?.batteryExempt == true) "电池优化 · 已豁免" else "电池优化设置").apply {
            id = R.id.settings_battery
            setOnClickListener { HostBackgroundRuntime.openBatterySettings(this@MainActivity) }
        }
        content.addView(battery)

        val contactsGranted = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val contacts = smallButton(if (contactsGranted) "联系人权限 · 已允许" else "允许访问联系人").apply {
            id = R.id.settings_contacts
            setOnClickListener {
                if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                } else {
                    requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_CONTACTS)
                }
            }
        }
        content.addView(contacts)
    }

    private fun addPairingForm(content: LinearLayout) {
        val serverField = inputField("服务器地址", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
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

    private fun renderDialer(content: LinearLayout) {
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val back = smallButton("返回").apply { id = R.id.screen_back }
        back.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        back.setOnClickListener { navigate(navigation.goHome()) }
        toolbar.addView(back)
        toolbar.addView(title("拨号"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(toolbar)

        val destinationField = inputField("联系人或号码", InputType.TYPE_CLASS_PHONE)
        val destination = destinationField.editText as TextInputEditText
        destination.id = R.id.dialer_destination
        destination.setSingleLine(true)
        destination.setText(callDestination)
        callDestinationEdit = destination
        content.addView(destinationField)

        val contactHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
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
        renderDialerContacts()
        destination.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                callDestination = s?.toString().orEmpty()
                contactsQuery = callDestination
                queryDialerContacts(contactsQuery)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        val keypad = GridLayout(this).apply {
            id = R.id.dialer_keypad
            columnCount = 3
            rowCount = 4
            useDefaultMargins = false
            alignmentMode = GridLayout.ALIGN_BOUNDS
            layoutParams = fullWidthParams()
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
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(0, 0, 0, 0)
                }
                setOnClickListener {
                    val at = destination.selectionStart.coerceAtLeast(0)
                    destination.text?.insert(at, key)
                    destination.setSelection((at + key.length).coerceAtMost(destination.length()))
                }
            }
            keypad.addView(keyButton)
        }
        content.addView(keypad)
        val selector = ChipGroup(this).apply {
            id = R.id.dialer_sim_selector
            isSaveEnabled = false
            isSaveFromParentEnabled = false
            isSingleSelection = true
            isSelectionRequired = false
            chipSpacingHorizontal = dp(8)
            layoutParams = fullWidthParams(top = 4, bottom = 4)
        }
        sims.sortedBy { it.slotIndex }.forEach { line ->
            val chip = Chip(this).apply {
                text = "SIM ${line.slotIndex + 1}"
                isCheckable = true
                isSingleLine = true
                maxLines = 1
                minHeight = dp(48)
                isChecked = line.simId == selectedSimId
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
        content.addView(selector)
        val selected = sims.firstOrNull { it.simId == selectedSimId }
        val backspace = smallButton("⌫").apply {
            id = R.id.dialer_backspace
            setIconResource(R.drawable.host_action_backspace)
            text = ""
            contentDescription = "删除号码"
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(48)).apply { gravity = Gravity.END }
        }
        backspace.setOnClickListener {
            val input = destination.text ?: return@setOnClickListener
            val start = destination.selectionStart.coerceAtLeast(0)
            val end = destination.selectionEnd.coerceAtLeast(0)
            if (start != end) input.delete(start, end) else if (start > 0) input.delete(start - 1, start)
        }
        content.addView(backspace)
        val dial = button("")
        dial.id = R.id.dialer_call_button
        dial.contentDescription = "拨出"
        dial.setIconResource(R.drawable.call_ui_phone)
        dial.iconSize = dp(28)
        dial.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(24, 132, 74))
        dial.iconTint = android.content.res.ColorStateList.valueOf(Color.WHITE)
        dial.setTextColor(Color.WHITE)
        dial.cornerRadius = dp(36)
        dial.layoutParams = LinearLayout.LayoutParams(dp(68), dp(68)).apply { gravity = Gravity.CENTER_HORIZONTAL }
        dial.setOnClickListener { beginDial(selected, destination.text?.toString().orEmpty()) }
        dialerCallButton = dial
        content.addView(dial)
        updateDialButtonState()
        if (session != null && callServerAvailable == null) refreshCallAvailability()
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED && dialerContacts.isEmpty()) {
            queryDialerContacts(contactsQuery)
        }
    }

    private fun renderDialerContacts() {
        val list = dialerContactsList ?: return
        list.removeAllViews()
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            list.addView(body("允许访问联系人以查找姓名和号码。"))
            return
        }
        val query = contactsQuery.trim()
        val matching = dialerContacts.filter { query.isBlank() || it.name.contains(query, true) || it.number.contains(query, true) }.take(3)
        if (matching.isEmpty()) {
            if (query.isNotBlank()) list.addView(body("没有匹配的联系人。"))
            return
        }
        matching.forEachIndexed { index, entry ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = selectableItemBackground()
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val number = entry.normalizedNumber?.takeIf { it.isNotBlank() } ?: entry.number
                    callDestination = normalizeDialNumber(number)
                    callDestinationEdit?.setText(callDestination)
                    callDestinationEdit?.setSelection(callDestination.length)
                }
            }
            row.addView(label(entry.name))
            row.addView(body(entry.number))
            list.addView(row)
            if (index < matching.lastIndex) list.addView(divider())
        }
    }

    private fun renderPhoneContacts(matches: List<ContactEntry>) {
        val list = phoneContactsList ?: return
        list.removeAllViews()
        val visible = matches.take(3)
        visible.forEachIndexed { index, entry ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = selectableItemBackground()
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    callDestination = normalizeDialNumber(entry.normalizedNumber?.takeIf(String::isNotBlank) ?: entry.number)
                    navigate(navigation.openDialer())
                }
            }
            row.addView(label(entry.name))
            row.addView(body(entry.number))
            list.addView(row)
            if (index < visible.lastIndex) list.addView(divider())
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

    private fun roundedSearchField(hint: String, id: Int): TextInputEditText = TextInputEditText(this).apply {
        this.id = id
        this.hint = hint
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        maxLines = 1
        minHeight = dp(52)
        setPadding(dp(18), dp(12), dp(18), dp(12))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(28).toFloat()
            setColor(materialColor(com.google.android.material.R.attr.colorSurfaceVariant))
        }
        setTextColor(materialColor(com.google.android.material.R.attr.colorOnSurface))
        setHintTextColor(materialColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        setSingleLine(true)
        layoutParams = fullWidthParams(top = 6, bottom = 8)
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

    private fun updateDialButtonState() {
        val line = sims.firstOrNull { it.simId == selectedSimId }
        val lineReady = line != null && line.canSend && gateway?.mappingRevision == line.mappingRevision
        val ready = session != null && gateway?.online == true && lineReady && callServerAvailable == true && localSipEngineAvailable()
        dialerCallButton?.isEnabled = ready
    }

    private fun beginDial(line: SimLine?, numberValue: String) {
        val number = normalizeDialNumber(numberValue)
        if (line == null || !line.canSend || gateway?.mappingRevision != line.mappingRevision || gateway?.online != true || callServerAvailable != true) {
            showToast("请先选择在线且已确认的 SIM 卡。")
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
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val back = smallButton("返回").apply { id = R.id.screen_back }
        back.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        back.setOnClickListener { navigate(navigation.goHome()) }
        toolbar.addView(back)
        val heading = MaterialTextView(this).apply {
            id = R.id.sms_thread_header
            text = route.peer.ifBlank { "新短信" }
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
            setTextColor(materialColor(com.google.android.material.R.attr.colorOnSurface))
            setPadding(dp(12), dp(8), 0, dp(8))
        }
        toolbar.addView(heading, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(toolbar)

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
        val savedBody = transferred?.second ?: if (route.isNew || savedDraft?.recipient == route.peer) savedDraft?.text.orEmpty() else ""
        bindingDraft = true
        recipient?.setText(transferred?.first ?: savedDraft?.recipient.orEmpty())

        smsConversationList = LinearLayout(this).apply {
            id = R.id.sms_conversation_list
            orientation = LinearLayout.VERTICAL
        }
        val records = if (route.simId == null) database.loadMessages(null) else database.loadMessages(route.simId)
        val threadRecords = if (route.peer.isBlank()) emptyList() else records
            .filter { it.peerAddress() == route.peer }
            .sortedBy { timestampMillis(it.createdAt) }
        if (threadRecords.isEmpty()) {
            smsConversationList?.addView(body(if (route.simId == null && !route.isNew) "此对话的 SIM 卡未知。选择线路后才能发送。" else "开始对话"))
        } else {
            val visibleRecords = threadRecords.takeLast(smsConversationVisibleCount)
            if (threadRecords.size > visibleRecords.size) {
                val earlier = smallButton("较早短信")
                earlier.setOnClickListener { smsConversationVisibleCount += 100; renderCurrentScreen(preserveInput = true) }
                smsConversationList?.addView(earlier)
            }
            visibleRecords.forEach { message -> smsConversationList?.addView(conversationMessage(message)) }
        }
        content.addView(smsConversationList)

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
            setBackgroundColor(materialColor(com.google.android.material.R.attr.colorSurface))
        }
        val simButton = smallButton(line?.let { "SIM ${it.slotIndex + 1} · ${it.label}" } ?: "选择 SIM 卡").apply {
            id = R.id.sms_thread_sim_selector
            setOnClickListener { chooseConversationSim(route) }
        }
        simButton.isEnabled = sims.isNotEmpty()
        composer.addView(simButton)
        val inputRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        val bodyField = inputField("短信内容", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        val bodyInput = bodyField.editText as TextInputEditText
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
        inputRow.addView(bodyField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val send = MaterialButton(this).apply {
            id = R.id.sms_thread_send
            text = "发送"
            isAllCaps = false
            minWidth = dp(64)
            minHeight = dp(48)
            setOnClickListener {
                val selected = sims.firstOrNull { it.simId == selectedSimId }
                if (selected == null) showToast("请选择 SIM 卡。")
                else confirmSend(selected, recipient?.text?.toString() ?: route.peer, bodyInput.text?.toString().orEmpty())
            }
        }
        smsThreadSendButton = send
        inputRow.addView(send)
        composer.addView(inputRow)
        updateSendButtonState()
        return composer
    }

    private fun conversationMessage(message: SmsRecord): View {
        val inbound = message.direction == "inbound"
        val bubble = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(materialColor(if (inbound) com.google.android.material.R.attr.colorSurfaceVariant else com.google.android.material.R.attr.colorSecondaryContainer))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = if (inbound) Gravity.START else Gravity.END
                topMargin = dp(4)
                bottomMargin = dp(4)
                marginStart = dp(8)
                marginEnd = dp(8)
            }
        }
        bubble.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            val parentWidth = (view.parent as? View)?.width ?: return@addOnLayoutChangeListener
            val targetWidth = (parentWidth * 0.78f).toInt().coerceAtLeast(dp(80))
            val params = view.layoutParams as? LinearLayout.LayoutParams ?: return@addOnLayoutChangeListener
            val available = (targetWidth - params.marginStart - params.marginEnd).coerceAtLeast(dp(80))
            if (params.width != available) {
                params.width = available
                view.layoutParams = params
            }
        }
        val stack = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10)) }
        stack.addView(body(message.text))
        val state = if (inbound) "收到 · ${formatHistoryTime(message.createdAt)}" else "${SmsStatus.display(message.status)} · ${formatHistoryTime(message.createdAt)}"
        stack.addView(label(state))
        if (message.taskKey != null && (message.status == SmsStatus.UNKNOWN || message.status == SmsStatus.SUBMITTING)) {
            val action = if (retryAction(message.taskKey, message.serverId) == RetryAction.RETRY_SAME_KEY) "重试发送" else "查询状态"
            val retry = smallButton(action)
            retry.setOnClickListener { retryOrCheck(message) }
            stack.addView(retry)
        }
        bubble.addView(stack)
        return bubble
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

    private fun refreshCallAvailability(force: Boolean = false) {
        if (session == null || callAvailabilityLoading) return
        if (!force && System.currentTimeMillis() - callAvailabilityCheckedAt < 60_000L) return
        callAvailabilityLoading = true
        renderCurrentScreen()
        val expected = session ?: return
        val expectedDatabase = database
        val expectedApi = ApiClient(expected.apiBaseUrl, sessionStore)
        executor.execute {
            val result = runCatching {
                if (sessionStore.read()?.sameSessionInstance(expected) != true || database !== expectedDatabase) throw SessionChanged()
                expectedApi.getSipConfiguration()
            }
            mainHandler.post {
                callAvailabilityLoading = false
                callAvailabilityCheckedAt = System.currentTimeMillis()
                val latest = sessionStore.read()
                val currentStillExpected = session?.sameSessionInstance(expected) == true && database === expectedDatabase
                val revoked = result.exceptionOrNull() is SessionNeedsPairing && latest == null && currentStillExpected
                if (!currentStillExpected && !revoked) return@post
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
        beginDial(line, recipient)
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
            createdAt = Instant.now().toString()
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
        navigation = navigation.openConversation(line.simId, recipient, isNew = false)
        composerTransfer = null
        statusMessage = "短信已加入发送队列。"
        renderCurrentScreen(preserveInput = false)
        executor.execute {
            try {
                if (sessionStore.read()?.sameSessionInstance(expectedSession) != true || database !== expectedDatabase) throw SessionChanged()
                val accepted = expectedApi.createMessage(retryEnvelope)
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
            RetryAction.NONE -> showToast("This item has no safe retry action.")
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
                val accepted = expectedApi.createMessage(current.second)
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
            statusMessage = "Refreshing server snapshots…"
            showDashboard()
        }
        executor.execute {
            try {
                if (sessionStore.read()?.sameSessionInstance(expectedSession) != true) throw SessionChanged()
                val api = ApiClient(expectedSession.apiBaseUrl, sessionStore)
                val oldRevision = expectedDatabase.loadGateway()?.mappingRevision
                val remoteGateways = api.listGateways()
                val current = remoteGateways.firstOrNull()
                    ?: throw IOException("No gateway is paired with this account")
                val (_, remoteSims) = api.listSims(current.gatewayId)
                val mappingChanged = oldRevision != null && oldRevision != current.mappingRevision
                expectedDatabase.saveGateway(current)
                expectedDatabase.replaceSims(remoteSims)
                syncSms(api)
                val remoteCallPage = runCatching { api.listCallHistoryPage(cursor = null, limit = 100) }.getOrNull()
                val mergedCallSnapshot = remoteCallPage?.let { page ->
                    val prior = expectedDatabase.syncStateValue(SYNC_KEY_CALL_HISTORY)?.let(::parseCallHistorySnapshot)
                    mergeCallHistory(prior?.first.orEmpty(), page.first).let { merged -> merged to page.second }
                }
                if (mergedCallSnapshot != null && sessionStore.read()?.sameSessionInstance(expectedSession) == true) {
                    expectedDatabase.saveSyncStateValue(SYNC_KEY_CALL_HISTORY, encodeCallHistory(mergedCallSnapshot.first, mergedCallSnapshot.second))
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
                        showCurrentSession("The paired account changed. Showing its own saved gateway and message cache.")
                        return@post
                    }
                    session = sessionStore.read()
                    gateway = current
                    sims = remoteSims
                    if (remoteCallPage != null) {
                        callHistory = mergeCallHistory(callHistory, mergedCallSnapshot?.first ?: remoteCallPage.first)
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
        session = sessionStore.read()
        navigation = HostNavigationState()
        composerTransfer = null
        selectedSimId = null
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

    private fun loadCallHistoryFromCache() {
        val raw = database.syncStateValue(SYNC_KEY_CALL_HISTORY) ?: run {
            callHistory = emptyList()
            callHistoryCursor = null
            return
        }
        runCatching {
            val parsed = parseCallHistorySnapshot(raw)
            callHistory = mergeCallHistory(emptyList(), parsed.first)
            callHistoryCursor = parsed.second
        }.onFailure {
            callHistory = emptyList()
            callHistoryCursor = null
        }
    }

    private fun parseCallHistorySnapshot(raw: String): Pair<List<RemoteCall>, String?> {
        val snapshot = JSONObject(raw)
        val items = snapshot.optJSONArray("items") ?: JSONArray()
        val parsed = (0 until items.length()).map { index -> items.getJSONObject(index).toRemoteCall() }
        val cursor = snapshot.optString("next_cursor").takeIf { it.isNotBlank() && it != "null" }
        return parsed to cursor
    }

    private fun encodeCallHistory(items: List<RemoteCall>, cursor: String?): String = JSONObject().apply {
        put("next_cursor", cursor)
        put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
    }.toString()

    private fun RemoteCall.toJson(): JSONObject = JSONObject()
        .put("call_id", callId)
        .put("gateway_id", gatewayId)
        .put("sim_id", simId)
        .put("mapping_revision", mappingRevision)
        .put("direction", direction)
        .put("state", state)
        .put("state_revision", stateRevision)
        .put("from", from)
        .put("to", to)
        .put("created_at", createdAt)
        .put("expires_at", expiresAt)
        .put("answered_at", answeredAt)
        .put("ended_at", endedAt)
        .put("reason", reason)

    private fun JSONObject.toRemoteCall(): RemoteCall = RemoteCall(
        callId = getString("call_id"),
        gatewayId = getString("gateway_id"),
        clientId = null,
        simId = getString("sim_id"),
        mappingRevision = optLong("mapping_revision"),
        direction = optString("direction"),
        state = optString("state"),
        stateRevision = optLong("state_revision"),
        from = optNullableString("from"),
        to = optNullableString("to"),
        createdAt = optString("created_at"),
        expiresAt = optNullableString("expires_at"),
        wakeNonce = null,
        answeredAt = optNullableString("answered_at"),
        endedAt = optNullableString("ended_at"),
        reason = optNullableString("reason"),
    )

    private fun JSONObject.optNullableString(key: String): String? = optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun mergeCallHistory(existing: List<RemoteCall>, incoming: List<RemoteCall>): List<RemoteCall> {
        val merged = LinkedHashMap<String, RemoteCall>()
        (existing + incoming).forEach { call ->
            val previous = merged[call.callId]
            if (previous == null || call.stateRevision >= previous.stateRevision) merged[call.callId] = call
        }
        return merged.values.sortedByDescending { timestampMillis(it.createdAt) }
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
                    callHistory = mergeCallHistory(callHistory, items)
                    callHistoryCursor = nextCursor
                    expectedDatabase.saveSyncStateValue(SYNC_KEY_CALL_HISTORY, encodeCallHistory(callHistory, nextCursor))
                }.onFailure { failure -> statusMessage = apiFailureText(failure as? Exception ?: IOException("Call history unavailable")) }
                renderCallHistoryRows()
            }
        }
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
                navigation = HostNavigationState()
                composerTransfer = null
                statusMessage = "Paired. Loading gateway state…"
                showDashboard()
                adaptiveHost?.post { if (hasWindowFocus()) ensurePairedRuntimeStarted() }
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
            view.post { updateAdaptiveLayout() }
            windowInsets
        }

        adaptiveHost = host
        mainScroll = scroll
        currentContent = content
        fixedBottomChrome = fixedBottomView
        fixedBottomChromeIsComposer = navigation.destination is HostDestination.Conversation
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

    private fun smsArchiveEntryButton(): MaterialButton = smallButton("短信备份与归档").apply {
        id = R.id.sms_backup_archive_entry
        setOnClickListener {
            startActivity(Intent(this@MainActivity, com.callagent.host.backup.HostSmsBackupActivity::class.java))
        }
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
