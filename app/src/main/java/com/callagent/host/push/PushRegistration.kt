package com.callagent.host.push

import android.content.Context
import android.os.SystemClock
import android.util.Log
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
 * Keeps this phone's FCM token registered on every paired node that sends
 * push. It does nothing without Firebase (no app config or no Google Play
 * services), and contacts a node only when [PushRegistrationPolicy] says so,
 * never on a timer of its own.
 */
object PushRegistration {
    private const val TAG = "HostPush"
    private const val PREFS = "host-push"
    private const val KEY_TOKEN = "token"
    private const val PASS_INTERVAL_MS = 10 * 60 * 1000L
    @Volatile private var lastPassAt = 0L
    private const val NODE_PREFIX = "node."
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "push-registration") }
    private val tokenRequested = AtomicBoolean()

    fun available(context: Context): Boolean =
        runCatching { FirebaseApp.getApps(context.applicationContext).isNotEmpty() }.getOrDefault(false)

    /** FCM issued a new token: every node registers it again. */
    fun onNewToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_TOKEN, token).apply()
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

    /** Whether the node behind [sessionInstanceId] wakes this phone for the current token. */
    fun isReady(context: Context, sessionInstanceId: String): Boolean {
        val prefs = prefs(context)
        val token = prefs.getString(KEY_TOKEN, null) ?: return false
        return PushRegistrationPolicy.ready(
            PushRegistrationPolicy.NodeState.decode(prefs.getString(NODE_PREFIX + sessionInstanceId, null)), hash(token))
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
        val token = prefs(app).getString(KEY_TOKEN, null)
        if (token == null) {
            requestToken(app)
            return
        }
        val tokenHash = hash(token)
        val prefs = prefs(app)
        val live = mutableSetOf<String>()
        for ((session, store) in pairings(app)) {
            val key = NODE_PREFIX + session.sessionInstanceId
            live += key
            val state = PushRegistrationPolicy.NodeState.decode(prefs.getString(key, null))
            val now = System.currentTimeMillis()
            if (!PushRegistrationPolicy.shouldAttempt(state, tokenHash, now)) continue
            val outcome = try {
                val api = ApiClient(session.apiBaseUrl, store)
                if (!api.pushAvailable()) PushRegistrationPolicy.Outcome.UNSUPPORTED
                else {
                    api.registerPushToken(token)
                    PushRegistrationPolicy.Outcome.REGISTERED
                }
            } catch (failure: ApiFailure) {
                if (failure.retryable) PushRegistrationPolicy.Outcome.RETRY else PushRegistrationPolicy.Outcome.REFUSED
            } catch (_: Exception) {
                PushRegistrationPolicy.Outcome.RETRY
            }
            prefs.edit().putString(key, PushRegistrationPolicy.after(outcome, state, tokenHash, now).encode()).apply()
            Log.i(TAG, "node ${session.apiBaseUrl.hashCode()} push=${outcome.name.lowercase()}")
        }
        // Forget nodes this phone no longer pairs with.
        prefs.all.keys.filter { it.startsWith(NODE_PREFIX) && it !in live }.takeIf { it.isNotEmpty() }?.let { stale ->
            prefs.edit().apply { stale.forEach(::remove) }.apply()
        }
    }

    /** Asks FCM for the token once per process; the answer arrives through [onNewToken]. */
    private fun requestToken(app: Context) {
        if (!tokenRequested.compareAndSet(false, true)) return
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> onNewToken(app, token) }
        }.onFailure { Log.w(TAG, "FCM unavailable: ${it.javaClass.simpleName}") }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
}
