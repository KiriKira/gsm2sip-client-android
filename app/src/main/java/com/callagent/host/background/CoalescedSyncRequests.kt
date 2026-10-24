package com.callagent.host.background

/** One owner and one pending pass. Offers made during a request must not disappear. */
internal class CoalescedSyncRequests {
    private var running = false
    private var pending = false

    @Synchronized fun offer(): Boolean {
        pending = true
        if (running) return false
        running = true
        pending = false
        return true
    }

    @Synchronized fun complete(): Boolean {
        if (pending) {
            pending = false
            return true
        }
        running = false
        return false
    }
}
