package com.callagent.host.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** Process-local path observations. No SSID, BSSID, IP or route is exported or persisted. */
internal object NetworkEnvironment {
    private val window = StableNetworkWindow()
    private val capabilities = mutableMapOf<Network, String>()
    private val links = mutableMapOf<Network, String>()
    private val main = Handler(Looper.getMainLooper())
    private val epoch = java.util.UUID.randomUUID().toString()
    private var generation = 0L
    private var registered = false
    private var registration: Triple<ConnectivityManager, ConnectivityManager.NetworkCallback, ConnectivityManager.NetworkCallback>? = null
    private var defaultNetwork: Network? = null
    val token: String @Synchronized get() = "$epoch:$generation"
    val stable: Boolean @Synchronized get() = registered && window.stable(SystemClock.elapsedRealtime())
    fun succeeded() = window.succeeded()
    fun failed() = window.failed(SystemClock.elapsedRealtime())

    @Synchronized fun start(context: Context) {
        val app = context.applicationContext
        val manager = app.getSystemService(ConnectivityManager::class.java)
        val previous = registration
        if (registered && previous?.first === manager) return
        previous?.let { old ->
            runCatching { old.first.unregisterNetworkCallback(old.second) }
            runCatching { old.first.unregisterNetworkCallback(old.third) }
        }
        capabilities.clear()
        links.clear()
        defaultNetwork = null
        window.changed("", SystemClock.elapsedRealtime())
        generation++
        val notify = Runnable {
            if (manager.activeNetwork == null) return@Runnable
            // Back online: check, silently, that each node's push still reaches this phone.
            com.callagent.host.push.PushProbe.onNetworkRecovered(app)
            if (!com.callagent.host.background.HostBackgroundService.isRunning &&
                !com.callagent.host.calls.CallRuntime.hasServiceForPush) return@Runnable
            com.callagent.host.mesh.NodeMonitor.environmentChanged(app)
            com.callagent.host.background.HostBackgroundService.onNetworkEnvironmentChanged()
            com.callagent.host.calls.CallRuntime.requestCallSyncFromWakeHint()
        }
        fun update(network: Network, capability: String? = null, link: String? = null, lost: Boolean = false, defaultAvailable: Boolean = false, defaultLost: Boolean = false) {
            synchronized(this) {
                if (defaultAvailable) defaultNetwork = network
                if (defaultLost && defaultNetwork == network) defaultNetwork = null
                if (lost) {
                    capabilities.remove(network)
                    links.remove(network)
                } else {
                    if (capability != null) capabilities[network] = capability
                    if (link != null) links[network] = link
                }
                // The default path counts with its interface, DNS and routes. Other
                // paths (a VPN's underlying Wi-Fi or mobile, or mobile kept up beside
                // Wi-Fi) count only by identity and transport: their addresses and
                // routes churn, IPv6 privacy addresses rotate, without moving this
                // phone's connections, and each change here restarts the WebSocket.
                val others = capabilities.entries.filter { it.key != defaultNetwork }
                    .sortedBy { it.key.toString() }.joinToString { "${it.key}:${it.value}" }
                val fingerprint = "default=$defaultNetwork:${capabilities[defaultNetwork].orEmpty()}:" +
                    "${links[defaultNetwork].orEmpty()}|$others"
                if (!window.changed(fingerprint, SystemClock.elapsedRealtime())) return
                generation++
            }
            com.callagent.host.data.StandbyDiagnostics.event("network_changed")
            main.removeCallbacks(notify)
            main.postDelayed(notify, 1_500L)
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = update(network)
            override fun onLost(network: Network) = update(network, lost = true)
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                // Exclude signal strength and bandwidth: they fluctuate without changing the path.
                val signature = listOf(NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.TRANSPORT_CELLULAR,
                    NetworkCapabilities.TRANSPORT_VPN, NetworkCapabilities.TRANSPORT_ETHERNET)
                    .joinToString { caps.hasTransport(it).toString() } +
                    ":${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}:${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)}"
                update(network, capability = signature)
            }
            override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
                // Addresses are left out: a rotated address leaves the route, and open sockets, as they were.
                update(network, link = listOf(properties.interfaceName.orEmpty(),
                    properties.dnsServers.map { it.hostAddress }.sortedBy { it }.toString(),
                    properties.routes.map { it.toString() }.sorted().toString()).joinToString("|"))
            }
        }
        // Include VPN and underlying Wi-Fi/mobile networks, even when VPN remains the default.
        val defaultCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = update(network, defaultAvailable = true)
            override fun onLost(network: Network) = update(network, defaultLost = true)
        }
        registered = runCatching {
            manager.registerNetworkCallback(NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), callback)
            try {
                manager.registerDefaultNetworkCallback(defaultCallback)
            } catch (failure: RuntimeException) {
                manager.unregisterNetworkCallback(callback)
                throw failure
            }
            true
        }.getOrDefault(false)
        registration = if (registered) Triple(manager, callback, defaultCallback) else null
    }
}

/** A quiet path also needs successful work before slower maintenance is allowed. */
internal class StableNetworkWindow {
    private var fingerprint: String? = null
    private var since = 0L
    private var successes = 0
    @Synchronized fun changed(value: String, now: Long): Boolean {
        if (fingerprint == value) return false
        fingerprint = value
        since = now
        successes = 0
        return true
    }
    @Synchronized fun succeeded() { successes = (successes + 1).coerceAtMost(3) }
    @Synchronized fun failed(now: Long) { since = now; successes = 0 }
    @Synchronized fun stable(now: Long) = !fingerprint.isNullOrEmpty() && successes >= 3 && now - since >= 600_000L
}
