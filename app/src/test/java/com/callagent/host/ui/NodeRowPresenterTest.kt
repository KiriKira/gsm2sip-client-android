package com.callagent.host.ui

import com.callagent.host.mesh.HostNode
import com.callagent.host.mesh.NodeHealth
import com.callagent.host.push.NodePushStatus
import com.callagent.host.push.NodePushStatus.Kind
import com.callagent.host.push.PushProbe
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
        assertEquals("使用中 · 140 ms · 仅短信", rows[1].detail)
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
        assertEquals("calls join the second line", "使用中 · 90 ms · 可通话", row.detail)
        assertNull("the third line is only for the push path", row.quality)

        val details = NodeRowPresenter.details(nodes[1], health, active = true, now = 25_000).toMap()
        assertEquals("正在使用", details["角色"])
        assertEquals("15 秒前", details["检测时间"])
        assertEquals("90 ms", details["延迟"])
        assertNull(details["最近结果"])
        assertTrue(details.getValue("通话").startsWith("可通话"))
        assertEquals("尚未检测", NodeRowPresenter.details(nodes[0], null, false, 0).toMap()["状态"])
    }

    @Test
    fun fcmEndsTheSecondLineAndThePushPathIsTheThird() {
        val hour = 60 * 60 * 1000L
        val now = 100 * hour
        val health = NodeHealth(consecutiveSuccesses = 3, rttMillis = 92, checkedAt = now, callReady = true)
        val rows = NodeRowPresenter.rows(nodes, mapOf("a" to health), activeKey = "a", push = mapOf(
            "a" to NodePushStatus(Kind.REGISTERED, checkedAt = now - 3 * hour),
            "b" to NodePushStatus(Kind.RETRYING, failures = 2, nextAttemptAt = now + 2 * 60_000L),
            "c" to NodePushStatus(Kind.RETRYING, failures = 1, stillRegistered = true)
        ), probes = mapOf(
            "a" to PushProbe.NodeProbe(supported = true, relayOk = true, reachedAt = now - 3 * 60_000L),
            "b" to PushProbe.NodeProbe(supported = true, relayOk = false, relayFailures = 3),
            "c" to PushProbe.NodeProbe(supported = false)
        ), now = now)
        assertEquals("使用中 · 92 ms · 可通话 · FCM", rows[0].detail)
        assertEquals("Kyubey 正常 · 本机已收到 · 3 分钟前", rows[0].quality)
        assertEquals("a node that failed to register shows no FCM", "备用 · 尚未检测", rows[1].detail)
        assertEquals("Kyubey 不通（3 次）", rows[1].quality)
        assertEquals("a registration still standing keeps FCM", "备用 · 尚未检测 · FCM", rows[2].detail)
        assertNull("an older node says nothing about its push path", rows[2].quality)

        assertEquals("Kyubey 正常 · 本机未收到测试推送", NodeRowPresenter.pushPath(
            PushProbe.NodeProbe(supported = true, relayOk = true, testLost = true), now))
        assertEquals("推送已登记（复查失败）",
            NodeRowPresenter.pushShort(NodePushStatus(Kind.RETRYING, failures = 1, stillRegistered = true)))
        val details = NodeRowPresenter.details(nodes[0], null, false, now,
            NodePushStatus(Kind.UNSUPPORTED, checkedAt = now - 30 * 60_000L),
            PushProbe.NodeProbe(supported = true, relayOk = true), elapsedNow = now).toMap()
        assertEquals("该节点的服务器不支持推送，请升级 SoulGem（30 分钟前检查）", details["推送"])
        assertEquals("Kyubey 正常", details["推送通道"])
        assertEquals("push shows even before a probe", "尚未检测", details["状态"])
    }
}
