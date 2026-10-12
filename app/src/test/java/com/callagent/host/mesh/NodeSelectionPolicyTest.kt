package com.callagent.host.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeSelectionPolicyTest {
    private val priority = listOf("home", "vps-a", "vps-b")
    private val ok = NodeObservation(reachable = true, gatewayOnline = true, rttMillis = 80)
    private val down = NodeObservation(reachable = false, gatewayOnline = false, rttMillis = null)

    @Test
    fun slowOrGatewayOfflineNodesAreUnhealthy() {
        assertTrue(NodeSelectionPolicy.healthy(ok))
        assertFalse(NodeSelectionPolicy.healthy(ok.copy(rttMillis = 3_500)))
        assertFalse(NodeSelectionPolicy.healthy(ok.copy(gatewayOnline = false)))
        assertFalse(NodeSelectionPolicy.healthy(down))
        assertEquals("延迟过高", NodeSelectionPolicy.record(null, ok.copy(rttMillis = 9_000), 1).issue)
        assertEquals("旧手机不在线", NodeSelectionPolicy.record(null, ok.copy(gatewayOnline = false), 1).issue)
    }

    @Test
    fun oneFailedProbeDoesNotSwitchButTwoDo() {
        var health = mapOf("home" to observe(null, ok), "vps-a" to observe(null, ok), "vps-b" to observe(null, ok))
        health = health + ("home" to observe(health["home"], down))
        assertNull(NodeSelectionPolicy.choose(priority, "home", health, busy = false))
        health = health + ("home" to observe(health["home"], down))
        assertEquals("vps-a", NodeSelectionPolicy.choose(priority, "home", health, busy = false))
    }

    @Test
    fun failoverSkipsUnhealthyNodesInPriorityOrder() {
        val health = mapOf(
            "home" to failing(2),
            "vps-a" to failing(1),
            "vps-b" to healthy(1)
        )
        assertEquals("vps-b", NodeSelectionPolicy.choose(priority, "home", health, busy = false))
        assertNull("nothing healthy: stay", NodeSelectionPolicy.choose(priority, "home",
            health + ("vps-b" to failing(1)), busy = false))
    }

    @Test
    fun failbackWaitsForSeveralHealthyProbes() {
        val recovering = mapOf("home" to healthy(2), "vps-a" to healthy(10), "vps-b" to healthy(10))
        assertNull(NodeSelectionPolicy.choose(priority, "vps-a", recovering, busy = false))
        val stable = recovering + ("home" to healthy(3))
        assertEquals("home", NodeSelectionPolicy.choose(priority, "vps-a", stable, busy = false))
        assertNull("lower priority nodes never pull the phone away",
            NodeSelectionPolicy.choose(priority, "home", stable, busy = false))
    }

    @Test
    fun busyPhoneNeverSwitches() {
        val health = mapOf("home" to failing(5), "vps-a" to healthy(5))
        assertNull(NodeSelectionPolicy.choose(priority, "home", health, busy = true))
        assertNull(NodeSelectionPolicy.choose(priority, "vps-a", mapOf("home" to healthy(9)), busy = true))
    }

    @Test
    fun unknownActiveNodeAdoptsTheFirstHealthyNode() {
        val health = mapOf("home" to failing(1), "vps-a" to healthy(1))
        assertEquals("vps-a", NodeSelectionPolicy.choose(priority, null, health, busy = false))
        assertEquals("vps-a", NodeSelectionPolicy.choose(priority, "removed", health, busy = false))
    }

    @Test
    fun countersResetOnStateChange() {
        val first = observe(null, ok)
        val second = observe(first, ok)
        assertEquals(2, second.consecutiveSuccesses)
        val broken = observe(second, down)
        assertEquals(0, broken.consecutiveSuccesses)
        assertEquals(1, broken.consecutiveFailures)
        assertEquals(0, observe(broken, ok).consecutiveFailures)
    }

    private fun observe(previous: NodeHealth?, observation: NodeObservation) =
        NodeSelectionPolicy.record(previous, observation, now = 1L)

    private fun healthy(successes: Int) = NodeHealth(consecutiveSuccesses = successes, checkedAt = 1L, gatewayOnline = true)

    private fun failing(failures: Int) = NodeHealth(consecutiveFailures = failures, checkedAt = 1L)
}
