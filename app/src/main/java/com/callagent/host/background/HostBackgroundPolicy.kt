package com.callagent.host.background

import com.callagent.host.data.HostSession
import com.callagent.host.data.SmsRecord
import com.callagent.host.data.sameSessionInstance
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException

/** Small policy functions shared by the service and regression tests. */
internal object HostBackgroundPolicy {
    const val DEFAULT_ENABLED = false
    const val INITIAL_BACKOFF_MS = 1_000L
    const val MAX_BACKOFF_MS = 5 * 60_000L

    /** WSS backs up delivery independently of FCM. Legacy argument retained for callers. */
    fun syncDelay(socketLive: Boolean, failures: Int, experiments: Boolean, stableNetwork: Boolean = false): Long {
        if (failures > 0 && com.callagent.host.BuildConfig.STANDBY_FAILURE_BACKOFF) return (30_000L shl failures.coerceAtMost(4)).coerceAtMost(300_000L)
        if (!socketLive) return 30_000L
        val normal = com.callagent.host.BuildConfig.STANDBY_SMS_LIVE_MS
        return if (stableNetwork) maxOf(normal, 300_000L) else normal
    }

    fun shouldRestore(enabled: Boolean, pairedClient: Boolean): Boolean = enabled && pairedClient

    fun shouldEnablePairedBinding(
        pairedClient: Boolean,
        bindingMatches: Boolean,
        enabled: Boolean,
        systemTaskStopped: Boolean
    ): Boolean = pairedClient && (!bindingMatches || !enabled || systemTaskStopped)

    /** A paired session starts only from a visible focused Activity, except for an already-running service. */
    fun shouldStartForPairedSession(
        pairedClient: Boolean,
        activityFocused: Boolean,
        bindingMatches: Boolean,
        enabled: Boolean,
        systemTaskStopped: Boolean,
        serviceRunning: Boolean
    ): Boolean = pairedClient && activityFocused && (
        !bindingMatches || !enabled || systemTaskStopped || (enabled && !serviceRunning)
    )

    fun isHistoricalBaseline(cursorPresent: Boolean, bootstrapped: Boolean, baselineInProgress: Boolean): Boolean =
        baselineInProgress || (!cursorPresent && !bootstrapped)

    fun shouldSuppressUserStopRestore(
        enabled: Boolean,
        pairedClient: Boolean,
        lastExplicitEnableAt: Long,
        lastExitReason: Int,
        lastExitAt: Long,
        userRequestedReason: Int
    ): Boolean = shouldRestore(enabled, pairedClient) &&
        lastExitReason == userRequestedReason && lastExitAt >= lastExplicitEnableAt

    fun newInboundNotificationIds(records: List<SmsRecord>, newlyObservedIds: Set<String>): List<String> =
        records.asSequence()
            .filter { it.direction == "inbound" }
            .mapNotNull { it.serverId }
            .filter { it in newlyObservedIds }
            .distinct()
            .toList()

    fun backoffDelay(attempt: Int, jitter: Double): Long {
        val shift = (attempt.coerceIn(0, 18))
        val base = (INITIAL_BACKOFF_MS * (1L shl shift)).coerceAtMost(MAX_BACKOFF_MS)
        return (base * jitter.coerceIn(0.75, 1.25)).toLong().coerceAtLeast(500L)
    }
}

/** Prevents rapid focused-Activity callbacks from enqueueing duplicate service starts. */
internal class BackgroundStartRequests {
    private var requestedStamp: String? = null

    @Synchronized
    fun isPending(stamp: String): Boolean = requestedStamp == stamp

    @Synchronized
    fun request(stamp: String) {
        requestedStamp = stamp
    }

    @Synchronized
    fun onStarted(stamp: String) {
        if (requestedStamp == stamp) requestedStamp = null
    }

    @Synchronized
    fun onRejected(stamp: String? = null) {
        if (stamp == null || requestedStamp == stamp) requestedStamp = null
    }

    @Synchronized
    fun onStopped(stamp: String?) {
        if (stamp != null && requestedStamp == stamp) requestedStamp = null
    }
}

internal fun isCurrentBackgroundSession(expected: HostSession, current: HostSession?): Boolean =
    current != null && expected.sameSessionInstance(current)

internal fun hostWakeWebSocketUrl(configuredBase: String): String {
    val base = configuredBase.toHttpUrlOrNull()
        ?: throw IOException("Invalid paired server URL")
    if ((base.scheme != "https" && base.scheme != "http") || base.username.isNotEmpty() || base.password.isNotEmpty() || base.query != null || base.fragment != null) {
        throw IOException("Paired server URL must be http:// or https://")
    }
    val normalized = if (base.encodedPath.trimEnd('/').endsWith("/v1")) base else base.newBuilder().addPathSegment("v1").build()
    val url = normalized.newBuilder().addPathSegment("ws").build().toString()
    // An https base wakes over wss://; a plain http (LAN/VPN) base over ws://.
    return if (base.isHttps) url.replaceFirst("https://", "wss://") else url.replaceFirst("http://", "ws://")
}
