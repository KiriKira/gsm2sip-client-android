package com.callagent.host.ui

import com.callagent.host.data.SimLine

/** Which SIM the dial pad offers first, and how a SIM is labelled there. */
internal object DialerSimChoice {
    /** Keeps the current choice, else the SIM last dialled from, else the first usable SIM. */
    fun choose(current: String?, lastUsed: String?, sims: List<SimLine>): String? = when {
        current != null && sims.any { it.simId == current } -> current
        lastUsed != null && sims.any { it.simId == lastUsed } -> lastUsed
        else -> sims.filter { it.canSend }.ifEmpty { sims }.minByOrNull { it.slotIndex }?.simId
    }

    /** "SIM1 • Rakuten", or "SIM1" when the gateway reports no carrier. */
    fun chipLabel(line: SimLine): String =
        "SIM${line.slotIndex + 1}" + line.carrierName?.trim()?.takeIf { it.isNotEmpty() }?.let { " • $it" }.orEmpty()
}
