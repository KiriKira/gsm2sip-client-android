# Host UI navigation and smoke evidence

The host app has three primary destinations: **电话**, **短信**, and **设置**. Dialing and SMS conversations open as separate pages. The layout keeps the same navigation model on compact and foldable windows, with system bars, the display cutout, the keyboard, and the hinge accounted for during resize and rotation.

## Visual style

The screens follow the Google Phone and Google Messages apps (Material 3 Expressive, dynamic color on Android 12+):

- Phone and Messages open with a full-width search pill; the old-phone status sits below it as a tonal card.
- Lists are stacks of rounded tiles: large corners at the ends of a group, small corners and 2 dp gaps between items. Call history is grouped into 今天 / 昨天 / 更早, with a direction arrow (incoming, outgoing, missed in red), SIM and time; the trailing phone button redials on the same SIM when calling is ready, otherwise it opens the dial pad.
- People get a colored circle with the first letter of the contact name, or a person icon for bare numbers.
- The dial pad shows a large centered number above pill-shaped tonal keys, SIM chips, a green call button and a backspace in the "#" column (long-press clears).
- Conversations use chat bubbles (incoming left in a neutral tone, sent right in the primary color) with day separators, and a composer row of SIM button, pill text box and round send button.
- Settings is a list of grouped tiles with icons, including the 自动复制验证码 switch.
- The in-call screen has a large avatar, round 静音 / 键盘 / 音频 toggles, a red hang-up pill, and round 拒接 / 接听 buttons for incoming calls.

Buttons that became icons keep their former label as the accessibility description (返回, 发送, 挂断, 接听, 拒接, 键盘), so the smoke anchors still find them by text.

## Screen map

| Screen | Main content | Smoke anchors |
|---|---|---|
| Remote gateway status (top of Phone and Messages; full card in Settings) | Old-phone online/offline with last-seen time, battery and charging, voice readiness, and per-SIM signal bars, network type, roaming and confirmation problems. Tapping the compact strip opens Settings. A heartbeat older than 120 s is shown as offline and hides stale signal. | `gateway_status_card` |
| Phone | Call-history rows; selecting one opens the dial pad with its number filled. The dial-pad floating button opens an empty dial pad. | `main_bottom_navigation`, `tab_phone`, `call_history_search`, `call_history_list`, `call_history_item`, `dialer_open` |
| Dial pad | Destination, contact candidates, SIM selector, 3×4 keypad, outbound button, and the reason calling is unavailable (not paired, server calling not configured, old phone offline, SIM awaiting confirmation, SIM revision changing) | `dialer_destination`, `dialer_contact_list`, `dialer_sim_selector`, `dialer_keypad`, `keypad_1` … `keypad_hash`, `dialer_call_button`, `dialer_readiness` |
| Messages | SMS threads, latest message, and a SIM badge on each thread | `tab_messages`, `sms_thread_list`, `sms_thread_item`, `sms_thread_sim_badge`, `sms_compose_fab` |
| Conversation/editor | Existing conversation shows the recipient in a read-only heading with reply fields; a new message adds a recipient input. SIM is selected here. | `sms_thread_header`, `sms_conversation_list`, new-message `sms_recipient`, `sms_thread_sim_selector`, `sms_body`, `sms_thread_send` |
| Settings | Server pairing, paired-device state, notification/battery/contacts permissions, and SMS backup/archive entry | `tab_settings`, `pairing_server`, `pairing_code`, `pairing_device_name`, `sms_backup_archive_entry` |

The phone can search the device's contacts for names and numbers. Without Android Contacts permission, the dial pad still accepts direct number entry and displays the permission explanation. The smoke leaves Contacts permission denied and does not read the emulator address book.

After pairing, background SMS synchronization starts automatically; there is no sync or history-backfill switch. Server pairing/device management and the existing **短信备份与归档** entry remain in Settings. Sending a message or placing a call still requires a deliberate user action.

## Automated screenshot route

The `Android KVM UI smoke` workflow builds the debug APK and runs the host smoke on an API 35, 7.6-inch foldable emulator. Pushes that touch the app, fixture, smoke scripts, or workflow start the workflow; `workflow_dispatch` can run it on demand. The fastest supported screenshot route is to push the finished branch, wait for the workflow, and inspect the `gsm2sip-host-android-ui-smoke` artifact.

The workflow attempts both stages and fails if any stage fails. Evidence upload runs even on failure. The stages are:

1. `scripts/android-ui-smoke.py --scenario host` for first-run prompts, the three tabs, phone/dial-pad navigation, Settings, synthetic pairing form entry, rotation, fold/unfold, cutout, and the shared SMS backup/archive UI.
2. `scripts/run-paired-ui-fixture.sh` for offline paired-state screenshots using local synthetic session, device, call-history, SIM, and SMS cache records.

The runners emit `summary.md`, JSON manifests, and original PNG/XML captures. The Markdown summaries embed the main screen images; the full artifacts retain all generated screenshots and diagnostics. No PDF is generated. The screenshot report should be updated from a completed run of this workflow; screenshots from the older dashboard UI are not evidence of the three-tab layout.

The paired fixture enables airplane mode and disables Wi-Fi and mobile data before seeding its synthetic paired session. The app may start its automatic remote-messaging foreground service while paired, but it has no working network and the session points only at `ui-smoke.invalid`; cleanup stops app services, removes the synthetic session and database, restores radio settings, and uninstalls the fixture APK. The fixture seeds three local SMS threads across SIM 1 and SIM 2. Call-screen captures seed only an in-process `CallSessionCoordinator` render state for outgoing setup, active controls, keypad, and incoming ringing. All four call-screen captures and their expected UI labels are required for the fixture to pass; a missing state or PNG fails the check. The keypad screenshot only opens the on-screen keypad; no DTMF is entered. These captures do not start call control, Telecom, audio, or carrier calling. No SMS is sent or received, and the incoming UI is not answered.

## Screenshot stages to review

Review the output Markdown and PNGs for these views:

- Phone tab with synthetic SIM 1 and SIM 2 call-history rows; selecting a row should fill the separate dial pad without starting a call.
- Dial pad with all twelve keys and the contact-permission explanation; direct keypad entry remains visible after history navigation.
- Messages tab with three synthetic threads and SIM 1/SIM 2 badges; an existing thread opens a reply editor, while **写短信** opens a new-message editor with its own SIM picker.
- New-message draft retaining the synthetic recipient and body across IME display, orientation change, fold, and unfold.
- Settings paired-device state and the existing backup/archive entry.
- Synthetic call-screen views for outgoing setup, active controls, keypad, and incoming ringing. These are layout captures only; no real call is created.

The app's shared SMS backup smoke also checks format warnings, password entry, rotation/fold/cutout layout, import preview, confirmation, imported history, and duplicate-import behavior using synthetic archive data.

Successful main runs save original key PNGs, source/APK/artifact hashes and a Chinese Markdown gallery to [the UI verification report](ui-redesign-verification/README.md). Publication requires all UI checks to pass and the tested app and harness to still match main. The initial partial run remains documented separately.

## Status feedback

Outcomes such as "queued", "result unknown, safe to retry", "server unreachable, showing cache" and "SIM mapping changed" are shown once as a Snackbar above the bottom navigation. Each distinct message is announced once; the offline message clears after the next successful sync.
