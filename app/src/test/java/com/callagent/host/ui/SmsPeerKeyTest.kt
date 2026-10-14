package com.callagent.host.ui

import com.callagent.host.data.SmsRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs on Robolectric because normalization uses the framework's PhoneNumberUtils. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmsPeerKeyTest {
    private fun sms(id: String, sim: String?, peer: String, inbound: Boolean, at: String) = SmsRecord(
        localId = id, serverId = id, commandId = null, simId = sim, mappingRevision = 1,
        direction = if (inbound) "inbound" else "outbound", from = if (inbound) peer else null,
        to = if (inbound) null else peer, text = id, status = "received", createdAt = at, expiresAt = null,
        partCount = 1, parts = emptyList(), taskKey = null, gatewayId = "gw", idempotencyBody = null
    )

    @Test
    fun nationalAndInternationalFormsShareOneKey() {
        assertEquals("+8613800138000", SmsPeerKey.key("13800138000", "CN"))
        assertEquals("+8613800138000", SmsPeerKey.key(" +8613800138000 ", "CN"))
        assertEquals("+8613800138000", SmsPeerKey.key("138 0013 8000", "CN"))
        // A foreign number keeps its own country code whatever the SIM's country.
        assertEquals("+447911123456", SmsPeerKey.key("+447911123456", "CN"))
    }

    @Test
    fun shortCodesAlphanumericAndUnknownCountryKeepTheRawPeer() {
        assertEquals("10086", SmsPeerKey.key("10086", "CN"))
        assertEquals("BANK", SmsPeerKey.key("BANK", "CN"))
        assertEquals("13800138000", SmsPeerKey.key("13800138000", null))
        assertEquals("13800138000", SmsPeerKey.key("13800138000", "zz9"))
        // The UI fixture's fictional 555 numbers are not valid, so they stay as typed.
        assertEquals("+15550102001", SmsPeerKey.key("+15550102001", "US"))
        assertEquals("+15550102001", SmsPeerKey.display("+15550102001", "US"))
    }

    @Test
    fun displayIsNationalOnlyForTheSimsOwnCountry() {
        assertEquals("138 0013 8000", SmsPeerKey.display("+8613800138000", "CN"))
        assertEquals("+8613800138000", SmsPeerKey.display("+8613800138000", "US"))
        assertEquals("+8613800138000", SmsPeerKey.display("+8613800138000", null))
        assertEquals("10086", SmsPeerKey.display("10086", "CN"))
        assertEquals("BANK", SmsPeerKey.display("BANK", "CN"))
    }

    @Test
    fun countryFallsBackFromSimToHostSimNetworkAndLocale() {
        val sims = mapOf("sim-1" to "CN")
        assertEquals("CN", SmsPeerKey.countryFor("sim-1", sims, "US"))
        assertEquals("US", SmsPeerKey.countryFor("sim-2", sims, "us"))
        assertEquals("US", SmsPeerKey.countryFor(null, sims, "US"))
        assertNull(SmsPeerKey.countryFor("sim-2", sims, null))
        assertEquals("CN", SmsPeerKey.hostCountryIso("cn", "us", "GB"))
        assertEquals("US", SmsPeerKey.hostCountryIso("", "us", "GB"))
        assertEquals("GB", SmsPeerKey.hostCountryIso(null, null, "GB"))
        assertNull(SmsPeerKey.hostCountryIso(null, "", ""))
    }

    @Test
    fun threadsMergeNationalAndInternationalPeersPerSim() {
        val countries = mapOf("sim-1" to "CN", "sim-2" to "CN")
        val threads = SmsThreadIndex.build(listOf(
            sms("out", "sim-1", "13800138000", inbound = false, at = "2026-10-01T10:00:00Z"),
            sms("in", "sim-1", "+8613800138000", inbound = true, at = "2026-10-01T10:05:00Z"),
            sms("other-sim", "sim-2", "+8613800138000", inbound = true, at = "2026-10-01T09:00:00Z"),
            sms("short", "sim-1", "10086", inbound = true, at = "2026-10-01T08:00:00Z"),
            sms("bank", "sim-1", "BANK", inbound = true, at = "2026-10-01T07:00:00Z"),
        )) { simId -> SmsPeerKey.countryFor(simId, countries, null) }
        assertEquals(
            listOf("sim-1" to "+8613800138000", "sim-2" to "+8613800138000", "sim-1" to "10086", "sim-1" to "BANK"),
            threads.map { it.simId to it.key }
        )
        val merged = threads.first()
        assertEquals(listOf("in", "out"), merged.records.map { it.localId })
        assertEquals("138 0013 8000", merged.peer)
        assertEquals(listOf("10086", "BANK"), threads.drop(2).map { it.peer })
        // Search matches the number in any form it was typed or received.
        listOf("13800138000", "+8613800138000", "138 0013", "0013 8000").forEach { query ->
            assertEquals(query, listOf("sim-1", "sim-2"), SmsThreadIndex.filter(threads, query).map { it.simId })
        }
        assertEquals(listOf("BANK"), SmsThreadIndex.filter(threads, "bank").map { it.peer })
    }

    @Test
    fun unknownSimCountryUsesTheHostFallback() {
        val threads = SmsThreadIndex.build(listOf(
            sms("out", "sim-9", "13800138000", inbound = false, at = "2026-10-01T10:00:00Z"),
            sms("in", "sim-9", "+8613800138000", inbound = true, at = "2026-10-01T10:05:00Z"),
        )) { simId -> SmsPeerKey.countryFor(simId, emptyMap(), "CN") }
        assertEquals(listOf("+8613800138000"), threads.map { it.key })
        assertEquals("138 0013 8000", threads.single().peer)
    }

    @Test
    fun digitMatchingIgnoresFormattingButNotLetters() {
        assertTrue(SmsPeerKey.digitsMatch("+86 138 0013 8000", "13800138000"))
        assertTrue(SmsPeerKey.digitsMatch("+8613800138000", "138-0013"))
        assertFalse(SmsPeerKey.digitsMatch("+8613800138000", "code 138"))
        assertFalse(SmsPeerKey.digitsMatch("+8613800138000", " "))
    }
}
