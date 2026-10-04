package com.callagent.host.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.callagent.host.MainActivity
import com.callagent.host.R
import com.callagent.host.data.ClientDatabase
import com.callagent.host.data.HostSession
import com.callagent.host.data.HostSyncEngine
import com.callagent.host.data.SessionNeedsPairing
import com.callagent.host.data.SessionStore
import com.callagent.host.data.SessionChanged
import com.callagent.host.data.SyncBudgetExhausted
import com.callagent.host.data.clientDatabaseName
import com.callagent.host.data.sameSessionInstance
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

class HostBackgroundService : Service() {
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val generation = AtomicLong(1L)
    private val syncQueued = AtomicBoolean(false)
    private val periodicStarted = AtomicBoolean(false)
    private val authReconnectPending = AtomicBoolean(false)
    private val webSocketLock = Any()
    private val socketGeneration = AtomicLong(0L)
    private lateinit var sessionStore: SessionStore
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var notificationManager: NotificationManager
    private val notificationCoalescers = mutableMapOf<String, InboundNotificationCoalescer>()
    private val wsClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .build()
    }
    @Volatile private var socket: WebSocket? = null
    @Volatile private var activeSession: HostSession? = null
    private val reconnectAttempt = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile private var callbackRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            setConnection("Connecting to server")
            enqueueSync()
            scheduleWebSocketReconnect(500L)
        }

        override fun onLost(network: Network) {
            val active = runCatching { connectivityManager.activeNetwork }.getOrNull()
            if (active == null) setConnection("Waiting for network")
        }
    }

    override fun onCreate() {
        super.onCreate()
        synchronized(HostBackgroundService::class.java) {
            liveService = this
            isRunning = true
        }
        sessionStore = SessionStore(applicationContext)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
        registerConnectivityCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            return if (stopFromUser()) START_NOT_STICKY else START_STICKY
        }

        val prefs = RuntimeState.preferences(this)
        val session = runCatching { sessionStore.read() }.getOrNull()
        if (intent == null && !HostBackgroundRuntime.restoreAllowed(this)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!HostBackgroundPolicy.shouldRestore(
                prefs.getBoolean(RuntimeState.KEY_ENABLED, HostBackgroundPolicy.DEFAULT_ENABLED),
                session?.role == "client"
            ) || !isBoundToOptIn(session)
        ) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {
            startForegroundCompat()
        } catch (_: RuntimeException) {
            prefs.edit().putBoolean(RuntimeState.KEY_ENABLED, false).commit()
            RuntimeState.update(this, connection = "Stopped", issue = "Android could not display the required background notification. Enable notifications and start background sync again.")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val previousSession = activeSession
        if (previousSession != null && !previousSession.sameSessionInstance(session!!)) {
            generation.incrementAndGet()
            closeWebSocket()
            cancelPendingNotifications(previousSession)
        }
        activeSession = session
        RuntimeState.update(this, connection = "Connecting", issue = null)
        ensureWebSocket()
        enqueueSync()
        if (periodicStarted.compareAndSet(false, true)) {
            scheduler.scheduleWithFixedDelay(
                {
                    if (isOptedInAndPaired()) enqueueSync()
                    else stopForInvalidSession(
                        "The paired account changed. Enable background sync for the current account.",
                        generation.get(),
                        runCatching { sessionStore.read() }.getOrNull(),
                        onlyIfStillInvalid = true
                    )
                },
                FALLBACK_SYNC_MS,
                FALLBACK_SYNC_MS,
                TimeUnit.MILLISECONDS
            )
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        generation.incrementAndGet()
        closeWebSocket()
        unregisterConnectivityCallback()
        scheduler.shutdownNow()
        synchronized(notificationCoalescers) { notificationCoalescers.clear() }
        synchronized(HostBackgroundService::class.java) {
            if (liveService === this) liveService = null
            isRunning = false
        }
        if (RuntimeState.preferences(this).getBoolean(RuntimeState.KEY_ENABLED, false)) {
            RuntimeState.update(this, connection = "Waiting for service restart")
        } else {
            RuntimeState.update(this, connection = "Stopped")
        }
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val notification = foregroundNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            // Android 14+ declares the SMS cross-device remoteMessaging type; older versions use legacy NONE.
            val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING else 0
            startForeground(FOREGROUND_NOTIFICATION_ID, notification, type)
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun enqueueSync() {
        if (!isOptedInAndPaired() || !syncQueued.compareAndSet(false, true)) return
        val expectedGeneration = generation.get()
        scheduler.execute {
            try {
                if (expectedGeneration != generation.get()) return@execute
                runSync(expectedGeneration)
            } finally {
                syncQueued.set(false)
                if (expectedGeneration != generation.get() && isOptedInAndPaired()) enqueueSync()
            }
        }
    }

    private fun runSync(expectedGeneration: Long) {
        val bound = sessionStore.read()
        if (bound == null || bound.role != "client") {
            stopForInvalidSession(expectedGeneration = expectedGeneration)
            return
        }
        val wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:host-sync"
        ).apply { setReferenceCounted(false) }
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val deadline = startedAt + WAKE_LOCK_TIMEOUT_MS
        try {
            wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
            val expectedRunGeneration = expectedGeneration
            val scopedCoalescer = notificationCoalescer(bound, expectedRunGeneration)
            HostSyncEngine(
                context = this,
                boundSession = bound,
                sessionIsCurrent = { expected -> isCurrent(expected, expectedGeneration) },
                persistInboundNotificationIds = scopedCoalescer::record
            ).use { engine ->
                engine.sync(deadline)
            }
            if (!isCurrent(bound, expectedGeneration)) return
            RuntimeState.update(this, connection = if (socket != null) "Live updates connected" else "Periodic HTTPS sync", lastSyncAt = System.currentTimeMillis(), issue = null)
            sendBroadcast(Intent(HostBackgroundRuntime.BACKGROUND_SYNCED).setPackage(packageName))
            val latest = sessionStore.read()
            if (latest != null && latest.sameSessionInstance(bound) && latest.accessToken != bound.accessToken) {
                authReconnectPending.set(false)
                closeWebSocket()
                scheduleWebSocketReconnect(300L)
            } else if (authReconnectPending.compareAndSet(true, false)) {
                scheduleWebSocketReconnect(300L)
            }
        } catch (_: SyncBudgetExhausted) {
            if (isCurrent(bound, expectedGeneration)) {
                RuntimeState.update(this, connection = "Periodic HTTPS sync", issue = "Sync paused at its time limit; it will continue on the next check.")
            }
        } catch (_: SessionNeedsPairing) {
            if (thisRunStillOwnsSession(bound, expectedGeneration)) {
                stopForInvalidSession("Session expired or was revoked. Pair again to resume background sync.", expectedGeneration, bound)
            }
        } catch (_: SessionChanged) {
            if (thisRunStillOwnsSession(bound, expectedGeneration)) {
                stopForInvalidSession("The paired account changed. Enable background sync for the current account.", expectedGeneration, bound)
            }
        } catch (_: Exception) {
            if (isCurrent(bound, expectedGeneration)) {
                RuntimeState.update(this, connection = "Periodic HTTPS sync", issue = "Server unavailable; retrying over HTTPS.")
            }
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
            val currentSession = runCatching { sessionStore.read() }.getOrNull()
            val stillEnabled = RuntimeState.preferences(this).getBoolean(RuntimeState.KEY_ENABLED, false)
            // Ordinary service recreation preserves alerts for the same pairing. Only an
            // explicit stop or pairing change discards the old session's pending alerts.
            if (!stillEnabled || currentSession?.sameSessionInstance(bound) != true) {
                cancelPendingNotifications(bound)
            }
        }
    }

    private fun isCurrent(expected: HostSession, expectedGeneration: Long): Boolean {
        if (expectedGeneration != generation.get()) return false
        if (!isBoundToOptIn(expected)) return false
        val current = runCatching { sessionStore.read() }.getOrNull() ?: return false
        return current.sameSessionInstance(expected) && current.role == "client"
    }

    private fun thisRunStillOwnsSession(expected: HostSession, expectedGeneration: Long): Boolean {
        if (expectedGeneration != generation.get()) return false
        val current = runCatching { sessionStore.read() }.getOrNull() ?: return true
        return current.sameSessionInstance(expected)
    }

    private fun isOptedInAndPaired(): Boolean {
        val session = runCatching { sessionStore.read() }.getOrNull()
        return isBoundToOptIn(session)
    }

    private fun isBoundToOptIn(session: HostSession?): Boolean {
        if (session == null || session.role != "client") return false
        val state = RuntimeState.preferences(this)
        return state.getBoolean(RuntimeState.KEY_ENABLED, false) &&
            state.getString(RuntimeState.KEY_SESSION_STAMP, null) == sessionStamp(session) &&
            state.getString(RuntimeState.KEY_SESSION_DB_NAME, null) == clientDatabaseName(session.apiBaseUrl, session.ownerId, session.deviceId)
    }

    private fun stopForInvalidSession(
        message: String = "",
        expectedGeneration: Long? = null,
        expectedSession: HostSession? = null,
        onlyIfStillInvalid: Boolean = false
    ) {
        if (expectedGeneration != null && expectedGeneration != generation.get()) return
        val current = runCatching { sessionStore.read() }.getOrNull()
        if (expectedSession != null) {
            if (current != null && !current.sameSessionInstance(expectedSession)) return
            if (RuntimeState.preferences(this).getString(RuntimeState.KEY_SESSION_STAMP, null) != sessionStamp(expectedSession)) return
            if (onlyIfStillInvalid && isBoundToOptIn(expectedSession)) return
        }
        val preferences = RuntimeState.preferences(this)
        if (!preferences.getBoolean(RuntimeState.KEY_ENABLED, false)) return
        preferences.edit().putBoolean(RuntimeState.KEY_ENABLED, false).commit()
        closeWebSocket()
        generation.incrementAndGet()
        cancelPendingNotifications()
        stopForeground(STOP_FOREGROUND_REMOVE)
        RuntimeState.update(this, connection = "Stopped", issue = message.takeIf { it.isNotBlank() })
        stopSelf()
    }

    private fun stopFromUser(): Boolean {
        if (!RuntimeState.preferences(this).edit().putBoolean(RuntimeState.KEY_ENABLED, false).commit()) {
            RuntimeState.update(this, issue = "Could not save the stopped state. Tap Stop again to retry.")
            return false
        }
        closeWebSocket()
        generation.incrementAndGet()
        cancelPendingNotifications()
        stopForeground(STOP_FOREGROUND_REMOVE)
        RuntimeState.update(this, connection = "Stopped", issue = null)
        stopSelf()
        return true
    }

    private fun setConnection(label: String) {
        if (isOptedInAndPaired()) RuntimeState.update(this, connection = label)
    }

    private fun notificationCoalescer(session: HostSession, expectedGeneration: Long): InboundNotificationCoalescer {
        val databaseName = clientDatabaseName(session.apiBaseUrl, session.ownerId, session.deviceId)
        return synchronized(notificationCoalescers) {
            val key = "$databaseName:${session.sessionInstanceId}:$expectedGeneration"
            notificationCoalescers.getOrPut(key) { InboundNotificationCoalescer(this, databaseName, session, expectedGeneration) }
        }
    }

    private fun cancelPendingNotifications(session: HostSession? = null) {
        val selected = synchronized(notificationCoalescers) {
            if (session == null) notificationCoalescers.values.toList()
            else notificationCoalescers.values.filter { it.isForSession(session) }
        }
        selected.forEach { it.cancelAndClear() }
        if (session == null) {
            val state = RuntimeState.preferences(this)
            clearPending(this, state.getString(RuntimeState.KEY_SESSION_DB_NAME, null), state.getString(RuntimeState.KEY_SESSION_STAMP, null))
        } else {
            val dbName = clientDatabaseName(session.apiBaseUrl, session.ownerId, session.deviceId)
            clearPending(this, dbName, sessionStamp(session))
        }
    }

    private fun ensureWebSocket() {
        synchronized(webSocketLock) {
            if (!isOptedInAndPaired() || socket != null) return
            val session = sessionStore.read() ?: return
            val expectedGeneration = generation.get()
            val request = try {
                Request.Builder()
                    .url(hostWakeWebSocketUrl(session.apiBaseUrl))
                    .header("Authorization", "Bearer ${session.accessToken}")
                    .build()
            } catch (_: Exception) {
                scheduleReconnectWithBackoff()
                return
            }
            val localSocketGeneration = socketGeneration.incrementAndGet()
            socket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                if (!isSocketCurrent(session, expectedGeneration, localSocketGeneration)) {
                    webSocket.close(1000, null)
                    return
                }
                reconnectAttempt.set(0)
                setConnection("Live updates connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isSocketCurrent(session, expectedGeneration, localSocketGeneration)) return
                val hint = runCatching { JSONObject(text) }.getOrNull() ?: return
                if (hint.optInt("protocol_version", -1) == 1 && hint.optString("type") == "sync_required") {
                    // The websocket is only a wake hint; all data comes from the authenticated HTTPS event API.
                    enqueueSync()
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = Unit

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                clearSocket(webSocket)
                if (isSocketCurrent(session, expectedGeneration, localSocketGeneration)) {
                    setConnection("Periodic HTTPS sync")
                    if (code == 1008) {
                        authReconnectPending.set(true)
                        enqueueSync()
                        return
                    }
                    enqueueSync()
                    scheduleReconnectWithBackoff()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                clearSocket(webSocket)
                if (!isSocketCurrent(session, expectedGeneration, localSocketGeneration)) return
                // A 401/1008 close is repaired by HTTPS token refresh in the next sync before reconnect.
                setConnection("Periodic HTTPS sync")
                if (response?.code == 401) {
                    authReconnectPending.set(true)
                    enqueueSync()
                    return
                }
                enqueueSync()
                scheduleReconnectWithBackoff()
            }
            })
        }
    }

    private fun isSocketCurrent(expected: HostSession, expectedGeneration: Long, expectedSocketGeneration: Long): Boolean {
        if (expectedGeneration != generation.get() || !isOptedInAndPaired()) return false
        if (expectedSocketGeneration != socketGeneration.get()) return false
        val current = runCatching { sessionStore.read() }.getOrNull() ?: return false
        return isCurrentBackgroundSession(expected, current)
    }

    private fun clearSocket(candidate: WebSocket) = synchronized(webSocketLock) {
        if (socket === candidate) {
            socket = null
        }
    }

    private fun closeWebSocket() {
        val old = synchronized(webSocketLock) {
            socketGeneration.incrementAndGet()
            socket.also { socket = null }
        }
        old?.cancel()
    }

    private fun scheduleReconnectWithBackoff() {
        if (!isOptedInAndPaired()) return
        val attempt = reconnectAttempt.getAndIncrement()
        val delay = HostBackgroundPolicy.backoffDelay(attempt, Random.nextDouble(0.75, 1.25))
        scheduleWebSocketReconnect(delay)
    }

    private fun scheduleWebSocketReconnect(delayMs: Long) {
        if (!isOptedInAndPaired()) return
        val expectedGeneration = generation.get()
        scheduler.schedule({
            if (expectedGeneration == generation.get() && socket == null && isOptedInAndPaired()) ensureWebSocket()
        }, delayMs.coerceAtLeast(500L), TimeUnit.MILLISECONDS)
    }

    private fun registerConnectivityCallback() {
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            callbackRegistered = true
        } catch (_: RuntimeException) {
            callbackRegistered = false
        }
    }

    private fun unregisterConnectivityCallback() {
        if (!callbackRegistered) return
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        callbackRegistered = false
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Background server sync", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows when secure server synchronization is active."
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_INBOUND, "Inbound SMS alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Generic alerts when new inbound SMS records arrive."
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
    }

    private fun foregroundNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            STOP_PENDING_INTENT,
            Intent(this, HostBackgroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return notificationBuilder(CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.host_icon)
            .setContentTitle("GSM2SIP background sync")
            .setContentText("Secure server sync is active")
            .setContentIntent(openAppPendingIntent())
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun openAppPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        OPEN_APP_PENDING_INTENT,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun notificationBuilder(channel: String): Notification.Builder =
        if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channel) else Notification.Builder(this)

    private inner class InboundNotificationCoalescer(
        private val context: Context,
        private val databaseName: String,
        private val boundSession: HostSession,
        private val expectedGeneration: Long
    ) {
        private val preferences = context.getSharedPreferences(
            "host-background-notifications-$databaseName-${sessionStamp(boundSession)}",
            Context.MODE_PRIVATE
        )
        private val lock = Any()
        private var scheduled = false
        private var scheduledFlush: java.util.concurrent.ScheduledFuture<*>? = null

        init {
            if (readIds(PENDING_IDS).isNotEmpty()) scheduleFlushLocked()
        }

        fun record(ids: List<String>): Boolean {
            if (ids.isEmpty()) return true
            return synchronized(lock) {
                val pending = readIds(PENDING_IDS).toMutableList()
                val recentlyNotified = readIds(NOTIFIED_IDS).toMutableList()
                val additions = ids.distinct().filter { it !in pending && it !in recentlyNotified }
                if (additions.isEmpty()) return@synchronized true
                pending.addAll(additions)
                while (pending.size > MAX_COALESCED_IDS) pending.removeAt(0)
                val saved = preferences.edit().putString(PENDING_IDS, encodeIds(pending)).commit()
                if (saved) scheduleFlushLocked()
                saved
            }
        }

        private fun scheduleFlushLocked(delayOverrideMs: Long? = null) {
            if (scheduled) return
            scheduled = true
            val elapsed = System.currentTimeMillis() - preferences.getLong(LAST_POSTED_AT, 0L)
            val delay = delayOverrideMs ?: (NOTIFICATION_WINDOW_MS - elapsed).coerceAtLeast(0L)
            scheduledFlush = scheduler.schedule({ flush() }, delay, TimeUnit.MILLISECONDS)
        }

        private fun flush() {
            synchronized(lock) {
                scheduled = false
                scheduledFlush = null
                if (!isCurrent(boundSession, expectedGeneration)) return
                val pending = readIds(PENDING_IDS)
                if (pending.isEmpty()) return
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val notificationsAvailable = manager.areNotificationsEnabled() &&
                    manager.getNotificationChannel(CHANNEL_INBOUND)?.importance != NotificationManager.IMPORTANCE_NONE &&
                    (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                if (!notificationsAvailable) {
                    RuntimeState.update(context, issue = "Enable notifications to receive inbound SMS alerts.")
                    scheduleFlushLocked(FALLBACK_SYNC_MS)
                    return
                }
                val summary = notificationBuilder(CHANNEL_INBOUND)
                    .setSmallIcon(R.drawable.host_icon)
                    .setContentTitle("New SMS received")
                    .setContentText(if (pending.size == 1) "Open the app to view the message." else "${pending.size} new messages are available.")
                    .setContentIntent(openAppPendingIntent())
                    .setVisibility(Notification.VISIBILITY_PRIVATE)
                    .setPublicVersion(notificationBuilder(CHANNEL_INBOUND)
                        .setSmallIcon(R.drawable.host_icon)
                        .setContentTitle("New SMS")
                        .setContentText("Open the app to view.")
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .build())
                    .setAutoCancel(true)
                    .build()
                val recentlyNotified = (readIds(NOTIFIED_IDS) + pending).distinct().takeLast(MAX_SEEN_NOTIFICATION_IDS)
                val persisted = preferences.edit()
                    .putString(PENDING_IDS, "[]")
                    .putString(NOTIFIED_IDS, encodeIds(recentlyNotified))
                    .putLong(LAST_POSTED_AT, System.currentTimeMillis())
                    .commit()
                if (!persisted) {
                    scheduleFlushLocked(FALLBACK_SYNC_MS)
                    return
                }
                runCatching { manager.notify(sessionStamp(boundSession), INBOUND_NOTIFICATION_ID, summary) }
                    .onFailure { RuntimeState.update(context, issue = "Android could not post the inbound SMS notification.") }
            }
        }

        fun isForSession(session: HostSession): Boolean = boundSession.sameSessionInstance(session)

        fun cancelAndClear() {
            synchronized(lock) {
                scheduledFlush?.cancel(false)
                scheduledFlush = null
                scheduled = false
                preferences.edit().putString(PENDING_IDS, "[]").commit()
                clearPending(context, databaseName, sessionStamp(boundSession))
            }
        }

        private fun readIds(key: String): List<String> = runCatching {
            val json = JSONArray(preferences.getString(key, "[]") ?: "[]")
            (0 until json.length()).mapNotNull { index -> json.optString(index).takeIf { it.isNotBlank() } }
        }.getOrDefault(emptyList())

        private fun encodeIds(ids: List<String>): String = JSONArray().also { array -> ids.forEach { array.put(it) } }.toString()
    }

    companion object {
        const val ACTION_START = "com.callagent.host.background.START"
        const val ACTION_STOP = "com.callagent.host.background.STOP"
        private const val FOREGROUND_NOTIFICATION_ID = 7101
        private const val INBOUND_NOTIFICATION_ID = 7102
        private const val STOP_PENDING_INTENT = 7103
        private const val OPEN_APP_PENDING_INTENT = 7104
        internal const val CHANNEL_SERVICE = "host-background-service"
        internal const val CHANNEL_INBOUND = "host-inbound-sms"
        private const val WAKE_LOCK_TIMEOUT_MS = 60_000L
        private const val FALLBACK_SYNC_MS = 30_000L
        private const val NOTIFICATION_WINDOW_MS = 12_000L
        private const val MAX_COALESCED_IDS = 99
        private const val MAX_SEEN_NOTIFICATION_IDS = 2_048
        private const val PENDING_IDS = "pending_ids"
        private const val NOTIFIED_IDS = "notified_ids"
        private const val LAST_POSTED_AT = "last_posted_at"
        @Volatile internal var isRunning: Boolean = false
            private set
        @Volatile private var liveService: HostBackgroundService? = null

        internal fun requestSync(context: Context) {
            liveService?.takeIf { isRunning }?.enqueueSync()
        }

        internal fun stopByUser(): Boolean = liveService?.stopFromUser() ?: true

        internal fun clearPending(context: Context, databaseName: String?, sessionStamp: String? = null) {
            if (databaseName.isNullOrBlank()) return
            if (!sessionStamp.isNullOrBlank()) {
                context.getSharedPreferences("host-background-notifications-$databaseName-$sessionStamp", Context.MODE_PRIVATE)
                    .edit().clear().commit()
            }
            val current = runCatching { SessionStore(context.applicationContext).read() }.getOrNull()
            val currentDb = current?.let { clientDatabaseName(it.apiBaseUrl, it.ownerId, it.deviceId) }
            val belongsToNewPairing = current != null && currentDb == databaseName &&
                sessionStamp != null && com.callagent.host.background.sessionStamp(current) != sessionStamp
            if (!belongsToNewPairing) clearBackgroundAlertQueue(context.applicationContext, databaseName)
            if (sessionStamp != null) {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(sessionStamp, INBOUND_NOTIFICATION_ID)
            }
        }
    }
}

private fun clearBackgroundAlertQueue(context: Context, databaseName: String) {
    runCatching {
        val database = ClientDatabase(context, databaseName)
        try {
            database.clearBackgroundNotificationQueue()
        } finally {
            database.close()
        }
    }
}
