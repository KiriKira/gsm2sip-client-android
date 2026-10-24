package com.callagent.host.data

import java.util.concurrent.locks.ReentrantLock

/** Independent pairing work never shares a network lock. Entries expire with the last waiter. */
internal object SessionWorkLocks {
    private class Entry(val lock: ReentrantLock = ReentrantLock(), var users: Int = 0)
    private val entries = mutableMapOf<String, Entry>()

    fun <T> withLock(key: String, action: () -> T): T {
        val entry = synchronized(entries) { entries.getOrPut(key) { Entry() }.also { it.users++ } }
        var acquired = false
        try {
            entry.lock.lockInterruptibly()
            acquired = true
            return action()
        } finally {
            if (acquired) entry.lock.unlock()
            synchronized(entries) { if (--entry.users == 0) entries.remove(key) }
        }
    }
}
