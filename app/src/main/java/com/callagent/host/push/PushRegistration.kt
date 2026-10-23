package com.callagent.host.push

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.callagent.host.BuildConfig
import com.callagent.host.data.ApiClient
import com.callagent.host.data.ApiFailure
import com.callagent.host.data.HostSession
import com.callagent.host.data.SessionStore
import com.callagent.host.mesh.HostNodes
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps this phone's push endpoint registered on every paired node that
 * supports push. The FCM token goes only to the Kyubey relay this build
 * names, which returns an endpoint URL; nodes see only that URL and need no
 * Firebase credentials. Nothing runs without Firebase, Google Play services
 * or a relay URL, and a node is contacted only when
 * [PushRegistrationPolicy] says so, never on a timer of its own.
 */
object PushRegistration {
    private const val TAG = "HostPush"
    private const val PREFS = "host-push"
    private const val KEY_TOKEN = "token"
    private const val KEY_ENDPOINT = "endpoint"
    private const val KEY_RELAY = "relay"
    private const val NODE_PREFIX = "node."
    private const val PASS_INTERVAL_MS = 10 * 60 * 1000L
    @Volatile private var lastPassAt = 0L
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "push-registration") }
    private val tokenRequested = AtomicBoolean()

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
     * minutes, and even then only nodes that are due are contacted.
     */
    fun sync(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        if (!available(app)) return
        val now = SystemClock.elapsedRealtime()
        if (!force && lastPassAt != 0L && now - lastPassAt < PASS_INTERVAL_MS) return
        lastPassAt = now
        executor.execute { runCatching { runSync(app) }.onFailure { Log.w(TAG, "registration pass failed: ${it.javaClass.simpleName}") } }
    }

    /** Whether the node behind [sessionInstanceId] wakes this phone at its current endpoint. */
    fun isReady(context: Context, sessionInstanceId: String): Boolean {
        val prefs = prefs(context)
        val endpoint = prefs.getString(KEY_ENDPOINT, null) ?: return false
        return PushRegistrationPolicy.ready(
            PushRegistrationPolicy.NodeState.decode(prefs.getString(NODE_PREFIX + sessionInstanceId, null)), hash(endpoint))
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
        for ((session, store) in pairings(app)) {
            val key = NODE_PREFIX + session.sessionInstanceId
            live += key
            val state = PushRegistrationPolicy.NodeState.decode(prefs.getString(key, null))
            val now = System.currentTimeMillis()
            if (!PushRegistrationPolicy.shouldAttempt(state, endpointHash, now)) continue
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
                    PushRegistrationPolicy.after(PushRegistrationPolicy.Outcome.REFUSED, relay, hash(token), now).encode()).apply()
                return
            }
        }
        // Forget nodes this phone no longer pairs with.
        prefs.all.keys.filter { it.startsWith(NODE_PREFIX) && it !in live }.takeIf { it.isNotEmpty() }?.let { stale ->
            prefs.edit().apply { stale.forEach(::remove) }.apply()
        }
    }

    /** The relay endpoint for [token], asking the relay only when there is none and its retry time has come. */
    private fun endpointFor(app: Context, token: String): String? {
        val prefs = prefs(app)
        prefs.getString(KEY_ENDPOINT, null)?.let { return it }
        val tokenHash = hash(token)
        val state = PushRegistrationPolicy.NodeState.decode(prefs.getString(KEY_RELAY, null))
        val now = System.currentTimeMillis()
        if (state.tokenHash == tokenHash && now < state.nextAttemptAt && !state.registered) return null
        val endpoint = runCatching { KyubeyClient(BuildConfig.PUSH_RELAY_URL).register(token) }
            .onFailure { Log.w(TAG, "relay registration failed: ${it.javaClass.simpleName}") }.getOrNull()
        val outcome = if (endpoint != null) PushRegistrationPolicy.Outcome.REGISTERED else PushRegistrationPolicy.Outcome.RETRY
        prefs.edit().putString(KEY_RELAY, PushRegistrationPolicy.after(outcome, state, tokenHash, now).encode())
            .apply { if (endpoint != null) putString(KEY_ENDPOINT, endpoint) }.apply()
        return endpoint
    }

    /** Asks FCM for the token once per process; the answer arrives through [onNewToken]. */
    private fun requestToken(app: Context) {
        if (!tokenRequested.compareAndSet(false, true)) return
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> onNewToken(app, token) }
        }.onFailure { Log.w(TAG, "FCM unavailable: ${it.javaClass.simpleName}") }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
}
