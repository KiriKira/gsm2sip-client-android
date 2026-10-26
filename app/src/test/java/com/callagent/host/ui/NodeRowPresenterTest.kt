package com.callagent.host.ui

import com.callagent.host.mesh.HostNode
import com.callagent.host.mesh.NodeHealth
import com.callagent.host.push.NodePushStatus
import com.callagent.host.push.NodePushStatus.Kind
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

    @Test
    fun pushStateJoinsTheQualityLineAndTheDetails() {
        val hour = 60 * 60 * 1000L
        val now = 100 * hour
        val health = NodeHealth(consecutiveSuccesses = 3, checkedAt = now, callReady = true)
        val rows = NodeRowPresenter.rows(nodes, mapOf("a" to health), activeKey = "a", push = mapOf(
            "a" to NodePushStatus(Kind.REGISTERED, checkedAt = now - 3 * hour),
            "b" to NodePushStatus(Kind.RETRYING, failures = 2, nextAttemptAt = now + 2 * 60_000L)
        ))
        assertEquals("可通话 · 推送已登记", rows[0].quality)
        assertEquals("a node never probed still shows push", "推送登记失败", rows[1].quality)
        assertNull(rows[2].quality)

        assertEquals("已登记，该节点可在后台唤醒此手机（3 小时前确认）",
            NodeRowPresenter.pushDetail(NodePushStatus(Kind.REGISTERED, checkedAt = now - 3 * hour), now))
        assertEquals("登记失败（连续 2 次，可能是节点或其到推送中继的网络问题），约 2 分钟后再试",
            NodeRowPresenter.pushDetail(NodePushStatus(Kind.RETRYING, failures = 2, nextAttemptAt = now + 2 * 60_000L), now))
        assertEquals("推送已登记（复查失败）",
            NodeRowPresenter.pushShort(NodePushStatus(Kind.RETRYING, failures = 1, stillRegistered = true)))
        val details = NodeRowPresenter.details(nodes[0], null, false, now,
            NodePushStatus(Kind.UNSUPPORTED, checkedAt = now - 30 * 60_000L)).toMap()
        assertEquals("该节点的服务器不支持推送，请升级 SoulGem（30 分钟前检查）", details["推送"])
        assertEquals("push shows even before a probe", "尚未检测", details["状态"])
    }
}
