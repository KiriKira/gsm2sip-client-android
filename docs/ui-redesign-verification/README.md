# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`d7d1d2c2a68e952a405555f8f48ac9d7f26173c8`
- GitHub Actions run：[38228156652](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/38228156652)
- artifact ID：`11738236394`
- artifact digest：`sha256:46ea2d86f14ca914b68c82f8214b8e28daeb289138f718e1e2be77978b90a25c`
- APK：`app-debug.apk`，SHA-256 `b5722f59f4425cae8219550d7350f47bb525f33af87c0ef0b221cf74b474546c`，19664637 字节
- artifact 内未配对 smoke：`pass`（2026-10-12T09:17:38.171443+00:00）
- artifact 内配对 fixture：`pass`（2026-10-12T09:21:14.848918+00:00）
- 报告生成时间：`2026-10-12T09:21:30Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `899f4ddb29f5ee31d363dbb88e2c47d1d58d90a175a472143f8a2692a40dc6ae` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `5fc46cf1fca3f3df4758a6e18e3c8dd848d234a1092310fae688e9c259270481` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `27fe50abf07ab5dbf26044e72c40566f3a2911e3de56bd5af9d29dbb5ad3199b` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `3bf78686076d78aeb1cd46e6935466bc5447b3e461858f7160af2fd92253dcb2` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `8713101bd72b507a0ad146a082f97dc5bda093cb03ea462c6f9c7a4f0b3cbab2` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `1925526df1aae84968fcbfc1627a403131441404b8a665a51d7a531165de668e` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `e3af307522bc604d0d4220345b94abf9ea9f361778776a69f1ba95abb3033f66` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `d31aaa7759fa686376aa7ddbbdb70b38f43e66ad6d301a49357df71343cb968a` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `82b00c620cae1d989b1cc5edff8bd02f6bdda65d13b060b2ce7c0f3f74e23941` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `1bb7e02c27af2205b1eacd7da04637b4afff414ff59ecd459ac664e45843de91` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `d57a35b486fb24a1935671b1f3fffeb64ef210b3eeccfe7735fcd17e0515578d` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `d9b7c9d7cf6845d9f46e64848922f1cfb82623500b975bccbb545688b95c15ab` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `7f0600fe48e84f0c9f0ef871489b21b8961780aaeb608796007ee951560bdec4` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `8c56cc746d527dce15f2100730829aafa022575d4b09e75cee163cbfe17e4c24` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `c03017d4bb4c5409d4c596df70285a5be5840cae51765373ef3da1952ac37c8e` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `c03017d4bb4c5409d4c596df70285a5be5840cae51765373ef3da1952ac37c8e` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `0a744c9480275e2bec02f89e294a1f4617809f32f4561678d445756b6a5cc9af` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `e860a9be3d8f75c70476b18a4503235973146048251c69b0b7c6b6a1776a9f6c` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `1df14ee9d78a27543563b0bcac3fa44d061fefd8e60569d7bb2c6aeab3941cba` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `1f21740238bedfb9a057ab0515bfbdd041e6c3944a12ff1ec4e056daf46ef502` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `1a746fa6bdf0fb31926562758c74c5df3816793c71c0a9e2fd5c900c04690a1a` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `1a746fa6bdf0fb31926562758c74c5df3816793c71c0a9e2fd5c900c04690a1a` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `465d7b00ff8e0d10aca00a718612a33f7d49a675853a2036577720a6130eb0bf` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `2b2343b45c798b4470f7578e474d4378dd127325575ea41d315f7098ceafee96` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `166d67aa8eef4cd25170b9f035533d116e910b626acc1b0b694a0cbbd14b3f5b` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `1995c45b2a42ce149892de7da6e1d9dbf507e87a81d9c8531f7b4f64b020857b` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `cef7577486d0335cf5e7edbb80e6d7ac3bd923ac66e973d8a9ba38dd1c801a79` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `9581a631361aa2432b9732784c9dbac1f318ce5ab5fa19f175668bb738d5c8e3` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `2ecf7ea942db26c44439a69ecde1ed8689d18843bb3f69c80a85a04c5ce666c5` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `fc6d06aea6b8c5c5c0960c7131e9b45803266a6398660ecc949596230253c65a` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `07b6918e2ef9c69c3665159b18f89d1a12ed3cc99bc38407510e0c8f8b06a7cf` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `bf507fc3c78431389704f5b0931e09a6fbce8228d723e95c9006e9ad80c8fef2` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `9b02b553082e4cc54a525246988499304d0cd60867b5a39e5b82c8c603cb164d` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `9ba6ba12ba67893a1fe9ccd9256790fd3dc05d48f3a92a54abcc02ac5db351bf` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `2858d7ac6bfefc8c9afb1babbcc9109168f37751112884668233d0d2329ae6b2` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `3165839faff0bf7a0d89cb46e28d6207201b5b8181734b2c75ad7e2e019a8f10` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `0f05c55301b9a24cb38de9648cbc6cbdba34896d3a351afa60d20c9a12dc9895` |

## 截图

### 未配对界面

#### 三标签导航

![三标签导航](images/host_navigation_initial.png)

### 电话与拨号

#### 未配对电话记录页

![未配对电话记录页](images/host_call_history.png)

#### 独立拨号键盘

![独立拨号键盘](images/host_dialpad.png)

### 短信

#### 短信会话列表

![短信会话列表](images/host_sms_thread_list.png)

### 设置

#### 设置与配对表单

![设置与配对表单](images/host_settings_tab.png)

#### 设置中的短信备份入口

![设置中的短信备份入口](images/host_settings_backup_entry.png)

### 窗口适配

#### 主界面合拢窗口

![主界面合拢窗口](images/folded_ui.png)

#### 主界面展开窗口

![主界面展开窗口](images/unfolded_ui.png)

#### 显示挖孔时的主界面

![显示挖孔时的主界面](images/hole_cutout_enabled.png)

### 短信备份

#### 滚动后可见的备份入口

![滚动后可见的备份入口](images/main-backup-entry-host.png)

#### 短信备份与归档页面

![短信备份与归档页面](images/backup-overview.png)

#### 备份页面横屏

![备份页面横屏](images/backup-rotation-landscape.png)

#### 备份页面竖屏

![备份页面竖屏](images/backup-rotation-portrait.png)

#### 备份页面合拢

![备份页面合拢](images/backup-folded.png)

#### 备份页面展开

![备份页面展开](images/backup-unfolded.png)

#### 挖孔安全区中的备份页面

![挖孔安全区中的备份页面](images/backup-cutout.png)

#### 合成短信归档导入预览

![合成短信归档导入预览](images/backup-import-preview.png)

#### 导入后的归档历史

![导入后的归档历史](images/backup-imported-history.png)

### 配对数据界面

#### 配对状态下的三标签

![配对状态下的三标签](images/paired-fixture/paired_three_tabs.png)

#### 双 SIM 合成通话记录

![双 SIM 合成通话记录](images/paired-fixture/paired_call_history.png)

#### 从通话记录预填号码的拨号页

![从通话记录预填号码的拨号页](images/paired-fixture/paired_dialpad_from_history.png)

#### 配对状态下的独立拨号键盘

![配对状态下的独立拨号键盘](images/paired-fixture/paired_dialpad.png)

#### 三条合成短信会话

![三条合成短信会话](images/paired-fixture/paired_sms_threads.png)

#### 合拢窗口中的短信会话

![合拢窗口中的短信会话](images/paired-fixture/paired_sms_threads_folded.png)

#### 展开窗口中的短信会话

![展开窗口中的短信会话](images/paired-fixture/paired_sms_threads_unfolded.png)

#### 既有短信对话与回复框

![既有短信对话与回复框](images/paired-fixture/paired_sms_conversation.png)

#### 新短信编辑页

![新短信编辑页](images/paired-fixture/paired_new_message.png)

#### 选择 SIM 2 的新短信页

![选择 SIM 2 的新短信页](images/paired-fixture/paired_new_message_sim_b.png)

#### 横屏且键盘显示时的短信草稿

![横屏且键盘显示时的短信草稿](images/paired-fixture/paired_new_message_landscape.png)

#### 合拢窗口中的短信草稿

![合拢窗口中的短信草稿](images/paired-fixture/paired_new_message_folded.png)

#### 展开窗口中的短信草稿

![展开窗口中的短信草稿](images/paired-fixture/paired_new_message_unfolded.png)

#### 配对状态下的设置

![配对状态下的设置](images/paired-fixture/paired_settings.png)

#### 配对设置中的短信备份入口

![配对设置中的短信备份入口](images/paired-fixture/paired_settings_backup.png)

### 合成通话界面

#### 合成出站呼叫准备界面

![合成出站呼叫准备界面](images/paired-fixture/call-synthetic-dialing.png)

#### 合成通话中界面

![合成通话中界面](images/paired-fixture/call-synthetic-active.png)

#### 合成通话键盘界面

![合成通话键盘界面](images/paired-fixture/call-synthetic-keypad.png)

#### 合成来电界面

![合成来电界面](images/paired-fixture/call-synthetic-incoming.png)

## 结果文件校验

| artifact 文件 | SHA-256 |
|---|---|
| `results.json` | `6b78339b58293a8466f6c105c09563a00052f29fa277ea9669acc43322bf8518` |
| `screenshot_manifest.json` | `6bd3504d163d1711312e66df30731ad13c5de0a65c4650450701e91c8a7e64af` |
| `paired-fixture/results.json` | `86da61cfa906156f481c5185803ae79775642db821e9117d727d4a4dd2224bc3` |
| `paired-fixture/fixture_manifest.json` | `11d1089515ea898ff0273e61d0b04b01af6f27ac00a132406260912e7b0cb6c3` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
