#!/usr/bin/env python3
"""Capture cached paired-dashboard UI using a disposable synthetic offline fixture.

The fixture runner reuses the ADB and screenshot primitives in
android-ui-smoke.py. It never pairs an account, sends SMS, registers SIP, or
starts a call. Its signed instrumentation APK writes only synthetic cache data
and removes that data after capture.
"""

from __future__ import annotations

import argparse
import datetime as dt
import importlib.util
import json
import os
import re
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any


SMOKE_MODULE_PATH = Path(__file__).with_name("android-ui-smoke.py")
SMOKE_SPEC = importlib.util.spec_from_file_location("android_ui_smoke", SMOKE_MODULE_PATH)
if SMOKE_SPEC is None or SMOKE_SPEC.loader is None:
    raise RuntimeError(f"Could not load ADB smoke helpers from {SMOKE_MODULE_PATH}")
SMOKE_MODULE = importlib.util.module_from_spec(SMOKE_SPEC)
sys.modules[SMOKE_SPEC.name] = SMOKE_MODULE
SMOKE_SPEC.loader.exec_module(SMOKE_MODULE)


class PairedUiFixture(SMOKE_MODULE.Smoke):
    EXPECTED_CHECKS = (
        "emulator_boot", "emulator_device", "emulator_api_level", "apk_install", "apk_native_abi",
        "fixture_harness_install", "network_disabled", "synthetic_fixture_seeded",
        "app_visible", "fixture_dashboard", "fixture_paired_hosts", "fixture_sim_selection",
        "fixture_initial_unfold", "fixture_fold_state_changed", "fixture_unfold_state_restored",
        "fixture_hosts_folded", "fixture_hosts_unfolded",
        "fixture_sms_inbox", "fixture_sms_outbound_preview", "fixture_sms_compose", "fixture_sms_compose_fields",
        "fixture_settings", "fixture_call_unavailable", "fixture_call_controls_disabled",
        "synthetic_fixture_cleanup", "fixture_harness_uninstall", "network_restored",
    )

    def __init__(self, args: argparse.Namespace) -> None:
        super().__init__(args)
        self.smoke_module = SMOKE_MODULE
        self.fixture_stages: list[dict[str, Any]] = []
        self.fixture_network: dict[str, Any] = {}
        self.fixture_instrumentation_installed = False
        self.fixture_prior_airplane: str | None = None
        self.fixture_prior_wifi: str | None = None
        self.fixture_prior_mobile_data: str | None = None

    def find_text_node(self, root: ET.Element, needle: str) -> ET.Element | None:
        for node in self.nodes(root):
            if needle in self.node_value(node):
                return node
        return None

    def visible_text(self, root: ET.Element, needle: str) -> bool:
        node = self.find_text_node(root, needle)
        return node is not None and self.node_is_on_screen(node)

    def fixture_scroll_area(self, root: ET.Element, target: ET.Element | None,
                            selector: str) -> tuple[int, int, int, int] | None:
        parents = {child: parent for parent in root.iter() for child in parent}
        current = target
        while current is not None:
            resource_id = current.attrib.get("resource-id", "")
            if (current.attrib.get("scrollable") == "true" or
                    resource_id.endswith("/main_content_scroll") or
                    resource_id.endswith("/horizontal_fold_top_scroll") or
                    resource_id.endswith("/horizontal_fold_bottom_scroll")):
                bounds = self.parse_bounds(current.attrib.get("bounds", ""))
                if bounds:
                    return bounds
            current = parents.get(current)

        preferred = ("horizontal_fold_bottom_scroll" if
                     selector in {"sms_search", "sms_recipient", "sms_body"} or
                     selector.startswith("SYNTHETIC UI fixture") else
                     "horizontal_fold_top_scroll")
        scroll = self.find_node(root, preferred)
        if scroll is None:
            scroll = self.find_node(root, "main_content_scroll")
        return self.parse_bounds(scroll.attrib.get("bounds", "")) if scroll is not None else None

    def fixture_ensure_visible(self, root: ET.Element, *, resource_id: str | None = None,
                               text: str | None = None, name: str) -> ET.Element:
        selector = resource_id or text or name
        for attempt in range(7):
            node = self.find_node(root, resource_id) if resource_id else self.find_text_node(root, text or "")
            if node is not None and self.node_is_on_screen(node):
                return root
            area = self.fixture_scroll_area(root, node, selector)
            width, height = self.last_image_size or (900, 1800)
            if area is None:
                area = (0, 0, width, height)
            left, top, right, bottom = area
            area_height = max(1, bottom - top)
            bounds = self.parse_bounds(node.attrib.get("bounds", "")) if node is not None else None
            if bounds is not None and bounds[1] >= bottom:
                start_y, end_y = bottom - min(100, area_height // 5), top + area_height // 3
            elif bounds is not None and bounds[3] <= top:
                start_y, end_y = top + area_height // 3, bottom - min(100, area_height // 5)
            else:
                start_y, end_y = bottom - min(100, area_height // 5), top + area_height // 3
            x = max(left + 8, min(right - 8, (left + right) // 2))
            self.shell("input", "swipe", str(x), str(max(top + 8, start_y)),
                       str(x), str(max(top + 8, end_y)), "350", check=True,
                       label=f"fixture_scroll_{SMOKE_MODULE.safe_name(name)}_{attempt}")
            self.wait(1)
            root = self.capture(f"fixture_scroll_{SMOKE_MODULE.safe_name(name)}_{attempt}")
        node = self.find_node(root, resource_id) if resource_id else self.find_text_node(root, text or "")
        if node is None:
            raise RuntimeError(f"Fixture UI element was not found: {selector}")
        if not self.node_is_on_screen(node):
            raise RuntimeError(f"Fixture UI element did not enter the visible viewport: {selector}")
        return root

    def record_fixture_stage(self, check: str, name: str, root: ET.Element,
                             required_visible_text: tuple[str, ...], detail: str,
                             status_override: str | None = None,
                             any_visible_text: tuple[str, ...] = ()) -> None:
        visible = [item for item in required_visible_text if self.visible_text(root, item)]
        matched_alternative = next((item for item in any_visible_text if self.visible_text(root, item)), None)
        okay = len(visible) == len(required_visible_text) and (not any_visible_text or matched_alternative is not None)
        status = status_override or ("pass" if okay else "fail")
        missing = [item for item in required_visible_text if item not in visible]
        observed = list(visible)
        if matched_alternative is not None:
            observed.append(matched_alternative)
        missing_detail = f"missing visible text={missing}"
        if any_visible_text and matched_alternative is None:
            missing_detail += f"; none of the cache labels were visible={list(any_visible_text)}"
        actual_detail = detail
        if matched_alternative is not None:
            actual_detail += f"; visible cached-host label={matched_alternative!r}"
        self.record(check, status, actual_detail if okay else f"{actual_detail}; {missing_detail}")
        self.fixture_stages.append({
            "name": name,
            "status": status,
            "screenshot": f"{SMOKE_MODULE.safe_name(name)}.png",
            "ui_hierarchy": f"{SMOKE_MODULE.safe_name(name)}.xml",
            "evidence": observed,
            "detail": actual_detail if okay else f"{actual_detail}; {missing_detail}",
        })

    def set_fixture_network_offline(self) -> None:
        self.fixture_prior_airplane = self.shell("settings", "get", "global", "airplane_mode_on",
                                                label="fixture_prior_airplane_mode")
        self.fixture_prior_wifi = self.shell("settings", "get", "global", "wifi_on",
                                             label="fixture_prior_wifi_state")
        self.fixture_prior_mobile_data = self.shell("settings", "get", "global", "mobile_data",
                                                   label="fixture_prior_mobile_data")
        if self.fixture_prior_airplane not in {"0", "1"}:
            raise RuntimeError(f"could not read prior airplane mode state: {self.fixture_prior_airplane!r}")
        if self.fixture_prior_wifi not in {"0", "1"}:
            raise RuntimeError(f"could not read prior Wi-Fi state: {self.fixture_prior_wifi!r}")
        if self.fixture_prior_mobile_data not in {"0", "1"}:
            raise RuntimeError(f"could not read prior mobile-data state: {self.fixture_prior_mobile_data!r}")
        self.command(["shell", "cmd", "connectivity", "airplane-mode", "enable"], check=True,
                     label="fixture_enable_airplane_mode")
        self.shell("svc", "wifi", "disable", check=True, label="fixture_disable_wifi")
        self.shell("svc", "data", "disable", check=True, label="fixture_disable_mobile_data")
        self.wait(2)
        airplane = self.shell("settings", "get", "global", "airplane_mode_on", label="fixture_airplane_mode_state")
        wifi = self.shell("settings", "get", "global", "wifi_on", label="fixture_wifi_state")
        mobile = self.shell("settings", "get", "global", "mobile_data", label="fixture_mobile_data_state")
        connectivity = ""
        active_network = ""
        no_active_validated_network = False
        network_deadline = time.monotonic() + 15
        while time.monotonic() < network_deadline:
            connectivity = self.shell("dumpsys", "connectivity", label="fixture_connectivity_state")
            active_match = re.search(
                r"(?im)^\s*(?:Active default network|Current default network|Default network):\s*([^\s,]+)",
                connectivity,
            )
            active_network = active_match.group(1).strip("{} ,") if active_match else ""
            if not active_network:
                active_result = self.command(["shell", "cmd", "connectivity", "get-active-network"],
                                             label="fixture_active_network")
                active_output = (str(active_result.stdout) + active_result.stderr.decode("utf-8", errors="replace")).strip()
                if active_result.returncode == 0 and re.search(
                        r"(?i)\b(null|none|no active network|no default network)\b", active_output):
                    active_network = "none"
                elif active_result.returncode == 0 and re.fullmatch(r"[0-9]+", active_output):
                    active_network = active_output
            active_token = active_network.lower()
            if active_token in {"none", "null", "-1", "0"}:
                no_active_validated_network = True
            elif active_network:
                network_blocks = re.split(r"(?=NetworkAgentInfo\s*\{)", connectivity)
                active_block = next((block for block in network_blocks
                                     if re.search(rf"network\s*\{{\s*{re.escape(active_network)}\s*\}}", block)), None)
                no_active_validated_network = active_block is not None and not re.search(
                    r"\bVALIDATED\b", active_block, re.IGNORECASE
                )
            if no_active_validated_network:
                break
            self.wait(2)
        (self.artifacts / "connectivity_offline.txt").write_text(connectivity, encoding="utf-8")
        okay = (airplane == "1" and wifi in {"0", "0.0"} and mobile in {"0", "0.0"}
                and no_active_validated_network)
        self.fixture_network = {
            "airplane_mode_enabled": airplane == "1",
            "wifi_disabled": wifi in {"0", "0.0"},
            "mobile_data_disabled": mobile in {"0", "0.0"},
            "no_active_validated_network": no_active_validated_network,
            "offline_confirmed": okay,
            "endpoint_host": "ui-smoke.invalid",
            "active_default_network": active_network or "unknown",
            "observed_settings": {
                "airplane_mode_on": airplane,
                "wifi_on": wifi,
                "mobile_data": mobile,
            },
        }
        self.record("network_disabled", "pass" if okay else "fail",
                    f"airplane_mode={airplane}, wifi={wifi}, mobile_data={mobile}, "
                    f"active_default_network={active_network or 'unknown'}, "
                    f"no_active_validated_network={no_active_validated_network}; synthetic endpoint only")
        if not okay:
            raise RuntimeError("could not confirm airplane mode and both radio settings are off")

    def restore_fixture_network(self) -> None:
        prior_states = (self.fixture_prior_airplane, self.fixture_prior_wifi, self.fixture_prior_mobile_data)
        if any(state not in {"0", "1"} for state in prior_states):
            self.record("network_restored", "fail",
                        f"prior radio states were not fully captured: airplane/wifi/mobile={prior_states}")
            return
        airplane_on = self.fixture_prior_airplane == "1"
        self.command(["shell", "cmd", "connectivity", "airplane-mode",
                      "enable" if airplane_on else "disable"], check=True,
                     label="fixture_restore_airplane_mode")
        if self.fixture_prior_wifi in {"0", "1"}:
            self.shell("svc", "wifi", "enable" if self.fixture_prior_wifi == "1" else "disable",
                       check=True,
                       label="fixture_restore_wifi")
        if self.fixture_prior_mobile_data in {"0", "1"}:
            self.shell("svc", "data", "enable" if self.fixture_prior_mobile_data == "1" else "disable",
                       check=True,
                       label="fixture_restore_mobile_data")
        self.wait(2)
        airplane = self.shell("settings", "get", "global", "airplane_mode_on", label="fixture_restored_airplane_mode")
        wifi = self.shell("settings", "get", "global", "wifi_on", label="fixture_restored_wifi_state")
        mobile = self.shell("settings", "get", "global", "mobile_data", label="fixture_restored_mobile_data")
        okay = (airplane == self.fixture_prior_airplane and
                (self.fixture_prior_wifi not in {"0", "1"} or wifi == self.fixture_prior_wifi) and
                (self.fixture_prior_mobile_data not in {"0", "1"} or mobile == self.fixture_prior_mobile_data))
        self.record("network_restored", "pass" if okay else "fail",
                    f"prior airplane/wifi/mobile={self.fixture_prior_airplane}/{self.fixture_prior_wifi}/{self.fixture_prior_mobile_data}; "
                    f"restored={airplane}/{wifi}/{mobile}")

    def run_fixture_instrumentation(self, mode: str) -> None:
        component = self.args.fixture_component
        result = self.command(["shell", "am", "instrument", "-w", "-r", "-e", "mode", mode, component],
                              timeout=self.smoke_module.ADB_TIMEOUT_SECONDS,
                              label=f"fixture_instrumentation_{mode}")
        output = str(result.stdout)
        codes = re.findall(r"^INSTRUMENTATION_CODE:\s*(-?\d+)", output, re.MULTILINE)
        failure = re.search(r"^INSTRUMENTATION_STATUS: failure=", output, re.MULTILINE)
        expected_state = "seeded" if mode == "seed" else "cleaned"
        if result.returncode != 0 or not codes or codes[-1] != "-1" or failure or \
                f"INSTRUMENTATION_STATUS: fixture={expected_state}" not in output:
            raise RuntimeError(f"Fixture instrumentation mode {mode} failed; see its command log")

    def run_fixture(self) -> None:
        self.command(["wait-for-device"], timeout=self.smoke_module.ADB_TIMEOUT_SECONDS,
                     check=True, label="wait_for_device")
        boot = self.shell("getprop", "sys.boot_completed", label="boot_completed")
        if boot != "1":
            deadline = time.monotonic() + 180
            while boot != "1" and time.monotonic() < deadline:
                self.wait(2)
                boot = self.shell("getprop", "sys.boot_completed", label="boot_completed_poll")
        if boot != "1":
            self.record("emulator_boot", "fail", f"sys.boot_completed={boot!r}")
            raise RuntimeError("emulator did not complete boot")
        self.record("emulator_boot", "pass", f"device {self.serial} boot completed")
        qemu = self.shell("getprop", "ro.kernel.qemu", label="fixture_qemu_property")
        if qemu == "1":
            self.record("emulator_device", "pass", "ro.kernel.qemu=1 confirms an Android Emulator target")
        else:
            self.record("emulator_device", "fail", f"expected Android Emulator ro.kernel.qemu=1, observed {qemu!r}")
            raise RuntimeError("paired UI fixture only runs on the API 35 Android Emulator")
        sdk = self.shell("getprop", "ro.build.version.sdk", label="device_api_level")
        if sdk == "35":
            self.record("emulator_api_level", "pass", "fixture requires Android API 35")
        else:
            self.record("emulator_api_level", "fail", f"expected API 35, observed {sdk!r}")
            raise RuntimeError("paired UI fixture requires API 35")
        self.check_apk_install()

        fixture_apk = Path(self.args.fixture_apk)
        if not fixture_apk.is_file():
            self.record("fixture_harness_install", "fail", f"fixture APK does not exist: {fixture_apk}")
            raise RuntimeError("signed fixture instrumentation APK is missing")

        seed_attempted = False
        try:
            install = self.command(["install", "-r", str(fixture_apk)], label="install_fixture_harness")
            install_text = str(install.stdout) + install.stderr.decode("utf-8", errors="replace")
            self.fixture_instrumentation_installed = install.returncode == 0 and "Success" in install_text
            self.record("fixture_harness_install", "pass" if self.fixture_instrumentation_installed else "fail",
                        "signed fixture instrumentation APK installed with matching host signer")
            if not self.fixture_instrumentation_installed:
                raise RuntimeError("fixture instrumentation APK could not be installed")

            self.set_fixture_network_offline()
            seed_attempted = True
            self.run_fixture_instrumentation("seed")
            self.record("synthetic_fixture_seeded", "pass",
                        "SessionStore.writeIfCurrent and production ClientDatabase APIs saved synthetic-only cache data")

            self.launch()
            self.verify_app_foreground()
            root = self.wait_for_app_tree(
                lambda tree: self.find_text_node(tree, "SYNTHETIC offline gateway") is not None,
                "synthetic_paired_dashboard")
            root = self.capture("paired_dashboard_top")
            self.record_fixture_stage("fixture_dashboard", "paired_dashboard_top", root,
                                      ("SYNTHETIC offline gateway",),
                                      "Cached offline dashboard with a visibly synthetic gateway")

            root = self.fixture_ensure_visible(root, text="SYNTHETIC Tablet Host", name="paired_hosts")
            root = self.capture("paired_hosts_cached")
            self.record_fixture_stage("fixture_paired_hosts", "paired_hosts_cached", root,
                                      ("SYNTHETIC Foldable Host", "SYNTHETIC Tablet Host"),
                                      "Two synthetic cached host rows",
                                      any_visible_text=(
                                          "显示上次读取的主机列表；刷新后更新。",
                                          "主机列表暂时无法更新，当前显示上次结果。点击刷新重试。",
                                      ))

            initial_unfold = self.command(["emu", "unfold"], timeout=self.smoke_module.ADB_TIMEOUT_SECONDS,
                                          label="fixture_prepare_unfolded_state")
            self.wait(6)
            before_fold = self.device_snapshot("fixture_fold_before")
            self.record("fixture_initial_unfold", "pass" if initial_unfold.returncode == 0 else "blocked",
                        f"emu unfold exit={initial_unfold.returncode}; initial observable state={before_fold}")
            fold = self.command(["emu", "fold"], timeout=self.smoke_module.ADB_TIMEOUT_SECONDS,
                                label="fixture_emulator_fold")
            self.wait(6)
            folded = self.device_snapshot("fixture_folded")
            fold_changed = bool((before_fold["size"] and folded["size"] and before_fold["size"] != folded["size"]) or
                                (before_fold["state"] and folded["state"] and before_fold["state"] != folded["state"]))
            folded_root = self.capture("fixture_folded_dashboard")
            folded_root = self.fixture_ensure_visible(folded_root, text="SYNTHETIC Tablet Host",
                                                       name="paired_hosts_folded")
            folded_root = self.capture("paired_hosts_folded")
            folded_visible = all(self.visible_text(folded_root, name)
                                 for name in ("SYNTHETIC Foldable Host", "SYNTHETIC Tablet Host"))
            fold_stage_status = "pass" if fold.returncode == 0 and fold_changed and folded_visible else "blocked"
            self.record_fixture_stage("fixture_hosts_folded", "paired_hosts_folded", folded_root,
                                      ("SYNTHETIC Foldable Host", "SYNTHETIC Tablet Host"),
                                      "Synthetic cached host rows after emulator fold",
                                      status_override=fold_stage_status)
            self.record("fixture_fold_state_changed",
                        "pass" if fold.returncode == 0 and fold_changed else "blocked",
                        f"fold exit={fold.returncode}; before={before_fold}; folded={folded}")

            unfold = self.command(["emu", "unfold"], timeout=self.smoke_module.ADB_TIMEOUT_SECONDS,
                                  label="fixture_emulator_unfold")
            self.wait(6)
            unfolded = self.device_snapshot("fixture_unfolded")
            unfold_restored = bool((before_fold["size"] and unfolded["size"] == before_fold["size"]) or
                                   (before_fold["state"] and unfolded["state"] == before_fold["state"]))
            unfolded_root = self.capture("fixture_unfolded_dashboard")
            unfolded_root = self.fixture_ensure_visible(unfolded_root, text="SYNTHETIC Tablet Host",
                                                         name="paired_hosts_unfolded")
            unfolded_root = self.capture("paired_hosts_unfolded")
            unfolded_visible = all(self.visible_text(unfolded_root, name)
                                   for name in ("SYNTHETIC Foldable Host", "SYNTHETIC Tablet Host"))
            unfold_stage_status = "pass" if (initial_unfold.returncode == 0 and unfold.returncode == 0 and
                                              unfold_restored and unfolded_visible) else "blocked"
            self.record_fixture_stage("fixture_hosts_unfolded", "paired_hosts_unfolded", unfolded_root,
                                      ("SYNTHETIC Foldable Host", "SYNTHETIC Tablet Host"),
                                      "Synthetic cached host rows after emulator unfold",
                                      status_override=unfold_stage_status)
            self.record("fixture_unfold_state_restored",
                        "pass" if (initial_unfold.returncode == 0 and unfold.returncode == 0 and unfold_restored)
                        else "blocked",
                        f"unfold exit={unfold.returncode}; before={before_fold}; unfolded={unfolded}")

            root = unfolded_root
            root = self.fixture_ensure_visible(root, text="SIM 1", name="select_fixture_sim_a")
            sim_node = next((node for node in self.nodes(root)
                             if node.attrib.get("text") == "SIM 1" and
                             node.attrib.get("checkable") == "true" and
                             "SYNTHETIC SIM A" in node.attrib.get("content-desc", "")), None)
            if sim_node is None:
                self.record("fixture_sim_selection", "fail", "selectable synthetic SIM A chip was not found")
            else:
                self.tap_node(sim_node, "select_synthetic_sim_a")
                self.wait(2)
                root = self.capture("fixture_sim_selected")
                selected_chips = [node for node in self.nodes(root)
                                  if node.attrib.get("checkable") == "true" and
                                  node.attrib.get("checked") == "true" and
                                  node.attrib.get("text") in {"SIM 1", "SIM 2"}]
                selected_a = (len(selected_chips) == 1 and
                              selected_chips[0].attrib.get("text") == "SIM 1")
                self.record("fixture_sim_selection", "pass" if selected_a else "fail",
                            "only synthetic SIM A is selected; no SMS action was activated" if selected_a else
                            "synthetic SIM A exclusive selection was not confirmed")

            root = self.fixture_ensure_visible(root, text="SYNTHETIC UI fixture inbox preview", name="sms_inbox_message")
            root = self.capture("sms_inbox")
            self.record_fixture_stage("fixture_sms_inbox", "sms_inbox", root,
                                      ("短信收件箱", "SYNTHETIC UI fixture inbox preview"),
                                      "Synthetic cached inbound preview displayed; no SMS was received")

            root = self.fixture_ensure_visible(root, text="SYNTHETIC UI fixture queued preview",
                                               name="sms_outbound_preview")
            root = self.capture("sms_outbound_preview")
            self.record_fixture_stage("fixture_sms_outbound_preview", "sms_outbound_preview", root,
                                      ("SYNTHETIC UI fixture queued preview",),
                                      "Synthetic cached queued preview displayed; no SMS was sent")

            root = self.fixture_ensure_visible(root, resource_id="sms_body", name="sms_compose_body")
            root = self.capture("sms_compose")
            recipient = self.find_node(root, "sms_recipient")
            body = self.find_node(root, "sms_body")
            compose_visible = bool(recipient is not None and body is not None and
                                   self.node_is_on_screen(recipient) and self.node_is_on_screen(body))
            self.record_fixture_stage("fixture_sms_compose", "sms_compose", root,
                                      ("发送线路：SYNTHETIC SIM A",),
                                      "Compose fields are visible; send was not tapped")
            self.record("fixture_sms_compose_fields", "pass" if compose_visible else "fail",
                        "recipient and body fields are both visible" if compose_visible else
                        "recipient and body fields were not both visible in the captured viewport")

            root = self.fixture_ensure_visible(root, text="后台接收", name="background_receive_settings")
            root = self.capture("background_receive_settings")
            self.record_fixture_stage("fixture_settings", "background_receive_settings", root,
                                      ("后台接收", "启用后台接收", "通知权限", "电池设置"),
                                      "Dashboard background-receive settings; no service or permission was enabled")

            unavailable = "无法读取服务器 SIP 可用状态。"
            deadline = time.monotonic() + self.smoke_module.UI_WAIT_SECONDS
            call_root: ET.Element | None = None
            while time.monotonic() < deadline:
                call_root = self.capture(f"fixture_call_wait_{int(time.time())}")
                if self.find_text_node(call_root, unavailable) is not None:
                    break
                self.wait(2)
            if call_root is None:
                raise RuntimeError("could not capture call panel while waiting for offline state")
            call_root = self.fixture_ensure_visible(call_root, text=unavailable, name="call_panel_unavailable")
            call_root = self.capture("call_panel_unavailable")
            dial = next((node for node in self.nodes(call_root)
                         if "拨出此远程 SIM" in self.node_value(node)), None)
            call_disabled = dial is not None and dial.attrib.get("enabled") == "false"
            self.record_fixture_stage("fixture_call_unavailable", "call_panel_unavailable", call_root,
                                      (unavailable, "远程网关未在线。"),
                                      "SIP service unavailable offline; gateway offline; no call was made")
            self.record("fixture_call_controls_disabled", "pass" if call_disabled else "fail",
                        "remote dial control is disabled" if call_disabled else
                        "remote dial control was not confirmed disabled")
        finally:
            if seed_attempted:
                try:
                    self.shell("am", "force-stop", self.package, label="fixture_force_stop_before_cleanup")
                except Exception as exc:
                    self.record("fixture_force_stop", "fail", str(exc))
                try:
                    self.run_fixture_instrumentation("cleanup")
                    self.record("synthetic_fixture_cleanup", "pass",
                                "synthetic encrypted session and account-scoped fixture database were removed")
                except Exception as exc:
                    self.record("synthetic_fixture_cleanup", "fail", str(exc))
            else:
                self.record("synthetic_fixture_cleanup", "blocked", "fixture seed was not attempted")

            if self.fixture_instrumentation_installed:
                try:
                    result = self.command(["uninstall", "com.callagent.host.paireduismoke"],
                                          label="uninstall_fixture_harness")
                    okay = result.returncode == 0 and "Success" in str(result.stdout)
                    self.record("fixture_harness_uninstall", "pass" if okay else "fail",
                                "CI-only fixture APK uninstalled" if okay else "fixture APK uninstall failed")
                except Exception as exc:
                    self.record("fixture_harness_uninstall", "fail", str(exc))
            else:
                self.record("fixture_harness_uninstall", "blocked", "fixture APK was not installed")
            try:
                self.restore_fixture_network()
            except Exception as exc:
                self.record("network_restored", "fail", str(exc))

    def finish(self, fatal: str | None = None) -> int:
        if fatal:
            self.record("fixture_runner", "fail", fatal)
        reached = {item["name"] for item in self.results}
        for name in self.EXPECTED_CHECKS:
            if name not in reached:
                self.record(name, "blocked", "check was not reached before fixture execution stopped")
        try:
            logcat = self.command(["logcat", "-d", "-t", "10000"], timeout=self.smoke_module.ADB_TIMEOUT_SECONDS,
                                  label="fixture_final_logcat")
            (self.artifacts / "logcat.txt").write_text(str(logcat.stdout), encoding="utf-8")
        except Exception as exc:
            (self.artifacts / "logcat_error.txt").write_text(str(exc), encoding="utf-8")

        fail = any(item["status"] == "fail" for item in self.results)
        blocked = any(item["status"] in {"blocked", "partial"} for item in self.results)
        overall = "fail" if fail else ("partial" if blocked else "pass")
        generated = dt.datetime.now(dt.timezone.utc).isoformat()
        scope = "synthetic cached offline paired-dashboard UI only; no real pairing, SMS, SIP, or call"
        checks = {item["name"]: {"status": item["status"], "detail": item["detail"]} for item in self.results}
        results = {
            "schema_version": 1,
            "status": overall,
            "scenario": "synthetic_cached_offline_paired_ui",
            "scope": scope,
            "synthetic": True,
            "secrets_excluded": True,
            "device_profile": self.args.device_profile,
            "device_kind": "Android Emulator (ro.kernel.qemu=1)",
            "package": self.package,
            "serial": self.serial,
            "generated_at_utc": generated,
            "checks": checks,
            "results": self.results,
            "artifacts": {
                "fixture_manifest": "fixture_manifest.json",
                "dashboard": "paired_dashboard_top.png",
                "paired_hosts": "paired_hosts_cached.png",
                "paired_hosts_folded": "paired_hosts_folded.png",
                "paired_hosts_unfolded": "paired_hosts_unfolded.png",
                "sms_inbox": "sms_inbox.png",
                "sms_outbound_preview": "sms_outbound_preview.png",
                "sms_compose": "sms_compose.png",
                "settings": "background_receive_settings.png",
                "call_unavailable": "call_panel_unavailable.png",
                "logcat": "logcat.txt",
            },
        }
        manifest = {
            "schema_version": 1,
            "fixture_type": "synthetic_cached_offline_paired_ui",
            "synthetic": True,
            "offline_cached_ui_only": True,
            "secrets_excluded": True,
            "scope": scope,
            "device_kind": "Android Emulator (ro.kernel.qemu=1)",
            "network": self.fixture_network,
            "safety": {
                "server_pairing_performed": False,
                "real_sms_sent": False,
                "real_sms_received": False,
                "sip_registration_attempted": False,
                "call_started": False,
                "synthetic_cache_removed_after_capture": checks.get("synthetic_fixture_cleanup", {}).get("status") == "pass",
                "fixture_apk_uninstalled": checks.get("fixture_harness_uninstall", {}).get("status") == "pass",
            },
            "overall": overall,
            "generated_at_utc": generated,
            "stages": self.fixture_stages,
            "checks": checks,
        }
        (self.artifacts / "results.json").write_text(json.dumps(results, indent=2, ensure_ascii=False) + "\n",
                                                       encoding="utf-8")
        (self.artifacts / "fixture_manifest.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n",
                                                               encoding="utf-8")
        summary = [
            f"# Paired UI fixture: {overall.upper()}", "", f"- Scope: {scope}",
            "- Screenshots use synthetic cached data; they are not evidence of server pairing or real messaging.",
            "- The fixture disables airplane-network radios during capture, removes its cache, and uninstalls its CI-only APK.",
            "", "| Check | Result | Evidence |", "|---|---|---|",
        ]
        summary.extend(f"| {item['name']} | {item['status']} | {item['detail'].replace('|', '/')} |"
                       for item in self.results)
        summary_text = "\n".join(summary) + "\n"
        (self.artifacts / "summary.md").write_text(summary_text, encoding="utf-8")
        step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
        if step_summary:
            with open(step_summary, "a", encoding="utf-8") as output:
                output.write(summary_text)
        print(f"Paired UI fixture status: {overall}\nResults: {self.artifacts / 'results.json'}\n"
              f"Manifest: {self.artifacts / 'fixture_manifest.json'}", flush=True)
        return 1 if overall in {"fail", "partial"} else 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, help="host debug APK")
    parser.add_argument("--package", default="com.callagent.host")
    parser.add_argument("--device-profile", default="unspecified")
    parser.add_argument("--artifact-dir", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--fixture-apk", required=True, help="signed instrumentation fixture APK")
    parser.add_argument("--fixture-component", required=True, help="instrumentation component name")
    return parser.parse_args()


def main() -> int:
    fixture = PairedUiFixture(parse_args())
    fatal: str | None = None
    try:
        fixture.run_fixture()
    except Exception as exc:
        fatal = f"{type(exc).__name__}: {exc}"
        print(f"Paired UI fixture stopped: {fatal}", file=sys.stderr, flush=True)
    return fixture.finish(fatal)


if __name__ == "__main__":
    raise SystemExit(main())
