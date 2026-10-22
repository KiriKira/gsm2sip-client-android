package com.callagent.host.ui

import com.callagent.host.data.GatewaySnapshot
import com.callagent.host.data.SimLine
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turns the cached gateway and SIM snapshots into what the main phone shows
 * about the remote old phone. Pure so freshness, offline and per-SIM rules are
 * unit-tested; [GatewayStatusView] only draws the result.
 */
object GatewayStatusPresenter {
    /** Server marks a gateway stale after 90 s; allow one extra sync interval. */
    const val ONLINE_GRACE_MILLIS = 120_000L

    enum class Tone { OK, WARNING, ERROR, UNKNOWN }

    data class SimStatus(
        val simId: String,
        val slotLabel: String,
        val title: String,
        val detail: String,
        /** 0–4 bars, or null when unknown or stale. */
        val signalLevel: Int?,
        val tone: Tone,
        val usable: Boolean
    )

    data class GatewayStatus(
        val headline: String,
        val detail: String,
        val tone: Tone,
        val online: Boolean,
        val voiceReady: Boolean,
        val sims: List<SimStatus>
    ) {
        /** One-line summary for compact surfaces and accessibility. */
        val accessibilitySummary: String get() = buildString {
            append(headline)
            if (detail.isNotBlank()) append("，").append(detail)
            sims.forEach { sim ->
                append("。").append(sim.slotLabel).append(" ").append(sim.title)
                if (sim.signalLevel != null) append("，信号 ").append(sim.signalLevel).append(" 格")
                if (sim.detail.isNotBlank()) append("，").append(sim.detail)
            }
        }
    }

    fun present(
        gateway: GatewaySnapshot?,
        sims: List<SimLine>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        server: ServerLink = ServerLink.Connected
    ): GatewayStatus {
        if (gateway == null) {
            if (server is ServerLink.Unreachable) {
                return GatewayStatus("无法连接服务器", server.reason, Tone.WARNING,
                    online = false, voiceReady = false, sims = emptyList())
            }
            return GatewayStatus("尚未连接旧手机", "旧手机完成配对并上报后会显示在这里。", Tone.UNKNOWN,
                online = false, voiceReady = false, sims = emptyList())
        }
        val lastSeen = gateway.lastSeenAt?.let(::parseInstant)
        val online = isOnline(gateway, lastSeen, nowMillis)
        val name = gateway.deviceName.ifBlank { "旧手机" }
        val voiceReady = online && gateway.voiceReady == true
        if (!online && server !is ServerLink.Connected) {
            // A stale snapshot only means this phone has not heard from the
            // server lately, not that the old phone went away.
            val seen = lastSeen?.let { "旧手机最后上报 ${relativeTime(it.toEpochMilli(), nowMillis, zone)}" }
            val detail = when (server) {
                is ServerLink.Unreachable -> listOfNotNull("${server.reason}，无法确认旧手机状态", seen)
                else -> listOfNotNull("正在连接服务器…", seen)
            }.joinToString(" · ")
            return GatewayStatus("$name · 状态未知", detail, Tone.WARNING, online = false, voiceReady = false,
                sims = sims.sortedBy { it.slotIndex }.map { simStatus(it, gatewayOnline = false) })
        }
        val headline = if (online) "$name · 在线" else "$name · 离线"
        val detail = if (online) {
            listOfNotNull(
                batteryText(gateway.batteryPercent, gateway.charging),
                if (gateway.voiceReady == true) "通话就绪" else "仅短信",
            ).joinToString(" · ")
        } else {
            val seen = lastSeen?.let { "最后在线 ${relativeTime(it.toEpochMilli(), nowMillis, zone)}" } ?: "从未上报"
            "$seen · 新短信任务会排队，旧手机恢复后发送"
        }
        val tone = when {
            !online -> Tone.ERROR
            sims.none { it.canSend } -> Tone.WARNING
            (gateway.batteryPercent ?: 100) <= 15 && gateway.charging != true -> Tone.WARNING
            else -> Tone.OK
        }
        return GatewayStatus(headline, detail, tone, online, voiceReady,
            sims.sortedBy { it.slotIndex }.map { simStatus(it, online) })
    }

    fun isOnline(gateway: GatewaySnapshot, lastSeen: Instant?, nowMillis: Long): Boolean {
        if (!gateway.online) return false
        // A cached snapshot keeps `online=true` after the app loses the server;
        // the heartbeat time is what actually ages.
        return lastSeen == null || nowMillis - lastSeen.toEpochMilli() <= ONLINE_GRACE_MILLIS
    }

    private fun simStatus(line: SimLine, gatewayOnline: Boolean): SimStatus {
        val slot = "SIM ${line.slotIndex + 1}"
        val radio = line.radio
        val operator = radio?.networkOperator ?: line.carrierName
        val title = listOfNotNull(line.label.takeIf { it.isNotBlank() }, operator?.takeIf { it != line.label })
            .joinToString(" · ").ifBlank { slot }
        val stateProblem = when {
            line.state == "pending_local_confirmation" -> "待旧手机确认"
            line.state == "removed" -> "已移除"
            !line.identityVerified || line.state == "unverified" -> "需在旧手机重新确认"
            line.state != "active" -> line.state
            else -> null
        }
        val service = when (line.serviceState) {
            null, "", "in_service" -> null
            "out_of_service" -> "无服务"
            "emergency_only" -> "仅紧急呼叫"
            "power_off" -> "射频关闭"
            "absent" -> "未检测到卡"
            else -> "服务状态未知"
        }
        val radioDetail = if (gatewayOnline && radio != null) {
            listOfNotNull(radio.networkType, if (radio.roaming == true) "漫游" else null).joinToString(" · ")
        } else ""
        val detail = listOfNotNull(stateProblem, service, radioDetail.takeIf { it.isNotBlank() },
            line.phoneNumber?.takeIf { it.isNotBlank() }).joinToString(" · ")
        val usable = line.canSend
        val tone = when {
            !usable -> Tone.ERROR
            !gatewayOnline -> Tone.UNKNOWN
            service != null -> Tone.WARNING
            else -> Tone.OK
        }
        return SimStatus(line.simId, slot, title, detail,
            signalLevel = if (gatewayOnline && usable) radio?.signalLevel else null, tone = tone, usable = usable)
    }

    fun batteryText(percent: Int?, charging: Boolean?): String? = when {
        percent == null && charging == true -> "充电中"
        percent == null -> null
        charging == true -> "电量 $percent% · 充电中"
        else -> "电量 $percent%"
    }

    fun relativeTime(thenMillis: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val delta = (nowMillis - thenMillis).coerceAtLeast(0)
        return when {
            delta < 60_000 -> "刚刚"
            delta < 3_600_000 -> "${delta / 60_000} 分钟前"
            delta < 86_400_000 -> "${delta / 3_600_000} 小时前"
            else -> DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)
                .format(Instant.ofEpochMilli(thenMillis).atZone(zone))
        }
    }

    private fun parseInstant(value: String): Instant? = runCatching { Instant.parse(value) }.getOrNull()
}
