package com.callagent.host

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
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
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : Activity() {
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
        if (session != null) {
            mainHandler.removeCallbacks(periodicSync)
            mainHandler.post(periodicSync)
        }
    }

    override fun onPause() {
        resumed = false
        mainHandler.removeCallbacks(periodicSync)
        super.onPause()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        database.close()
        super.onDestroy()
    }

    private fun setUpWindow() {
        window.statusBarColor = Color.rgb(245, 247, 245)
        window.navigationBarColor = Color.rgb(245, 247, 245)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }

    private fun showPairing(message: String? = null) {
        session = null
        val content = verticalRoot()
        content.addView(title("GSM2SIP Host"))
        content.addView(body("Pair this unrooted phone with the server to view the gateway's two remote SIM lines and exchange SMS."))
        message?.let { content.addView(messageCard(it, error = true)) }

        val serverInput = edit("https://your-server.example", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        serverInput.setText(preferences.apiBaseUrl)
        content.addView(label("Server URL"))
        content.addView(serverInput)

        val pairingCode = edit("One-time client pairing code", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        pairingCode.maxLines = 1
        content.addView(label("Pairing code"))
        content.addView(pairingCode)

        val deviceName = edit("Device name", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME)
        deviceName.setText(android.os.Build.MODEL.orEmpty().take(100))
        content.addView(label("This phone"))
        content.addView(deviceName)

        val pairButton = button("Pair this phone")
        pairButton.setOnClickListener {
            val base = serverInput.text.toString().trim()
            val code = pairingCode.text.toString().trim()
            val name = deviceName.text.toString().trim().ifBlank { "Android host" }.take(100)
            if (base.isBlank() || code.isBlank()) {
                showPairing("Enter the HTTPS server URL and one-time pairing code.")
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
        content.addView(body("Calling is not enabled yet. Gateway audio and server calling setup still need to be verified."))
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
        val headingText = title("GSM2SIP Host")
        heading.addView(headingText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val syncButton = smallButton("Refresh")
        syncButton.setOnClickListener { syncFromServer(showProgress = true) }
        heading.addView(syncButton)
        content.addView(heading)

        content.addView(body("Paired as ${currentSession.role} · device ${shortId(currentSession.deviceId)}"))
        content.addView(body("Your remote lines stay on the gateway phone. This app connects to them through your server."))
        if (statusMessage.isNotBlank()) content.addView(messageCard(statusMessage, error = statusMessage.startsWith("Offline") || statusMessage.startsWith("Session")))

        content.addView(sectionTitle("Gateway"))
        val currentGateway = gateway
        if (currentGateway == null) {
            content.addView(messageCard("No gateway snapshot is cached yet. Refresh to load the paired gateway."))
        } else {
            val connection = if (currentGateway.online) "Online" else "Offline"
            val heartbeat = currentGateway.lastSeenAt?.let { " · last seen $it" } ?: " · no heartbeat time"
            content.addView(messageCard("${currentGateway.deviceName}: $connection$heartbeat\nMapping revision ${currentGateway.mappingRevision}"))
            val rootState = currentGateway.root?.let { if (it) "root available" else "root unavailable" } ?: "root state unknown"
            val sipState = currentGateway.sipRegistered?.let { if (it) "SIP registered" else "SIP not registered" } ?: "SIP state unknown"
            val power = currentGateway.batteryPercent?.let { " · battery $it%" }.orEmpty()
            content.addView(body("$rootState · $sipState$power"))
        }

        content.addView(sectionTitle("Remote SIM lines"))
        if (sims.isEmpty()) {
            content.addView(messageCard("No SIM bindings are available. The gateway must confirm its SIM mappings first."))
        } else {
            val selectedStillPresent = sims.any { it.simId == selectedSimId }
            if (!selectedStillPresent) selectedSimId = null
            val options = mutableListOf("Choose a remote SIM")
            options += sims.map { line ->
                val details = listOfNotNull(line.carrierName, line.phoneNumber, "revision ${line.mappingRevision}").joinToString(" · ")
                val availability = if (line.canSend) "ready" else line.stateLabel()
                "${line.label} · $details · $availability"
            }
            val spinner = Spinner(this)
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
            val selectedIndex = sims.indexOfFirst { it.simId == selectedSimId }
            spinner.setSelection(if (selectedIndex >= 0) selectedIndex + 1 else 0, false)
            spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                private var first = true
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (first) { first = false; return }
                    val next = sims.getOrNull(position - 1)?.simId
                    if (next != selectedSimId) {
                        saveVisibleDraft()
                        selectedSimId = next
                        showDashboard()
                    }
                }
            }
            content.addView(spinner)
            sims.sortedBy { it.slotIndex }.forEach { line ->
                val carrier = line.carrierName?.let { " · $it" }.orEmpty()
                val number = line.phoneNumber?.let { " · $it" }.orEmpty()
                val status = if (line.canSend) "confirmed" else line.stateLabel()
                content.addView(body("Slot ${line.slotIndex + 1}: ${line.label}$carrier$number · $status · revision ${line.mappingRevision}"))
            }
        }

        content.addView(sectionTitle("Calls"))
        content.addView(body("Calling is not enabled yet. Gateway audio and server calling setup still need to be verified."))
        val callButton = button("Calling unavailable")
        callButton.isEnabled = false
        content.addView(callButton)

        content.addView(sectionTitle("SMS inbox"))
        val search = edit("Search message text or number", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        search.setText(searchQuery)
        content.addView(search)
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

        content.addView(sectionTitle("Compose SMS"))
        val selected = sims.firstOrNull { it.simId == selectedSimId }
        if (selected == null) {
            content.addView(body("Choose a remote SIM above before writing. If a mapping changed, refresh and select a line again."))
        } else {
            val readiness = if (selected.canSend) "Selected: ${selected.label}${selected.phoneNumber?.let { " · $it" }.orEmpty()}" else "Cannot send: ${selected.label} is ${selected.stateLabel()}. Refresh the SIM mapping."
            content.addView(messageCard(readiness, error = !selected.canSend))
            content.addView(label("Recipient"))
            val recipient = edit("Phone number or short code", InputType.TYPE_CLASS_PHONE)
            val body = edit("Write an SMS", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
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
            content.addView(recipient)
            content.addView(body)
            val send = button("Send SMS")
            send.isEnabled = selected.canSend
            send.setOnClickListener { confirmSend(selected, recipient.text.toString(), body.text.toString()) }
            content.addView(send)
        }

        val pending = database.loadPendingTasks()
        if (pending.isNotEmpty()) {
            content.addView(sectionTitle("Unconfirmed submissions"))
            content.addView(body("These submissions were saved while the server result was unknown. Check status or continue the same saved submission."))
            pending.forEach { record ->
                val label = if (retryAction(record.taskKey, record.serverId) == RetryAction.RETRY_SAME_KEY) "Retry same task" else "Check status"
                val retry = smallButton("$label · ${record.to.orEmpty()}")
                retry.setOnClickListener { retryOrCheck(record) }
                content.addView(retry)
            }
        }

        val unpair = smallButton("Unpair this phone")
        unpair.setOnClickListener { confirmUnpair() }
        content.addView(unpair)
        installContent(content)
    }

    private fun renderMessages() {
        val list = smsList ?: return
        list.removeAllViews()
        val filter = searchQuery.trim().lowercase()
        val simId = selectedSimId
        if (simId != null) {
            val records = database.loadMessages(simId).filter { matchesSearch(it, filter) }
            if (records.isEmpty()) list.addView(body("No saved messages on this line yet.")) else {
                records.groupBy { it.peerAddress().ifBlank { "Unknown contact" } }.forEach { (peer, messages) ->
                    list.addView(sectionTitle("Conversation · $peer"))
                    messages.forEach { message -> list.addView(messageCardView(message, canReply = message.simId != null)) }
                }
            }
        } else {
            list.addView(body("Select a line to view its conversations."))
        }
        val unknown = database.loadMessages(null).filter { matchesSearch(it, filter) }
        if (unknown.isNotEmpty()) {
            list.addView(sectionTitle("SIM could not be resolved"))
            list.addView(body("Unknown-SIM messages stay separate. Reply on the original SIM is disabled."))
            unknown.forEach { list.addView(messageCardView(it, canReply = false)) }
        }
    }

    private fun messageCardView(message: SmsRecord, canReply: Boolean): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(if (message.direction == "outbound") Color.rgb(232, 243, 237) else Color.WHITE)
        }
        val address = message.peerAddress().ifBlank { "Unknown sender" }
        card.addView(label(if (message.direction == "inbound") "From $address" else "To $address"))
        card.addView(body(message.text))
        card.addView(body("${SmsStatus.display(message.status)} · ${message.createdAt}"))
        if (message.partCount != null && message.partCount > 1) {
            card.addView(body("${message.parts.count { it.state == "submitted" || it.state == "delivered" }} / ${message.partCount} parts submitted"))
        }
        message.parts.filter { !it.error.isNullOrBlank() }.forEach { part ->
            card.addView(body("Part ${part.index + 1}: ${part.error}"))
        }
        if (message.direction == "inbound" && canReply && !message.from.isNullOrBlank()) {
            val reply = smallButton("Reply on original SIM")
            reply.setOnClickListener {
                val originalLine = sims.firstOrNull { it.simId == message.simId }
                if (originalLine == null) {
                    statusMessage = "Original SIM mapping is unavailable. Refresh and choose a confirmed line."
                    showDashboard()
                } else {
                    selectedSimId = originalLine.simId
                    showDashboard()
                    composeRecipient?.setText(message.from)
                    composeRecipient?.setSelection(message.from.length)
                }
            }
            card.addView(reply)
        } else if (message.direction == "inbound" && message.simId == null) {
            card.addView(body("SIM is unknown; original-line reply is unavailable."))
        }
        if (message.taskKey != null && (message.status == SmsStatus.UNKNOWN || message.status == SmsStatus.SUBMITTING)) {
            val action = if (retryAction(message.taskKey, message.serverId) == RetryAction.RETRY_SAME_KEY) "Retry this task with the same key" else "Check server state"
            val button = smallButton(action)
            button.setOnClickListener { retryOrCheck(message) }
            card.addView(button)
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
        AlertDialog.Builder(this)
            .setTitle("Send from ${line.label}$lineNumber?")
            .setMessage("To: $recipient\n\n${text.take(240)}$offlineNotice")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create SMS task") { _, _ -> submitNewTask(line, currentGateway, recipient, text) }
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
        AlertDialog.Builder(this)
            .setTitle("Retry the same SMS task?")
            .setMessage("Continue this saved submission on its original SIM line. If the first request already reached the server, it will return that same SMS task instead of creating another.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Retry same task") { _, _ -> submitExistingTask(record, envelope) }
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
        AlertDialog.Builder(this)
            .setTitle("Unpair this phone?")
            .setMessage("The server will revoke this client's token family. Local SMS and gateway caches will be cleared after the server confirms.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Unpair") { _, _ ->
                executor.execute {
                    try {
                        client().revoke()
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
        setPadding(dp(18), dp(18), dp(18), dp(24))
        setBackgroundColor(Color.rgb(245, 247, 245))
    }

    private fun installContent(content: LinearLayout) {
        val scroll = ScrollView(this)
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(scroll)
    }

    private fun title(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 24f
        setTextColor(Color.rgb(28, 45, 34))
        setPadding(0, dp(8), dp(8), dp(12))
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTextColor(Color.rgb(32, 69, 47))
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(0, dp(20), 0, dp(8))
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.rgb(70, 79, 73))
        setPadding(0, dp(8), 0, dp(4))
    }

    private fun body(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.rgb(54, 62, 57))
        setPadding(0, dp(4), 0, dp(6))
        setTextIsSelectable(true)
    }

    private fun messageCard(text: String, error: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(if (error) Color.rgb(111, 44, 39) else Color.rgb(46, 58, 49))
        setPadding(dp(12), dp(11), dp(12), dp(11))
        setBackgroundColor(if (error) Color.rgb(252, 236, 232) else Color.WHITE)
        setTextIsSelectable(true)
    }

    private fun edit(hint: String, inputTypeValue: Int): EditText = EditText(this).apply {
        this.hint = hint
        inputType = inputTypeValue
        textSize = 16f
        setPadding(dp(12), dp(8), dp(12), dp(8))
        setBackgroundColor(Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(3)
            bottomMargin = dp(5)
        }
    }

    private fun button(text: String): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
            bottomMargin = dp(5)
        }
    }

    private fun smallButton(text: String): Button = button(text).apply {
        textSize = 13f
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showToast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
