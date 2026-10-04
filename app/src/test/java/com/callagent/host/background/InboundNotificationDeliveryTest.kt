package com.callagent.host.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InboundNotificationDeliveryTest {
    @Test
    fun coalescerRejectsOverCapacityBatchWithoutDroppingExistingOrRequestedIds() {
        val pending = (0 until 99).map { "queued-$it" }
        val requested = listOf("new-sms")

        val overflow = InboundNotificationDelivery.appendPendingIds(
            pending = pending,
            recentlyNotified = emptyList(),
            ids = requested,
            capacity = 99
        )

        assertEquals(null, overflow)
        assertEquals(99, pending.size)
        assertEquals(listOf("new-sms"), requested)
        assertEquals(
            99,
            InboundNotificationDelivery.appendPendingIds(
                pending = pending.dropLast(1),
                recentlyNotified = emptyList(),
                ids = requested,
                capacity = 99
            )!!.size
        )
    }

    @Test
    fun failedPostLeavesDurablePendingBatchForProcessReplay() {
        var durablePending = listOf("sms-1")
        var acknowledgements = 0

        val failedPost = InboundNotificationDelivery.postThenAcknowledge(
            post = { throw SecurityException("notifications disabled") },
            acknowledge = {
                acknowledgements++
                durablePending = emptyList()
                true
            },
            restorePending = { durablePending = listOf("sms-1") }
        )

        assertEquals(InboundNotificationDelivery.Result.POST_FAILED, failedPost)
        assertEquals(listOf("sms-1"), durablePending)
        assertEquals(0, acknowledgements)

        // A new service process reads the same durable pending IDs and retries.
        val replay = InboundNotificationDelivery.postThenAcknowledge(
            post = {},
            acknowledge = {
                acknowledgements++
                durablePending = emptyList()
                true
            },
            restorePending = { durablePending = listOf("sms-1") }
        )

        assertEquals(InboundNotificationDelivery.Result.ACKNOWLEDGED, replay)
        assertTrue(durablePending.isEmpty())
        assertEquals(1, acknowledgements)
    }

    @Test
    fun successfulPostWithFailedAckRestoresBatchUntilReplayAcknowledges() {
        val durablePending = mutableListOf("sms-1")
        val postedBatches = mutableListOf<List<String>>()
        val order = mutableListOf<String>()

        val ackFailure = InboundNotificationDelivery.postThenAcknowledge(
            post = {
                order += "post"
                postedBatches += durablePending.toList()
            },
            acknowledge = {
                order += "ack"
                durablePending.clear()
                false
            },
            restorePending = {
                order += "restore"
                durablePending += "sms-1"
            }
        )

        assertEquals(InboundNotificationDelivery.Result.ACK_FAILED, ackFailure)
        assertEquals(listOf("post", "ack", "restore"), order)
        assertEquals(listOf("sms-1"), durablePending)

        val replay = InboundNotificationDelivery.postThenAcknowledge(
            post = {
                order += "post-replay"
                postedBatches += durablePending.toList()
            },
            acknowledge = {
                order += "ack-replay"
                durablePending.clear()
                true
            },
            restorePending = { durablePending += "sms-1" }
        )

        assertEquals(InboundNotificationDelivery.Result.ACKNOWLEDGED, replay)
        assertEquals(listOf("sms-1", "sms-1"), postedBatches.flatten())
        assertTrue(durablePending.isEmpty())
    }
}
