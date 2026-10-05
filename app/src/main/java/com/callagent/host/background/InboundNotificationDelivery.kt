package com.callagent.host.background

/**
 * Keeps a notification batch retryable until Android accepts the post and the
 * coalescer durably records its acknowledgement.
 */
internal object InboundNotificationDelivery {
    enum class Result {
        POST_FAILED,
        ACK_FAILED,
        ACKNOWLEDGED
    }

    /** Returns null when the whole requested addition cannot fit. */
    fun appendPendingIds(
        pending: List<String>,
        recentlyNotified: List<String>,
        ids: List<String>,
        capacity: Int
    ): List<String>? {
        val additions = ids.distinct().filter { it !in pending && it !in recentlyNotified }
        if (pending.size + additions.size > capacity.coerceAtLeast(0)) return null
        return pending + additions
    }

    fun postThenAcknowledge(
        post: () -> Unit,
        acknowledge: () -> Boolean,
        restorePending: () -> Unit
    ): Result {
        try {
            post()
        } catch (_: Exception) {
            return Result.POST_FAILED
        }

        val acknowledged = try {
            acknowledge()
        } catch (_: Exception) {
            false
        }
        if (acknowledged) return Result.ACKNOWLEDGED

        runCatching(restorePending)
        return Result.ACK_FAILED
    }
}
