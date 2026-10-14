package com.callagent.host.ui

import com.callagent.host.data.SmsRecord
import java.time.Instant

/**
 * One conversation on the messages tab: a peer reached through one gateway SIM.
 * [key] identifies the peer (see [SmsPeerKey]) and is what navigation carries;
 * [peer] is its readable form for the screen.
 */
data class SmsThread(val simId: String?, val key: String, val peer: String, val latest: SmsRecord, val records: List<SmsRecord>) {
    /** Every form this peer was seen in, so search matches what the user typed. */
    val searchPeers: List<String> = (listOf(peer, key) + records.map { it.peerAddress().trim() }).distinct()
}

/**
 * Groups cached messages into conversations. The activity builds this once per
 * screen render and filters it in memory on each search keystroke, instead of
 * re-reading the whole message table for every character typed.
 */
object SmsThreadIndex {
    const val UNKNOWN_PEER = "未知号码"

    /** Thread key of [record] on its own SIM, with [countryFor] giving each SIM's country. */
    fun key(record: SmsRecord, countryFor: (String?) -> String?): String =
        key(record.peerAddress(), countryFor(record.simId))

    fun key(peer: String, countryIso: String?): String =
        SmsPeerKey.key(peer, countryIso).ifBlank { UNKNOWN_PEER }

    fun build(records: List<SmsRecord>, countryFor: (String?) -> String? = { null }): List<SmsThread> {
        // Few distinct peers recur across many messages; parse each one once.
        val keys = HashMap<Pair<String?, String>, String>()
        return records
            .groupBy { record -> record.simId to keys.getOrPut(record.simId to record.peerAddress()) { key(record, countryFor) } }
            .map { (key, messages) ->
                val sorted = messages.sortedByDescending { timestampMillis(it.createdAt) }
                SmsThread(key.first, key.second, SmsPeerKey.display(key.second, countryFor(key.first)), sorted.first(), sorted)
            }
            .sortedByDescending { timestampMillis(it.latest.createdAt) }
    }

    fun filter(threads: List<SmsThread>, query: String): List<SmsThread> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return threads
        return threads.filter { thread ->
            thread.searchPeers.any { it.lowercase().contains(needle) || SmsPeerKey.digitsMatch(it, needle) } ||
                thread.latest.text.lowercase().contains(needle)
        }
    }

    fun timestampMillis(value: String): Long = runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
}
