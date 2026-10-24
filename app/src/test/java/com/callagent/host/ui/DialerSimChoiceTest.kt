package com.callagent.host.ui

import com.callagent.host.data.SimLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DialerSimChoiceTest {
    private fun sim(id: String, slot: Int, carrier: String?, state: String = "active", verified: Boolean = true) =
        SimLine(id, slot, "SIM ${slot + 1}", carrier, null, state, 1L, verified, null)

    private val first = sim("a", 0, "Rakuten")
    private val second = sim("b", 1, "docomo")

    @Test
    fun keepsTheChoiceThenTheLastUsedThenTheFirstUsableSim() {
        assertEquals("b", DialerSimChoice.choose("b", "a", listOf(first, second)))
        assertEquals("b", DialerSimChoice.choose(null, "b", listOf(first, second)))
        assertEquals("b", DialerSimChoice.choose("gone", "b", listOf(first, second)))
        assertEquals("a", DialerSimChoice.choose(null, "gone", listOf(second, first)))
        assertEquals("an unusable first slot is skipped", "b",
            DialerSimChoice.choose(null, null, listOf(sim("a", 0, null, verified = false), second)))
        assertNull(DialerSimChoice.choose(null, "a", emptyList()))
    }

    @Test
    fun labelsNameTheSlotAndCarrier() {
        assertEquals("SIM1 • Rakuten", DialerSimChoice.chipLabel(first))
        assertEquals("SIM2", DialerSimChoice.chipLabel(sim("c", 1, " ")))
        assertEquals("SIM2", DialerSimChoice.chipLabel(sim("c", 1, null)))
    }
}
