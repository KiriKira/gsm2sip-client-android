package com.callagent.host.mesh

import android.content.Context
import android.os.SystemClock
import com.callagent.host.data.ApiClient
import com.callagent.host.data.ApiFailure
import com.callagent.host.data.HostSession
import com.callagent.host.data.HostSyncEngine
import com.callagent.host.data.SessionStore
import com.callagent.host.data.databaseName
import com.callagent.host.data.sameSessionInstance
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.util.UUID

/** One server node of this phone's profile, in the user's priority order. */
data class HostNode(
    val key: String,
    /** The pairing this node holds; matches the active store when the node is active. */
    val sessionInstanceId: String,
    val apiBaseUrl: String,
    val label: String,
    val nodeScope: String
)

class NodeSetupFailure(message: String) : IOException(message)

/**
 * The phone's server nodes. Every node is an independent server paired
 * separately with the same old phone; they share one local profile database.
 *
 * The active node's session lives in the ordinary [SessionStore], so the rest
 * of the app is unchanged; inactive nodes keep theirs in per-node stores. A
 * switch moves sessions between the two under the store's process lock. The
 * registry itself holds no secrets.
 */
object HostNodes {
    const val MESH_EXTENSION = "mesh"
    private const val PREFS = "host-nodes"
    private const val KEY_REGISTRY = "registry_v1"
    private const val NODE_PREFS_PREFIX = "host-node-"
    private val registryLock = Any()

    internal data class Registry(
        val profileId: String?,
        val installId: String?,
        val nodes: List<HostNode>,
        val health: Map<String, NodeHealth>
    )

    fun nodes(context: Context): List<HostNode> = registry(context).nodes

    fun health(context: Context): Map<String, NodeHealth> = registry(context).health

    /** True once a second node was added; single-node phones keep the original behavior. */
    fun isMultiNode(context: Context): Boolean = nodes(context).size > 1

    fun activeNode(context: Context): HostNode? {
        val active = runCatching { SessionStore(context).read() }.getOrNull() ?: return null
        return nodes(context).firstOrNull { it.sessionInstanceId == active.sessionInstanceId }
    }

    /** The store holding [node]'s session right now. */
    fun storeFor(context: Context, node: HostNode): SessionStore {
        val active = runCatching { SessionStore(context).read() }.getOrNull()
        return if (active?.sessionInstanceId == node.sessionInstanceId) SessionStore(context)
        else SessionStore(context, NODE_PREFS_PREFIX + node.key)
    }

    fun sessionFor(context: Context, node: HostNode): HostSession? =
        runCatching { storeFor(context, node).read() }.getOrNull()
            ?.takeIf { it.sessionInstanceId == node.sessionInstanceId }

    fun reorder(context: Context, orderedKeys: List<String>): Boolean = synchronized(registryLock) {
        val current = registry(context)
        val byKey = current.nodes.associateBy { it.key }
        if (orderedKeys.toSet() != byKey.keys || orderedKeys.size != byKey.size) return false
        save(context, current.copy(nodes = orderedKeys.map { byKey.getValue(it) }))
    }

    /**
     * Pair another server node for the same old phone. Blocking; run off the
     * main thread. The node is accepted only when both servers support the
     * mesh extension and report the same old phone (install ID), so data of
     * two different gateways can never be merged. Follow with [catchUpSilently].
     */
    fun add(context: Context, serverUrl: String, pairingCode: String, deviceName: String): HostNode {
        val app = context.applicationContext
        val activeStore = SessionStore(app)
        val active = activeStore.read() ?: throw NodeSetupFailure("请先完成当前服务器的配对")
        val existing = registry(app)
        val probe = ApiClient(serverUrl, activeStore)
        val normalizedUrl = normalize(serverUrl)
        val knownUrls = (existing.nodes.map { it.apiBaseUrl } + active.apiBaseUrl).map(::normalize)
        if (normalizedUrl in knownUrls) throw NodeSetupFailure("这个服务器已经在节点列表中")
        if (!supportsMesh(probe)) throw NodeSetupFailure("该服务器版本不支持多节点，请先升级服务器")
        if (!supportsMesh(ApiClient(active.apiBaseUrl, activeStore))) {
            throw NodeSetupFailure("当前服务器版本不支持多节点，请先升级服务器")
        }
        val installId = ApiClient(active.apiBaseUrl, activeStore).listGateways().firstOrNull()?.installId
            ?: throw NodeSetupFailure("旧手机还没有向当前服务器报告身份。请升级并打开旧手机应用后再试")
        if (existing.installId != null && existing.installId != installId) {
            throw NodeSetupFailure("当前服务器上的旧手机与节点列表记录的不是同一台")
        }

        val key = UUID.randomUUID().toString()
        val nodeStore = SessionStore(app, NODE_PREFS_PREFIX + key)
        nodeStore.clear()
        val paired = ApiClient(serverUrl, nodeStore).pair(pairingCode, deviceName)
        try {
            val sameGateway = ApiClient(paired.apiBaseUrl, nodeStore).listGateways().any { it.installId == installId }
            if (!sameGateway) {
                throw NodeSetupFailure("该服务器上没有同一台旧手机。请先在旧手机的“其他节点”里添加这个服务器")
            }
        } catch (failure: Exception) {
            runCatching { ApiClient(paired.apiBaseUrl, nodeStore).revoke() }
            runCatching { nodeStore.clear() }
            throw failure
        }

        val node = try {
            register(app, key, paired, installId)
        } catch (failure: Exception) {
            runCatching { ApiClient(paired.apiBaseUrl, nodeStore).revoke() }
            runCatching { nodeStore.clear() }
            throw failure
        }
        return node
    }

    /**
     * Record a verified node pairing (already saved in its own store under
     * [key]) and turn the active pairing into the first node of the profile.
     */
    internal fun register(context: Context, key: String, paired: HostSession, installId: String): HostNode =
        synchronized(registryLock) {
            val activeStore = SessionStore(context)
            val nodeStore = SessionStore(context, NODE_PREFS_PREFIX + key)
            val profiled = convertActiveToProfile(activeStore)
                ?: throw NodeSetupFailure("配对账户在添加期间发生变化，请重试")
            val node = HostNode(
                key = key,
                sessionInstanceId = paired.sessionInstanceId,
                apiBaseUrl = paired.apiBaseUrl,
                label = hostLabel(paired.apiBaseUrl),
                nodeScope = "node-${key.take(8)}"
            )
            nodeStore.replace(paired.copy(
                profileId = profiled.profileId,
                profileDatabase = profiled.profileDatabase,
                nodeScope = node.nodeScope
            ))
            val current = registry(context)
            val nodes = current.nodes.ifEmpty {
                listOf(HostNode(UUID.randomUUID().toString(), profiled.sessionInstanceId,
                    profiled.apiBaseUrl, hostLabel(profiled.apiBaseUrl), ""))
            }
            if (!save(context, Registry(profiled.profileId, installId, nodes + node, current.health))) {
                throw NodeSetupFailure("无法保存节点列表")
            }
            node
        }

    /** Where an inactive node keeps its session. */
    internal fun inactiveStore(context: Context, key: String) = SessionStore(context, NODE_PREFS_PREFIX + key)

    /** Remove an inactive node and revoke its pairing on that server. Blocking. */
    fun remove(context: Context, key: String) {
        val app = context.applicationContext
        val node = nodes(app).firstOrNull { it.key == key } ?: return
        if (activeNode(app)?.key == key) throw NodeSetupFailure("这是正在使用的节点，请先切换到其他节点")
        val store = SessionStore(app, NODE_PREFS_PREFIX + key)
        runCatching { ApiClient(node.apiBaseUrl, store).revoke() }
        runCatching { store.clear() }
        synchronized(registryLock) {
            val current = registry(app)
            save(app, current.copy(nodes = current.nodes.filterNot { it.key == key }, health = current.health - key))
        }
    }

    /**
     * Make [key] the active node. Returns its session, or null when the node
     * has no usable session. The previously active session moves to its own
     * per-node store so its refresh token is kept.
     */
    fun activate(context: Context, key: String): HostSession? {
        val app = context.applicationContext
        val registry = registry(app)
        val target = registry.nodes.firstOrNull { it.key == key } ?: return null
        val switched = SessionStore.exclusive {
            val main = SessionStore(app)
            val active = runCatching { main.read() }.getOrNull()
            if (active?.sessionInstanceId == target.sessionInstanceId) return@exclusive active
            val targetStore = SessionStore(app, NODE_PREFS_PREFIX + target.key)
            val targetSession = runCatching { targetStore.read() }.getOrNull()
                ?.takeIf { it.sessionInstanceId == target.sessionInstanceId && it.profileId == registry.profileId }
                ?: return@exclusive null
            if (active != null) {
                val activeNode = registry.nodes.firstOrNull { it.sessionInstanceId == active.sessionInstanceId }
                    ?: return@exclusive null
                SessionStore(app, NODE_PREFS_PREFIX + activeNode.key).replace(active)
            }
            main.replace(targetSession)
            runCatching { targetStore.clear() }
            targetSession
        }
        return switched
    }

    /**
     * After the active node's pairing was lost (revoked or expired), continue
     * on the next node that still has a session instead of asking to re-pair.
     */
    fun recoverActive(context: Context): HostSession? {
        val app = context.applicationContext
        if (runCatching { SessionStore(app).read() }.getOrNull() != null) return null
        val candidates = nodes(app).filter { node ->
            runCatching { SessionStore(app, NODE_PREFS_PREFIX + node.key).read() }.getOrNull()
                ?.sessionInstanceId == node.sessionInstanceId
        }
        return candidates.firstNotNullOfOrNull { activate(app, it.key) }
    }

    /** Forget every node after the user unpaired this phone. Blocking. */
    fun forgetAll(context: Context) {
        val app = context.applicationContext
        val nodes = registry(app).nodes
        nodes.forEach { node ->
            val store = SessionStore(app, NODE_PREFS_PREFIX + node.key)
            runCatching { ApiClient(node.apiBaseUrl, store).revoke() }
            runCatching { store.clear() }
        }
        synchronized(registryLock) {
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    internal fun recordHealth(context: Context, updates: Map<String, NodeHealth>) = synchronized(registryLock) {
        val current = registry(context)
        save(context, current.copy(health = current.health.filterKeys { key -> current.nodes.any { it.key == key } } + updates))
    }

    internal fun installId(context: Context): String? = registry(context).installId

    internal fun registry(context: Context): Registry {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_REGISTRY, null) ?: return Registry(null, null, emptyList(), emptyMap())
        val parsed = runCatching { decode(JSONObject(raw)) }.getOrNull() ?: return Registry(null, null, emptyList(), emptyMap())
        val active = runCatching { SessionStore(context.applicationContext).read() }.getOrNull()
        // A standalone pairing made after the profile was abandoned owns the
        // phone now; the old node list no longer describes it.
        if (active != null && active.profileId != parsed.profileId) return Registry(null, null, emptyList(), emptyMap())
        return parsed
    }

    /** Servers older than the mesh extension have no /capabilities endpoint. */
    private fun supportsMesh(api: ApiClient): Boolean = try {
        MESH_EXTENSION in api.capabilities()
    } catch (failure: ApiFailure) {
        if (failure.httpStatus == 404) false else throw failure
    }

    private fun convertActiveToProfile(activeStore: SessionStore): HostSession? {
        repeat(3) {
            val current = activeStore.read() ?: return null
            if (current.profileId != null) return current
            val converted = current.copy(
                profileId = current.sessionInstanceId,
                profileDatabase = current.databaseName(),
                nodeScope = ""
            )
            if (activeStore.writeIfCurrent(current, converted)) return converted
        }
        return null
    }

    /**
     * Fold a newly added node's history into the profile database without
     * alerts, so a later switch to it only alerts about SMS that no other
     * node delivered. Blocking and best-effort; run right after [add].
     */
    fun catchUpSilently(context: Context, node: HostNode) {
        val store = SessionStore(context, NODE_PREFS_PREFIX + node.key)
        val session = runCatching { store.read() }.getOrNull() ?: return
        runCatching {
            HostSyncEngine(
                context = context,
                boundSession = session,
                sessionIsCurrent = { expected -> store.read()?.sameSessionInstance(expected) == true },
                sessionStore = store
            ).use { engine ->
                val deadline = SystemClock.elapsedRealtime() + CATCH_UP_BUDGET_MS
                repeat(CATCH_UP_ROUNDS) { if (engine.catchUpSilently(deadline)) return@use }
            }
        }
    }

    private fun save(context: Context, registry: Registry): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_REGISTRY, encode(registry).toString()).commit()

    private fun encode(registry: Registry): JSONObject = JSONObject()
        .put("profile_id", registry.profileId ?: JSONObject.NULL)
        .put("install_id", registry.installId ?: JSONObject.NULL)
        .put("nodes", JSONArray().also { array ->
            registry.nodes.forEach { node ->
                array.put(JSONObject()
                    .put("key", node.key)
                    .put("session_instance_id", node.sessionInstanceId)
                    .put("api_base_url", node.apiBaseUrl)
                    .put("label", node.label)
                    .put("node_scope", node.nodeScope))
            }
        })
        .put("health", JSONObject().also { json ->
            registry.health.forEach { (key, health) ->
                json.put(key, JSONObject()
                    .put("failures", health.consecutiveFailures)
                    .put("successes", health.consecutiveSuccesses)
                    .put("rtt_ms", health.rttMillis ?: JSONObject.NULL)
                    .put("gateway_online", health.gatewayOnline)
                    .put("checked_at", health.checkedAt)
                    .put("issue", health.issue ?: JSONObject.NULL)
                    .put("gateway_id", health.gatewayId ?: JSONObject.NULL)
                    .put("call_ready", health.callReady))
            }
        })

    private fun decode(json: JSONObject): Registry {
        val nodes = json.optJSONArray("nodes") ?: JSONArray()
        val health = json.optJSONObject("health") ?: JSONObject()
        return Registry(
            profileId = json.optNullable("profile_id"),
            installId = json.optNullable("install_id"),
            nodes = (0 until nodes.length()).map { index ->
                val node = nodes.getJSONObject(index)
                HostNode(node.getString("key"), node.getString("session_instance_id"),
                    node.getString("api_base_url"), node.optString("label"), node.optString("node_scope"))
            },
            health = health.keys().asSequence().associateWith { key ->
                val item = health.getJSONObject(key)
                NodeHealth(
                    consecutiveFailures = item.optInt("failures"),
                    consecutiveSuccesses = item.optInt("successes"),
                    rttMillis = if (item.isNull("rtt_ms")) null else item.optLong("rtt_ms"),
                    gatewayOnline = item.optBoolean("gateway_online"),
                    checkedAt = item.optLong("checked_at"),
                    issue = item.optNullable("issue"),
                    gatewayId = item.optNullable("gateway_id"),
                    callReady = item.optBoolean("call_ready")
                )
            }
        )
    }

    private fun JSONObject.optNullable(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    private fun normalize(url: String): String = runCatching {
        val uri = URI(url.trim())
        val path = uri.rawPath.orEmpty().trimEnd('/').removeSuffix("/v1")
        "${uri.scheme.lowercase()}://${uri.rawAuthority.lowercase()}$path"
    }.getOrDefault(url.trim().lowercase())

    internal fun hostLabel(url: String): String = runCatching { URI(url).host }.getOrNull() ?: url

    private const val CATCH_UP_BUDGET_MS = 55_000L
    private const val CATCH_UP_ROUNDS = 6
}
