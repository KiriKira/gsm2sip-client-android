package com.callagent.host.ui

import com.callagent.host.data.SimLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallReadinessTest {
    private val line = SimLine("sim-a", 0, "Work", null, null, "active", 4, true, "in_service")

    private fun evaluate(
        paired: Boolean = true, online: Boolean = true, sip: Boolean? = true, revision: Long? = 4,
        selected: SimLine? = line, server: Boolean? = true, reason: String? = null, engine: Boolean = true,
        link: ServerLink = ServerLink.Connected
    ) = CallReadiness.evaluate(paired, online, sip, revision, selected, server, reason, engine, link)

    @Test
    fun anUnreachableServerIsNotBlamedOnTheOldPhone() {
        assertEquals("无法连接服务器，暂时无法拨号。请检查本机网络或服务器。",
            evaluate(online = false, link = ServerLink.Unreachable("无法连接服务器")).blocker)
        assertEquals("正在连接服务器…", evaluate(online = false, link = ServerLink.Checking).blocker)
        assertTrue(evaluate(link = ServerLink.Checking).ready)
    }

    @Test
    fun blockersFollowTheOrderAUserCanFixThem() {
        assertEquals("请先在设置中完成配对。", evaluate(paired = false, online = false).blocker)
        assertEquals("正在检查服务器通话能力…", evaluate(server = null).blocker)
        assertEquals("服务器尚未配置通话（SIP/Asterisk）。", evaluate(server = false, reason = "sip_not_configured").blocker)
        assertEquals("服务器暂不可用（HTTP_503）。", evaluate(server = false, reason = "服务器暂不可用（HTTP_503）。").blocker)
        assertEquals("本机通话组件不可用。", evaluate(engine = false).blocker)
        assertEquals("旧手机离线，暂时无法拨号。", evaluate(online = false).blocker)
        assertEquals("请选择用于拨号的 SIM 卡。", evaluate(selected = null).blocker)
        assertEquals("SIM 1 需在旧手机上确认后才能使用。", evaluate(selected = line.copy(identityVerified = false)).blocker)
        assertEquals("SIM 配置已变化，正在同步…", evaluate(revision = 5).blocker)
    }

    @Test
    fun unregisteredGatewayVoiceIsOnlyAHint() {
        val result = evaluate(sip = false)
        assertTrue(result.ready)
        assertTrue(result.hint!!.contains("语音未注册"))
        assertNull(evaluate().hint)
    }
}
