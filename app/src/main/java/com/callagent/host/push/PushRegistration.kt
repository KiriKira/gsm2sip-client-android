package com.callagent.host.push

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.callagent.host.BuildConfig
import com.callagent.host.data.ApiClient
import com.callagent.host.data.ApiFailure
import com.callagent.host.data.HostSession
import com.callagent.host.data.ServerPushSignal
import com.callagent.host.data.SessionStore
import com.callagent.host.data.normalizeApiBaseUrl
import com.callagent.host.mesh.HostNodes
import com.callagent.host.mesh.NodeMonitor
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * What this phone last learned about push wake-ups from one node. It is read
 * from local state only: showing it never contacts the node or the relay.
 */
data class NodePushStatus(
    val kind: Kind,
    /** Consecutive transient failures, for [Kind.RETRYING]. */
    val failures: Int = 0,
    /** When the node last answered, or 0 if unknown. */
    val checkedAt: Long = 0L,
    /** When the phone next asks on its own, or 0 if unknown. */
    val nextAttemptAt: Long = 0L,
    /** For [Kind.RETRYING]: an earlier registration still stands. */
    val stillRegistered: Boolean = false
) {
    enum class Kind {
        /** This build has no relay or no Firebase. */
        UNAVAILABLE,
        /** No FCM token or relay endpoint yet. */
        NO_ENDPOINT,
        /** The relay could not be reached; the phone asks it again later. */
        RELAY_RETRYING,
        /** Not asked yet with the current endpoint. */
        PENDING,
        REGISTERED,
        /** The server does not send push wake-ups. */
        UNSUPPORTED,
        REFUSED,
        /** The node or its route to the relay failed; the phone asks again later. */
        RETRYING
    }
}

/**
 * Keeps this phone's push endpoint registered on every paired node that
 * supports push. The FCM token goes only to the Kyubey relay this build
 * names, which returns an endpoint URL; nodes see only that URL and need no
 * Firebase credentials. Nothing runs without Firebase, Google Play services
 * or a relay URL, and a node is contacted only when
 * [PushRegistrationPolicy] says so, never on a timer of its own. A build
 * without push withdraws what an earlier build registered, so nodes stop
 * posting wake-ups this build cannot receive.
 */
object PushRegistration {
    private const val TAG = "HostPush"
    private const val PREFS = "host-push"
    private const val KEY_TOKEN = "token"
    private const val KEY_ENDPOINT = "endpoint"
    private const val KEY_ENDPOINT_RELAY = "endpoint_relay"
    private const val KEY_RELAY = "relay"
    private const val NODE_PREFIX = "node."
    /** Stands in for the endpoint hash in a node's state while withdrawing. */
    private const val WITHDRAWING = "withdraw"
    private const val PASS_INTERVAL_MS = 10 * 60 * 1000L
    @Volatile private var lastPassAt = 0L
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "push-registration") }
    private val tokenRequested = AtomicBoolean()
    /** Nodes already asked again in this process because their server started advertising push. */
    private val rechecked = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun available(context: Context): Boolean = BuildConfig.PUSH_RELAY_URL.isNotEmpty() &&
        runCatching { FirebaseApp.getApps(context.applicationContext).isNotEmpty() }.getOrDefault(false)

    /** FCM issued a new token: it is exchanged for a new endpoint and every node registers that. */
    fun onNewToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_TOKEN, token).remove(KEY_ENDPOINT).apply()
        sync(context, force = true)
    }

    /**
     * Registers where due. Background syncs call this often, so without
     * [force] (a new token, pairing or node) a pass runs at most every ten
     * minutes, and even then only nodes that are due are contacted. A server
     * newly seen advertising push skips that wait.
     */
    fun sync(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        val withdraw = !available(app)
        if (withdraw && prefs(app).all.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        val newlyAdvertised = ServerPushSignal.consumeChange()
        if (!force && !newlyAdvertised && lastPassAt != 0L && now - lastPassAt < PASS_INTERVAL_MS) return
        lastPassAt = now
        executor.execute {
            runCatching { if (withdraw) runWithdraw(app) else runSync(app) }
                .onFailure { Log.w(TAG, "registration pass failed: ${it.javaClass.simpleName}") }
        }
    }

    /** Whether the node behind [sessionInstanceId] wakes this phone at its current endpoint. */
    fun isReady(context: Context, sessionInstanceId: String): Boolean {
        if (!available(context)) return false
        val prefs = prefs(context)
        val endpoint = currentEndpoint(prefs) ?: return false
        return PushRegistrationPolicy.ready(
            PushRegistrationPolicy.NodeState.decode(prefs.getString(NODE_PREFIX + sessionInstanceId, null)), hash(endpoint))
    }

    /** The push state of the node whose pairing is [sessionInstanceId], from local state only. */
    fun status(context: Context, sessionInstanceId: String): NodePushStatus {
        if (!available(context)) return NodePushStatus(NodePushStatus.Kind.UNAVAILABLE)
        val prefs = prefs(context)
        val endpoint = currentEndpoint(prefs)
        if (endpoint == null) {
            val relay = PushRegistrationPolicy.NodeState.decode(prefs.getString(KEY_RELAY, null))
            return if (relay.failures > 0) {
                NodePushStatus(NodePushStatus.Kind.RELAY_RETRYING, relay.failures, relay.checkedAt, relay.nextAttemptAt)
            } else {
                NodePushStatus(NodePushStatus.Kind.NO_ENDPOINT)
            }
        }
        val state = PushRegistrationPolicy.NodeState.decode(prefs.getString(NODE_PREFIX + sessionInstanceId, null))
        if (state.tokenHash != hash(endpoint)) return NodePushStatus(NodePushStatus.Kind.PENDING)
        // State saved before outcomes were recorded: a registration was confirmed a day before its recheck.
        val checkedAt = state.checkedAt.takeIf { it > 0L }
            ?: (state.nextAttemptAt - PushRegistrationPolicy.RECHECK_MS).takeIf { state.registered && it > 0L } ?: 0L
        val kind = when {
            state.outcome == PushRegistrationPolicy.Outcome.RETRY || (!state.registered && state.failures > 0) -> NodePushStatus.Kind.RETRYING
            state.registered -> NodePushStatus.Kind.REGISTERED
            state.outcome == PushRegistrationPolicy.Outcome.UNSUPPORTED -> NodePushStatus.Kind.UNSUPPORTED
            state.outcome == PushRegistrationPolicy.Outcome.REFUSED -> NodePushStatus.Kind.REFUSED
            else -> NodePushStatus.Kind.PENDING
        }
        return NodePushStatus(kind, state.failures, checkedAt, state.nextAttemptAt, stillRegistered = state.registered)
    }

    /**
     * The user asked to check now: nodes and a relay that are not registered
     * are asked at once instead of at their backoff time. Registered nodes
     * keep their daily recheck.
     */
    fun retryNow(context: Context) {
        val app = context.applicationContext
        if (!available(app)) return
        val prefs = prefs(app)
        val due = prefs.all.filter { (key, value) ->
            (key.startsWith(NODE_PREFIX) || key == KEY_RELAY) && value is String &&
                !PushRegistrationPolicy.NodeState.decode(value).registered
        }
        if (due.isNotEmpty()) {
            prefs.edit().apply {
                due.forEach { (key, value) ->
                    putString(key, PushRegistrationPolicy.NodeState.decode(value as String).copy(nextAttemptAt = 0L).encode())
                }
            }.apply()
        }
        if (prefs.getString(KEY_TOKEN, null) == null) tokenRequested.set(false)
        sync(app, force = true)
    }

    /** Every pairing this phone holds, with the store its session lives in. */
    internal fun pairings(context: Context): List<Pair<HostSession, SessionStore>> {
        val app = context.applicationContext
        val nodes = HostNodes.nodes(app)
        if (nodes.isEmpty()) {
            val store = SessionStore(app)
            return listOfNotNull(runCatching { store.read() }.getOrNull()?.takeIf { it.role == "client" }?.let { it to store })
        }
        return nodes.mapNotNull { node ->
            HostNodes.sessionFor(app, node)?.takeIf { it.role == "client" }?.let { it to HostNodes.storeFor(app, node) }
        }
    }

    private fun runSync(app: Context) {
        val prefs = prefs(app)
        val token = prefs.getString(KEY_TOKEN, null)
        if (token == null) {
            requestToken(app)
            return
        }
        val endpoint = endpointFor(app, token) ?: return
        val endpointHash = hash(endpoint)
        val live = mutableSetOf<String>()
        var contacted = false
        for ((session, store) in pairings(app)) {
            val key = NODE_PREFIX + session.sessionInstanceId
            live += key
            val state = PushRegistrationPolicy.NodeState.decode(prefs.getString(key, null))
            val now = System.currentTimeMillis()
            // A node marked without push before its server was upgraded: ask once now, not in a day.
            val upgraded = !state.registered && ServerPushSignal.advertises(normalizeApiBaseUrl(session.apiBaseUrl)) &&
                rechecked.add(key)
            if (!upgraded && !PushRegistrationPolicy.shouldAttempt(state, endpointHash, now)) continue
            contacted = true
            var endpointRejected = false
            val outcome = try {
                val api = ApiClient(session.apiBaseUrl, store)
                if (!api.pushAvailable()) PushRegistrationPolicy.Outcome.UNSUPPORTED
                else {
                    api.registerPushEndpoint(endpoint)
                    PushRegistrationPolicy.Outcome.REGISTERED
                }
            } catch (failure: ApiFailure) {
                endpointRejected = failure.code == "PUSH_ENDPOINT_REJECTED"
                if (failure.retryable) PushRegistrationPolicy.Outcome.RETRY else PushRegistrationPolicy.Outcome.REFUSED
            } catch (_: Exception) {
                PushRegistrationPolicy.Outcome.RETRY
            }
            prefs.edit().putString(key, PushRegistrationPolicy.after(outcome, state, endpointHash, now).encode()).apply()
            Log.i(TAG, "node ${session.apiBaseUrl.hashCode()} push=${outcome.name.lowercase()}")
            // The relay no longer reaches the phone through this endpoint (a
            // new relay key, or a relay for another Firebase project): ask it
            // for a new one, but not before a day has passed.
            if (endpointRejected) {
                val relay = PushRegistrationPolicy.NodeState.decode(prefs.getString(KEY_RELAY, null))
                prefs.edit().remove(KEY_ENDPOINT).putString(KEY_RELAY,
                    PushRegistrationPolicy.after(PushRegistrationPolicy.Outcome.REFUSED, relay, relayKey(token), now).encode()).apply()
                announce(app)
                return
            }
        }
        if (contacted) announce(app)
        // Forget nodes this phone no longer pairs with.
        prefs.all.keys.filter { it.startsWith(NODE_PREFIX) && it !in live }.takeIf { it.isNotEmpty() }?.let { stale ->
            prefs.edit().apply { stale.forEach(::remove) }.apply()
        }
    }

    /**
     * This build has no push (no relay URL or no Firebase): ask every node an
     * earlier build registered with to forget the endpoint, then forget the
     * token. Unreachable nodes are asked again with the usual backoff.
     */
    private fun runWithdraw(app: Context) {
        val prefs = prefs(app)
        val live = mutableSetOf<String>()
        for ((session, store) in pairings(app)) {
            val key = NODE_PREFIX + session.sessionInstanceId
            if (!prefs.contains(key)) continue
            live += key
            val state = PushRegistrationPolicy.NodeState.decode(prefs.getString(key, null))
            val now = System.currentTimeMillis()
            if (!PushRegistrationPolicy.shouldAttempt(state, WITHDRAWING, now)) continue
            val done = try {
                ApiClient(session.apiBaseUrl, store).deletePushEndpoint()
                true
            } catch (failure: ApiFailure) {
                // A revoked or unknown device holds no endpoint either.
                !failure.retryable
            } catch (_: Exception) {
                false
            }
            Log.i(TAG, "node ${session.apiBaseUrl.hashCode()} push=${if (done) "withdrawn" else "withdraw-retry"}")
            if (done) {
                prefs.edit().remove(key).apply()
                live -= key
            } else {
                prefs.edit().putString(key, PushRegistrationPolicy.after(
                    PushRegistrationPolicy.Outcome.RETRY, state, WITHDRAWING, now).encode()).apply()
            }
        }
        // Nodes no longer paired revoked this phone's session, and its endpoint with it.
        if (live.isEmpty()) prefs.edit().clear().apply()
    }

    /** Lets a visible node list show the new push state. */
    private fun announce(app: Context) {
        runCatching { app.sendBroadcast(Intent(NodeMonitor.ACTION_NODE_CHANGED).setPackage(app.packageName)) }
    }

    /** The cached endpoint, if it came from the relay this build uses. */
    private fun currentEndpoint(prefs: android.content.SharedPreferences): String? =
        prefs.getString(KEY_ENDPOINT, null)?.takeIf { prefs.getString(KEY_ENDPOINT_RELAY, null) == BuildConfig.PUSH_RELAY_URL }

    /** The relay endpoint for [token], asking the relay only when there is none and its retry time has come. */
    private fun endpointFor(app: Context, token: String): String? {
        val prefs = prefs(app)
        currentEndpoint(prefs)?.let { return it }
        // Keyed to the relay too, so a build with another relay asks it at once.
        val tokenHash = relayKey(token)
        val state = PushRegistrationPolicy.NodeState.decode(prefs.getString(KEY_RELAY, null))
        val now = System.currentTimeMillis()
        if (state.tokenHash == tokenHash && now < state.nextAttemptAt && !state.registered) return null
        val endpoint = runCatching { KyubeyClient(BuildConfig.PUSH_RELAY_URL).register(token) }
            .onFailure { Log.w(TAG, "relay registration failed: ${it.javaClass.simpleName}") }.getOrNull()
        val outcome = if (endpoint != null) PushRegistrationPolicy.Outcome.REGISTERED else PushRegistrationPolicy.Outcome.RETRY
        prefs.edit().putString(KEY_RELAY, PushRegistrationPolicy.after(outcome, state, tokenHash, now).encode())
            .apply { if (endpoint != null) putString(KEY_ENDPOINT, endpoint).putString(KEY_ENDPOINT_RELAY, BuildConfig.PUSH_RELAY_URL) }.apply()
        return endpoint
    }

    /** Asks FCM for the token once per process; the answer arrives through [onNewToken]. */
    private fun requestToken(app: Context) {
        if (!tokenRequested.compareAndSet(false, true)) return
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> onNewToken(app, token) }
        }.onFailure { Log.w(TAG, "FCM unavailable: ${it.javaClass.simpleName}") }
    }

    private fun relayKey(token: String) = hash(BuildConfig.PUSH_RELAY_URL + "\n" + token)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
}
