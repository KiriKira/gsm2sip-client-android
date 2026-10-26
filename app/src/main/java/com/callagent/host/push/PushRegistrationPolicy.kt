package com.callagent.host.push

/**
 * When to (re)register this phone's FCM token with one server node. A node
 * that does not send push, or refuses the token, is asked again only after a
 * day, so a missing or wrong server key costs the phone one small request per
 * node per day and nothing else.
 */
internal object PushRegistrationPolicy {
    const val RECHECK_MS = 24 * 60 * 60 * 1000L
    private const val FIRST_RETRY_MS = 60 * 1000L
    private const val MAX_RETRY_MS = 6 * 60 * 60 * 1000L

    enum class Outcome { REGISTERED, UNSUPPORTED, REFUSED, RETRY }

    data class NodeState(
        val tokenHash: String? = null,
        val registered: Boolean = false,
        val failures: Int = 0,
        val nextAttemptAt: Long = 0L,
        /** The last answer, for showing; null before the first attempt or in state saved by 0.4.4 and earlier. */
        val outcome: Outcome? = null,
        val checkedAt: Long = 0L
    ) {
        fun encode(): String =
            listOf(tokenHash.orEmpty(), registered, failures, nextAttemptAt, outcome?.name.orEmpty(), checkedAt).joinToString("|")

        companion object {
            fun decode(raw: String?): NodeState {
                val parts = raw?.split('|') ?: return NodeState()
                if (parts.size != 4 && parts.size != 6) return NodeState()
                return NodeState(
                    tokenHash = parts[0].ifEmpty { null },
                    registered = parts[1].toBoolean(),
                    failures = parts[2].toIntOrNull() ?: 0,
                    nextAttemptAt = parts[3].toLongOrNull() ?: 0L,
                    outcome = parts.getOrNull(4)?.let { name -> Outcome.entries.firstOrNull { it.name == name } },
                    checkedAt = parts.getOrNull(5)?.toLongOrNull() ?: 0L
                )
            }
        }
    }

    /** Whether to contact the node now. A new token is registered at once. */
    fun shouldAttempt(state: NodeState, tokenHash: String, now: Long): Boolean =
        state.tokenHash != tokenHash || now >= state.nextAttemptAt

    fun after(outcome: Outcome, state: NodeState, tokenHash: String, now: Long): NodeState = when (outcome) {
        // Registered tokens are confirmed daily, so a server that lost or
        // stopped sending to it is noticed.
        Outcome.REGISTERED -> NodeState(tokenHash, true, 0, now + RECHECK_MS, outcome, now)
        Outcome.UNSUPPORTED, Outcome.REFUSED -> NodeState(tokenHash, false, 0, now + RECHECK_MS, outcome, now)
        Outcome.RETRY -> {
            val failures = state.failures + 1
            val delay = (FIRST_RETRY_MS shl minOf(failures - 1, 16)).coerceAtMost(MAX_RETRY_MS)
            NodeState(tokenHash, state.registered && state.tokenHash == tokenHash, failures, now + delay, outcome, now)
        }
    }

    /** Wake-ups from this node can be relied on for the current token. */
    fun ready(state: NodeState, tokenHash: String?): Boolean =
        tokenHash != null && state.registered && state.tokenHash == tokenHash
}
