package com.callagent.host.ui

import com.callagent.host.data.SmsRecord
import java.time.Instant

/** One conversation on the messages tab: a peer reached through one gateway SIM. */
data class SmsThread(val simId: String?, val peer: String, val latest: SmsRecord, val records: List<SmsRecord>)

/**
 * Groups cached messages into conversations. The activity builds this once per
 * screen render and filters it in memory on each search keystroke, instead of
 * re-reading the whole message table for every character typed.
 */
object SmsThreadIndex {
    const val UNKNOWN_PEER = "未知号码"

    fun build(records: List<SmsRecord>): List<SmsThread> = records
        .groupBy { it.simId to it.peerAddress().ifBlank { UNKNOWN_PEER } }
        .map { (key, messages) ->
            val sorted = messages.sortedByDescending { timestampMillis(it.createdAt) }
            SmsThread(key.first, key.second, sorted.first(), sorted)
        }
        .sortedByDescending { timestampMillis(it.latest.createdAt) }

    fun filter(threads: List<SmsThread>, query: String): List<SmsThread> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return threads
        return threads.filter { thread ->
            thread.peer.lowercase().contains(needle) || thread.latest.text.lowercase().contains(needle)
        }
    }

    fun timestampMillis(value: String): Long = runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
}
