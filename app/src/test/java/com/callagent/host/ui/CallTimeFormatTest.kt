package com.callagent.host.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class CallTimeFormatTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = Instant.parse("2026-10-29T04:00:00Z") // 12:00 local

    private fun format(at: String) = CallTimeFormat.format(Instant.parse(at), now, zone)

    @Test fun olderCallsKeepTheirTimeBesideTheDate() {
        assertEquals("09:30", format("2026-10-29T01:30:00Z"))
        assertEquals("昨天 sits under its own heading", "23:05", format("2026-10-28T15:05:00Z"))
        assertEquals("10月27日 21:05", format("2026-10-27T13:05:00Z"))
        assertEquals("2025年12月31日 23:59", format("2025-12-31T15:59:00Z"))
    }
}
