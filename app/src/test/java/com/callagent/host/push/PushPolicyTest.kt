package com.callagent.host.push

import com.callagent.host.calls.RtcCallEngine
import com.callagent.host.push.PushRegistrationPolicy.NodeState
import com.callagent.host.push.PushRegistrationPolicy.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushPolicyTest {
    private val day = PushRegistrationPolicy.RECHECK_MS

    @Test
    fun aNodeWithoutPushIsAskedAgainOnlyAfterADay() {
        val now = 1_000_000L
        for (outcome in listOf(Outcome.UNSUPPORTED, Outcome.REFUSED)) {
            val state = PushRegistrationPolicy.after(outcome, NodeState(), "t1", now)
            assertFalse(PushRegistrationPolicy.ready(state, "t1"))
            assertFalse(PushRegistrationPolicy.shouldAttempt(state, "t1", now + day - 1))
            assertTrue(PushRegistrationPolicy.shouldAttempt(state, "t1", now + day))
        }
    }

    @Test
    fun transientFailuresBackOffUpToSixHours() {
        var state = NodeState()
        var now = 0L
        val delays = (1..12).map {
            val next = PushRegistrationPolicy.after(Outcome.RETRY, state, "t1", now)
            val delay = next.nextAttemptAt - now
            state = next; now = next.nextAttemptAt
            delay
        }
        assertEquals(60_000L, delays.first())
        assertEquals(6 * 60 * 60 * 1000L, delays.last())
        assertTrue(delays.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun aRegisteredTokenIsConfirmedDailyAndANewTokenAtOnce() {
        val state = PushRegistrationPolicy.after(Outcome.REGISTERED, NodeState(), "t1", 0L)
        assertTrue(PushRegistrationPolicy.ready(state, "t1"))
        assertFalse(PushRegistrationPolicy.shouldAttempt(state, "t1", day - 1))
        assertTrue(PushRegistrationPolicy.shouldAttempt(state, "t1", day))
        assertFalse("another token is not registered", PushRegistrationPolicy.ready(state, "t2"))
        assertTrue(PushRegistrationPolicy.shouldAttempt(state, "t2", 1))
        assertEquals(state, NodeState.decode(state.encode()))
        assertEquals(NodeState(), NodeState.decode("garbage"))
    }

    @Test
    fun withoutPushPollingIsUnchanged() {
        assertEquals(8_000L, RtcCallEngine.idlePollDelay(live = false, wakeChannelLive = true, pushReady = false))
        assertEquals(3_000L, RtcCallEngine.idlePollDelay(live = false, wakeChannelLive = false, pushReady = false))
        assertEquals(750L, RtcCallEngine.idlePollDelay(live = true, wakeChannelLive = true, pushReady = true))
        assertEquals(30_000L, RtcCallEngine.idlePollDelay(live = false, wakeChannelLive = true, pushReady = true))
        assertEquals(10_000L, RtcCallEngine.idlePollDelay(live = false, wakeChannelLive = false, pushReady = true))
        // Failing idle polls back off to a minute; a live call never does.
        assertEquals(listOf(6_000L, 12_000L, 24_000L, 48_000L, 60_000L, 60_000L),
            (1..6).map { RtcCallEngine.idlePollDelay(live = false, wakeChannelLive = false, pushReady = false, failedPolls = it) })
        assertEquals(60_000L, RtcCallEngine.idlePollDelay(live = false, wakeChannelLive = true, pushReady = true, failedPolls = 9))
        assertEquals(750L, RtcCallEngine.idlePollDelay(live = true, wakeChannelLive = false, pushReady = false, failedPolls = 9))
    }

    @Test
    fun aCallWakeUpIsReportedOnlyWhenNoNodeAnswered() {
        assertTrue(PushWake.callUnreachable(callShowing = false, lastPollSucceededAt = 100L, wokenAt = 200L))
        assertFalse("the call is ringing", PushWake.callUnreachable(callShowing = true, lastPollSucceededAt = 100L, wokenAt = 200L))
        assertFalse("call control answered; the call already ended", PushWake.callUnreachable(false, 300L, 200L))
    }
}
