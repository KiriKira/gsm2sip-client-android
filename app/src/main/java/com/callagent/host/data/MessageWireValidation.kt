package com.callagent.host.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.Base64

/** Reject a malformed page before any transaction can advance its cursor. */
internal object MessageWireValidation {
    private val uuid = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private fun reject(): Nothing = throw IOException("Invalid durable message page; reception cursor was not advanced")

    fun message(json: JSONObject): JSONObject {
        for (field in listOf("message_id", "gateway_id")) {
            val value = json.opt(field) as? String ?: reject()
            if (!uuid.matches(value)) reject()
        }
        if (json.opt("text") !is String || json.opt("status") !is String || json.opt("direction") !in listOf("inbound", "outbound")) reject()
        val createdAt = json.opt("created_at") as? String ?: reject()
        if (runCatching { Instant.parse(createdAt) }.isFailure) reject()
        if (json.opt("parts") !is JSONArray) reject()
        for (field in listOf("sim_id", "command_id")) {
            if (!json.has(field)) reject()
            if (!json.isNull(field) && !uuid.matches(json.opt(field) as? String ?: reject())) reject()
        }
        return json
    }

    fun messageItems(json: JSONObject): JSONArray {
        val items = json.optJSONArray("items") ?: reject()
        for (index in 0 until items.length()) message(items.optJSONObject(index) ?: reject())
        return items
    }

    fun eventItems(json: JSONObject, requestedCursor: String?): JSONArray {
        val items = json.optJSONArray("items") ?: reject()
        var previous = requestedCursor?.let(::cursor) ?: 0L
        var last: String? = null
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: reject()
            message(item.optJSONObject("message") ?: reject())
            val encoded = item.opt("cursor") as? String ?: reject()
            val value = cursor(encoded)
            if (value <= previous) reject()
            previous = value
            last = encoded
        }
        if (!json.has("next_cursor")) reject()
        if (!json.isNull("next_cursor")) {
            val next = json.opt("next_cursor") as? String ?: reject()
            if (next != last) reject()
        }
        return items
    }

    private fun cursor(encoded: String): Long {
        val text = runCatching { String(Base64.getUrlDecoder().decode(encoded), Charsets.US_ASCII) }.getOrElse { reject() }
        val value = text.toLongOrNull() ?: reject()
        if (value < 0L) reject()
        return value
    }
}
