package com.callagent.host.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The time on a call log row. Today and yesterday sit under their own
 * heading, so the clock time is enough there; older calls keep it beside
 * the date, since when in the day someone called still matters.
 */
internal object CallTimeFormat {
    private val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    private val thisYear = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)
    private val earlier = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA)

    fun format(at: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val day = at.atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        val formatter = when {
            day == today || day == today.minusDays(1) -> clock
            day.year == today.year -> thisYear
            else -> earlier
        }
        return formatter.format(at.atZone(zone))
    }
}
