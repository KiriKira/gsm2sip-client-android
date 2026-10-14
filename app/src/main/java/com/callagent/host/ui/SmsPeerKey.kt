package com.callagent.host.ui

import android.telephony.PhoneNumberUtils
import com.callagent.host.data.SimLine
import java.util.Locale

/**
 * Conversation identity for an SMS peer. The same contact can appear as
 * `13800138000` (typed by the user) and `+8613800138000` (reported by the
 * network), so peers are normalized to E.164 using the country of the gateway
 * SIM that carried them. Short codes, alphanumeric senders and anything the
 * framework cannot validate keep their raw (trimmed) form. Presentation only:
 * stored records and the wire format never change.
 */
object SmsPeerKey {
    private val COUNTRY_ISO = Regex("^[A-Z]{2}$")
    private val DIALABLE = Regex("^\\+?[0-9 ()./-]+$")

    /** Returns [raw] as `^[A-Z]{2}$` uppercase, or null when it is not a country code. */
    fun countryIso(raw: String?): String? = raw?.trim()?.uppercase(Locale.ROOT)?.takeIf { COUNTRY_ISO.matches(it) }

    /** SIM ID to the gateway SIM's reported country, for SIMs whose radio reported one. */
    fun simCountries(sims: List<SimLine>): Map<String, String> =
        sims.mapNotNull { line -> line.radio?.countryIso?.let { line.simId to it } }.toMap()

    /** The first usable country among the host phone's SIM, network and locale countries. */
    fun hostCountryIso(simCountry: String?, networkCountry: String?, localeCountry: String?): String? =
        countryIso(simCountry) ?: countryIso(networkCountry) ?: countryIso(localeCountry)

    /** Country used to normalize peers on [simId]: the SIM's own, else the host phone's fallback. */
    fun countryFor(simId: String?, simCountries: Map<String, String>, fallback: String?): String? =
        simId?.let(simCountries::get) ?: countryIso(fallback)

    /** E.164 form of [raw] in [countryIso], or the trimmed raw peer when it cannot be normalized. */
    fun key(raw: String, countryIso: String?): String {
        val trimmed = raw.trim()
        val iso = countryIso(countryIso) ?: return trimmed
        if (!DIALABLE.matches(trimmed)) return trimmed
        return runCatching { PhoneNumberUtils.formatNumberToE164(trimmed, iso) }.getOrNull() ?: trimmed
    }

    /**
     * Readable form of a [key]: the national format when the E.164 number belongs
     * to [countryIso], the E.164 number otherwise, and the raw key when it was
     * never normalized.
     */
    fun display(key: String, countryIso: String?): String {
        val iso = countryIso(countryIso) ?: return key
        if (!key.startsWith("+")) return key
        // Country calling codes are one to three digits; the split whose national
        // part maps back to the same E.164 number in this country is the right one.
        val national = (1..3).asSequence()
            .filter { it < key.length - 1 }
            .map { key.substring(1 + it) }
            .firstOrNull { key(it, iso) == key }
            ?: return key
        return runCatching { PhoneNumberUtils.formatNumber(national, iso) }.getOrNull() ?: national
    }

    /** True when [query] digits occur in [value] digits, ignoring spaces, dashes and a leading `+`. */
    fun digitsMatch(value: String, query: String): Boolean {
        if (!DIALABLE.matches(query.trim())) return false
        val needle = query.filter(Char::isDigit)
        return needle.isNotEmpty() && value.filter(Char::isDigit).contains(needle)
    }
}
