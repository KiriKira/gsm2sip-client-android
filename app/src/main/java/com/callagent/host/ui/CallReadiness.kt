package com.callagent.host.ui

import com.callagent.host.data.SimLine

/**
 * Why the dial button is disabled, in the order a user can fix it. The rules
 * mirror [com.callagent.host.MainActivity.beginDial]; a null blocker means a
 * call may start. [hint] is advisory and never blocks.
 */
object CallReadiness {
    data class Result(val blocker: String?, val hint: String?) {
        val ready: Boolean get() = blocker == null
    }

    fun evaluate(
        paired: Boolean,
        gatewayOnline: Boolean,
        gatewaySipRegistered: Boolean?,
        gatewayRevision: Long?,
        line: SimLine?,
        callServerAvailable: Boolean?,
        callServerReason: String?,
        localEngineAvailable: Boolean,
        server: ServerLink = ServerLink.Connected
    ): Result {
        val blocker = when {
            !paired -> "请先在设置中完成配对。"
            callServerAvailable == null -> "正在检查服务器通话能力…"
            callServerAvailable == false -> when (callServerReason) {
                null, "" -> "服务器未启用通话。"
                "sip_not_configured" -> "服务器尚未配置通话（SIP/Asterisk）。"
                else -> if (callServerReason.any { it.code > 0x7f }) callServerReason else "服务器未启用通话（$callServerReason）。"
            }
            !localEngineAvailable -> "本机通话组件不可用。"
            // Only a stale snapshot is blamed on the server; a fresh one still allows a dial,
            // which reports its own failure if the server is really down.
            !gatewayOnline && server is ServerLink.Unreachable -> "无法连接服务器，暂时无法拨号。请检查本机网络或服务器。"
            !gatewayOnline && server is ServerLink.Checking -> "正在连接服务器…"
            !gatewayOnline -> "旧手机离线，暂时无法拨号。"
            line == null -> "请选择用于拨号的 SIM 卡。"
            !line.canSend -> "SIM ${line.slotIndex + 1} 需在旧手机上确认后才能使用。"
            gatewayRevision != line.mappingRevision -> "SIM 配置已变化，正在同步…"
            else -> null
        }
        val hint = if (blocker == null && gatewaySipRegistered == false) {
            "旧手机语音未注册，呼叫可能失败；请在旧手机完成“远程通话”设置。"
        } else null
        return Result(blocker, hint)
    }
}
