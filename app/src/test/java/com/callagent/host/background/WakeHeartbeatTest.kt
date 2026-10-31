package com.callagent.host.background

import org.junit.Assert.assertEquals
import org.junit.Test

class WakeHeartbeatTest {
    @Test
    fun connectionsThatOutliveTheIntervalStretchItToFiveMinutes() {
        var seconds = WakeHeartbeat.START_SECONDS
        repeat(5) { seconds = WakeHeartbeat.next(seconds, livedMs = 4L * seconds * 1000, failed = false) }
        assertEquals(WakeHeartbeat.MAX_SECONDS, seconds)
    }

    @Test
    fun aConnectionDroppedBetweenPingsHalvesItDownToAMinute() {
        assertEquals(60, WakeHeartbeat.next(120, livedMs = 150_000, failed = true))
        assertEquals(WakeHeartbeat.MIN_SECONDS, WakeHeartbeat.next(60, livedMs = 90_000, failed = true))
    }

    @Test
    fun otherEndingsSayNothingAboutTheInterval() {
        // Dropped before the first ping (a network change), or closed by the node.
        assertEquals(120, WakeHeartbeat.next(120, livedMs = 30_000, failed = true))
        assertEquals(120, WakeHeartbeat.next(120, livedMs = 150_000, failed = false))
    }
}
