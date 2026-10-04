package com.callagent.host.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class MessageWireValidationTest {
    private fun sms() = JSONObject()
        .put("message_id", "11111111-1111-4111-8111-111111111111")
        .put("gateway_id", "22222222-2222-4222-8222-222222222222")
        .put("command_id", JSONObject.NULL).put("sim_id", JSONObject.NULL)
        .put("direction", "inbound").put("text", "验证码🙂")
        .put("status", "received").put("created_at", "2026-10-04T03:00:00Z")
        .put("parts", JSONArray())

    private fun event(cursor: String, message: JSONObject = sms()) = JSONObject().put("cursor", cursor).put("message", message)
    private fun page(vararg events: JSONObject, next: String? = null) = JSONObject()
        .put("items", JSONArray(events.toList())).put("next_cursor", next ?: JSONObject.NULL)

    @Test fun nullableSimAndUnicodeRemainDeliverable() {
        assertEquals("验证码🙂", MessageWireValidation.message(sms()).getString("text"))
        assertEquals(2, MessageWireValidation.eventItems(page(event("MQ"), event("Mg"), next = "Mg"), null).length())
    }

    @Test fun malformedSecondMessageRejectsTheEntirePage() {
        val bad = sms().apply { remove("text") }
        assertThrows(IOException::class.java) { MessageWireValidation.eventItems(page(event("MQ"), event("Mg",bad)), null) }
    }

    @Test fun numericSmsBodyCannotSilentlyBecomeAnEmptyOrCoercedString() {
        assertThrows(IOException::class.java) { MessageWireValidation.message(sms().put("text", 123456)) }
    }

    @Test fun outOfOrderOrAlreadyCommittedEventsCannotAdvanceReception() {
        assertThrows(IOException::class.java) { MessageWireValidation.eventItems(page(event("Mg"),event("MQ")), null) }
        assertThrows(IOException::class.java) { MessageWireValidation.eventItems(page(event("MQ")), "MQ") }
    }

    @Test fun paginationCursorCannotSkipUndeliveredEvents() {
        assertThrows(IOException::class.java) { MessageWireValidation.eventItems(page(event("MQ"), next="MTAw"),null) }
        assertThrows(IOException::class.java) { MessageWireValidation.eventItems(page(next="MQ"),null) }
    }

    @Test fun malformedSnapshotRowCannotBeDroppedFromHistoryImport() {
        val json=JSONObject().put("items",JSONArray().put(sms()).put(JSONObject().put("text","lost")))
        assertThrows(IOException::class.java) { MessageWireValidation.messageItems(json) }
    }
}
