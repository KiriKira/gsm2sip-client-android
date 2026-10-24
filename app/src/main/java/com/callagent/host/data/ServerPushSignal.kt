package com.callagent.host.data

import java.util.concurrent.ConcurrentHashMap

/**
 * Servers seen advertising push wake-ups in this process. Any /capabilities
 * read records it, so a node found without push before its server was
 * upgraded is registered at once instead of at its daily recheck.
 */
internal object ServerPushSignal {
    private val advertised = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var changed = false

    fun observed(apiRoot: String, push: Boolean) {
        if (push && advertised.add(apiRoot)) changed = true
    }

    fun advertises(apiRoot: String): Boolean = apiRoot in advertised

    /** True once after a server is first seen advertising push. */
    fun consumeChange(): Boolean {
        if (!changed) return false
        changed = false
        return true
    }
}
