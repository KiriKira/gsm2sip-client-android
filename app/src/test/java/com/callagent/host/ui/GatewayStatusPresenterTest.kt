package com.callagent.host.ui

import com.callagent.host.data.GatewaySnapshot
import com.callagent.host.data.SimLine
import com.callagent.host.data.SimRadio
import com.callagent.host.ui.GatewayStatusPresenter.Tone
import java.time.Instant
import java.time.ZoneOffset
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GatewayStatusPresenterTest {
    private val now = Instant.parse("2026-10-11T08:00:00Z").toEpochMilli()

    private fun gateway(lastSeen: String? = "2026-10-11T07:59:30Z", online: Boolean = true, sip: Boolean? = true,
                        battery: Int? = 80, charging: Boolean? = true) = GatewaySnapshot(
        gatewayId = "gw", deviceName = "旧小米", online = online, lastSeenAt = lastSeen, mappingRevision = 3,
        root = true, sipRegistered = sip, batteryPercent = battery, charging = charging
    )

    private fun line(slot: Int, state: String = "active", verified: Boolean = true, service: String? = "in_service",
                     radio: SimRadio? = SimRadio(3, "LTE", "中国移动", false, "2026-10-11T07:59:30Z")) = SimLine(
        simId = "sim-$slot", slotIndex = slot, label = "卡${slot + 1}", carrierName = "Carrier", phoneNumber = null,
        state = state, mappingRevision = 3, identityVerified = verified, serviceState = service, radio = radio
    )

    @Test
    fun onlineGatewayShowsBatteryVoiceAndPerSimSignal() {
        val status = GatewayStatusPresenter.present(gateway(), listOf(line(1), line(0)), now, ZoneOffset.UTC)
        assertTrue(status.online)
        assertTrue(status.voiceReady)
        assertEquals(Tone.OK, status.tone)
        assertEquals("旧小米 · 在线", status.headline)
        assertEquals("电量 80% · 充电中 · 通话就绪", status.detail)
        assertEquals(listOf("SIM 1", "SIM 2"), status.sims.map { it.slotLabel })
        assertEquals("卡1 · 中国移动", status.sims[0].title)
        assertEquals("LTE", status.sims[0].detail)
        assertEquals(3, status.sims[0].signalLevel)
    }

    @Test
    fun staleCachedSnapshotIsTreatedAsOfflineAndHidesRadio() {
        val status = GatewayStatusPresenter.present(gateway(lastSeen = "2026-10-11T07:50:00Z"), listOf(line(0)), now, ZoneOffset.UTC)
        assertFalse(status.online)
        assertFalse(status.voiceReady)
        assertEquals(Tone.ERROR, status.tone)
        assertEquals("旧小米 · 离线", status.headline)
        assertTrue(status.detail.startsWith("最后在线 10 分钟前"))
        assertNull(status.sims.single().signalLevel)
        assertEquals(Tone.UNKNOWN, status.sims.single().tone)
    }

    @Test
    fun staleSnapshotWithoutTheServerIsUnknownNotOffline() {
        val stale = gateway(lastSeen = "2026-10-11T07:50:00Z")
        val unreachable = GatewayStatusPresenter.present(stale, listOf(line(0)), now, ZoneOffset.UTC,
            server = ServerLink.Unreachable("无法连接服务器"))
        assertFalse(unreachable.online)
        assertEquals(Tone.WARNING, unreachable.tone)
        assertEquals("旧小米 · 状态未知", unreachable.headline)
        assertEquals("无法连接服务器，无法确认旧手机状态 · 旧手机最后上报 10 分钟前", unreachable.detail)
        assertNull(unreachable.sims.single().signalLevel)

        val checking = GatewayStatusPresenter.present(stale, listOf(line(0)), now, ZoneOffset.UTC, server = ServerLink.Checking)
        assertEquals("旧小米 · 状态未知", checking.headline)
        assertTrue(checking.detail.startsWith("正在连接服务器…"))

        // A fresh snapshot is still shown while the next sync is pending.
        assertEquals("旧小米 · 在线", GatewayStatusPresenter.present(gateway(), listOf(line(0)), now, ZoneOffset.UTC,
            server = ServerLink.Checking).headline)
        // Only a successful sync can report the old phone offline.
        assertEquals("旧小米 · 离线", GatewayStatusPresenter.present(stale, listOf(line(0)), now, ZoneOffset.UTC,
            server = ServerLink.Connected).headline)
        assertEquals("无法连接服务器", GatewayStatusPresenter.present(null, emptyList(), now,
            server = ServerLink.Unreachable("服务器返回错误 502")).headline)
    }

    @Test
    fun unusableSimsExplainWhatToDoOnTheOldPhone() {
        val status = GatewayStatusPresenter.present(gateway(sip = false),
            listOf(line(0, state = "unverified", verified = false), line(1, service = "out_of_service", radio = null)),
            now, ZoneOffset.UTC)
        assertEquals("电量 80% · 充电中 · 仅短信", status.detail)
        assertEquals("需在旧手机重新确认 · LTE", status.sims[0].detail)
        assertFalse(status.sims[0].usable)
        assertNull(status.sims[0].signalLevel)
        assertEquals(Tone.WARNING, status.sims[1].tone)
        assertEquals("无服务", status.sims[1].detail)
        assertEquals(Tone.OK, status.tone)
        val none = GatewayStatusPresenter.present(gateway(), listOf(line(0, state = "removed")), now, ZoneOffset.UTC)
        assertEquals(Tone.WARNING, none.tone)
    }

    @Test
    fun lowBatteryWarnsOnlyWhenNotCharging() {
        assertEquals(Tone.WARNING, GatewayStatusPresenter.present(gateway(battery = 10, charging = false), listOf(line(0)), now).tone)
        assertEquals(Tone.OK, GatewayStatusPresenter.present(gateway(battery = 10, charging = true), listOf(line(0)), now).tone)
        assertEquals(Tone.UNKNOWN, GatewayStatusPresenter.present(null, emptyList(), now).tone)
    }

    @Test
    fun relativeTimeAndRadioParsingAreBounded() {
        assertEquals("刚刚", GatewayStatusPresenter.relativeTime(now - 5_000, now))
        assertEquals("3 小时前", GatewayStatusPresenter.relativeTime(now - 3 * 3_600_000L, now))
        assertEquals("10月9日 08:00", GatewayStatusPresenter.relativeTime(now - 2 * 86_400_000L, now, ZoneOffset.UTC))
        val parsed = SimRadio.fromJson(JSONObject("""{"signal_level":9,"network_type":"LTE","network_operator":"  A\u0007","roaming":null,"updated_at":"2026-10-11T07:59:30Z"}"""))!!
        assertNull(parsed.signalLevel)
        assertEquals("LTE", parsed.networkType)
        assertNull(parsed.networkOperator)
        assertNull(parsed.roaming)
        assertEquals(parsed, SimRadio.fromJson(parsed.toJson()))
        assertNull(SimRadio.fromJson(null))
    }

    @Test
    fun radioCountryIsoIsOptionalAndStrict() {
        fun country(member: String) = SimRadio.fromJson(JSONObject("""{"signal_level":3$member}"""))!!.countryIso
        assertEquals("CN", country(""","country_iso":"CN""""))
        assertNull(country(""","country_iso":null"""))
        assertNull(country(""))
        listOf("\"cn\"", "\"CHN\"", "\" CN\"", "\"C1\"", "\"\"", "86").forEach { invalid ->
            assertNull(invalid, country(""","country_iso":$invalid"""))
        }
        val parsed = SimRadio.fromJson(JSONObject("""{"network_operator":"中国移动","country_iso":"CN"}"""))!!
        assertEquals(parsed, SimRadio.fromJson(parsed.toJson()))
        assertEquals(mapOf("sim-1" to "CN"), SmsPeerKey.simCountries(listOf(
            line(0).copy(simId = "sim-1", radio = parsed), line(1).copy(simId = "sim-2"), line(2).copy(simId = "sim-3", radio = null)
        )))
    }
}
