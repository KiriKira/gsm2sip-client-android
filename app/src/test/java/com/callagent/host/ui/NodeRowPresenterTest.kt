package com.callagent.host.ui

import com.callagent.host.mesh.HostNode
import com.callagent.host.mesh.NodeHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeRowPresenterTest {
    private val nodes = listOf(
        HostNode("a", "ia", "https://home.lan/v1", "home.lan", ""),
        HostNode("b", "ib", "https://vps.example/v1", "vps.example", "node-b"),
        HostNode("c", "ic", "https://edge.example/v1", "edge.example", "node-c")
    )

    @Test
    fun rowsShowPriorityRoleAndHealth() {
        val rows = NodeRowPresenter.rows(nodes, mapOf(
            "a" to NodeHealth(consecutiveFailures = 2, checkedAt = 5, issue = "无法连接"),
            "b" to NodeHealth(consecutiveSuccesses = 4, rttMillis = 140, gatewayOnline = true, checkedAt = 5)
        ), activeKey = "b")

        assertEquals(listOf("1. home.lan", "2. vps.example", "3. edge.example"), rows.map { it.title })
        assertEquals("备用 · 无法连接", rows[0].detail)
        assertEquals(false, rows[0].healthy)
        assertEquals("使用中 · 正常 · 延迟 140 ms", rows[1].detail)
        assertTrue(rows[1].active)
        assertFalse("the active node cannot be removed", rows[1].removable)
        assertEquals("备用 · 尚未检测", rows[2].detail)
        assertNull(rows[2].healthy)
    }

    @Test
    fun qualityLineAndDetailsDescribeTheNode() {
        val health = NodeHealth(consecutiveSuccesses = 3, rttMillis = 90, gatewayOnline = true, checkedAt = 10_000,
            gatewayId = "gw", callReady = true)
        val row = NodeRowPresenter.rows(nodes, mapOf("b" to health), activeKey = "b")[1]
        assertEquals("only the current state, no probe history", "可通话", row.quality)
        assertNull("no quality before any probe", NodeRowPresenter.rows(nodes, emptyMap(), "a")[0].quality)

        val details = NodeRowPresenter.details(nodes[1], health, active = true, now = 25_000).toMap()
        assertEquals("正在使用", details["角色"])
        assertEquals("15 秒前", details["检测时间"])
        assertEquals("90 ms", details["延迟"])
        assertNull(details["最近结果"])
        assertTrue(details.getValue("通话").startsWith("可通话"))
        assertEquals("尚未检测", NodeRowPresenter.details(nodes[0], null, false, 0).toMap()["状态"])
    }
}
