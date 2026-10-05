#!/usr/bin/env python3
"""Capture paired host navigation using a disposable synthetic offline fixture.

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
        "app_visible", "fixture_three_tabs", "fixture_call_history", "fixture_history_prefill",
        "fixture_dialpad", "fixture_dialpad_input", "fixture_dialer_contacts",
        "fixture_sms_thread_list", "fixture_sms_sim_badges",
        "fixture_initial_unfold", "fixture_fold_state_changed", "fixture_sms_folded",
        "fixture_unfold_state_restored", "fixture_sms_unfolded", "fixture_sms_conversation",
        "fixture_sms_reply_fields", "fixture_sms_new_message", "fixture_sms_new_message_fields",
        "fixture_new_message_sim_selection", "fixture_sms_draft", "fixture_sms_rotation_ime",
        "fixture_sms_draft_folded", "fixture_sms_draft_unfolded", "fixture_settings",
        "fixture_settings_devices", "fixture_settings_backup", "fixture_active_call_ui",
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
        def without_bottom_navigation(bounds: tuple[int, int, int, int] | None) -> tuple[int, int, int, int] | None:
            if bounds is None:
                return None
            navigation = self.find_node(root, "main_bottom_navigation")
            navigation_bounds = (self.parse_bounds(navigation.attrib.get("bounds", ""))
                                 if navigation is not None else None)
            if navigation_bounds is None:
                return bounds
            left, top, right, bottom = bounds
            return left, top, right, min(bottom, navigation_bounds[1])

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
                    return without_bottom_navigation(bounds)
            current = parents.get(current)

        preferred = ("horizontal_fold_bottom_scroll" if
                     selector in {"sms_search", "sms_recipient", "sms_body"} or
                     selector.startswith("SYNTHETIC UI fixture") else
                     "horizontal_fold_top_scroll")
        scroll = self.find_node(root, preferred)
        if scroll is None:
            scroll = self.find_node(root, "main_content_scroll")
        bounds = self.parse_bounds(scroll.attrib.get("bounds", "")) if scroll is not None else None
        return without_bottom_navigation(bounds)

    def fixture_ensure_visible(self, root: ET.Element, *, resource_id: str | None = None,
                               text: str | None = None, name: str) -> ET.Element:
        selector = resource_id or text or name
        # A target above the current viewport may be absent from UIAutomator's
        # tree entirely. Return this pane to its top first, then search forward;
        # otherwise a missing earlier section (for example settings after SMS
        # compose) would only be scrolled farther out of reach.
        node = self.find_node(root, resource_id) if resource_id else self.find_text_node(root, text or "")
        target_bounds = self.parse_bounds(node.attrib.get("bounds", "")) if node is not None else None
        current_area = self.fixture_scroll_area(root, node, selector) if node is not None else None
        target_is_above_viewport = (target_bounds is not None and current_area is not None and
                                    target_bounds[3] <= current_area[1])
        if node is None or target_is_above_viewport:
            for reset_attempt in range(8):
                area = self.fixture_scroll_area(root, None, selector)
                width, height = self.last_image_size or (900, 1800)
                if area is None:
                    area = (0, 0, width, height)
                left, top, right, bottom = area
                area_height = max(1, bottom - top)
                x = max(left + 8, min(right - 8, (left + right) // 2))
                start_y = top + area_height // 3
                end_y = bottom - min(100, area_height // 5)
                self.shell("input", "swipe", str(x), str(max(top + 8, start_y)),
                           str(x), str(max(top + 8, end_y)), "350", check=True,
                           label=f"fixture_scroll_to_top_{SMOKE_MODULE.safe_name(name)}_{reset_attempt}")
                self.wait(1)
                root = self.capture(f"fixture_scroll_to_top_{SMOKE_MODULE.safe_name(name)}_{reset_attempt}")
                node = self.find_node(root, resource_id) if resource_id else self.find_text_node(root, text or "")
                if node is not None and self._node_in_main_content_viewport(root, node):
                    return root

        for attempt in range(7):
            node = self.find_node(root, resource_id) if resource_id else self.find_text_node(root, text or "")
            if node is not None and self._node_in_main_content_viewport(root, node):
                return root
            area = self.fixture_scroll_area(root, node, selector)
            width, height = self.last_image_size or (900, 1800)
            if area is None:
                area = (0, 0, width, height)
            left, top, right, bottom = area
            area_height = max(1, bottom - top)
            bounds = self.parse_bounds(node.attrib.get("bounds", "")) if node is not None else None
            if bounds is not None and (bounds[1] >= bottom or bounds[3] > bottom):
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
        if not self._node_in_main_content_viewport(root, node):
            raise RuntimeError(f"Fixture UI element did not enter the unobscured content viewport: {selector}")
        return root

    def record_fixture_stage(self, check: str, name: str, root: ET.Element,
                             required_visible_text: tuple[str, ...], detail: str,
                             status_override: str | None = None,
                             any_visible_text: tuple[str, ...] = (),
                             require_positive_visible_bounds: bool = False) -> None:
        def item_is_visible(item: str) -> bool:
            node = self.find_text_node(root, item)
            if node is None:
                return False
            return (self.node_has_positive_visible_bounds(node) if require_positive_visible_bounds
                    else self.node_is_on_screen(node))

        visible = [item for item in required_visible_text if item_is_visible(item)]
        matched_alternative = next((item for item in any_visible_text if item_is_visible(item)), None)
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

    def require_node(self, root: ET.Element, resource_id: str, name: str) -> ET.Element:
        node = self.find_node(root, resource_id)
        if node is None or not self.node_is_on_screen(node):
            raise RuntimeError(f"{name} is missing or outside the viewport: {resource_id}")
        return node

    def tap_tab(self, root: ET.Element, resource_id: str, label: str, name: str) -> ET.Element:
        tab = self.require_node(root, resource_id, f"{label} tab")
        text = self.find_text_node(root, label)
        if text is None or not self.node_is_on_screen(text):
            raise RuntimeError(f"Bottom navigation label is missing: {label}")
        self.tap_node(tab, name)
        self.wait(1)
        return self.capture(name)

    def visible_sim_badges(self, root: ET.Element) -> set[str]:
        return {
            node.attrib.get("text", "").strip()
            for node in self.nodes(root)
            if node.attrib.get("resource-id", "").endswith("/sms_thread_sim_badge")
            and node.attrib.get("text", "").strip() in {"SIM 1", "SIM 2"}
            and self.node_is_on_screen(node)
        }

    def verify_sms_thread_list(self, root: ET.Element, name: str, check: str,
                               record_badges: bool = False) -> ET.Element:
        root = self.capture(name)
        thread_list = self.find_node(root, "sms_thread_list")
        thread_rows = [node for node in self.nodes(root)
                       if node.attrib.get("resource-id", "").endswith("/sms_thread_item")
                       and self.node_is_on_screen(node)]
        inbound_a = self.find_text_containing(root, "SYNTHETIC UI fixture inbox preview")
        inbound_b = self.find_text_containing(root, "SYNTHETIC SIM B fixture preview")
        badges = self.visible_sim_badges(root)
        list_ok = (thread_list is not None and self.node_is_on_screen(thread_list) and len(thread_rows) >= 3 and
                   inbound_a is not None and self.node_is_on_screen(inbound_a) and
                   inbound_b is not None and self.node_is_on_screen(inbound_b))
        self.record_fixture_stage(check, name, root,
                                  ("SYNTHETIC UI fixture inbox preview", "SYNTHETIC SIM B fixture preview"),
                                  f"Three cached synthetic SMS threads across both SIMs; visible rows={len(thread_rows)}; no SMS was received or sent",
                                  status_override=None if list_ok else "fail")
        if not list_ok:
            raise RuntimeError("SMS thread list did not show all three synthetic SIM conversations")
        if record_badges:
            self.record("fixture_sms_sim_badges", "pass" if badges == {"SIM 1", "SIM 2"} else "fail",
                        f"visible circular SIM badges={sorted(badges)}; expected one thread on each synthetic SIM")
        if record_badges and badges != {"SIM 1", "SIM 2"}:
            raise RuntimeError(f"SMS threads did not expose both SIM badges: {sorted(badges)}")
        return root

    def exercise_host_navigation(self) -> None:
        root = self.capture("paired_three_tabs")
        navigation = self.find_node(root, "main_bottom_navigation")
        tab_specs = (("tab_phone", "电话"), ("tab_messages", "短信"), ("tab_settings", "设置"))
        tab_results = []
        for resource_id, label in tab_specs:
            tab = self.find_node(root, resource_id)
            label_node = self.find_text_node(root, label)
            tab_results.append(
                tab is not None and self.node_is_on_screen(tab) and
                label_node is not None and self.node_is_on_screen(label_node)
            )
        tabs_ok = navigation is not None and self.node_is_on_screen(navigation) and all(tab_results)
        self.record_fixture_stage("fixture_three_tabs", "paired_three_tabs", root,
                                  ("电话", "短信", "设置"),
                                  f"Three primary navigation destinations are visible; tab ids={[spec[0] for spec in tab_specs]}",
                                  status_override=None if tabs_ok else "fail")
        if not tabs_ok:
            raise RuntimeError("Paired host screen does not show all three bottom tabs")

        root = self.tap_tab(root, "tab_phone", "电话", "paired_call_history")
        root = self.wait_for_app_tree(
            lambda tree: self.find_node(tree, "call_history_search") is not None and
            self.find_node(tree, "call_history_list") is not None and
            self.find_node(tree, "dialer_open") is not None,
            "paired_call_history"
        )
        root = self.capture("paired_call_history")
        history_list = self.find_node(root, "call_history_list")
        history_search = self.find_node(root, "call_history_search")
        incoming_number = self.find_text_node(root, "+15550102001")
        outgoing_number = self.find_text_node(root, "+15550102002")
        history_ok = all(node is not None and self.node_is_on_screen(node)
                         for node in (history_list, history_search, incoming_number, outgoing_number))
        self.record_fixture_stage("fixture_call_history", "paired_call_history", root,
                                  ("+15550102001", "+15550102002"),
                                  "Two local synthetic call-history rows from SIM 1 and SIM 2 are visible; no call was started",
                                  status_override=None if history_ok else "fail")
        if not history_ok:
            raise RuntimeError("Synthetic call-history list or its two SIM rows are missing")

        first_history_item = self.find_node(root, "call_history_item")
        if first_history_item is None or not self.node_is_on_screen(first_history_item):
            raise RuntimeError("Synthetic call-history item is not tappable")
        self.tap_node(first_history_item, "open_synthetic_history_call")
        root = self.wait_for_app_tree(
            lambda tree: self.find_node(tree, "dialer_destination") is not None and
            self.find_node(tree, "dialer_keypad") is not None,
            "dialer_from_history"
        )
        root = self.capture("paired_dialpad_from_history")
        prefilled_number = self.field_text(root, "dialer_destination")
        history_prefill_ok = prefilled_number == "+15550102001"
        self.record("fixture_history_prefill", "pass" if history_prefill_ok else "fail",
                    f"history row opens separate dialpad with destination={prefilled_number!r}; call button was not pressed")
        if not history_prefill_ok:
            raise RuntimeError("Tapping synthetic call history did not prefill its remote number")

        self.shell("input", "keyevent", "KEYCODE_BACK", check=True, label="return_to_phone_history")
        root = self.wait_for_app_tree(lambda tree: self.find_node(tree, "dialer_open") is not None,
                                      "phone_history_after_prefill")

        dialer_open = self.require_node(root, "dialer_open", "Dialpad entry")
        self.tap_node(dialer_open, "open_paired_dialpad")
        root = self.wait_for_app_tree(
            lambda tree: self.find_node(tree, "dialer_destination") is not None and
            self.find_node(tree, "dialer_keypad") is not None and
            self.find_node(tree, "dialer_contact_list") is not None,
            "paired_dialpad"
        )
        root = self.capture("paired_dialpad")
        keypad = self.find_node(root, "dialer_keypad")
        destination = self.find_node(root, "dialer_destination")
        keys = [f"keypad_{digit}" for digit in "123456789"] + ["keypad_star", "keypad_0", "keypad_hash"]
        missing_keys = [key for key in keys
                        if (node := self.find_node(root, key)) is None or not self.node_is_on_screen(node)]
        dialpad_ok = (self.find_text_node(root, "拨号") is not None and
                      keypad is not None and self.node_is_on_screen(keypad) and
                      destination is not None and self.node_is_on_screen(destination) and not missing_keys)
        self.record_fixture_stage("fixture_dialpad", "paired_dialpad", root, ("拨号",),
                                  f"Separate dialpad shows destination and all 12 keys; missing={missing_keys}; no call was made",
                                  status_override=None if dialpad_ok else "fail")
        if not dialpad_ok:
            raise RuntimeError("Paired dialpad destination or one of its 12 keys is missing")

        contacts = self.find_node(root, "dialer_contact_list")
        contact_hint = self.find_text_containing(root, "允许访问联系人以查找姓名和号码")
        contacts_ok = (contacts is not None and self.node_is_on_screen(contacts) and
                       contact_hint is not None and self.node_is_on_screen(contact_hint))
        self.record("fixture_dialer_contacts", "pass" if contacts_ok else "fail",
                    "contact candidate container is visible; no contacts permission was granted" if contacts_ok
                    else "contact permission hint or candidate container is missing")
        if not contacts_ok:
            raise RuntimeError("Dialer contact candidates are not represented on screen")

        destination_value = self.field_text(root, "dialer_destination") or ""
        if destination_value:
            destination = self.require_node(root, "dialer_destination", "Dialer destination field")
            self.tap_node(destination, "clear_prefilled_dialer_destination")
            self.shell("input", "keyevent", "KEYCODE_MOVE_END", check=True,
                       label="move_to_end_of_history_number")
            for index in range(len(destination_value)):
                self.shell("input", "keyevent", "KEYCODE_DEL", check=True,
                           label=f"delete_history_number_digit_{index}")
            self.shell("input", "keyevent", "KEYCODE_BACK", label="hide_dialer_ime_before_keypad")
            root = self.capture("paired_dialpad_cleared")
            if self.field_text(root, "dialer_destination") != "":
                raise RuntimeError("Could not clear the synthetic history number before direct keypad test")

        for resource_id in ("keypad_1", "keypad_2", "keypad_3"):
            self.tap_node(self.require_node(root, resource_id, "dialpad key"), f"tap_{resource_id}")
        root = self.capture("paired_dialpad_synthetic_digits")
        digits = self.field_text(root, "dialer_destination")
        digits_ok = digits == "123"
        self.record("fixture_dialpad_input", "pass" if digits_ok else "fail",
                    f"three synthetic keypad digits read back as {digits!r}; call button was not pressed")
        if not digits_ok:
            raise RuntimeError("Dialpad did not append synthetic digits to its destination field")
        self.shell("input", "keyevent", "KEYCODE_BACK", check=True, label="close_paired_dialpad")
        root = self.wait_for_app_tree(lambda tree: self.find_node(tree, "tab_messages") is not None,
                                      "paired_home_after_dialpad")

        root = self.tap_tab(root, "tab_messages", "短信", "paired_sms_threads")
        root = self.wait_for_app_tree(lambda tree: self.find_node(tree, "sms_thread_list") is not None,
                                      "paired_sms_threads")
        root = self.verify_sms_thread_list(root, "paired_sms_threads", "fixture_sms_thread_list",
                                           record_badges=True)

        initial_unfold = self.command(["emu", "unfold"], timeout=self.smoke_module.ADB_TIMEOUT_SECONDS,
                                      label="fixture_prepare_unfolded_state")
        self.wait(6)
        before_fold = self.device_snapshot("fixture_fold_before")
        self.record("fixture_initial_unfold", "pass" if initial_unfold.returncode == 0 else "blocked",
                    f"emu unfold exit={initial_unfold.returncode}; state={before_fold}")
        fold = self.command(["emu", "fold"], timeout=self.smoke_module.ADB_TIMEOUT_SECONDS,
                            label="fixture_emulator_fold")
        self.wait(6)
        folded = self.device_snapshot("fixture_folded")
        fold_changed = bool((before_fold["size"] and folded["size"] and before_fold["size"] != folded["size"]) or
                            (before_fold["state"] and folded["state"] and before_fold["state"] != folded["state"]))
        folded_root = self.verify_sms_thread_list(root, "paired_sms_threads_folded", "fixture_sms_folded")
        self.record("fixture_fold_state_changed", "pass" if fold.returncode == 0 and fold_changed else "blocked",
                    f"fold exit={fold.returncode}; before={before_fold}; folded={folded}")
        unfold = self.command(["emu", "unfold"], timeout=self.smoke_module.ADB_TIMEOUT_SECONDS,
                              label="fixture_emulator_unfold")
        self.wait(6)
        unfolded = self.device_snapshot("fixture_unfolded")
        unfold_restored = bool((before_fold["size"] and unfolded["size"] == before_fold["size"]) or
                               (before_fold["state"] and unfolded["state"] == before_fold["state"]))
        root = self.verify_sms_thread_list(folded_root, "paired_sms_threads_unfolded", "fixture_sms_unfolded")
        self.record("fixture_unfold_state_restored",
                    "pass" if initial_unfold.returncode == 0 and unfold.returncode == 0 and unfold_restored else "blocked",
                    f"unfold exit={unfold.returncode}; before={before_fold}; unfolded={unfolded}")

        inbound = self.find_text_containing(root, "SYNTHETIC UI fixture inbox preview")
        if inbound is None or not self.node_is_on_screen(inbound):
            raise RuntimeError("Synthetic SIM 1 thread row is not visible to open")
        self.tap_node(inbound, "open_synthetic_sms_thread")
        root = self.wait_for_app_tree(
            lambda tree: self.find_node(tree, "sms_thread_header") is not None and
            self.find_node(tree, "sms_conversation_list") is not None,
            "paired_sms_conversation"
        )
        root = self.capture("paired_sms_conversation")
        thread_header = self.find_node(root, "sms_thread_header")
        conversation = self.find_node(root, "sms_conversation_list")
        selector = self.find_node(root, "sms_thread_sim_selector")
        message = self.find_text_containing(root, "SYNTHETIC UI fixture inbox preview")
        send = self.find_node(root, "sms_thread_send")
        conversation_ok = all(node is not None and self.node_is_on_screen(node)
                              for node in (thread_header, conversation, selector, message, send))
        self.record_fixture_stage("fixture_sms_conversation", "paired_sms_conversation", root,
                                  ("SYNTHETIC UI fixture inbox preview",),
                                  f"Separate SMS conversation/editor exposes its header, SIM selector, body and send control={conversation_ok}; send was not tapped",
                                  status_override=None if conversation_ok else "fail")
        if not conversation_ok:
            raise RuntimeError("SMS thread did not open its separate conversation/editor view")

        reply_recipient = self.node_value(thread_header).strip() if thread_header is not None else ""
        editable_recipient = self.find_node(root, "sms_recipient")
        reply_body = self.find_node(root, "sms_body")
        reply_fields_ok = (reply_recipient == "+15550102001" and editable_recipient is None and
                           reply_body is not None and self.node_is_on_screen(reply_body) and
                           selector is not None and self.node_is_on_screen(selector) and
                           send is not None and self.node_is_on_screen(send))
        self.record("fixture_sms_reply_fields", "pass" if reply_fields_ok else "fail",
                    f"existing thread shows read-only recipient heading={reply_recipient!r}, body, SIM selector, and send control; send was not tapped"
                    if reply_fields_ok else
                    f"read-only heading/body/SIM/send check failed: heading={reply_recipient!r}, editable_recipient={editable_recipient is not None}")
        if not reply_fields_ok:
            raise RuntimeError("Existing SMS conversation does not expose its read-only recipient and reply composer")

        self.shell("input", "keyevent", "KEYCODE_BACK", check=True, label="return_to_sms_thread_list")
        root = self.wait_for_app_tree(lambda tree: self.find_node(tree, "sms_thread_list") is not None,
                                      "sms_thread_list_after_conversation")
        compose = self.require_node(root, "sms_compose_fab", "New message entry")
        self.tap_node(compose, "open_new_message")
        root = self.wait_for_app_tree(
            lambda tree: all(self.find_node(tree, resource_id) is not None
                             for resource_id in ("sms_recipient", "sms_body", "sms_thread_sim_selector")),
            "paired_new_message"
        )
        root = self.capture("paired_new_message")
        recipient = self.find_node(root, "sms_recipient")
        body = self.find_node(root, "sms_body")
        selector = self.find_node(root, "sms_thread_sim_selector")
        fields_ok = all(node is not None and self.node_is_on_screen(node) for node in (recipient, body, selector))
        self.record_fixture_stage("fixture_sms_new_message", "paired_new_message", root,
                                  (),
                                  f"New message opens a dedicated editor; fields visible={fields_ok}; no send was activated",
                                  status_override=None if fields_ok else "fail")
        self.record("fixture_sms_new_message_fields", "pass" if fields_ok else "fail",
                    "recipient, message body, and SIM selector are visible" if fields_ok
                    else "new-message fields are incomplete")
        if not fields_ok:
            raise RuntimeError("New message editor is missing recipient, body, or SIM selector")

        self.tap_node(selector, "open_new_message_sim_picker")
        root = self.wait_for_app_tree(
            lambda tree: self.find_text_containing(tree, "SIM 1") is not None and
            self.find_text_containing(tree, "SIM 2") is not None,
            "new_message_sim_picker"
        )
        sim_b_option = self.find_text_containing(root, "SIM 2 · SYNTHETIC SIM B")
        if sim_b_option is None:
            sim_b_option = self.find_text_containing(root, "SIM 2")
        picker_options = (self.find_text_containing(root, "SIM 1") is not None and sim_b_option is not None)
        if picker_options:
            self.tap_node(sim_b_option, "select_synthetic_sim_b")
            root = self.wait_for_app_tree(
                lambda tree: self.find_node(tree, "sms_thread_sim_selector") is not None and
                "SIM 2" in self.node_value(self.find_node(tree, "sms_thread_sim_selector")),
                "new_message_sim_b_selected"
            )
        root = self.capture("paired_new_message_sim_b")
        selector = self.find_node(root, "sms_thread_sim_selector")
        selected_b = picker_options and selector is not None and "SIM 2" in self.node_value(selector)
        self.record_fixture_stage("fixture_new_message_sim_selection", "paired_new_message_sim_b", root,
                                  ("SIM 2",),
                                  "Synthetic SIM B was selected in the new-message editor; nothing was sent",
                                  status_override=None if selected_b else "fail")
        if not selected_b:
            raise RuntimeError("New-message SIM picker did not select synthetic SIM B")

        self.input_text(root, "sms_recipient", "+15550102003")
        root = self.capture("paired_new_message_recipient")
        self.input_text(root, "sms_body", "synthetic_draft_only")
        root = self.capture("paired_new_message_draft")
        recipient_value = self.field_text(root, "sms_recipient")
        body_value = self.field_text(root, "sms_body")
        draft_ok = recipient_value == "+15550102003" and body_value == "synthetic_draft_only"
        self.record("fixture_sms_draft", "pass" if draft_ok else "fail",
                    f"recipient/body draft preserved={draft_ok}; no SMS POST or send action was invoked")

        portrait_before = self.last_image_size
        self.shell("settings", "put", "system", "accelerometer_rotation", "0", check=True,
                   label="fixture_disable_auto_rotation")
        rotation_details = ""
        try:
            self.shell("settings", "put", "system", "user_rotation", "1", check=True,
                       label="fixture_rotate_new_message_landscape")
            self.wait(5)
            landscape_root = self.capture("paired_new_message_landscape")
            landscape_size = self.last_image_size
            landscape_ime, ime_detail = self._ime_visible()
            landscape_recipient = self.field_text(landscape_root, "sms_recipient")
            landscape_body = self.field_text(landscape_root, "sms_body")
            self.shell("settings", "put", "system", "user_rotation", "0", check=True,
                       label="fixture_rotate_new_message_portrait")
            self.wait(5)
            portrait_root = self.capture("paired_new_message_portrait")
            portrait_after = self.last_image_size
            restored_recipient = self.field_text(portrait_root, "sms_recipient")
            restored_body = self.field_text(portrait_root, "sms_body")
            actual_rotation = bool(portrait_before and landscape_size and portrait_after and
                                   portrait_before[0] < portrait_before[1] and
                                   landscape_size[0] > landscape_size[1] and
                                   portrait_after[0] < portrait_after[1])
            rotation_ok = (draft_ok and actual_rotation and landscape_ime and
                           landscape_recipient == "+15550102003" and landscape_body == "synthetic_draft_only" and
                           restored_recipient == "+15550102003" and restored_body == "synthetic_draft_only")
            rotation_details = (f"IME landscape={landscape_ime} ({ime_detail}); size={portrait_before}/{landscape_size}/{portrait_after}; "
                                f"landscape values={landscape_recipient!r}/{landscape_body!r}; "
                                f"portrait values={restored_recipient!r}/{restored_body!r}")
        finally:
            self.shell("settings", "put", "system", "user_rotation", "0",
                       label="fixture_restore_portrait")
            self.shell("settings", "put", "system", "accelerometer_rotation", "1",
                       label="fixture_restore_auto_rotation")
        self.record("fixture_sms_rotation_ime", "pass" if rotation_ok else "fail",
                    f"synthetic draft and open keyboard survive orientation changes; {rotation_details}")
        self.record_fixture_stage("fixture_sms_rotation_ime", "paired_new_message_landscape",
                                  landscape_root, (),
                                  f"Synthetic draft survived rotation with IME visible={landscape_ime}; {rotation_details}",
                                  status_override=None if rotation_ok else "fail")

        fold_before_draft = self.device_snapshot("fixture_draft_fold_before")
        fold = self.command(["emu", "fold"], timeout=self.smoke_module.ADB_TIMEOUT_SECONDS,
                            label="fixture_fold_with_sms_draft")
        self.wait(6)
        folded_draft_root = self.capture("paired_new_message_folded")
        folded_recipient = self.field_text(folded_draft_root, "sms_recipient")
        folded_body = self.field_text(folded_draft_root, "sms_body")
        folded_ime, folded_ime_detail = self._ime_visible()
        folded_state = self.device_snapshot("fixture_draft_folded")
        folded_changed = bool((fold_before_draft["size"] and folded_state["size"] and
                               fold_before_draft["size"] != folded_state["size"]) or
                              (fold_before_draft["state"] and folded_state["state"] and
                               fold_before_draft["state"] != folded_state["state"]))
        draft_fold_ok = (fold.returncode == 0 and folded_changed and folded_ime and
                         folded_recipient == "+15550102003" and folded_body == "synthetic_draft_only")
        self.record("fixture_sms_draft_folded", "pass" if draft_fold_ok else "fail",
                    f"fold exit={fold.returncode}; device transition={fold_before_draft}/{folded_state}; "
                    f"draft={folded_recipient!r}/{folded_body!r}; IME={folded_ime} ({folded_ime_detail})")
        self.record_fixture_stage("fixture_sms_draft_folded", "paired_new_message_folded",
                                  folded_draft_root, (),
                                  "New-message recipient and body survived emulator fold; no message was sent",
                                  status_override=None if draft_fold_ok else "fail")

        unfold = self.command(["emu", "unfold"], timeout=self.smoke_module.ADB_TIMEOUT_SECONDS,
                              label="fixture_unfold_with_sms_draft")
        self.wait(6)
        unfolded_draft_root = self.capture("paired_new_message_unfolded")
        unfolded_recipient = self.field_text(unfolded_draft_root, "sms_recipient")
        unfolded_body = self.field_text(unfolded_draft_root, "sms_body")
        unfolded_ime, unfolded_ime_detail = self._ime_visible()
        unfolded_state = self.device_snapshot("fixture_draft_unfolded")
        draft_unfold_ok = (unfold.returncode == 0 and unfolded_ime and
                           unfolded_recipient == "+15550102003" and unfolded_body == "synthetic_draft_only")
        self.record("fixture_sms_draft_unfolded", "pass" if draft_unfold_ok else "fail",
                    f"unfold exit={unfold.returncode}; draft={unfolded_recipient!r}/{unfolded_body!r}; "
                    f"IME={unfolded_ime} ({unfolded_ime_detail}); device={unfolded_state}")
        self.record_fixture_stage("fixture_sms_draft_unfolded", "paired_new_message_unfolded",
                                  unfolded_draft_root, (),
                                  "New-message draft survived emulator fold and unfold; no message was sent",
                                  status_override=None if draft_unfold_ok else "fail")

        visible_ime, _ = self._ime_visible()
        if visible_ime:
            self.shell("input", "keyevent", "KEYCODE_BACK", label="fixture_hide_sms_ime")
            self.wait(1)
        self.shell("input", "keyevent", "KEYCODE_BACK", check=True, label="fixture_exit_new_message")
        root = self.wait_for_app_tree(lambda tree: self.find_node(tree, "tab_settings") is not None,
                                      "settings_after_new_message")
        root = self.tap_tab(root, "tab_settings", "设置", "paired_settings")
        root = self.wait_for_app_tree(
            lambda tree: self.find_text_node(tree, "设置") is not None and
            self.find_node(tree, "sms_backup_archive_entry") is not None,
            "paired_settings"
        )
        root = self.capture("paired_settings")
        settings_title = self.find_text_node(root, "设置")
        paired_label = self.find_text_containing(root, "已配对此手机")
        settings_ok = settings_title is not None and self.node_is_on_screen(settings_title) and paired_label is not None
        self.record_fixture_stage("fixture_settings", "paired_settings", root,
                                  ("设置",),
                                  "Paired Settings shows this device state; automatic sync has no user toggle",
                                  status_override=None if settings_ok else "fail")
        if not settings_ok:
            raise RuntimeError("Paired settings page does not show the current device state")

        paired_host = self.find_text_containing(root, "SYNTHETIC Tablet Host")
        if paired_host is None or not self.node_is_on_screen(paired_host):
            root = self.fixture_ensure_visible(root, text="SYNTHETIC Tablet Host", name="paired_settings_device_list")
            root = self.capture("paired_settings_devices")
            paired_host = self.find_text_containing(root, "SYNTHETIC Tablet Host")
        devices_ok = paired_host is not None and self.node_is_on_screen(paired_host)
        self.record_fixture_stage("fixture_settings_devices", "paired_settings_devices", root,
                                  (),
                                  "Synthetic account-scoped paired device row is cached; no pairing request was made",
                                  status_override=None if devices_ok else "fail")
        if not devices_ok:
            raise RuntimeError("Settings did not display the synthetic paired device list")

        root = self.fixture_ensure_visible(root, resource_id="sms_backup_archive_entry",
                                           name="paired_settings_backup")
        root = self.capture("paired_settings_backup")
        backup_entry = self.find_node(root, "sms_backup_archive_entry")
        backup_ok = backup_entry is not None and self.node_has_positive_visible_bounds(backup_entry)
        self.record_fixture_stage("fixture_settings_backup", "paired_settings_backup", root,
                                  ("短信备份与归档",),
                                  "SMS archive entry is reachable from Settings",
                                  status_override=None if backup_ok else "fail")
        if not backup_ok:
            raise RuntimeError("Paired Settings did not expose the SMS archive entry")

        self.capture_synthetic_call_screens()

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
        expected_state = {"seed": "seeded", "call-screens": "call_screens_captured",
                          "cleanup": "cleaned"}.get(mode)
        if result.returncode != 0 or not codes or codes[-1] != "-1" or failure or \
                f"INSTRUMENTATION_STATUS: fixture={expected_state}" not in output:
            raise RuntimeError(f"Fixture instrumentation mode {mode} failed; see its command log")

    def capture_synthetic_call_screens(self) -> None:
        screenshots = (
            "call-synthetic-dialing.png", "call-synthetic-active.png",
            "call-synthetic-keypad.png", "call-synthetic-incoming.png",
        )
        try:
            self.run_fixture_instrumentation("call-screens")
            for filename in screenshots:
                result = self.command(["exec-out", "run-as", self.package, "cat",
                                       f"cache/host-ui-call-fixture/{filename}"],
                                      binary=True, label=f"export_{filename}")
                content = result.stdout
                if result.returncode != 0 or not isinstance(content, bytes) or not content.startswith(b"\x89PNG"):
                    raise RuntimeError(f"instrumentation screenshot is missing or not PNG: {filename}")
                (self.artifacts / filename).write_bytes(content)
                dimensions = None
                if len(content) >= 24:
                    dimensions = [int.from_bytes(content[16:20], "big"), int.from_bytes(content[20:24], "big")]
                self.fixture_stages.append({
                    "name": filename.removesuffix(".png"),
                    "status": "pass",
                    "screenshot": filename,
                    "ui_hierarchy": None,
                    "size": dimensions,
                    "evidence": ["synthetic local call UI phase"],
                    "detail": "In-process CallSessionCoordinator render state only; no SIP, Telecom, audio, or carrier call.",
                })
        except Exception as exc:
            detail = f"local-only call screen capture failed: {type(exc).__name__}: {exc}"
            self.record("fixture_active_call_ui", "fail", detail)
            raise RuntimeError(detail) from exc
        self.record("fixture_active_call_ui", "pass",
                    "saved synthetic outgoing, active controls, keypad, and incoming screens without activating call controls")

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
        self.keep_contacts_permission_denied()

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
            root = self.wait_for_app_tree(
                lambda tree: self.find_node(tree, "main_bottom_navigation") is not None,
                "synthetic_paired_host_navigation")
            self.verify_app_foreground()
            self.record("app_visible", "pass", "synthetic paired session opened in the three-tab host UI")
            self.exercise_host_navigation()
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
        scope = ("synthetic cached paired host UI only; airplane mode is enabled and Wi-Fi/mobile data are disabled; "
                 "no real pairing, SMS, SIP registration, Telecom call, audio session, or carrier call is performed")
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
                "call_history": "paired_call_history.png",
                "dialpad": "paired_dialpad.png",
                "sms_threads": "paired_sms_threads.png",
                "sms_conversation": "paired_sms_conversation.png",
                "new_message": "paired_new_message.png",
                "settings": "paired_settings.png",
                "synthetic_call_screens": [
                    "call-synthetic-dialing.png", "call-synthetic-active.png",
                    "call-synthetic-keypad.png", "call-synthetic-incoming.png",
                ] if all((self.artifacts / filename).is_file() for filename in (
                    "call-synthetic-dialing.png", "call-synthetic-active.png",
                    "call-synthetic-keypad.png", "call-synthetic-incoming.png",
                )) else [],
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
                "real_call_started": False,
                "call_ui_state_is_local_synthetic_coordinator_only": True,
                "sip_telecom_audio_carrier_call_started": False,
                "automatic_messaging_service_may_start_offline": True,
                "wifi_and_mobile_data_disabled": self.fixture_network.get("offline_confirmed", False),
                "synthetic_cache_removed_after_capture": checks.get("synthetic_fixture_cleanup", {}).get("status") == "pass",
                "app_services_stopped_after_capture": checks.get("synthetic_fixture_cleanup", {}).get("status") == "pass",
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
            "- The fixture enables airplane mode and disables Wi-Fi and mobile data during capture. The automatic remote-messaging service may start offline. Call-screen images use local synthetic coordinator states only; SIP, Telecom, audio, carrier calling, and SMS sending are not invoked.",
            "- Cleanup stops app-owned services, removes its session/database cache, restores prior radio settings, and uninstalls the CI-only fixture APK.",
            "", "| Check | Result | Evidence |", "|---|---|---|",
        ]
        summary.extend(f"| {item['name']} | {item['status']} | {item['detail'].replace('|', '/')} |"
                       for item in self.results)
        summary.extend(["", "## UI screenshots", ""])
        for stage in self.fixture_stages:
            summary.extend([
                f"### {stage['name']}",
                f"![{stage['name']}]({stage['screenshot']})",
                *([f"[UI hierarchy]({stage['ui_hierarchy']})"] if stage.get("ui_hierarchy") else []), "",
            ])
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
