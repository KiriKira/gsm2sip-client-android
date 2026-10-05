# Host UI navigation and smoke evidence

The host app has three primary destinations: **电话**, **短信**, and **设置**. Dialing and SMS conversations open as separate pages. The layout keeps the same navigation model on compact and foldable windows, with system bars, the display cutout, the keyboard, and the hinge accounted for during resize and rotation.

## Screen map

| Screen | Main content | Smoke anchors |
|---|---|---|
| Phone | Call-history rows; selecting one opens the dial pad with its number filled. The dial-pad floating button opens an empty dial pad. | `main_bottom_navigation`, `tab_phone`, `call_history_search`, `call_history_list`, `call_history_item`, `dialer_open` |
| Dial pad | Destination, contact candidates, SIM selector, 3×4 keypad, and outbound button | `dialer_destination`, `dialer_contact_list`, `dialer_sim_selector`, `dialer_keypad`, `keypad_1` … `keypad_hash`, `dialer_call_button` |
| Messages | SMS threads, latest message, and a SIM badge on each thread | `tab_messages`, `sms_thread_list`, `sms_thread_item`, `sms_thread_sim_badge`, `sms_compose_fab` |
| Conversation/editor | Existing conversation shows the recipient in a read-only heading with reply fields; a new message adds a recipient input. SIM is selected here. | `sms_thread_header`, `sms_conversation_list`, new-message `sms_recipient`, `sms_thread_sim_selector`, `sms_body`, `sms_thread_send` |
| Settings | Server pairing, paired-device state, notification/battery/contacts permissions, and SMS backup/archive entry | `tab_settings`, `pairing_server`, `pairing_code`, `pairing_device_name`, `sms_backup_archive_entry` |

The phone can search the device's contacts for names and numbers. Without Android Contacts permission, the dial pad still accepts direct number entry and displays the permission explanation. The smoke leaves Contacts permission denied and does not read the emulator address book.

After pairing, background SMS synchronization starts automatically; there is no sync or history-backfill switch. Server pairing/device management and the existing **短信备份与归档** entry remain in Settings. Sending a message or placing a call still requires a deliberate user action.

## Automated screenshot route

The `Android KVM UI smoke` workflow builds the debug APK and runs the host smoke on an API 35, 7.6-inch foldable emulator. Pushes that touch the app, fixture, smoke scripts, or workflow start the workflow; `workflow_dispatch` can run it on demand. The fastest supported screenshot route is to push the finished branch, wait for the workflow, and inspect the `gsm2sip-host-android-ui-smoke` artifact.

The workflow runs:

1. `scripts/android-ui-smoke.py --scenario host` for first-run prompts, the three tabs, phone/dial-pad navigation, Settings, synthetic pairing form entry, rotation, fold/unfold, cutout, and the shared SMS backup/archive UI.
2. `scripts/run-native-smoke.sh` for the native engine checks.
3. `scripts/run-paired-ui-fixture.sh` for offline paired-state screenshots using local synthetic session, device, call-history, SIM, and SMS cache records.

The runners emit `summary.md`, JSON manifests, and original PNG/XML captures. The Markdown summaries embed the main screen images; the full artifacts retain all generated screenshots and diagnostics. No PDF is generated. The screenshot report should be updated from a completed run of this workflow; screenshots from the older dashboard UI are not evidence of the three-tab layout.

The paired fixture enables airplane mode and disables Wi-Fi and mobile data before seeding its synthetic paired session. The app may start its automatic remote-messaging foreground service while paired, but it has no working network and the session points only at `ui-smoke.invalid`; cleanup stops app services, removes the synthetic session and database, restores radio settings, and uninstalls the fixture APK. `sipAvailable` is false. The fixture seeds three local SMS threads across SIM 1 and SIM 2. Call-screen captures seed only an in-process `CallSessionCoordinator` render state for outgoing setup, active controls, keypad, and incoming ringing. All four call-screen captures and their expected UI labels are required for the fixture to pass; a missing state or PNG fails the check. The keypad screenshot only opens the on-screen keypad; no DTMF is entered. These captures do not start SIP, Telecom, audio, or carrier calling. No SMS is sent or received, and the incoming UI is not answered.

## Screenshot stages to review

Review the output Markdown and PNGs for these views:

- Phone tab with synthetic SIM 1 and SIM 2 call-history rows; selecting a row should fill the separate dial pad without starting a call.
- Dial pad with all twelve keys and the contact-permission explanation; direct keypad entry remains visible after history navigation.
- Messages tab with three synthetic threads and SIM 1/SIM 2 badges; an existing thread opens a reply editor, while **写短信** opens a new-message editor with its own SIM picker.
- New-message draft retaining the synthetic recipient and body across IME display, orientation change, fold, and unfold.
- Settings paired-device state and the existing backup/archive entry.
- Synthetic call-screen views for outgoing setup, active controls, keypad, and incoming ringing. These are layout captures only; no real call is created.

The app's shared SMS backup smoke also checks format warnings, password entry, rotation/fold/cutout layout, import preview, confirmation, imported history, and duplicate-import behavior using synthetic archive data.
